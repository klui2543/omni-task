package app.omnitask.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class WebKindsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val empty = "{}"

    private fun add(state: String, name: String, emoji: String = "") = json.decodeFromString<WebKinds.AddOut>(WebKinds.add(state, name, emoji))
    private fun stateOf(view: WebKinds.ViewOut) = Json.encodeToString(WebKinds.StateIn.serializer(), view.state)

    @Test
    fun addsACustomKindWithItsTag() {
        val r = add(empty, " งาน บ้าน ", "🏠")
        assertTrue(r.ok)
        assertEquals("งานบ้าน", r.tag)
        assertEquals(listOf(WebKinds.CustomOut("งาน บ้าน", "🏠", "งานบ้าน", "🏠 งาน บ้าน")), r.view.custom)
        // Kept as Android keeps it: "name\temoji\ttag".
        assertEquals(listOf("งาน บ้าน\t🏠\tงานบ้าน"), r.view.state.custom)
    }

    @Test
    fun refusesAnEmptyOrTakenName() {
        assertEquals("empty", add(empty, " #?! ").error)
        val one = add(empty, "อ่าน", "📖")
        assertEquals("taken", add(stateOf(one.view), "อ่าน").error)
        assertEquals("taken", add(stateOf(one.view), "อ่าน", "x").error)
        // A built-in kind's own tag is taken too.
        assertEquals("taken", add(empty, "รอ").error)
        assertEquals("taken", add(empty, "#สักวัน").error)
    }

    @Test
    fun keepsKindsSortedAndRemovesByTag() {
        var state = empty
        for (n in listOf("Zebra", "apple", "ม้า")) state = stateOf(add(state, n).view)
        val view = json.decodeFromString<WebKinds.ViewOut>(WebKinds.view(state))
        assertEquals(listOf("apple", "Zebra", "ม้า"), view.custom.map { it.name })
        val after = json.decodeFromString<WebKinds.ViewOut>(WebKinds.remove(state, "APPLE"))
        assertEquals(listOf("Zebra", "ม้า"), after.custom.map { it.name })
    }

    @Test
    fun hidesAndShowsBuiltInKinds() {
        var view = json.decodeFromString<WebKinds.ViewOut>(WebKinds.toggleHidden(empty, "WAITING"))
        assertEquals(listOf("WAITING"), view.state.hidden)
        assertTrue(view.builtin.first { it.id == "WAITING" }.hidden)
        // NORMAL cannot be hidden, and a wrong name changes nothing.
        view = json.decodeFromString(WebKinds.toggleHidden(stateOf(view), "NORMAL"))
        view = json.decodeFromString(WebKinds.toggleHidden(stateOf(view), "NOPE"))
        assertEquals(listOf("WAITING"), view.state.hidden)
        view = json.decodeFromString(WebKinds.toggleHidden(stateOf(view), "WAITING"))
        assertEquals(emptyList(), view.state.hidden)
        // Damaged text starts clean.
        assertEquals(emptyList(), json.decodeFromString<WebKinds.ViewOut>(WebKinds.view("not json")).custom)
    }

    @Test
    fun pickerHidesHiddenKindsExceptTheTasksOwn() {
        val state = stateOf(json.decodeFromString(WebKinds.toggleHidden(stateOf(add(empty, "อ่าน", "📖").view), "FUTURE")))
        val plain = json.decodeFromString<WebKinds.PickerOut>(WebKinds.picker(state, "- [ ] ล้างจาน"))
        assertEquals(listOf("NORMAL", "WAITING", "SOMEDAY"), plain.builtin.map { it.id })
        assertEquals("NORMAL", plain.current)
        assertEquals(listOf("อ่าน"), plain.custom.map { it.tag })

        val future = json.decodeFromString<WebKinds.PickerOut>(WebKinds.picker(state, "- [ ] วางแผน #อนาคต"))
        assertEquals("FUTURE", future.current)
        assertTrue(future.builtin.any { it.id == "FUTURE" })

        val read = json.decodeFromString<WebKinds.PickerOut>(WebKinds.picker(state, "- [ ] Atomic Habits #อ่าน"))
        assertEquals("อ่าน", read.customTag)
        assertEquals("NORMAL", read.current)
        assertFalse(read.hint != null)

        val waiting = json.decodeFromString<WebKinds.PickerOut>(WebKinds.picker(state, "- [ ] ส่งของ #รอ/สมชาย"))
        assertEquals("WAITING", waiting.current)
        assertEquals("สมชาย", waiting.who)
        assertTrue(waiting.hint!!.startsWith("สมชาย รออยู่"))
    }

    private fun edit(text: String, raw: String, op: String) = WebCore.editTask(text, raw, 0, "2026-10-08", op)

    @Test
    fun kindEditsSwapEveryKindTag() {
        val raw = "- [ ] อ่านหนังสือ #อ่าน #งาน"
        val a = edit(raw, raw, """{"op":"kind","value":"WAITING","who":" สมชาย ","custom":["อ่าน"]}""")
        assertTrue(a.contains("- [ ] อ่านหนังสือ #งาน #รอ/สมชาย"), a)

        val waiting = "- [ ] ส่งของ #รอ/สมชาย"
        val b = edit(waiting, waiting, """{"op":"customKind","value":"อ่าน","custom":["อ่าน"]}""")
        assertTrue(b.contains("- [ ] ส่งของ #อ่าน"), b)

        val c = edit(raw, raw, """{"op":"kind","value":"NORMAL","custom":["อ่าน"]}""")
        assertTrue(c.contains("- [ ] อ่านหนังสือ #งาน"), c)
        assertFalse(c.contains("#อ่าน"), c)

        // Without the owner's kinds the old built-in swap works as before, and #รอ without a name stays plain.
        val d = edit(waiting, waiting, """{"op":"kind","value":"FUTURE"}""")
        assertTrue(d.contains("- [ ] ส่งของ #อนาคต"), d)
        val e = edit("- [ ] x", "- [ ] x", """{"op":"kind","value":"WAITING"}""")
        assertTrue(e.contains("- [ ] x #รอ"), e)
    }
}
