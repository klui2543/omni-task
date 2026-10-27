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
}
