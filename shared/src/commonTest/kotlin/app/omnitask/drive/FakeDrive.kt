package app.omnitask.drive

/**
 * Google Drive in memory: a tree of files with versions that move on every write, each write kept as a revision,
 * a change log, and a switch for no network.
 */
class FakeDrive : DriveFiles {

    class Node(val id: String, var name: String, val folder: Boolean, var parent: String?, var text: String = "", var version: Int = 1, var trashed: Boolean = false) {
        /** Every text the file held, oldest first, by revision id. */
        val revisions = mutableListOf("$id-r1" to text)
        val head get() = revisions.last().first

        fun save(text: String) {
            this.text = text
            version++
            revisions += "$id-r$version" to text
        }
    }

    val nodes = LinkedHashMap<String, Node>()
    private var next = 0

    var offline = false

    /** Downloads of file text, to check what a refresh reads. */
    var downloads = 0

    /** Runs once right after the next read of a file's text, as if someone else wrote meanwhile. */
    var afterRead: ((Node) -> Unit)? = null

    /** Runs once right before the next write lands, as if someone else wrote after the version check. */
    var beforeWrite: ((Node) -> Unit)? = null

    /** Requests of each kind, to check what a refresh asks. */
    var listings = 0
    var changeReads = 0
    var revisionAsks = 0

    /** Drive's change log: the id of each file or folder touched, in order. A token is a position in it. */
    val log = ArrayList<String>()

    /** Drive forgot the log before this position (a token older than it is refused). */
    var forgottenBefore = 0

    /** Records that [id] changed in a way the store must see (a test changing a node by hand). */
    fun touch(id: String) {
        log += id
    }

    fun folder(name: String, parent: String?): String {
        val id = "d${next++}"
        nodes[id] = Node(id, name, true, parent)
        log += id
        return id
    }

    fun file(name: String, parent: String, text: String): String {
        val id = "f${next++}"
        nodes[id] = Node(id, name, false, parent, text)
        log += id
        return id
    }

    /** Someone else (the web, Obsidian) changes a file. */
    fun change(id: String, text: String) {
        nodes.getValue(id).save(text)
        log += id
    }

    fun textOf(parent: String, name: String): String? = nodes.values.firstOrNull { it.parent == parent && it.name == name && !it.trashed }?.text

    private fun up() {
        if (offline) throw DriveUnavailable(message = "offline")
    }

    private fun node(id: String): Node = nodes[id]?.takeIf { !it.trashed } ?: throw DriveHttpError(404, "not found")

    private fun entry(n: Node) = DriveEntry(n.id, n.name, if (n.folder) DriveRest.FOLDER else "text/markdown", listOfNotNull(n.parent), n.version.toString())

    override fun findFolders(name: String): List<DriveEntry> {
        up()
        return nodes.values.filter { it.folder && !it.trashed && it.name == name }.map(::entry)
    }

    override fun childrenOfMany(parentIds: List<String>): List<DriveEntry> {
        up()
        listings++
        return nodes.values.filter { !it.trashed && it.parent in parentIds }.map(::entry)
    }

    override fun get(id: String): DriveEntry {
        up()
        return entry(node(id))
    }

    override fun read(id: String): Versioned {
        up()
        val n = node(id)
        downloads++
        val out = Versioned(n.text, n.version.toString(), n.head)
        afterRead?.let { afterRead = null; it(n) }
        return out
    }

    override fun version(id: String): String {
        up()
        return node(id).version.toString()
    }

    override fun write(id: String, text: String, mimeType: String): Written {
        up()
        val n = node(id)
        beforeWrite?.let { beforeWrite = null; it(n) }
        n.save(text)
        log += id
        return Written(n.version.toString(), n.head)
    }

    override fun create(parentId: String, name: String, mimeType: String, text: String): DriveEntry {
        up()
        return entry(nodes.getValue(file(name, parentId, text)))
    }

    override fun createFolder(parentId: String, name: String): String {
        up()
        return folder(name, parentId)
    }

    override fun trash(id: String) {
        up()
        node(id).trashed = true
        log += id
    }

    override fun revisionBefore(id: String, revision: String): String? {
        up()
        revisionAsks++
        val revs = node(id).revisions
        val at = revs.indexOfFirst { it.first == revision }
        return if (at > 0) revs[at - 1].first else null
    }

    override fun readRevision(id: String, revision: String): String {
        up()
        return node(id).revisions.firstOrNull { it.first == revision }?.second ?: throw DriveHttpError(404, "no revision")
    }

    override fun startPageToken(): String {
        up()
        return log.size.toString()
    }

    override fun changes(token: String): DriveChanges {
        up()
        changeReads++
        val from = token.toInt()
        if (from < forgottenBefore) throw DriveHttpError(400, "invalid page token")
        val out = log.subList(from, log.size).distinct().map { id ->
            val n = nodes[id]
            if (n == null) DriveChange(id, true, null) else DriveChange(id, false, entry(n), n.trashed)
        }
        return DriveChanges(out, log.size.toString())
    }
}
