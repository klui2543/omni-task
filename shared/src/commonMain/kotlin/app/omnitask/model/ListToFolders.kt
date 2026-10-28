package app.omnitask.model

import app.omnitask.data.VaultText

/**
 * Lists become plain project folders (owner's pick, 2026-10-28, way ก: "ตัด list ออกไปแล้วให้เป็นแค่โฟลเดอร์ธรรมดา"):
 * Bucket list is the project `bucketlist`, its folders are branches. The lines stay in the list's note; each one gets
 * the folder tag it sits in (`#เที่ยว` becomes `#bucketlist/เที่ยว`, a line with no tag gets `#bucketlist`), and the
 * header that made the note a list goes, so the apps read it as any other note.
 */
object ListToFolders {

    class Result(
        val list: OmniList,
        val text: String,
        /** Task lines rewritten. */
        val changed: Int,
        /** The folders saved in the header (`bucketlist/เรียนรู้`), to be kept as branches. */
        val folders: List<String>,
    )

    /** The note at [path] made a plain note, or null when it is not a list; [otherLists] are the other lists' tags. */
    fun migrate(path: String, text: String, otherLists: Collection<String> = emptyList()): Result? {
        val list = OmniList.parse(path, text) ?: return null
        val separator = if ("\r\n" in text) "\r\n" else "\n"
        val lines = text.split(separator).toMutableList()
        var changed = 0
        VaultText.parseFile(path, path, text).forEach { t ->
            val i = t.lineIndex
            if (lines.getOrNull(i) != t.raw) return@forEach
            // A subtask without a tag of the list belongs to its parent; it is left as it is.
            if (t.parent != null && t.tags.none { ListFolders.isListTag(list, it) } && ListFolders.pathOf(list, t).isEmpty()) return@forEach
            // A line here put in another list (`#watchlist/หนัง`) goes with that list, not this one.
            if (t.tags.none { ListFolders.isListTag(list, it) } && t.tags.any { tag -> otherLists.any { tag.equals(it, true) || tag.startsWith("$it/", true) } }) return@forEach
            val next = ListFolders.place(t.raw, list, ListFolders.pathOf(list, t), inNote = true)
            if (next != t.raw) {
                lines[i] = next
                changed++
            }
        }
        val folders = (list.categories + list.folders).map(ListFolders::clean).filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.map { "${list.tag}/$it" }
        return Result(list, withoutHeader(lines.joinToString(separator), separator), changed, folders)
    }

    private val KEYS = setOf("omni-list", "icon", "categories", "tag", "folders")

    /** [text] without the header keys of a list; the header goes when nothing else is in it. */
    private fun withoutHeader(text: String, separator: String): String {
        if (!text.startsWith("---")) return text
        val lines = text.split(separator)
        val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: return text
        val kept = lines.subList(1, end).filterNot { line -> line.substringBefore(':').trim() in KEYS && ':' in line }
        val body = lines.drop(end + 1).dropWhile { it.isBlank() }
        val head = if (kept.all { it.isBlank() }) emptyList() else listOf("---") + kept + "---"
        return (head + body).joinToString(separator)
    }

    /**
     * A line in another note put in [list] (tagged `#bucketlist #เที่ยว`), with its folder written as one tag, or null
     * when it needs nothing.
     */
    fun pulledLine(list: OmniList, t: Task): String? {
        if (t.list != null || t.todayCopy || t.tags.none { ListFolders.isListTag(list, it) }) return null
        return ListFolders.place(t.raw, list, ListFolders.pathOf(list, t)).takeIf { it != t.raw }
    }
}
