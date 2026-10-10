@file:OptIn(ExperimentalJsExport::class)

package app.omnitask.web

/** The entry points of the Assistant page; see [WebAssistant]. Kept apart from [WebApi] so the two pages' work does not collide. */
@JsExport
object WebAssistantApi {
    fun profile(text: String?, today: String): String = WebAssistant.profile(text, today)

    fun profileApply(text: String?, change: String, today: String): String = WebAssistant.profileApply(text, change, today)

    fun insight(fileKey: String, path: String, text: String, state: String): String = WebAssistant.insight(fileKey, path, text, state)

    fun insightYes(fileKey: String, path: String, text: String, state: String): String = WebAssistant.insightYes(fileKey, path, text, state)

    fun kindOf(title: String): String = WebAssistant.kindOf(title)

    fun route(request: String, today: String): String = WebAssistant.route(request, today)

    fun slots(fileKey: String, path: String, text: String, state: String, request: String, minutes: Int): String =
        WebAssistant.slots(fileKey, path, text, state, request, minutes)

    fun rank(fileKey: String, path: String, text: String, state: String): String = WebAssistant.rank(fileKey, path, text, state)

    fun today(fileKey: String, path: String, text: String, state: String): String = WebAssistant.today(fileKey, path, text, state)

    fun agenda(fileKey: String, path: String, text: String, state: String, from: String, to: String): String =
        WebAssistant.agenda(fileKey, path, text, state, from, to)

    fun planRange(fileKey: String, path: String, text: String, state: String, from: String, to: String): String =
        WebAssistant.planRange(fileKey, path, text, state, from, to)

    fun weekly(fileKey: String, path: String, text: String, state: String): String = WebAssistant.weekly(fileKey, path, text, state)

    fun snapshot(fileKey: String, path: String, text: String, state: String): String = WebAssistant.snapshot(fileKey, path, text, state)

    fun slotLine(title: String, day: String, start: String?, today: String): String = WebAssistant.slotLine(title, day, start, today)

    fun addLines(text: String, lines: String): String = WebAssistant.addLines(text, lines)

    fun sweep(live: String, archive: String, days: Int, today: String): String = WebAssistant.sweep(live, archive, days, today)

    fun scheduleTasks(text: String, items: String): String = WebAssistant.scheduleTasks(text, items)
}
