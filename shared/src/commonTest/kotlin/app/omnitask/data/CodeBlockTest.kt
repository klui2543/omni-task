package app.omnitask.data

import kotlin.test.assertEquals
import kotlin.test.Test

/** Checkbox lines inside a code block are examples (as in the secretary's own guide), never tasks. */
class CodeBlockTest {

    @Test
    fun checkboxInsideCodeBlockIsNotATask() {
        // The real shape of Claude/ระบบเลขา.md: a format sample in a fenced block.
        val text = listOf(
            "รูปแบบไฟล์:",
            "",
            "```",
            "omni-inbox/1",
            "## งานค้าง",
            "- [ ] #siriraj/ป้าย #kluido สิ่งที่ Klui ต้องทำ",
            "```",
            "",
            "- [ ] งานจริง #omni",
        ).joinToString("\n")
        val tasks = VaultText.parseFile("f", "Claude/ระบบเลขา.md", text)
        assertEquals(listOf("งานจริง"), tasks.map { it.title })
        assertEquals(8, tasks.single().lineIndex)
    }

    @Test
    fun tildeFenceAndIndentedFenceUnderATask() {
        val text = listOf(
            "- [ ] งานหลัก",
            "    - ตัวอย่าง:",
            "    ```",
            "    - [ ] ไม่ใช่งานย่อย",
            "    ```",
            "    - [ ] งานย่อยจริง",
            "~~~",
            "- [x] ไม่ใช่งาน",
            "~~~",
            "- [ ] งานท้าย",
        ).joinToString("\n")
        val tasks = VaultText.parseFile("f", "x.md", text)
        assertEquals(listOf("งานหลัก", "งานย่อยจริง", "งานท้าย"), tasks.map { it.title })
        val main = tasks.first()
        assertEquals(main.key, tasks[1].parent)
        // The sample stays part of the main task's notes.
        assertEquals(true, main.notes.any { it.contains("ไม่ใช่งานย่อย") })
    }

    @Test
    fun aFenceInsideCodeOfOtherKindDoesNotClose() {
        val text = listOf(
            "````",
            "```",
            "- [ ] ยังอยู่ในกล่อง",
            "```",
            "````",
            "- [ ] งานจริง",
        ).joinToString("\n")
        assertEquals(listOf("งานจริง"), VaultText.parseFile("f", "x.md", text).map { it.title })
    }

    @Test
    fun inlineCodeAndAnUnclosedFenceHideNothing() {
        val text = listOf(
            "```x``` ตัวอย่างโค้ดในบรรทัด",
            "- [ ] งานแรก",
            "```",
            "- [ ] งานหลังเครื่องหมายที่ไม่ปิด",
        ).joinToString("\r\n")
        assertEquals(listOf("งานแรก", "งานหลังเครื่องหมายที่ไม่ปิด"), VaultText.parseFile("f", "x.md", text).map { it.title })
    }
}
