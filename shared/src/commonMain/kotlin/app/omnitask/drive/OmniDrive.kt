package app.omnitask.drive

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One file of the Omni folder as last read from Drive, or as edited on the phone while Drive was out of reach. */
@Serializable
class CachedFile(
    var id: String? = null,
    var version: String = "",
    var text: String = "",
    /** Drive's text when the first offline edit was made: what [Merge3] joins from. Null for a file made offline. */
    var base: String? = null,
    /** [text] holds edits not on Drive yet. */
    var pending: Boolean = false,
)

/** Lines edited on the phone while offline that clashed with a change made elsewhere; Drive's version was kept. */
@Serializable
class LostEdit(val path: String, val lines: List<String>)

@Serializable
class OmniDriveState(
    var dirId: String? = null,
    /** By path inside the Omni folder, e.g. "Omni note.md" or "Notes/x.md". */
    val files: MutableMap<String, CachedFile> = mutableMapOf(),
    /** Folder ids by path inside the Omni folder ("" is the folder itself). */
    val folders: MutableMap<String, String> = mutableMapOf(),
    /** The names in each folder at the last listing, conflict copies and other files included. */
    val names: MutableMap<String, List<String>> = mutableMapOf(),
    val lost: MutableList<LostEdit> = mutableListOf(),
    /** When Drive was last read in full (epoch milliseconds), 0 before the first time. */
    var lastSync: Long = 0,
)

/**
 * The Omni folder on Google Drive as the phone reads and writes it: Drive is the one copy both the phone and the web
 * write, so there is no second copy for a sync app to clash over.
 *
 * Every write reads the file fresh, applies the change, checks that the version did not move, and writes (as the web
 * does). While Drive is out of reach the change goes onto the cached text and the file is marked pending; the next
 * time Drive answers, the pending text is joined with what Drive has now ([Merge3]) and written.
 *
 * Not thread safe: the app holds one and takes a lock around every call. Paths are relative to the Omni folder.
 */
class OmniDrive(private val drive: DriveFiles, saved: String?, private val persist: (String) -> Unit) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val state: OmniDriveState = saved?.let { runCatching { json.decodeFromString(OmniDriveState.serializer(), it) }.getOrNull() } ?: OmniDriveState()

    val connected get() = state.dirId != null

    val pendingPaths: List<String> get() = state.files.filterValues { it.pending }.keys.sorted()

    /** Starts working on the Omni folder [dirId]; the cache of another folder is dropped. */
    fun connect(dirId: String) {
        if (state.dirId != dirId) {
            clear()
            state.dirId = dirId
        }
        save()
    }

    /** Stops using Drive. Edits still pending are dropped, so the app asks first. */
    fun disconnect() {
        clear()
        save()
    }

    private fun clear() {
        state.dirId = null
        state.files.clear()
        state.folders.clear()
        state.names.clear()
        state.lost.clear()
        state.lastSync = 0
    }

    class Listing(
        /** Text of every Markdown and JSON file of the folder, by path. */
        val texts: Map<String, String>,
        /** Whether Drive was reached; false means the texts are the cached ones. */
        val online: Boolean,
        /** Why Drive was not read, when it was not. */
        val problem: Exception? = null,
    )

    /** Sends pending edits, then reads the folder, downloading only files whose version changed. */
    fun refresh(): Listing {
        val dir = state.dirId ?: return Listing(emptyMap(), false)
        val problem = try {
            flushPending()
            walk(dir)
            state.lastSync = Clock.System.now().toEpochMilliseconds()
            null
        } catch (e: DriveUnavailable) {
            e
        } catch (e: DriveHttpError) {
            e
        }
        save()
        return Listing(state.files.filter { isText(it.key) }.mapValues { it.value.text }, problem == null, problem)
    }

    /** The file's text: fresh when Drive answers (one version check when nothing changed), else as cached. */
    fun read(path: String): String? {
        val k = key(path) ?: path
        state.files[k]?.takeIf { it.pending }?.let { runCatching { flushOne(k, it) } }
        val c = state.files[k]
        if (c?.pending == true) return c.text
        return try {
            val id = c?.id ?: resolve(path) ?: return null
            val version = drive.version(id)
            if (c != null && c.id == id && c.version == version) return c.text
            val fresh = drive.read(id)
            state.files[k] = CachedFile(id, fresh.version, fresh.text)
            save()
            fresh.text
        } catch (e: DriveUnavailable) {
            c?.text
        } catch (e: DriveHttpError) {
            if (e.status != 404) throw e
            state.files.remove(k)
            save()
            null
        }
    }

    /**
     * Changes the file with [op], which gets its text (null when there is none) and returns the new text, or null
     * to leave it. Returns the new text, or null when nothing changed. Exceptions from [op] pass through untouched.
     */
    fun edit(path: String, op: (String?) -> String?): String? {
        val k = key(path) ?: path
        state.files[k]?.takeIf { it.pending }?.let { runCatching { flushOne(k, it) } }
        if (state.files[k]?.pending != true) {
            try {
                return editOnline(k, op)
            } catch (e: DriveUnavailable) {
                // Kept on the phone below and sent later.
            }
        }
        val c = state.files[k]
        val current = c?.text
        val out = op(current) ?: return null
        if (out == current) return null
        val entry = c ?: CachedFile()
        if (!entry.pending) {
            entry.base = if (c == null || c.id == null) null else c.text
            entry.pending = true
        }
        entry.text = out
        state.files[k] = entry
        save()
        return out
    }

    private fun editOnline(path: String, op: (String?) -> String?): String? {
        repeat(ATTEMPTS) {
            val c = state.files[path]
            val id = c?.id ?: resolve(path)
            val fresh = try {
                id?.let { drive.read(it) }
            } catch (e: DriveHttpError) {
                if (e.status != 404) throw e
                // Moved or deleted since it was cached: look it up again by name.
                state.files.remove(path)
                return@repeat
            }
            val out = op(fresh?.text) ?: return null
            if (out == fresh?.text) return null
            if (id != null && fresh != null) {
                if (drive.version(id) != fresh.version) return@repeat
                state.files[path] = CachedFile(id, drive.write(id, out, mimeOf(path)), out)
            } else {
                val made = drive.create(folderId(parentOf(path), create = true)!!, nameOf(path), mimeOf(path), out)
                state.files[path] = CachedFile(made.id, made.version, out)
                state.names[parentOf(path)] = state.names[parentOf(path)].orEmpty() + made.name
            }
            save()
            return out
        }
        throw DriveUnavailable(message = "busy")
    }

    /** Moves the file to Drive's trash. Needs Drive: throws [DriveUnavailable] when it cannot be reached. */
    fun delete(path: String) {
        val k = key(path) ?: path
        val id = state.files[k]?.id ?: resolve(path)
        if (id != null) drive.trash(id)
        state.files.remove(k)
        state.names[parentOf(k)]?.let { names -> state.names[parentOf(k)] = names.filterNot { sameName(it, nameOf(k)) } }
        save()
    }

    /** Whether Drive answers right now (one small request). */
    fun reachable(): Boolean {
        val dir = state.dirId ?: return false
        return try {
            drive.version(dir)
            true
        } catch (e: DriveUnavailable) {
            false
        } catch (e: DriveHttpError) {
            false
        }
    }

    /** Names in a folder of the Omni folder at the last listing (empty when unknown). */
    fun namesIn(dir: String): List<String> = state.names.entries.firstOrNull { samePath(it.key, dir) }?.value.orEmpty()

    /** Edits made offline that clashed with a change made elsewhere, once each. */
    fun takeLost(): List<LostEdit> {
        if (state.lost.isEmpty()) return emptyList()
        val out = state.lost.toList()
        state.lost.clear()
        save()
        return out
    }

    /** Sends every pending file; one Drive refuses stays pending, and no network stops the round. */
    private fun flushPending() {
        for (path in pendingPaths) {
            try {
                state.files[path]?.let { flushOne(path, it) }
            } catch (e: DriveHttpError) {
                // Left pending; tried again next time.
            }
        }
    }

    /** Sends one pending file: joined with Drive's current text when that moved since the phone's copy. */
    private fun flushOne(path: String, c: CachedFile) {
        repeat(ATTEMPTS) {
            val id = c.id ?: resolve(path)
            if (id == null) {
                val made = drive.create(folderId(parentOf(path), create = true)!!, nameOf(path), mimeOf(path), c.text)
                state.files[path] = CachedFile(made.id, made.version, c.text)
                save()
                return
            }
            val remote = drive.read(id)
            val merged = if (c.id == id && remote.version == c.version) Merge3.Result(c.text, emptyList())
            else Merge3.merge(c.base ?: "", c.text, remote.text)
            var version = remote.version
            if (merged.text != remote.text) {
                if (drive.version(id) != remote.version) return@repeat
                version = drive.write(id, merged.text, mimeOf(path))
            }
            state.files[path] = CachedFile(id, version, merged.text)
            merged.lost.forEach { state.lost += LostEdit(path, it) }
            save()
            return
        }
    }

    /** Lists the folder and its subfolders, and downloads each Markdown or JSON file whose version changed. */
    private fun walk(dir: String) {
        val files = LinkedHashMap<String, DriveEntry>()
        val folders = linkedMapOf("" to dir)
        val names = HashMap<String, MutableList<String>>()
        var level = listOf("" to dir)
        while (level.isNotEmpty()) {
            val pathOf = level.associate { (path, id) -> id to path }
            level.forEach { names[it.first] = ArrayList() }
            val next = ArrayList<Pair<String, String>>()
            for (e in drive.childrenOfMany(level.map { it.second })) {
                val parent = e.parents.firstNotNullOfOrNull { pathOf[it] } ?: continue
                names.getValue(parent) += e.name
                // .trash and other hidden folders hold nothing live.
                if (e.name.startsWith(".")) continue
                val path = if (parent.isEmpty()) e.name else "$parent/${e.name}"
                if (e.isFolder) {
                    if (path !in folders) {
                        folders[path] = e.id
                        next += path to e.id
                    }
                } else if (isText(e.name) && path !in files) {
                    files[path] = e
                }
            }
            level = next
        }
        for ((path, e) in files) {
            val c = state.files[path]
            if (c != null && (c.pending || (c.id == e.id && c.version == e.version))) continue
            val fresh = drive.read(e.id)
            state.files[path] = CachedFile(e.id, fresh.version, fresh.text)
        }
        state.files.keys.filter { it !in files && state.files[it]?.pending != true }.forEach { state.files.remove(it) }
        state.folders.clear()
        state.folders.putAll(folders)
        state.names.clear()
        state.names.putAll(names)
    }

    /** The file's id on Drive, looked up by name folder by folder; null when it is not there. */
    private fun resolve(path: String): String? {
        val parent = folderId(parentOf(path), create = false) ?: return null
        return drive.childrenOfMany(listOf(parent)).firstOrNull { !it.isFolder && sameName(it.name, nameOf(path)) }?.id
    }

    /** The id of a folder inside the Omni folder, made when missing if [create]. */
    private fun folderId(dir: String, create: Boolean): String? {
        var id = state.dirId ?: return null
        if (dir.isEmpty()) return id
        var path = ""
        for (segment in dir.split('/')) {
            path = if (path.isEmpty()) segment else "$path/$segment"
            val known = state.folders.entries.firstOrNull { samePath(it.key, path) }?.value
            id = known ?: drive.childrenOfMany(listOf(id)).firstOrNull { it.isFolder && sameName(it.name, segment) }?.id
                ?: if (create) drive.createFolder(id, segment) else return null
            state.folders[path] = id
        }
        return id
    }

    /** The cache key that names [path], allowing for emoji written differently. */
    private fun key(path: String): String? = if (path in state.files) path else state.files.keys.firstOrNull { samePath(it, path) }

    private fun save() = persist(json.encodeToString(OmniDriveState.serializer(), state))

    companion object {
        private const val ATTEMPTS = 3

        fun isText(name: String) = name.endsWith(".md", ignoreCase = true) || name.endsWith(".json", ignoreCase = true)

        fun mimeOf(path: String) = if (path.endsWith(".json", ignoreCase = true)) "application/json" else "text/markdown"

        fun parentOf(path: String) = path.substringBeforeLast('/', "")

        fun nameOf(path: String) = path.substringAfterLast('/')

        fun samePath(a: String, b: String): Boolean {
            val x = a.split('/')
            val y = b.split('/')
            return x.size == y.size && x.indices.all { sameName(x[it], y[it]) }
        }

        /**
         * Finds the Omni folder on Drive: a folder named like the last part of [dirPath] whose parents carry the
         * other parts (e.g. "📁 Folder/หลังบ้าน/Omni"). With more than one, the one inside a folder named [vaultName].
         */
        fun locate(drive: DriveFiles, dirPath: String, vaultName: String?): String? {
            val segments = dirPath.split('/')
            val found = drive.findFolders(segments.last()).mapNotNull { folder ->
                var parentId = folder.parents.firstOrNull() ?: return@mapNotNull null
                for (segment in segments.dropLast(1).asReversed()) {
                    val parent = drive.get(parentId)
                    if (!sameName(parent.name, segment)) return@mapNotNull null
                    parentId = parent.parents.firstOrNull() ?: return@mapNotNull null
                }
                folder.id to drive.get(parentId).name
            }
            return (found.firstOrNull { vaultName != null && sameName(it.second, vaultName) } ?: found.firstOrNull())?.first
        }
    }
}
