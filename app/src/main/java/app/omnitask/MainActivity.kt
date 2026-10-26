package app.omnitask

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import app.omnitask.data.BehaviorLog
import app.omnitask.data.TaskLine
import app.omnitask.model.Appearance
import app.omnitask.model.Behavior
import app.omnitask.model.Lang
import app.omnitask.model.load
import app.omnitask.model.Shared
import app.omnitask.ui.OmniTaskApp
import app.omnitask.ui.OmniTheme
import app.omnitask.ui.MonthRequest
import app.omnitask.ui.QuickAddRequest
import app.omnitask.ui.TaskViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import app.omnitask.time.*

class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        Lang.load(this)
        Appearance.load(this)
        Appearance.systemDark = systemDark()
        setContent {
            // Follows the phone's dark mode while the app is open, and keeps the bar icons readable.
            val phoneDark = isSystemInDarkTheme()
            SideEffect { Appearance.systemDark = phoneDark }
            val dark = Appearance.dark
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
                onDispose { }
            }
            OmniTheme { OmniTaskApp(viewModel) }
        }
        handle(intent)
        CrashLog.read(this)?.let { viewModel.showCrash(it) }
    }

    private fun systemDark() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Widgets and notifications open the app with an action: type a task, say it, go to the assistant, or note stuck tasks. */
    private fun handle(intent: Intent?) {
        // Opened from a task reminder: noted in the behavior log, so the assistant learns which reminders work.
        intent?.getStringExtra(EXTRA_REMINDED)?.let { raw ->
            TaskLine.parse(raw)?.let { BehaviorLog.record(this, Behavior.of(Behavior.OPEN, it, LocalDateTime.now(), BehaviorLog.SRC)) }
            intent?.removeExtra(EXTRA_REMINDED)
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val shared = Shared.read(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
            viewModel.requestQuickAdd(QuickAddRequest(text = shared.title, link = shared.link))
            intent.action = null
            return
        }
        when (intent?.getStringExtra(EXTRA_ACTION)) {
            ACTION_ADD -> viewModel.requestQuickAdd(QuickAddRequest())
            ACTION_VOICE -> viewModel.requestQuickAdd(QuickAddRequest(voice = true))
            ACTION_ASSISTANT -> viewModel.requestQuickAdd(QuickAddRequest(assistant = true))
            ACTION_STUCK -> viewModel.requestStuck(true)
            ACTION_CALENDAR -> viewModel.requestCalendar(
                intent.getStringExtra(EXTRA_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
                reveal = true,
            )
            ACTION_MONTH -> viewModel.requestMonth(
                MonthRequest(
                    intent.getStringExtra(EXTRA_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
                    intent.getBooleanExtra(EXTRA_PICK, false),
                ),
            )
            else -> return
        }
        intent.removeExtra(EXTRA_ACTION)
    }

    companion object {
        const val EXTRA_ACTION = "app.omnitask.ACTION"
        const val EXTRA_REMINDED = "app.omnitask.REMINDED"
        const val ACTION_ADD = "add"
        const val ACTION_VOICE = "voice"
        const val ACTION_ASSISTANT = "assistant"
        /** From the weekly review: ask why each long-open task is still open. */
        const val ACTION_STUCK = "stuck"
        /** From the month widget: the frames and emoji page, at [EXTRA_DAY], that day selected with [EXTRA_PICK]. */
        const val ACTION_MONTH = "month"
        /** From the month widget: the Tasks tab's calendar at [EXTRA_DAY], that day's work listed. */
        const val ACTION_CALENDAR = "calendar"
        const val EXTRA_DAY = "app.omnitask.DAY"
        const val EXTRA_PICK = "app.omnitask.PICK"
    }

    // Obsidian or the sync app may have changed files while the app was in the background.
    override fun onResume() {
        super.onResume()
        viewModel.resumed()
    }
}
