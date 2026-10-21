package app.omnitask.data

import android.content.Context
import app.omnitask.model.AiMemory
import java.io.File
import java.text.BreakIterator
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Finds the past exchanges a question is about. Each exchange of the conversation log is turned into a vector by
 * Gemini once and kept in a file of the app's own ([FILE]: rebuilt from the log whenever it is lost), so a question
 * is matched by meaning, which works for Thai without spaces between words. Without a key, or when Gemini cannot
 * be reached, it matches by shared words instead, split with the phone's Thai dictionary.
 */
class AiRecall(private val context: Context, private val gemini: Gemini) {

    private val file get() = File(context.filesDir, FILE)

    private fun load(): MutableMap<String, FloatArray> = runCatching {
        val o = JSONObject(file.readText())
        if (o.optInt("dims") != AiMemory.DIMS) return@runCatching mutableMapOf()
        val items = o.getJSONObject("items")
        items.keys().asSequence().associateWith { k ->
            val a = items.getJSONArray(k)
            FloatArray(a.length()) { a.getDouble(it).toFloat() }
        }.toMutableMap()
    }.getOrElse { mutableMapOf() }

    private fun save(vectors: Map<String, FloatArray>) = runCatching {
        val items = JSONObject()
        vectors.forEach { (k, v) -> items.put(k, JSONArray().apply { v.forEach { put(it.toDouble()) } }) }
        file.writeText(JSONObject().put("dims", AiMemory.DIMS).put("items", items).toString())
    }

    /**
     * Up to [k] exchanges from [entries] closest to [query], best first. Exchanges not yet turned into vectors are
     * done now, a batch at a time ([PER_CALL] at most, so the first search over a long log stays quick; the rest
     * follow on later searches). Blocking: call it off the main thread.
     */
    fun find(entries: List<AiMemory.Entry>, query: String, k: Int = 6): List<AiMemory.Entry> {
        if (entries.isEmpty() || query.isBlank()) return emptyList()
        val byMeaning = runCatching {
            val known = load()
            val ids = entries.map { it.id }.toSet()
            val missing = entries.filter { it.id !in known }.take(PER_CALL)
            missing.chunked(BATCH).forEach { batch ->
                gemini.embed(batch.map(AiMemory::embedText), query = false).forEachIndexed { i, v -> known[batch[i].id] = v }
            }
            // Exchanges deleted from the log leave the cache too.
            known.keys.retainAll(ids)
            save(known)
            val q = gemini.embed(listOf(query.take(1000)), query = true).first()
            val best = AiMemory.nearest(q, known, k)
            val byId = entries.associateBy { it.id }
            best.mapNotNull { byId[it] }
        }.getOrNull()
        return byMeaning?.takeIf { it.isNotEmpty() } ?: AiMemory.byWords(entries, query, k, ::thaiWords)
    }

    companion object {
        const val FILE = "ai-recall.json"
        private const val BATCH = 100
        private const val PER_CALL = 300

        /** Words as the phone's dictionary splits them, Thai included. */
        fun thaiWords(text: String): List<String> {
            val it = BreakIterator.getWordInstance(Locale("th"))
            it.setText(text)
            val out = mutableListOf<String>()
            var start = it.first()
            var end = it.next()
            while (end != BreakIterator.DONE) {
                val w = text.substring(start, end).trim()
                if (w.isNotEmpty() && w.any { c -> c.isLetterOrDigit() }) out += w
                start = end
                end = it.next()
            }
            return out
        }
    }
}
