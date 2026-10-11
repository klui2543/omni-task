package app.omnitask.web

import app.omnitask.model.OmniList
import app.omnitask.model.Priority
import app.omnitask.model.QuickAdd
import app.omnitask.ui.ListEmoji
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The quick-add sheet for the web, as Android's: what the sentence is read as (shown as chips before saving) and,
 * while a "#word" is being typed, the lists, their categories and the tags it can turn into.
 */
object WebQuickAdd {

    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }

    @Serializable
    data class ListOut(val path: String, val name: String, val emoji: String, val categories: List<String>)

    /** One choice for the "#word": a list (with one of its categories, or none) or a tag. */
    @Serializable
    data class PickOut(val list: ListOut? = null, val category: String? = null, val tag: String? = null)

    @Serializable
    data class Out(
        val title: String,
        val due: String? = null,
        /** The reminder time, HH:mm. */
        val time: String? = null,
        val tags: List<String> = emptyList(),
        val priority: String = Priority.NONE.name,
        val priorityLabel: String? = null,
        /** The "#word" being typed (without the #), or null. */
        val token: String? = null,
        val picks: List<PickOut> = emptyList(),
    )

    private fun out(l: OmniList) = ListOut(l.path, l.name, ListEmoji.of(l.icon), l.categories)

    /** [notesJson]: the list notes (`{path, text}`); [tagsJson]: the tags in use, most used first. */
    fun preview(sentence: String, today: String, notesJson: String, tagsJson: String): String {
        val draft = QuickAdd.parse(sentence, LocalDate.parse(today))
        val token = QuickAdd.hashToken(sentence)
        val picks = token?.let { t ->
            val lists = json.decodeFromString<List<WebProjects.NoteIn>>(notesJson).mapNotNull { OmniList.parse(it.path, it.text) }
            QuickAdd.hashPicks(t, lists, json.decodeFromString<List<String>>(tagsJson)).map { p ->
                when (p) {
                    is QuickAdd.HashPick.ToList -> PickOut(list = out(p.list), category = p.category)
                    is QuickAdd.HashPick.ToTag -> PickOut(tag = p.tag)
                }
            }
        }.orEmpty()
        return json.encodeToString(
            Out(
                title = draft.title, due = draft.due?.toString(),
                time = draft.time?.let { app.omnitask.time.hhmm(it.hour, it.minute) },
                tags = draft.tags, priority = draft.priority.name,
                priorityLabel = draft.priority.takeIf { it != Priority.NONE }?.label,
                token = token, picks = picks,
            ),
        )
    }
}
