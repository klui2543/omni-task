package app.omnitask.data

import android.content.Context
import android.net.Uri
import app.omnitask.model.AiIndex
import app.omnitask.model.AiMemory
import java.io.File
import java.time.LocalDate
import org.json.JSONObject

/**
 * The conversation log as a library that stays in order for decades (see [AiIndex]): what a question reads, how
 * old exchanges are found, and the upkeep that closes months, writes the index and the README, and keeps the
 * search files. Everything here is blocking: call it off the main thread.
 */
class AiLibrary(private val context: Context, private val repo: VaultRepository, private val gemini: Gemini) {

    val recall = AiRecall(context, gemini)

    private fun kd(day: LocalDate) = kotlinx.datetime.LocalDate(day.year, day.monthValue, day.dayOfMonth)

    // ---- what a question reads ----

    /**
     * The exchanges of this month and the last, newest first: what goes to the model whole (or its newest part).
     * Older months reach the model as their index lines, and by search.
     */
    fun recent(vault: Uri, today: LocalDate): List<AiMemory.Entry> {
        runCatching { moveLegacy(vault) }
        val now = AiIndex.monthKey(kd(today))
        return AiMemory.merge(listOf(now, AiIndex.previous(now)).map { read(vault, AiIndex.pathOf(it)) })
    }

    fun index(vault: Uri): String? = read(vault, AiIndex.INDEX_FILE)

    private fun read(vault: Uri, path: String): String? = runCatching { repo.readPath(vault, path) }.getOrNull()

    /**
     * Up to [k] past exchanges about [query] that are not among [recent], best first: by meaning over every month
     * this phone has vectors for, or, without Gemini, by words in the months whose summary matches.
     */
    fun find(vault: Uri, query: String, recent: List<AiMemory.Entry>, index: String?, k: Int = 6): List<AiMemory.Entry> {
        if (query.isBlank()) return emptyList()
        val recentIds = recent.associateBy { it.id }
        val notes = HashMap<String, Map<String, AiMemory.Entry>>()
        fun entryOf(month: String, id: String): AiMemory.Entry? = recentIds[id] ?: notes.getOrPut(month) {
            AiMemory.entries(read(vault, AiIndex.pathOf(month))).associateBy { it.id }
        }[id]
        val byMeaning = runCatching {
            if (AiKey.get(context) == null) return@runCatching emptyList<AiMemory.Entry>()
            recall.search(query, k).mapNotNull { (month, id) -> entryOf(month, id) }
        }.getOrDefault(emptyList())
        if (byMeaning.isNotEmpty()) return byMeaning
        val months = AiIndex.monthsByWords(AiIndex.months(index), query, 3, { AiRecall.thaiWords(it) })
        val pool = recent + months.flatMap { m -> AiMemory.entries(read(vault, AiIndex.pathOf(m.key))) }
        return AiMemory.byWords(pool, query, k, { AiRecall.thaiWords(it) })
    }

    // ---- upkeep ----

    private val stateFile get() = File(context.filesDir, STATE)

    private fun loadState(): JSONObject = runCatching { JSONObject(stateFile.readText()) }.getOrElse { JSONObject() }

    private fun saveState(o: JSONObject) = runCatching { stateFile.writeText(o.toString()) }

    /**
     * Keeps the library in order. Runs fully at most once a day ([force] runs it now): the README in today's form,
     * every month before this one closed with its line in the index (summed up by Gemini, or by its topics until
     * Gemini can), each year over summed up, and the search files made for what is new. Once a week every month is
     * read again, so one the owner edited is summed up again. A step that fails is tried again next time; nothing
     * here can lose a line of the log.
     */
    fun upkeep(vault: Uri, today: LocalDate, force: Boolean = false) {
        val state = loadState()
        val now = System.currentTimeMillis()
        if (!force && now - state.optLong("lastRun") < DAY_MS) return
        val full = force || now - state.optLong("lastFull") > WEEK_MS
        val digests = state.optJSONObject("digests") ?: JSONObject()
        val hasKey = AiKey.get(context) != null
        val tday = kd(today)
        val thisMonth = AiIndex.monthKey(tday)

        runCatching { moveLegacy(vault) }
        runCatching {
            if (AiIndex.readmeNeeded(read(vault, AiIndex.README_FILE))) repo.writePath(vault, AiIndex.README_FILE, AiIndex.README)
        }

        val months = runCatching {
            AiMemory.yearFolders(repo.namesIn(vault, AiMemory.LOG_DIR)).flatMap { y ->
                AiMemory.monthNotes(repo.namesIn(vault, "${AiMemory.LOG_DIR}/$y")).mapNotNull { AiIndex.monthKeyOf("$y/$it") }
            }
        }.getOrElse { return }

        var index = read(vault, AiIndex.INDEX_FILE)
        val listed = AiIndex.months(index).associateBy { it.key }
        var summaries = 0
        var budget = EMBED_PER_RUN
        months.filter { it < thisMonth }.sortedDescending().forEach { key ->
            val line = listed[key]
            val redo = line == null || (hasKey && line.auto) || !digests.has(key) || full || (hasKey && !recall.upToDate(key, digests.optString(key)))
            if (!redo) return@forEach
            val path = AiIndex.pathOf(key)
            val text = read(vault, path) ?: return@forEach
            val entries = AiMemory.entries(text)
            val digest = AiIndex.digest(entries)
            val changed = digests.optString(key) != digest
            if (line == null || changed || (hasKey && line.auto)) {
                if (entries.isEmpty()) return@forEach
                val month = if (hasKey && summaries < SUMMARIES_PER_RUN) {
                    summaries++
                    runCatching {
                        AiIndex.Month(key, entries.size, AiIndex.summaryText(gemini.text(AiIndex.monthRequest(key, entries))), AiIndex.topTags(entries))
                    }.getOrNull()
                } else null
                val put = month ?: AiIndex.autoMonth(key, entries).takeIf { line == null || changed }
                if (put != null) {
                    runCatching {
                        repo.editPath(vault, AiIndex.INDEX_FILE) { old -> AiIndex.setMonth(old, put) }
                        digests.put(key, digest)
                    }
                }
            }
            if (!AiIndex.isClosed(text)) runCatching { repo.editPath(vault, path) { old -> old?.let { AiIndex.withFront(it, key, tday) } } }
            if (hasKey && budget > 0 && !recall.upToDate(key, digest)) {
                runCatching { budget -= recall.shelve(key, entries, digest, budget) }
            }
        }

        // This month and the last get their vectors too, so a search finds them by meaning while they are recent.
        if (hasKey) listOf(thisMonth, AiIndex.previous(thisMonth)).forEach { key ->
            val entries = AiMemory.entries(read(vault, AiIndex.pathOf(key)))
            if (entries.isNotEmpty() && budget > 0) runCatching { budget -= recall.shelve(key, entries, AiIndex.digest(entries), budget) }
        }

        // A year that is over, with every month summed up by Gemini, is summed up once.
        index = read(vault, AiIndex.INDEX_FILE)
        if (hasKey) {
            val all = AiIndex.months(index)
            val done = AiIndex.years(index)
            all.map { it.key.take(4) }.distinct().filter { it !in done && AiIndex.yearReady(it, all, tday) }.forEach { year ->
                runCatching {
                    val summary = AiIndex.summaryText(gemini.text(AiIndex.yearRequest(year, all.filter { it.key.startsWith("$year-") })))
                    repo.editPath(vault, AiIndex.INDEX_FILE) { old -> AiIndex.setYear(old, year, summary) }
                }
            }
        }

        // Search files of months that are no longer in the vault go too.
        recall.months().filter { it !in months }.forEach(recall::drop)
        File(context.filesDir, OLD_CACHE).delete()

        state.put("lastRun", now)
        if (full) state.put("lastFull", now)
        state.put("digests", digests)
        saveState(state)
    }

    /** The first version's single log note, cut into month notes once and then removed. */
    private fun moveLegacy(vault: Uri) {
        val old = repo.readPath(vault, AiMemory.LEGACY_LOG_FILE) ?: return
        val split = AiMemory.splitByMonth(old)
        // The old note goes only once every month note has what it held; a failure leaves it for the next try.
        split.forEach { (path, moved) -> repo.editPath(vault, path) { text -> AiMemory.addMoved(text, moved) } }
        repo.deletePath(vault, AiMemory.LEGACY_LOG_FILE)
    }

    companion object {
        private const val STATE = "ai-library.json"
        private const val OLD_CACHE = "ai-recall.json"
        private const val DAY_MS = 20 * 60 * 60 * 1000L
        private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L

        /** At most this many months are summed up by Gemini in one run, so catching up never uses the quota at once. */
        private const val SUMMARIES_PER_RUN = 6

        /** At most this many exchanges are turned into vectors in one run; the rest follow on the next days. */
        private const val EMBED_PER_RUN = 300
    }
}
