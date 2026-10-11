package app.omnitask.web

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
    data class EdgeOut(val kind: String, val d: String, val state: String? = null)

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
                    EdgeOut(e.kind.name, "M${n(e.x1)} ${n(e.y1)} C${n(e.cx1)} ${n(e.cy1)} ${n(e.cx2)} ${n(e.cy2)} ${n(e.x2)} ${n(e.y2)}", e.state?.name)
                },
                width = layout.width, height = layout.height, matched = tree.matched, worlds = tree.worlds.size, links = tree.links.size,
                tags = tree.worlds.flatMap { w -> w.flatten().map { it.node.tag } },
            ),
        )
    }
}
