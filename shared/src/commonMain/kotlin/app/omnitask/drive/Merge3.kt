package app.omnitask.drive

/**
 * Joins two edits of one note made from the same starting text, line by line: edits made offline on the phone
 * ([local]) and what Drive holds now ([remote]), both from [base].
 *
 * A note's lines are tasks, so changes to different lines never clash, even next to each other. Where both sides
 * changed the same lines differently, Drive's version is kept and the phone's lines are reported in [Result.lost],
 * so nothing is overwritten unseen. When both added lines at the same place, both additions are kept (Drive's
 * first) and a line added on both sides appears once.
 */
object Merge3 {

    class Result(val text: String, val lost: List<List<String>>)

    /** Lines of [base] from [start] until [end] replaced by [lines]; an insertion when start == end. */
    private class Hunk(val start: Int, val end: Int, val lines: List<String>)

    fun merge(base: String, local: String, remote: String): Result {
        if (local == base || local == remote) return Result(remote, emptyList())
        if (remote == base) return Result(local, emptyList())
        val b = base.split("\n")
        val mine = diff(b, local.split("\n"))
        val theirs = diff(b, remote.split("\n"))
        val out = ArrayList<String>()
        val lost = ArrayList<List<String>>()
        var pos = 0
        var i = 0
        var j = 0
        while (i < mine.size || j < theirs.size) {
            // The next group: hunks of either side that touch each other, starting from the earliest.
            val first = when {
                i >= mine.size -> theirs[j]
                j >= theirs.size -> mine[i]
                // At the same place an insertion goes before a change of the lines that follow it.
                mine[i].start < theirs[j].start || (mine[i].start == theirs[j].start && mine[i].end <= theirs[j].end) -> mine[i]
                else -> theirs[j]
            }
            var start = first.start
            var end = first.end
            val gi = i
            val gj = j
            var grew = true
            while (grew) {
                grew = false
                if (i < mine.size && (mine[i] === first || touches(mine[i], start, end))) {
                    start = minOf(start, mine[i].start); end = maxOf(end, mine[i].end); i++; grew = true
                }
                if (j < theirs.size && (theirs[j] === first || touches(theirs[j], start, end))) {
                    start = minOf(start, theirs[j].start); end = maxOf(end, theirs[j].end); j++; grew = true
                }
            }
            while (pos < start) out += b[pos++]
            val myHunks = mine.subList(gi, i)
            val theirHunks = theirs.subList(gj, j)
            when {
                myHunks.isEmpty() -> out += apply(b, start, end, theirHunks)
                theirHunks.isEmpty() -> out += apply(b, start, end, myHunks)
                else -> {
                    val m = apply(b, start, end, myHunks)
                    val t = apply(b, start, end, theirHunks)
                    when {
                        m == t -> out += t
                        start == end -> out += t + m.filter { it !in t }
                        else -> {
                            out += t
                            if (m.isNotEmpty()) lost += m
                        }
                    }
                }
            }
            pos = maxOf(pos, end)
        }
        while (pos < b.size) out += b[pos++]
        return Result(out.joinToString("\n"), lost)
    }

    /**
     * Whether [h] belongs to the group over base lines [start, end): it overlaps them, or both are insertions at
     * the same place, or it inserts strictly inside them. Changes that only meet at an edge stay apart.
     */
    private fun touches(h: Hunk, start: Int, end: Int): Boolean = when {
        h.start == h.end && start == end -> h.start == start
        h.start == h.end -> h.start in (start + 1) until end
        start == end -> start in (h.start + 1) until h.end
        else -> h.start < end && start < h.end
    }

    /** Base lines [start, end) with [hunks] (all inside that range, in order) applied. */
    private fun apply(b: List<String>, start: Int, end: Int, hunks: List<Hunk>): List<String> {
        val out = ArrayList<String>()
        var pos = start
        for (h in hunks) {
            while (pos < h.start) out += b[pos++]
            out += h.lines
            pos = maxOf(pos, h.end)
        }
        while (pos < end) out += b[pos++]
        return out
    }

    /** The changes that turn [a] into [c], as hunks in order, from a longest common subsequence of lines. */
    private fun diff(a: List<String>, c: List<String>): List<Hunk> {
        var pre = 0
        while (pre < a.size && pre < c.size && a[pre] == c[pre]) pre++
        var suf = 0
        while (suf < a.size - pre && suf < c.size - pre && a[a.size - 1 - suf] == c[c.size - 1 - suf]) suf++
        val x = a.subList(pre, a.size - suf)
        val y = c.subList(pre, c.size - suf)
        val n = x.size
        val m = y.size
        // lcs[i][j]: length of the longest common subsequence of x[i..] and y[j..].
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (p in n - 1 downTo 0) for (q in m - 1 downTo 0) {
            lcs[p][q] = if (x[p] == y[q]) lcs[p + 1][q + 1] + 1 else maxOf(lcs[p + 1][q], lcs[p][q + 1])
        }
        val hunks = ArrayList<Hunk>()
        var p = 0
        var q = 0
        var hs = -1
        var added = ArrayList<String>()
        fun close() {
            if (hs >= 0) hunks += Hunk(pre + hs, pre + p, added)
            hs = -1
            added = ArrayList()
        }
        while (p < n || q < m) {
            when {
                p < n && q < m && x[p] == y[q] -> { close(); p++; q++ }
                q < m && (p == n || lcs[p][q + 1] >= lcs[p + 1][q]) -> { if (hs < 0) hs = p; added += y[q]; q++ }
                else -> { if (hs < 0) hs = p; p++ }
            }
        }
        close()
        return hunks
    }
}
