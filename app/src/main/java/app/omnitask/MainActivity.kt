package app.omnitask

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
    }

    // Obsidian or the sync app may have changed files while the app was in the background.
    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }
}
