package app.omnitask.data

import android.content.Context
import app.omnitask.model.AiMemory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Finds past exchanges by meaning. Each exchange is turned into a vector by Gemini once and kept on this phone, one
 * small file per month (`ai-recall/2026-10.bin`, a byte per number), so thirty years of talk stays a few megabytes
 * and a search reads one month at a time. The files are only a cache: lost, from an older embedding model, or out
 * of date, they are made again from the notes in the vault.
 */
class AiRecall(private val context: Context, private val gemini: Gemini) {

    private val dir get() = File(context.filesDir, DIR).apply { mkdirs() }

    private fun fileOf(month: String) = File(dir, "$month.bin")

    /** One month's vectors: which embedding model made them, the fingerprint of the month they cover, and the vectors by exchange. */
    class Shelf(val model: String, val digest: String, val vectors: Map<String, ByteArray>)

    fun load(month: String): Shelf? = read(fileOf(month))

    private fun read(f: File): Shelf? = runCatching {
        DataInputStream(f.inputStream().buffered()).use { s ->
            if (s.readInt() != MAGIC) return null
            val model = s.readUTF()
            val digest = s.readUTF()
            val dims = s.readInt()
            val n = s.readInt()
            val out = LinkedHashMap<String, ByteArray>(n)
            repeat(n) { out[s.readUTF()] = ByteArray(dims).also { s.readFully(it) } }
            Shelf(model, digest, out)
        }
    }.getOrNull()

    private fun write(month: String, shelf: Shelf) {
        val tmp = File(dir, "$month.tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { s ->
            s.writeInt(MAGIC)
            s.writeUTF(shelf.model)
            s.writeUTF(shelf.digest)
            s.writeInt(AiMemory.DIMS)
            s.writeInt(shelf.vectors.size)
            shelf.vectors.forEach { (id, v) -> s.writeUTF(id); s.write(v) }
        }
        tmp.renameTo(fileOf(month))
    }

    /** The months that have a file, so files of months that are gone can be dropped. */
    fun months(): List<String> = dir.list().orEmpty().filter { it.endsWith(".bin") }.map { it.removeSuffix(".bin") }

    fun drop(month: String) { fileOf(month).delete() }

    /** Whether [month]'s file is whole and made from the month as it is now ([digest]) by today's embedding model. */
    fun upToDate(month: String, digest: String): Boolean =
        load(month)?.let { it.digest == digest && it.model == AiKey.embedModel(context) } == true

    /**
     * Gives every exchange of [month] a vector: kept ones are reused, at most [budget] new ones are made now (the
     * rest on the next run). Returns how many were made. Blocking; throws when Gemini cannot be reached.
     */
    fun shelve(month: String, entries: List<AiMemory.Entry>, digest: String, budget: Int): Int {
        val old = load(month)?.takeIf { it.model == AiKey.embedModel(context) }
        val ids = entries.map { it.id }.toSet()
        val kept = LinkedHashMap(old?.vectors.orEmpty().filterKeys { it in ids })
        val missing = entries.filter { it.id !in kept }.take(budget.coerceAtLeast(0))
        var model = old?.model ?: AiKey.embedModel(context)
        missing.chunked(BATCH).forEach { batch ->
            val (used, vectors) = gemini.embed(batch.map(AiMemory::embedText), query = false)
            if (used != model) { kept.clear(); model = used }
            vectors.forEachIndexed { i, v -> kept[batch[i].id] = quantize(v) }
        }
        val whole = entries.all { it.id in kept }
        write(month, Shelf(model, if (whole) digest else "", kept))
        return missing.size
    }

    /**
     * The exchanges closest in meaning to [query] over every month kept (or only [months]), best first, as
     * (month, exchange id). Blocking; throws when Gemini cannot be reached.
     */
    fun search(query: String, k: Int, months: Collection<String>? = null, min: Float = 0.55f): List<Pair<String, String>> {
        val (model, qs) = gemini.embed(listOf(query.take(1000)), query = true)
        val q = quantize(qs.first())
        val best = ArrayList<Triple<Float, String, String>>()
        (months ?: months()).forEach { month ->
            val shelf = load(month)?.takeIf { it.model == model } ?: return@forEach
            shelf.vectors.forEach { (id, v) ->
                val score = cosine(q, v)
                if (score >= min) best += Triple(score, month, id)
            }
            if (best.size > k * 4) { best.sortByDescending { it.first }; while (best.size > k) best.removeAt(best.size - 1) }
        }
        return best.sortedByDescending { it.first }.take(k).map { it.second to it.third }
    }

    companion object {
        const val DIR = "ai-recall"
        private const val MAGIC = 0x4f4d5232 // "OMR2"
        private const val BATCH = 100

        /** The vector as one byte per number, scaled to its largest; the angle between vectors stays nearly the same. */
        fun quantize(v: FloatArray): ByteArray {
            val top = v.maxOfOrNull { abs(it) }?.takeIf { it > 0f } ?: 1f
            return ByteArray(v.size) { (v[it] / top * 127f).roundToInt().coerceIn(-127, 127).toByte() }
        }

        fun cosine(a: ByteArray, b: ByteArray): Float {
            if (a.isEmpty() || a.size != b.size) return 0f
            var dot = 0L; var na = 0L; var nb = 0L
            for (i in a.indices) { val x = a[i].toInt(); val y = b[i].toInt(); dot += x * y; na += x * x; nb += y * y }
            return if (na == 0L || nb == 0L) 0f else (dot / (sqrt(na.toDouble()) * sqrt(nb.toDouble()))).toFloat()
        }

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
