package app.omnitask.web

import app.omnitask.data.TaskLine
import app.omnitask.data.VaultText
import app.omnitask.model.Branches
import app.omnitask.model.OmniList
import app.omnitask.model.Priority
import app.omnitask.model.Projects
import app.omnitask.model.Ygdrasil
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Project Ygdrasil for the web: every project in one tree, grown and laid out by [Ygdrasil] as on Android. The page
 * only draws what comes out: each spot with its place, and each line as a ready SVG path.
 */
object WebYgdrasil {

    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }

    /** The filter as the page keeps it; names are [Ygdrasil.Show], [Ygdrasil.Dates] and [Priority] entries. */
    @Serializable
    data class FilterIn(
        val show: String = "OPEN",
        val dates: String = "ANY",
        val priorities: List<String> = emptyList(),
        val blockedOnly: Boolean = false,
        val starredOnly: Boolean = false,
        val parked: Boolean = false,
        val links: Boolean = true,
        val text: String = "",
    ) {
        fun toFilter() = Ygdrasil.Filter(
            show = Ygdrasil.Show.entries.firstOrNull { it.name == show } ?: Ygdrasil.Show.OPEN,
            dates = Ygdrasil.Dates.entries.firstOrNull { it.name == dates } ?: Ygdrasil.Dates.ANY,
            priorities = priorities.mapNotNull { n -> Priority.entries.firstOrNull { it.name == n } }.toSet(),
            blockedOnly = blockedOnly, starredOnly = starredOnly, parked = parked, links = links, text = text,
        )
    }

    @Serializable
    data class SpotOut(
        /** TRUNK, LIMB, TASK or MORE. */
        val kind: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        /** The limb's tag (a task's or "more" line's: the limb holding it). */
        val tag: String? = null,
        val project: String? = null,
        val path: String? = null,
        val name: String? = null,
        val state: String? = null,
        /** Tasks done and in all of the limb, filter aside. */
        val done: Int = 0,
        val total: Int = 0,
        /** The task's key. */
        val key: String? = null,
        /** "And N more", the tasks under a folded limb, or every task shown (the trunk). */
        val count: Int = 0,
        val folded: Boolean = false,
    )

    /** One line: BRANCH (coloured by [state]), TASK, LINK or LINK_ACROSS, as an SVG path. */
    @Serializable
    data class EdgeOut(
        val kind: String,
        val d: String,
        val state: String? = null,
        /** For a wait: the keys of the task waited for and of the one waiting. */
        val blocker: String? = null,
        val waiting: String? = null,
    )

    @Serializable
    data class Out(
        val spots: List<SpotOut>,
        val edges: List<EdgeOut>,
        val width: Float,
        val height: Float,
        val matched: Int,
        val worlds: Int,
        val links: Int,
        /** Every limb's tag, for folding them all. */
        val tags: List<String>,
    )

    private fun n(v: Float): String = ((v * 10).toInt() / 10.0).toString()

    /**
     * The tree for the TaskForge note at [text], the list notes in [notesJson] (their tags are not projects), what this
     * device chose ([stateJson], as for [WebProjects.build]), the filter and the folded limbs' tags.
     */
    fun build(fileKey: String, path: String, text: String, notesJson: String, stateJson: String, filterJson: String, foldedJson: String, today: String): String {
        val st = json.decodeFromString<WebProjects.StateIn>(stateJson)
        val notes = json.decodeFromString<List<WebProjects.NoteIn>>(notesJson)
        val lists = notes.mapNotNull { OmniList.parse(it.path, it.text) }
        Projects.ignoredTags = lists.flatMap { listOf(it.tag) + it.categories }.toSet() + st.kindTags

        val filter = json.decodeFromString<FilterIn>(filterJson).toFilter()
        val folded = json.decodeFromString<List<String>>(foldedJson).toSet()
        val tree = Ygdrasil.grow(
            WebNotes.tasks(fileKey, path, text), Branches.parse(st.branches.toSet()), LocalDate.parse(today), filter,
            st.starred.toSet(), st.order,
        )
        val layout = Ygdrasil.layout(tree, folded)
        return json.encodeToString(
            Out(
                spots = layout.spots.map { s ->
                    val node = s.limb?.node
                    SpotOut(
                        kind = s.kind.name, x = s.x, y = s.y, w = s.w, h = s.h,
                        tag = node?.tag, project = node?.project, path = node?.path, name = node?.name, state = node?.state?.name,
                        done = node?.done ?: 0, total = node?.all?.size ?: 0, key = s.task?.key, count = s.count, folded = s.folded,
                    )
                },
                edges = layout.edges.map { e ->
                    EdgeOut(
                        e.kind.name, "M${n(e.x1)} ${n(e.y1)} C${n(e.cx1)} ${n(e.cy1)} ${n(e.cx2)} ${n(e.cy2)} ${n(e.x2)} ${n(e.y2)}", e.state?.name,
                        e.blocker?.key, e.waiting?.key,
                    )
                },
                width = layout.width, height = layout.height, matched = tree.matched, worlds = tree.worlds.size, links = tree.links.size,
                tags = tree.worlds.flatMap { w -> w.flatten().map { it.node.tag } },
            ),
        )
    }

    // ---- Waits drawn by hand (an arrow from one task to another in Excalidraw) ----

    @Serializable
    data class Ref(val raw: String, val lineIndex: Int)

    @Serializable
    data class LinkOut(val ok: Boolean, val text: String? = null, val error: String? = null, val id: String? = null)

    private fun find(lines: List<String>, ref: Ref): Int =
        if (lines.getOrNull(ref.lineIndex) == ref.raw) ref.lineIndex else lines.indexOf(ref.raw)

    /**
     * [waiting] waits for [blocker]: the blocker gets a `🆔` (a new one when it has none) and the waiting task names it
     * in `⛔`. Refused when either line changed (`conflict`), when it is the same task (`self`), or when the blocker
     * already waits on the waiting task, directly or further along (`cycle`).
     */
    fun link(text: String, blockerJson: String, waitingJson: String): String {
        val separator = VaultText.separatorOf(text)
        val lines = text.split(separator).toMutableList()
        val b = find(lines, json.decodeFromString<Ref>(blockerJson))
        val w = find(lines, json.decodeFromString<Ref>(waitingJson))
        if (b < 0 || w < 0) return json.encodeToString(LinkOut(false, error = "conflict"))
        if (b == w) return json.encodeToString(LinkOut(false, error = "self"))
        val parsed = VaultText.parseFile("", "", text)
        val byLine = parsed.associateBy { it.lineIndex }
        val blocker = byLine[b] ?: return json.encodeToString(LinkOut(false, error = "conflict"))
        val waiting = byLine[w] ?: return json.encodeToString(LinkOut(false, error = "conflict"))
        val byId = parsed.filter { it.id != null }.associateBy { it.id!! }
        // Does the blocker already wait, step by step, on the waiting task?
        if (waiting.id != null) {
            val seen = HashSet<String>()
            val todo = ArrayDeque(blocker.dependsOn)
            while (todo.isNotEmpty()) {
                val id = todo.removeFirst()
                if (id == waiting.id) return json.encodeToString(LinkOut(false, error = "cycle"))
                if (seen.add(id)) byId[id]?.let { todo.addAll(it.dependsOn) }
            }
        }
        val taken = byId.keys
        val id = blocker.id ?: run {
            var made: String
            do { made = "o" + (1..5).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("") } while (made in taken)
            made
        }
        if (blocker.id == null) lines[b] = TaskLine.setId(lines[b], id)
        if (id !in waiting.dependsOn) lines[w] = TaskLine.setDependsOn(lines[w], waiting.dependsOn + id)
        return json.encodeToString(LinkOut(true, lines.joinToString(separator), id = id))
    }

    /** The wait drawn from [blocker] to [waiting] is taken away: the `⛔` goes, the blocker keeps its `🆔`. */
    fun unlink(text: String, blockerJson: String, waitingJson: String): String {
        val separator = VaultText.separatorOf(text)
        val lines = text.split(separator).toMutableList()
        val b = find(lines, json.decodeFromString<Ref>(blockerJson))
        val w = find(lines, json.decodeFromString<Ref>(waitingJson))
        if (b < 0 || w < 0) return json.encodeToString(LinkOut(false, error = "conflict"))
        val parsed = VaultText.parseFile("", "", text).associateBy { it.lineIndex }
        val id = parsed[b]?.id ?: return json.encodeToString(LinkOut(false, error = "none"))
        val waiting = parsed[w] ?: return json.encodeToString(LinkOut(false, error = "conflict"))
        if (id !in waiting.dependsOn) return json.encodeToString(LinkOut(false, error = "none"))
        lines[w] = TaskLine.setDependsOn(lines[w], waiting.dependsOn - id)
        return json.encodeToString(LinkOut(true, lines.joinToString(separator), id = id))
    }

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
}
