package app.omnitask.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings file in the vault against the phone's own settings. The case from the owner's clip (2026-11-20):
 * the month grid went back to "สีตามความแน่น" after it was set to "ชื่องาน" on the phone.
 */
@RunWith(AndroidJUnit4::class)
class SettingsSyncTest {

    private lateinit var context: Context
    private val prefs get() = context.getSharedPreferences("omnitask", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs.edit().clear().commit()
    }

    /** A settings file as Android and the web write it. */
    private fun file(savedAt: Long, vararg strings: Pair<String, String>): String {
        val body = strings.joinToString(",\n") { (k, v) -> "    \"$k\": { \"string\": \"$v\" }" }
        return "{\n  \"version\": 1,\n  \"savedAt\": $savedAt,\n  \"settings\": {\n$body\n  }\n}"
    }

    @Test
    fun aChoiceMadeOnThePhoneIsNotOverwrittenByAnOlderOneFromTheFile() {
        // The phone and the file agree: the grid shades by load.
        SettingsSync.loadText(context, file(100, "monthCells" to "tint", "tasks.layout" to "CALENDAR"))
        assertEquals("tint", prefs.getString("monthCells", null))
        // The owner picks titles on the phone; the save to the vault has not gone out yet (or failed).
        prefs.edit().putString("monthCells", "bars").commit()
        // Meanwhile another device (the web tab in Obsidian) writes the file: it carries the old choice and a newer time.
        SettingsSync.loadText(context, file(200, "monthCells" to "tint", "tasks.layout" to "LIST"))
        assertEquals("the phone's own new choice must stay", "bars", prefs.getString("monthCells", null))
        assertEquals("what the other device changed still comes in", "LIST", prefs.getString("tasks.layout", null))
    }

    /** CONTROL: passes before and after the fix. A setting left alone on the phone takes the file's newer value. */
    @Test
    fun aSettingNotChangedOnThePhoneTakesTheNewerFile() {
        SettingsSync.loadText(context, file(100, "monthCells" to "bars"))
        SettingsSync.loadText(context, file(200, "monthCells" to "tint"))
        assertEquals("tint", prefs.getString("monthCells", null))
    }
}
