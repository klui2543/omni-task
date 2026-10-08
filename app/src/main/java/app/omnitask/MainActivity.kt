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
import app.omnitask.model.Appearance
import app.omnitask.model.Lang
import app.omnitask.model.load
import app.omnitask.model.Shared
import app.omnitask.ui.OmniTaskApp
import app.omnitask.ui.OmniTheme
import app.omnitask.ui.QuickAddRequest
import app.omnitask.ui.TaskViewModel

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

    /** Widgets open the app with an action: type a task, say it, or go straight to the assistant. */
    private fun handle(intent: Intent?) {
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
            else -> return
        }
        intent.removeExtra(EXTRA_ACTION)
    }

    companion object {
        const val EXTRA_ACTION = "app.omnitask.ACTION"
        const val ACTION_ADD = "add"
        const val ACTION_VOICE = "voice"
        const val ACTION_ASSISTANT = "assistant"
    }

    // Obsidian or the sync app may have changed files while the app was in the background.
    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }
}
