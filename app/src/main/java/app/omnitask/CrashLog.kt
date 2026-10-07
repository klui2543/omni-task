package app.omnitask

import android.content.Context
import java.io.File
import java.time.LocalDateTime

/**
 * Keeps the last crash on the phone, so the next launch can show it and the owner can pass it on.
 * Without this a crash on the owner's phone leaves nothing to debug from.
 */
object CrashLog {

    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                file(app).writeText("Omni Task $version\n${LocalDateTime.now()}\n\n${error.stackTraceToString()}")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last crash report, if the app crashed since it was last shown. */
    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        file(context).delete()
    }
}
