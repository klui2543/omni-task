package app.omnitask.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WebLinksTest {

    private fun found(text: String) = WebLinks.find(text).map { text.substring(it.first, it.last + 1) }

    @Test
    fun aSharedLinkOnItsOwnLine() {
        // How a shared link is kept under its task: one sub-bullet line.
        assertEquals(listOf("https://youtu.be/Xn7KWR9EOGQ"), found("- https://youtu.be/Xn7KWR9EOGQ"))
        assertEquals(listOf("http://example.com/a?b=1&c=2#top"), found("http://example.com/a?b=1&c=2#top"))
    }

    @Test
    fun severalLinksAndThaiAround() {
        assertEquals(
            listOf("https://a.com/x", "https://th.wikipedia.org/wiki/ยา"),
            found("ดูที่ https://a.com/x และ https://th.wikipedia.org/wiki/ยา ด้วย"),
        )
    }

    @Test
    fun punctuationAfterALinkIsNotPartOfIt() {
        assertEquals(listOf("https://a.com/x"), found("อ่าน https://a.com/x."))
        assertEquals(listOf("https://a.com/x"), found("(https://a.com/x)"))
        assertEquals(listOf("https://a.com/x"), found("ลิงก์: https://a.com/x, แล้วก็"))
        // A bracket that belongs to the address stays.
        assertEquals(listOf("https://en.wikipedia.org/wiki/Pin_(album)"), found("https://en.wikipedia.org/wiki/Pin_(album)"))
        // A Markdown link: only the address.
        assertEquals(listOf("https://a.com/x"), found("[ชื่อ](https://a.com/x)"))
    }

    @Test
    fun noLink() {
        assertEquals(emptyList<String>(), found("ไม่มีลิงก์ http:// หรือ www"))
        assertEquals(emptyList<String>(), found("ftp://a.com"))
    }

    @Test
    fun aLinkIsShownByItsSite() {
        assertEquals("docs.google.com", WebLinks.label("https://docs.google.com/document/d/1xQ7rTzP4kLm9vB2nWcYdE5f/edit?usp=sharing"))
        assertEquals("youtube.com", WebLinks.label("https://www.youtube.com/watch?v=Jq3pX8sLwKc&list=PLm4"))
        assertEquals("youtu.be", WebLinks.label("https://youtu.be/Xn7KWR9EOGQ"))
        assertEquals("example.com", WebLinks.label("http://example.com?b=1"))
        assertEquals("example.com", WebLinks.label("http://example.com#top"))
        assertEquals("example.com", WebLinks.label("https://user@example.com:8080/a"))
        assertEquals("th.wikipedia.org", WebLinks.label("https://th.wikipedia.org/wiki/ยา"))
    }
}
