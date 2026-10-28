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
    fun savedFoldersGoOneLevelInCategoriesAndDeeperInFolders() {
        val more = ListFolders.withFolder(ListFolders.withFolder(bucket, "เที่ยว/ญี่ปุ่น"), "ดนตรี")
        assertEquals(listOf("เที่ยว", "ประสบการณ์", "เรียนรู้", "ดนตรี"), more.categories)
        assertEquals(listOf("เที่ยว/ญี่ปุ่น"), more.folders)
        // Written and read back from the note's header.
        assertEquals(more, OmniList.parse(more.path, more.render()))
        val less = ListFolders.withoutFolder(more, "เที่ยว")
        assertEquals(listOf("ประสบการณ์", "เรียนรู้", "ดนตรี"), less.categories)
        assertEquals(emptyList(), less.folders)
    }

    @Test
    fun aPulledInTaskKeepsATagThatOnlySharesACategoryName() {
        // Not in the list yet: #เที่ยว is the owner's own tag and stays.
        assertEquals(
            "- [ ] ทริปบริษัท #งาน #เที่ยว #bucketlist/ประสบการณ์",
            ListFolders.place("- [ ] ทริปบริษัท #งาน #เที่ยว", bucket, "ประสบการณ์"),
        )
    }

    @Test
    fun onlyTheCategoryThatPlacedTheLineComesOff() {
        assertEquals(
            "- [ ] คอร์สถ่ายรูป #เรียนรู้ #bucketlist/ประสบการณ์",
            ListFolders.place("- [ ] คอร์สถ่ายรูป #bucketlist #เที่ยว #เรียนรู้", bucket, "ประสบการณ์"),
        )
    }

    @Test
    fun foldersDifferingOnlyByCaseAreOne() {
        val watch = OmniList("Watch list", "Omni/Watch list.md", "", listOf("Movies"))
        val items = listOf(task("- [ ] Dune #watchlist/movies", list = "Watch list"))
        assertEquals(listOf("Movies"), ListFolders.all(watch, items))
        assertTrue(ListFolders.inside("Movies", ListFolders.pathOf(watch, items[0])))
    }

    @Test
    fun aListWithNoIconWritesNoIconLine() {
        val text = OmniList("Books", "Omni/Books.md", "", emptyList()).render()
        assertFalse("icon:" in text)
        assertEquals("Books", OmniList.parse("Omni/Books.md", text)?.name)
    }

    @Test
    fun aFolderLetGoOnAnotherGoesUnderItKeepingItsName() {
        assertEquals("ประสบการณ์/ญี่ปุ่น", ListFolders.movedTo("เที่ยว/ญี่ปุ่น", "ประสบการณ์", sameList = true))
        assertEquals("ญี่ปุ่น", ListFolders.movedTo("เที่ยว/ญี่ปุ่น", "", sameList = true))
        // Let go where it already is, or inside itself: nothing to do.
        assertEquals(null, ListFolders.movedTo("เที่ยว/ญี่ปุ่น", "เที่ยว", sameList = true))
        assertEquals(null, ListFolders.movedTo("เที่ยว", "เที่ยว/ญี่ปุ่น", sameList = true))
        // To another list, the same spot is fine.
        assertEquals("เที่ยว/ญี่ปุ่น", ListFolders.movedTo("เที่ยว/ญี่ปุ่น", "เที่ยว", sameList = false))
    }

    @Test
    fun itemsDeeperInAMovedFolderKeepTheirPlaceInIt() {
        assertEquals("ประสบการณ์/ญี่ปุ่น/ฮอกไกโด", ListFolders.rebase("เที่ยว/ญี่ปุ่น/ฮอกไกโด", "เที่ยว/ญี่ปุ่น", "ประสบการณ์/ญี่ปุ่น"))
        assertEquals("ประสบการณ์/ญี่ปุ่น", ListFolders.rebase("เที่ยว/ญี่ปุ่น", "เที่ยว/ญี่ปุ่น", "ประสบการณ์/ญี่ปุ่น"))
    }

    @Test
    fun aLineLeavesItsListWithItsPlacingCategory() {
        assertEquals("- [ ] Shogun #อ่าน", ListFolders.unplace("- [ ] Shogun #bucketlist #เที่ยว #อ่าน", bucket))
    }
}
