package app.omnitask.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class WebQuickAddTest {

    private val bucket = "---\nomni-list: true\nicon: target\ncategories: เที่ยว, กิน\ntag: bucket\n---\n# Bucket list\n"
    private val notes = """[{"key":"b","path":"Omni/Bucket list.md","text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), bucket)}}]"""
    private fun read(sentence: String, tags: String = "[\"งาน\",\"บ้าน\"]") =
        Json.decodeFromString<WebQuickAdd.Out>(WebQuickAdd.preview(sentence, "2026-10-08", notes, tags))

    @Test
    fun theSentenceIsReadAsAndroidReadsIt() {
        val r = read("ส่งรายงาน พรุ่งนี้ 9:30 #งาน !!")
        assertEquals("ส่งรายงาน", r.title)
        assertEquals("2026-10-09", r.due)
        assertEquals("09:30", r.time)
        assertEquals(listOf("งาน"), r.tags)
        assertEquals("HIGH", r.priority)
        assertNull(r.token)
    }

    @Test
    fun aHashWordOffersListsTheirCategoriesAndTags() {
        val all = read("ไปญี่ปุ่น #")
        assertEquals("", all.token)
        assertEquals("Bucket list", all.picks.first().list!!.name)
        assertEquals(listOf("เที่ยว", "กิน"), all.picks.first().list!!.categories)
        val cat = read("ไปญี่ปุ่น #เที่ยว")
        assertEquals("เที่ยว", cat.picks.single { it.list != null }.category)
        val tag = read("ซื้อของ #บ้")
        assertEquals("บ้าน", tag.picks.single().tag)
    }
}
