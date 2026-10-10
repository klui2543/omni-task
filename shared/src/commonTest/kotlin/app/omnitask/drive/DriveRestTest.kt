package app.omnitask.drive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DriveRestTest {

    private val sent = ArrayList<HttpRequest>()

    private fun rest(vararg answers: HttpResponse, refused: (String) -> Unit = {}): DriveRest {
        val queue = ArrayDeque(answers.toList())
        return DriveRest({ r -> sent += r; queue.removeFirst() }, { "t${sent.size}" }, refused)
    }

    @Test
    fun encodesThaiAndEmojiForTheQuery() {
        assertEquals("%F0%9F%93%81%20Folder", urlEncode("📁 Folder"))
        assertEquals("a-b_c.d~e", urlEncode("a-b_c.d~e"))
        assertEquals("%E0%B8%81", urlEncode("ก"))
    }

    @Test
    fun listsFollowPagesAndReadEntries() {
        val drive = rest(
            HttpResponse(200, """{"nextPageToken":"p2","files":[{"id":"1","name":"Omni","mimeType":"application/vnd.google-apps.folder","parents":["0"]}]}"""),
            HttpResponse(200, """{"files":[{"id":"2","name":"Omni","mimeType":"application/vnd.google-apps.folder","parents":["9"],"version":"4"}]}"""),
        )
        val found = drive.findFolders("Omni")
        assertEquals(listOf("1", "2"), found.map { it.id })
        assertTrue(found.all { it.isFolder })
        assertEquals("4", found[1].version)
        assertTrue(sent[0].url.contains("q=name%20%3D%20%27Omni%27"))
        assertTrue(sent[1].url.contains("pageToken=p2"))
        assertEquals("Bearer t0", sent[0].headers["Authorization"])
    }

    @Test
    fun writeSendsTheTextAndReturnsTheVersion() {
        val drive = rest(HttpResponse(200, """{"version":"12"}"""))
        assertEquals("12", drive.write("abc", "- [ ] ก\n", "text/markdown"))
        assertEquals("PATCH", sent[0].method)
        assertEquals("- [ ] ก\n", sent[0].body)
        assertTrue(sent[0].url.startsWith("${DriveRest.UPLOAD}/files/abc?uploadType=media"))
    }

    @Test
    fun readAsksTheVersionThenTheText() {
        val drive = rest(HttpResponse(200, """{"version":"3"}"""), HttpResponse(200, "hello"))
        val v = drive.read("x")
        assertEquals("3", v.version)
        assertEquals("hello", v.text)
        assertTrue(sent[1].url.contains("alt=media"))
    }

    @Test
    fun aRefusedTokenIsReplacedOnce() {
        val refused = ArrayList<String>()
        val drive = rest(HttpResponse(401, ""), HttpResponse(200, """{"version":"1"}"""), refused = { refused += it })
        assertEquals("1", drive.version("x"))
        assertEquals(listOf("t0"), refused)
        val again = rest(HttpResponse(401, ""), HttpResponse(401, ""))
        assertTrue(assertFailsWith<DriveUnavailable> { again.version("x") }.signIn)
    }

    @Test
    fun noNetworkAndServerTroubleMeanTryLater() {
        val down = DriveRest({ throw RuntimeException("no route") }, { "t" })
        assertFailsWith<DriveUnavailable> { down.version("x") }
        assertFailsWith<DriveUnavailable> { rest(HttpResponse(503, "")).version("x") }
        assertFailsWith<DriveUnavailable> { rest(HttpResponse(429, "")).version("x") }
        assertEquals(404, assertFailsWith<DriveHttpError> { rest(HttpResponse(404, "gone")).version("x") }.status)
    }

    @Test
    fun noSignInMeansTryLater() {
        val drive = DriveRest({ HttpResponse(200, "{}") }, { throw DriveUnavailable(signIn = true) })
        assertTrue(assertFailsWith<DriveUnavailable> { drive.version("x") }.signIn)
    }

    @Test
    fun createSendsNameParentAndText() {
        val drive = rest(HttpResponse(200, """{"id":"n","name":"x.md","version":"1","parents":["p"]}"""))
        val made = drive.create("p", "x.md", "text/markdown", "body")
        assertEquals("n", made.id)
        val body = sent[0].body!!
        assertTrue(body.contains(""""name":"x.md""""))
        assertTrue(body.contains(""""parents":["p"]"""))
        assertTrue(body.contains("\r\n\r\nbody\r\n"))
        assertTrue(sent[0].headers.getValue("Content-Type").startsWith("multipart/related; boundary="))
    }

    @Test
    fun quoteEscapes() {
        assertEquals("""'it\'s \\ ok'""", DriveRest.quote("""it's \ ok"""))
    }
}
