package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class FolderFilterTest {
    private val today = LocalDate.parse("2026-11-03")
    private fun t(line: String) = TaskLine.parse(line)!!

    @Test
    fun aFolderCoversTheFoldersInsideIt() {
        val shift = t("- [ ] เขียนถ้อยคำ #kluido #Siriraj/ตารางเวรเภสัช 📅 2026-11-05")
        val wording = t("- [ ] ป้าย #Siriraj/ตารางเวรเภสัช/wording")
        val other = t("- [ ] ซื้อของ #บ้าน")
        val near = t("- [ ] ไม่ใช่ #Siriraj/ตารางเวรเภสัชเก่า")
        val all = listOf(shift, wording, other, near)
        val q = TaskQuery(groupBy = GroupBy.NONE, folders = setOf("Siriraj/ตารางเวรเภสัช"))
        assertEquals(setOf(shift, wording), q.run(all, today).single().tasks.toSet())
        assertEquals(setOf(shift, wording, near), q.copy(folders = setOf("siriraj")).run(all, today).single().tasks.toSet())
        assertEquals(1, q.activeFilters)
        assertEquals(TaskQuery(groupBy = GroupBy.NONE), q.activeChips().single().second)
    }

    @Test
    fun aSavedFilterWithoutFoldersStaysSevenFieldsForTheWeb() {
        val q = TaskQuery(tags = setOf("รอ"))
        assertEquals(7, q.encode().split('\u001E').size)
        val f = q.copy(folders = setOf("Siriraj/ตารางเวรเภสัช"))
        assertEquals(8, f.encode().split('\u001E').size)
        assertEquals(f, TaskQuery.decode(f.encode(), TaskQuery()))
    }

    @Test
    fun theKeptViewComesBackWhole() {
        val q = TaskQuery(
            folders = setOf("Siriraj"), tags = setOf("kluido"), hideDone = false, groupBy = GroupBy.TAG,
            sortBy = SortBy.PRIORITY, ascending = false, thenBy = listOf(SortBy.DUE to true), text = "ค้น",
        )
        assertEquals(q.copy(text = ""), TaskQuery.decodeView(q.encodeView()))
        assertEquals(TaskQuery.DEFAULT, TaskQuery.decodeView(""))
    }
}
