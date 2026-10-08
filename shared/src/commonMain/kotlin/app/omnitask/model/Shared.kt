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
        val title = og ?: Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        return title?.let { decode(it).replace(Regex("""\s+"""), " ").trim() }?.takeIf { it.isNotEmpty() }
    }

    private fun decode(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    fun read(subject: String?, text: String?): Item {
        val body = text.orEmpty().trim()
        val link = URL.find(body)?.value
        val rest = URL.replace(body, " ").replace(Regex("""\s+"""), " ").trim()
        val title = subject?.trim()?.takeIf { it.isNotEmpty() } ?: rest
        return Item(title, link)
    }
}
