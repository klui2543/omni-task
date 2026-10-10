package app.omnitask.web

import app.omnitask.data.TaskLine
import app.omnitask.model.CustomKind
import app.omnitask.model.Focus
import app.omnitask.model.Task
import app.omnitask.model.TaskKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The task kinds the owner manages: the built-in ones (#รอ, #อนาคต, #สักวัน) that can be hidden, and the kinds
 * they made (a name, an emoji and a tag). The choices cross as one small JSON text that the web keeps on the
 * device; custom kinds are in Android's own "name\temoji\ttag" form so they can travel to omni-settings.json later.
 */
object WebKinds {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Serializable
    data class StateIn(val custom: List<String> = emptyList(), val hidden: List<String> = emptyList())

    @Serializable
    data class BuiltinOut(val id: String, val label: String, val tag: String?, val hidden: Boolean)

    @Serializable
    data class CustomOut(val name: String, val emoji: String, val tag: String, val label: String)

    /** The kinds as the Settings page lists them; [state] is the cleaned-up choice to keep. */
    @Serializable
    data class ViewOut(val state: StateIn, val builtin: List<BuiltinOut>, val custom: List<CustomOut>)

    @Serializable
    data class AddOut(val ok: Boolean, val error: String? = null, val tag: String = "", val view: ViewOut)

    /** What the edit panel offers for one task: the kinds it may pick and the one it has. */
    @Serializable
    data class PickerOut(
        /** A built-in kind id (NORMAL when the task has one of the owner's own kinds or none). */
        val current: String,
        val customTag: String?,
        val who: String?,
        val hint: String?,
        val builtin: List<BuiltinOut>,
        val custom: List<CustomOut>,
    )

    private fun read(stateJson: String): Pair<List<CustomKind>, Set<TaskKind>> {
        val s = runCatching { json.decodeFromString<StateIn>(stateJson) }.getOrDefault(StateIn())
        val hidden = s.hidden.mapNotNull { n -> TaskKind.entries.firstOrNull { it.name == n } }.filter { it != TaskKind.NORMAL }.toSet()
        return CustomKind.parse(s.custom.toSet()) to hidden
    }

    private fun stateOf(custom: List<CustomKind>, hidden: Set<TaskKind>) = StateIn(
        CustomKind.encode(custom.sortedBy { it.name.lowercase() }).toList(),
        TaskKind.entries.filter { it in hidden }.map { it.name },
    )

    private fun builtin(hidden: Set<TaskKind>) = TaskKind.entries.map { BuiltinOut(it.name, it.label, it.tag, it in hidden) }

    private fun customOut(custom: List<CustomKind>) = custom.map { CustomOut(it.name, it.emoji, it.tag, it.label) }

    private fun view(custom: List<CustomKind>, hidden: Set<TaskKind>) =
        ViewOut(stateOf(custom, hidden), builtin(hidden), customOut(custom))

    fun view(stateJson: String): String {
        val (custom, hidden) = read(stateJson)
        return json.encodeToString(view(custom, hidden))
    }

    /** A new kind of [name] with [emoji]; refused when the name leaves no tag or the tag is taken (any letter case). */
    fun add(stateJson: String, name: String, emoji: String): String {
        val (custom, hidden) = read(stateJson)
        val tag = CustomKind.tagFor(name)
        val taken = custom.any { it.tag.equals(tag, ignoreCase = true) } ||
            TaskKind.entries.mapNotNull { it.tag }.any { it.equals(tag, ignoreCase = true) }
        val error = when {
            tag.isEmpty() -> "empty"
            taken -> "taken"
            else -> null
        }
        val next = if (error == null) custom + CustomKind(name.trim(), emoji.trim(), tag) else custom
        return json.encodeToString(AddOut(error == null, error, tag, view(next.sortedBy { it.name.lowercase() }, hidden)))
    }

    /** Forgets a kind; the tags already on tasks stay in the vault as ordinary tags. */
    fun remove(stateJson: String, tag: String): String {
        val (custom, hidden) = read(stateJson)
        return json.encodeToString(view(custom.filter { !it.tag.equals(tag, ignoreCase = true) }, hidden))
    }

    /** Hides a built-in kind from the choices and its card on Focus, or brings it back. */
    fun toggleHidden(stateJson: String, kind: String): String {
        val (custom, hidden) = read(stateJson)
        val k = TaskKind.entries.firstOrNull { it.name == kind && it != TaskKind.NORMAL } ?: return json.encodeToString(view(custom, hidden))
        return json.encodeToString(view(custom, if (k in hidden) hidden - k else hidden + k))
    }

    /**
     * The kinds the edit panel offers for [raw] (a task line): every built-in one except a hidden one the task
     * does not have, then the owner's own, as Android's kind field does.
     */
    fun picker(stateJson: String, raw: String): String {
        val (custom, hidden) = read(stateJson)
        val task = TaskLine.parse(raw)
        val own = task?.let { CustomKind.of(it, custom) }
        val kind = if (own != null || task == null) TaskKind.NORMAL else TaskKind.of(task)
        val who = task?.takeIf { kind == TaskKind.WAITING }?.let { Focus.waitingFor(it) }
        val hint = when (kind) {
            TaskKind.NORMAL -> null
            TaskKind.WAITING -> (who?.let { "$it รออยู่" } ?: "มีคนรออยู่") + " ขึ้นในการ์ด \"คนรออยู่\" ตามที่รอนานสุด"
            TaskKind.FUTURE -> "หมุนเวียนขึ้นหน้าโฟกัสวันละงาน ทบทวนทุก 14 วัน"
            TaskKind.SOMEDAY -> "ไม่ขึ้นในรายการหลัก แต่จะกลับมาให้ทบทวนทุก 30 วัน ไม่หายไปไหน"
        }
        val offered = builtin(hidden).filter { it.id == TaskKind.NORMAL.name || it.id == kind.name || !it.hidden }
        return json.encodeToString(PickerOut(kind.name, own?.tag, who, hint, offered, customOut(custom)))
    }

    /** The task line without any kind tag, built-in (also `#รอ/name`) or the owner's own. */
    internal fun cleared(task: Task, raw: String, customTags: List<String>): String {
        val own = customTags.map { it.lowercase() }
        val tags = TaskKind.kindTags(task) + task.tags.filter { it.lowercase() in own }
        return tags.fold(raw) { acc, tag -> TaskLine.removeTag(acc, tag) }
    }

    /** The task line with [kind]'s tag in place of any other kind; waiting can name who ([who]). */
    internal fun withKind(task: Task, raw: String, kind: TaskKind, who: String?, customTags: List<String>): String {
        val line = cleared(task, raw, customTags)
        val tag = kind.tag ?: return line
        return TaskLine.addTag(line, if (kind == TaskKind.WAITING && !who.isNullOrBlank()) "$tag/${who.trim()}" else tag)
    }

    internal fun withCustomKind(task: Task, raw: String, tag: String, customTags: List<String>): String =
        TaskLine.addTag(cleared(task, raw, customTags), tag)
}
