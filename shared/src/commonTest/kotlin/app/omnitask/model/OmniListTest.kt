package app.omnitask.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

class OmniListTest {

    @Test
    fun readsAndWritesTheHeader() {
        val list = OmniList("Watch list", "Omni/Watch list.md", "🎬", listOf("หนัง", "ซีรีส์"))
        val text = list.render() + "- [ ] Shogun #ซีรีส์\n"
        assertEquals(list, OmniList.parse("Omni/Watch list.md", text))
        assertNull(OmniList.parse("Notes/x.md", "# just a note\n- [ ] task"))
        assertNull(OmniList.parse("Notes/y.md", "---\ntags: [a]\n---\n- [ ] task"))
    }

    @Test
    fun listsGoByATag() {
        assertEquals("watchlist", OmniList.tagFor("Watch list"))
        assertEquals("หนังสือที่อยากอ่าน", OmniList.tagFor("หนังสือ ที่อยากอ่าน"))
        // A note written before lists had tags still gets one from its name.
        val old = "---\nomni-list: true\nicon: 🏔️\ncategories: เที่ยว\n---\n# Bucket list\n"
        assertEquals("bucketlist", OmniList.parse("Omni/Bucket list.md", old)!!.tag)
        val named = old.replace("categories: เที่ยว", "categories: เที่ยว\ntag: #ฝัน")
        assertEquals("ฝัน", OmniList.parse("Omni/Bucket list.md", named)!!.tag)
        // The list's tag never names a project.
        val t = app.omnitask.data.TaskLine.parse("- [ ] ดู Shogun #watchlist")!!
        Projects.ignoredTags = setOf("watchlist")
        try { assertNull(Projects.projectOf(t)) } finally { Projects.ignoredTags = emptySet() }
    }

    @Test
    fun oldIconNamesBecomeEmoji() {
        assertEquals("🎬", app.omnitask.ui.ListEmoji.of("film"))
        assertEquals("🍿", app.omnitask.ui.ListEmoji.of("🍿"))
        assertEquals(app.omnitask.ui.ListEmoji.DEFAULT, app.omnitask.ui.ListEmoji.of("unknown"))
        assertEquals(app.omnitask.ui.ListEmoji.DEFAULT, app.omnitask.ui.ListEmoji.of(""))
    }

    @Test
    fun listItemsStayOutOfTheTaskViewsUnlessChosen() {
        val today = LocalDate.parse("2026-10-08")
        val item = app.omnitask.data.TaskLine.parse("- [ ] Shogun #ซีรีส์")!!.copy(list = "Watch list")
        val task = app.omnitask.data.TaskLine.parse("- [ ] ส่งรายงาน")!!
        val q = TaskQuery(groupBy = GroupBy.NONE)
        assertEquals(listOf(task), q.run(listOf(item, task), today).single().tasks)
        assertEquals(listOf(item), q.copy(lists = setOf("Watch list")).run(listOf(item, task), today).single().tasks)
        val saved = q.copy(kinds = setOf(TaskKind.WAITING), buckets = setOf(DateBucket.TODAY), tags = setOf("a b"))
        assertEquals(saved, TaskQuery.decode(saved.encode(), q))
    }
}
