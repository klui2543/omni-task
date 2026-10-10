package app.omnitask.drive

import kotlin.test.Test
import kotlin.test.assertEquals

class Merge3Test {

    private fun lines(vararg l: String) = l.joinToString("\n")

    @Test
    fun oneSideChangedTakesThatSide() {
        val base = lines("- [ ] a", "- [ ] b")
        assertEquals(lines("- [x] a", "- [ ] b"), Merge3.merge(base, lines("- [x] a", "- [ ] b"), base).text)
        assertEquals(lines("- [ ] a", "- [x] b"), Merge3.merge(base, base, lines("- [ ] a", "- [x] b")).text)
    }

    @Test
    fun differentLinesBothKept() {
        val base = lines("- [ ] a", "- [ ] b", "- [ ] c")
        val r = Merge3.merge(base, lines("- [x] a", "- [ ] b", "- [ ] c"), lines("- [ ] a", "- [ ] b", "- [ ] c #tag"))
        assertEquals(lines("- [x] a", "- [ ] b", "- [ ] c #tag"), r.text)
        assertEquals(emptyList(), r.lost)
    }

    @Test
    fun neighbouringLinesBothKept() {
        // Today's conflict: one side ticked a task, the other moved the Routine and tagged the next line.
        val base = lines("- [ ] Routine ⏳ 2026-10-09", "- [ ] How to #Invest", "- [/] ปรับระบบ")
        val phone = lines("- [ ] Routine ⏳ 2026-10-09", "- [ ] How to #Invest", "- [x] ปรับระบบ ✅ 2026-10-10")
        val web = lines("- [ ] Routine ⏳ 2026-10-10", "- [ ] How to #Invest #อนาคต", "- [/] ปรับระบบ")
        val r = Merge3.merge(base, phone, web)
        assertEquals(lines("- [ ] Routine ⏳ 2026-10-10", "- [ ] How to #Invest #อนาคต", "- [x] ปรับระบบ ✅ 2026-10-10"), r.text)
        assertEquals(emptyList(), r.lost)
    }

    @Test
    fun bothAddedAtTheEndKeepsBothOnce() {
        val base = lines("- [ ] a", "")
        val r = Merge3.merge(base, lines("- [ ] a", "- [ ] phone", "- [ ] same", ""), lines("- [ ] a", "- [ ] web", "- [ ] same", ""))
        assertEquals(lines("- [ ] a", "- [ ] web", "- [ ] same", "- [ ] phone", ""), r.text)
        assertEquals(emptyList(), r.lost)
    }

    @Test
    fun sameLineChangedDifferentlyKeepsDriveAndReportsThePhone() {
        val base = lines("- [ ] a", "- [ ] b")
        val r = Merge3.merge(base, lines("- [ ] a phone", "- [ ] b"), lines("- [ ] a web", "- [ ] b"))
        assertEquals(lines("- [ ] a web", "- [ ] b"), r.text)
        assertEquals(listOf(listOf("- [ ] a phone")), r.lost)
    }

    @Test
    fun sameChangeOnBothSidesIsNotAClash() {
        val base = lines("- [ ] a", "- [ ] b")
        val both = lines("- [x] a", "- [ ] b")
        val r = Merge3.merge(base, lines("- [x] a", "- [ ] b", "- [ ] c"), both)
        assertEquals(lines("- [x] a", "- [ ] b", "- [ ] c"), r.text)
        assertEquals(emptyList(), r.lost)
    }

    @Test
    fun insertionBeforeALineTheOtherSideChanged() {
        val base = lines("- [ ] a", "- [ ] b")
        val r = Merge3.merge(base, lines("- [ ] a", "- [ ] new", "- [ ] b"), lines("- [ ] a", "- [x] b"))
        assertEquals(lines("- [ ] a", "- [ ] new", "- [x] b"), r.text)
    }

    @Test
    fun deletionAndAnEditElsewhere() {
        val base = lines("- [ ] a", "- [ ] b", "- [ ] c")
        val r = Merge3.merge(base, lines("- [ ] a", "- [ ] c"), lines("- [ ] a", "- [ ] b", "- [x] c"))
        assertEquals(lines("- [ ] a", "- [x] c"), r.text)
    }

    @Test
    fun windowsLineEndingsStay() {
        val base = "- [ ] a\r\n- [ ] b\r\n"
        val r = Merge3.merge(base, "- [x] a\r\n- [ ] b\r\n", "- [ ] a\r\n- [x] b\r\n")
        assertEquals("- [x] a\r\n- [x] b\r\n", r.text)
    }

    @Test
    fun subtaskBlocksMoveWithTheirParent() {
        val base = lines("- [ ] p", "    - [ ] s1", "- [ ] q")
        val phone = lines("- [ ] p", "    - [x] s1", "    - [ ] s2", "- [ ] q")
        val web = lines("- [ ] p", "    - [ ] s1", "- [ ] q", "- [ ] r")
        assertEquals(lines("- [ ] p", "    - [x] s1", "    - [ ] s2", "- [ ] q", "- [ ] r"), Merge3.merge(base, phone, web).text)
    }
}
