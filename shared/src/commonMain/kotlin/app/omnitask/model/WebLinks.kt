package app.omnitask.model

/** Web addresses inside a task's details, so the apps can show them as links that open in the browser. */
object WebLinks {

    private val URL = Regex("""https?://[^\s<>"\[\]]+""")
    private const val TRAILING = ".,;:!?'"

    /**
     * Where each web address sits in [text], as index ranges. Punctuation right after an address (a full stop, a
     * comma, a closing bracket that has no opening one inside it) belongs to the sentence, not the address.
     */
    fun find(text: String): List<IntRange> = URL.findAll(text).mapNotNull { m ->
        var end = m.range.last
        while (end > m.range.first) {
            val c = text[end]
            val body = text.substring(m.range.first, end + 1)
            val unbalanced = c == ')' && body.count { it == ')' } > body.count { it == '(' }
            if (c in TRAILING || unbalanced) end-- else break
        }
        // "http://" alone is not an address.
        val host = text.substring(m.range.first, end + 1).substringAfter("://")
        if (host.isEmpty()) null else m.range.first..end
    }.toList()

    /**
     * The short name a link is shown by on the task's page: the site without `www.`, so a long address leaves room
     * to tap the details and write. `https://www.youtube.com/watch?v=x` is `youtube.com`.
     */
    fun label(url: String): String {
        val site = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':')
        val short = site.removePrefix("www.")
        return short.ifEmpty { url }
    }
}
