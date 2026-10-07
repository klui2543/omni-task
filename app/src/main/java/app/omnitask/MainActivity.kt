package app.omnitask

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.omnitask.model.Lang
import app.omnitask.ui.OmniTaskApp
import app.omnitask.ui.OmniTheme
import app.omnitask.ui.QuickAddRequest
import app.omnitask.ui.TaskViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.load(this)
        // The app is always dark, so the system bars use light icons on a transparent bar.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            OmniTheme { OmniTaskApp(viewModel) }
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Widgets open the app with an action: type a task, say it, or go straight to the assistant. */
    private fun handle(intent: Intent?) {
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
