package app.omnitask.model

/** Text shared from another app, split into a title for the task and the link for its details. */
object Shared {

    data class Item(val title: String, val link: String?)

    private val URL = Regex("""https?://\S+""")

    /**
     * YouTube and browsers send the link as the text and often the page title as the subject; others send
     * "title url" in one string. The link never goes in the title, where its digits could read as a date.
     */
    fun read(subject: String?, text: String?): Item {
        val body = text.orEmpty().trim()
        val link = URL.find(body)?.value
        val rest = URL.replace(body, " ").replace(Regex("""\s+"""), " ").trim()
        val title = subject?.trim()?.takeIf { it.isNotEmpty() } ?: rest
        return Item(title, link)
    }
}
