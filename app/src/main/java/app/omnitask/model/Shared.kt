package app.omnitask.model

/** Text shared from another app, split into a title for the task and the link for its details. */
object Shared {

    data class Item(val title: String, val link: String?)

    private val URL = Regex("""https?://\S+""")

    /**
     * YouTube and browsers send the link as the text and often the page title as the subject; others send
     * "title url" in one string. The link never goes in the title, where its digits could read as a date.
     */
    /** The page's title from its HTML: og:title first, then <title>; entities like &amp; decoded. */
    fun titleFromHtml(html: String): String? {
        val og = Regex("""<meta[^>]+property=["']og:title["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]*property=["']og:title["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        val title = og ?: Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)
        return title?.let { decode(it).replace(Regex("""\s+"""), " ").trim() }?.takeIf { it.isNotEmpty() }
    }

    private fun decode(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    /** Downloads the start of the page and reads its title; null when offline or the page has none. Call off the main thread. */
    fun fetchTitle(link: String): String? = runCatching {
        val conn = java.net.URL(link).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) OmniTask")
        conn.setRequestProperty("Accept-Language", "th,en")
        try {
            // The title sits in the head, so the first 256 KB is plenty.
            val bytes = conn.inputStream.use { input -> input.readNBytesCompat(256 * 1024) }
            titleFromHtml(String(bytes, Charsets.UTF_8))
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun read(subject: String?, text: String?): Item {
        val body = text.orEmpty().trim()
        val link = URL.find(body)?.value
        val rest = URL.replace(body, " ").replace(Regex("""\s+"""), " ").trim()
        val title = subject?.trim()?.takeIf { it.isNotEmpty() } ?: rest
        return Item(title, link)
    }
}
