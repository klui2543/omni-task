package app.omnitask.model

import app.omnitask.data.TaskLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Owner, 2026-10-28 (way ก): a list's folders nest without limit, as tags under the list's tag. */
class ListFoldersTest {

    private val bucket = OmniList("Bucket list", "Omni/Bucket list.md", "🏔️", listOf("เที่ยว", "ประสบการณ์", "เรียนรู้"))

    private fun task(raw: String, list: String? = "Bucket list") = TaskLine.parse(raw)!!.copy(list = list)

    @Test
    fun aNestedTagIsAFolderPath() {
        assertEquals("เที่ยว/ญี่ปุ่น", ListFolders.pathOf(bucket, task("- [ ] เกียวโต #bucketlist/เที่ยว/ญี่ปุ่น")))
    }

    @Test
    fun anOldCategoryTagStillCountsAsItsFolder() {
        assertEquals("เที่ยว", ListFolders.pathOf(bucket, task("- [ ] แสงเหนือ #bucketlist #เที่ยว")))
    }

    @Test
    fun aLineWithNoFolderSitsOnTheListItself() {
        assertEquals("", ListFolders.pathOf(bucket, task("- [ ] มาราธอน #bucketlist")))
    }

    @Test
    fun everyLevelOfAPathIsAFolder() {
        val items = listOf(task("- [ ] ซัปโปโร #bucketlist/เที่ยว/ญี่ปุ่น/ฮอกไกโด"))
        assertEquals(
            listOf("ประสบการณ์", "เที่ยว", "เที่ยว/ญี่ปุ่น", "เที่ยว/ญี่ปุ่น/ฮอกไกโด", "เรียนรู้"),
            ListFolders.all(bucket, items),
        )
        assertEquals(listOf("เที่ยว/ญี่ปุ่น"), ListFolders.children(ListFolders.all(bucket, items), "เที่ยว"))
    }

    @Test
    fun placingALineSwapsItsOldPlaceForTheNewOne() {
        assertEquals(
            "- [ ] แสงเหนือ #bucketlist/เที่ยว/ยุโรป ➕ 2026-10-01",
            ListFolders.place("- [ ] แสงเหนือ #bucketlist #เที่ยว ➕ 2026-10-01", bucket, "เที่ยว/ยุโรป"),
        )
        assertEquals(
            "- [ ] แสงเหนือ #อ่าน #bucketlist",
            ListFolders.place("- [ ] แสงเหนือ #bucketlist/เที่ยว #อ่าน", bucket, ""),
        )
    }

    @Test
    fun aTypedFolderNameBecomesATag() {
        assertEquals("bucketlist/เที่ยว/เกาะ-เต่า", ListFolders.tagOf(bucket, " เที่ยว / เกาะ เต่า "))
    }

    @Test
    fun aTaskElsewhereBelongsByItsFolderTag() {
        assertTrue(ListFolders.belongs(bucket, task("- [ ] เกียวโต #BucketList/เที่ยว", list = null)))
        assertFalse(ListFolders.belongs(bucket, task("- [ ] เกียวโต #bucketlists", list = null)))
    }

    @Test
    fun aFolderUnderAListNeverNamesAProject() {
        val before = Projects.listRoots
        try {
            Projects.listRoots = setOf("bucketlist")
            assertFalse(Projects.isPlain("bucketlist/เที่ยว"))
            assertTrue(Projects.isPlain("bucketlists/เที่ยว"))
        } finally {
            Projects.listRoots = before
        }
    }

    @Test
    fun savedFoldersGainAndLosePaths() {
        val more = bucket.copy(categories = ListFolders.withFolder(bucket, "เที่ยว/ญี่ปุ่น"))
        assertEquals(listOf("เที่ยว", "ประสบการณ์", "เรียนรู้", "เที่ยว/ญี่ปุ่น"), more.categories)
        assertEquals(listOf("ประสบการณ์", "เรียนรู้"), ListFolders.withoutFolder(more, "เที่ยว"))
    }

    @Test
    fun aListWithNoIconWritesNoIconLine() {
        val text = OmniList("Books", "Omni/Books.md", "", emptyList()).render()
        assertFalse("icon:" in text)
        assertEquals("Books", OmniList.parse("Omni/Books.md", text)?.name)
    }
}
