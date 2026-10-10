package app.omnitask.drive

/** Google Drive in memory: a tree of files with versions that move on every write, and a switch for no network. */
class FakeDrive : DriveFiles {

    class Node(val id: String, var name: String, val folder: Boolean, var parent: String?, var text: String = "", var version: Int = 1, var trashed: Boolean = false)

    val nodes = LinkedHashMap<String, Node>()
    private var next = 0

    var offline = false

    /** Downloads of file text, to check what a refresh reads. */
    var downloads = 0

    /** Runs once right after the next read of a file's text, as if someone else wrote meanwhile. */
    var afterRead: ((Node) -> Unit)? = null

    fun folder(name: String, parent: String?): String {
        val id = "d${next++}"
        nodes[id] = Node(id, name, true, parent)
        return id
    }

    fun file(name: String, parent: String, text: String): String {
        val id = "f${next++}"
        nodes[id] = Node(id, name, false, parent, text)
        return id
    }

    /** Someone else (the web, Obsidian) changes a file. */
    fun change(id: String, text: String) {
        val n = nodes.getValue(id)
        n.text = text
        n.version++
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
        val out = Versioned(n.text, n.version.toString())
        afterRead?.let { afterRead = null; it(n) }
        return out
    }

    override fun version(id: String): String {
        up()
        return node(id).version.toString()
    }

    override fun write(id: String, text: String, mimeType: String): String {
        up()
        val n = node(id)
        n.text = text
        n.version++
        return n.version.toString()
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
    }
}
