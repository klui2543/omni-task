package app.omnitask.web

import app.omnitask.notify.AlarmPlan
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Reminders for the web, which cannot set alarms: while the page is open it asks which task reminders ring between two
 * moments, by the phone's own rules ([AlarmPlan.taskAlarms]: open tasks with a ⏰ or 🎯 time, parked branches left out),
 * and how many tasks are overdue or due today, for the app's badge.
 */
object WebNotify {

    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }

    /** [since] and [until] (local `yyyy-MM-ddTHH:mm`): the reminders after the first and no later than the second. */
    @Serializable
    data class StateIn(
        val now: String,
        val since: String,
        val until: String,
        /** Branch states as Android saves them; tasks in a parked branch stay quiet, as on the phone. */
        val branches: List<String> = emptyList(),
    )

    /** One reminder: [id] is the phone's alarm id (`task:<note>:<line>`), [key] the task's key as the page knows it. */
    @Serializable
    data class ReminderOut(val id: String, val key: String, val raw: String, val title: String, val text: String, val at: String)

    @Serializable
    data class Out(val badge: Int, val reminders: List<ReminderOut>)

    fun reminders(fileKey: String, path: String, text: String, stateJson: String): String {
        val s = json.decodeFromString<StateIn>(stateJson)
        val now = LocalDateTime.parse(s.now)
        val today = now.date
        val tasks = WebCore.withoutParked(WebNotes.tasks(fileKey, path, text, today), s.branches)
        val alarms = AlarmPlan.taskAlarms(tasks, LocalDateTime.parse(s.since), LocalDateTime.parse(s.until))
        val out = alarms.mapNotNull { a ->
            val t = tasks.firstOrNull { it.isOpen && it.raw == a.taskRaw && it.fileUri == a.fileUri && it.reminderAt == a.at } ?: return@mapNotNull null
            ReminderOut(a.id, t.key, t.raw, a.title, a.text, a.at.toString())
        }.distinctBy { it.id + "@" + it.at }.sortedBy { it.at }
        val badge = tasks.count { t -> t.isOpen && !t.todayCopy && t.due?.let { it <= today } == true }
        return json.encodeToString(Out(badge, out))
    }
}
