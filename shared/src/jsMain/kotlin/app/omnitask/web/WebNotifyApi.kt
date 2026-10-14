@file:OptIn(ExperimentalJsExport::class)

package app.omnitask.web

/** The entry points of the web's reminders; see [WebNotify]. */
@JsExport
object WebNotifyApi {
    fun reminders(fileKey: String, path: String, text: String, state: String): String = WebNotify.reminders(fileKey, path, text, state)
}
