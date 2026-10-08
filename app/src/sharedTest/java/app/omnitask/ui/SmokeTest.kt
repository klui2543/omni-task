package app.omnitask.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the app's screens and sheets, so a crash shows up in CI before it reaches the phone. It runs twice:
 * on the JVM with Robolectric, and on an Android emulator, which catches what only Android's own runtime
 * trips on (its regex engine, for one).
 */
@RunWith(AndroidJUnit4::class)
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
        // The sentence is read as it is typed: the time shows as a chip.
        rule.onAllNodes(hasSetTextAction()).onLast().performTextInput("ส่งรายงาน พรุ่งนี้ 9:00 #งาน !!")
        rule.waitForIdle()
        check(rule.onAllNodesWithText("เตือน 09:00").fetchSemanticsNodes().isNotEmpty()) { "the time was not read" }
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
