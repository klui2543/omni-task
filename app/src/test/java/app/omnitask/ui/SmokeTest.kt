package app.omnitask.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opens the app's screens and sheets on the JVM, so a crash shows up in CI before it reaches the phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmokeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun vm(): TaskViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // A vault is "picked" so the app shows its screens instead of the welcome page; reading it simply fails.
        app.getSharedPreferences("omnitask", Context.MODE_PRIVATE).edit()
            .putString("vault", "content://com.android.externalstorage.documents/tree/primary%3AVault").commit()
        return TaskViewModel(app)
    }

    @Test
    fun addButtonOpensQuickAdd() {
        val vm = vm()
        rule.setContent { OmniTheme { OmniTaskApp(vm) } }
        rule.onNodeWithContentDescription("เพิ่มงาน").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("เพิ่มงาน").fetchSemanticsNodes().isNotEmpty().let { check(it) }
    }

    @Test
    fun everyScreenRenders() {
        val vm = vm()
        rule.setContent { OmniTheme { OmniTaskApp(vm) } }
        listOf("งาน", "มุมมอง", "โปรเจกต์", "ผู้ช่วย", "โฟกัส").forEach { label ->
            rule.onAllNodesWithText(label).fetchSemanticsNodes().let { check(it.isNotEmpty()) { "no $label" } }
            rule.onAllNodesWithText(label)[0].performClick()
            rule.waitForIdle()
        }
    }
}
