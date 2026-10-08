package app.omnitask.model

/** Downloads the start of the page and reads its title; null when offline or the page has none. Call off the main thread. */
fun Shared.fetchTitle(link: String): String? = runCatching {
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
