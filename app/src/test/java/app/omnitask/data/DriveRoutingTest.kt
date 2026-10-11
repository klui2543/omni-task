package app.omnitask.data

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.omnitask.drive.DriveEntry
import app.omnitask.drive.DriveFiles
import app.omnitask.drive.DriveHttpError
import app.omnitask.drive.DriveRest
import app.omnitask.drive.DriveUnavailable
import app.omnitask.drive.OmniDrive
import app.omnitask.drive.Versioned
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With Drive connected, the files of the Omni folder are read and written on Drive (a stand-in here) and every
 * other note stays on the picked folder. The picked folder has no provider in this test, so it reads as empty.
 */
@RunWith(AndroidJUnit4::class)
class DriveRoutingTest {

    /** Drive in memory, with a switch for no network. */
    private class MemoryDrive : DriveFiles {
        class Node(val id: String, val name: String, val folder: Boolean, val parent: String, var text: String = "", var version: Int = 1, var trashed: Boolean = false)

        val nodes = LinkedHashMap<String, Node>()
        var offline = false

        fun folder(name: String, parent: String) = add(Node("d${nodes.size}", name, true, parent))
        fun file(name: String, parent: String, text: String) = add(Node("f${nodes.size}", name, false, parent, text))
        private fun add(n: Node): String = n.id.also { nodes[it] = n }
        fun text(parent: String, name: String) = nodes.values.firstOrNull { it.parent == parent && it.name == name && !it.trashed }?.text

        private fun up() { if (offline) throw DriveUnavailable(message = "offline") }
        private fun node(id: String) = nodes[id]?.takeIf { !it.trashed } ?: throw DriveHttpError(404, "gone")
        private fun entry(n: Node) = DriveEntry(n.id, n.name, if (n.folder) DriveRest.FOLDER else "text/markdown", listOf(n.parent), n.version.toString())

        override fun findFolders(name: String) = run { up(); nodes.values.filter { it.folder && it.name == name }.map(::entry) }
        override fun childrenOfMany(parentIds: List<String>) = run { up(); nodes.values.filter { !it.trashed && it.parent in parentIds }.map(::entry) }
        override fun get(id: String) = run { up(); entry(node(id)) }
        override fun read(id: String) = run { up(); node(id).let { Versioned(it.text, it.version.toString()) } }
        override fun version(id: String) = run { up(); node(id).version.toString() }
        override fun write(id: String, text: String, mimeType: String) = run { up(); node(id).let { it.text = text; it.version++; it.version.toString() } }
        override fun create(parentId: String, name: String, mimeType: String, text: String) = run { up(); entry(nodes.getValue(file(name, parentId, text))) }
        override fun createFolder(parentId: String, name: String) = run { up(); folder(name, parentId) }
        override fun trash(id: String) { up(); node(id).trashed = true }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = context.getSharedPreferences("omnitask", Context.MODE_PRIVATE)
    private val vault = Uri.parse("content://app.omnitask.test.documents/tree/vault")
    private val drive = MemoryDrive()
    private lateinit var omni: String
    private val today = LocalDate(2026, 10, 10)

    @Before
    fun before() {
        val root = drive.folder("Vault", "root")
        omni = drive.folder("Omni", drive.folder("หลังบ้าน", drive.folder("📁 Folder", root)))
        drive.file("Omni note.md", omni, "- [ ] a\n- [x] b ✅ 2026-10-01\n")
        prefs.edit().putBoolean(DriveLink.KEY_ON, true).commit()
        DriveLink.useForTest(OmniDrive(drive, null) {}.also { it.connect(omni) })
    }

    @After
    fun after() {
        prefs.edit().remove(DriveLink.KEY_ON).commit()
        DriveLink.useForTest(null)
    }

    @Test
    fun tasksComeFromDriveAndTicksGoBack() {
        val repo = VaultRepository(context)
        val snap = repo.load(vault)
        val a = snap.tasks.first { it.title == "a" }
        check(a.fileUri.startsWith(VaultRepository.DRIVE_URI)) { a.fileUri }
        check(a.filePath == VaultText.TASK_FILE) { a.filePath }
        check(snap.drive?.online == true)
        repo.rewriteLine(a) { TaskLine.setDone(it, true, today) }
        check(drive.text(omni, "Omni note.md") == "- [x] a ✅ 2026-10-10\n- [x] b ✅ 2026-10-01\n") { drive.text(omni, "Omni note.md")!! }
    }

    @Test
    fun addArchiveAndUndoUseDrive() {
        val repo = VaultRepository(context)
        repo.appendLine(vault, VaultRepository.TASK_FILE, "- [ ] c")
        check(drive.text(omni, "Omni note.md")!!.contains("- [ ] c"))
        val b = repo.load(vault).tasks.first { it.title == "b" }
        val cut = repo.archiveTask(vault, b, today)
        check(!drive.text(omni, "Omni note.md")!!.contains("- [x] b"))
        check(drive.text(omni, "Omni note Archive.md")!!.contains("- [x] b ✅ 2026-10-01"))
        repo.unarchive(vault, cut)
        check(drive.text(omni, "Omni note.md")!!.contains("- [x] b"))
        check(!drive.text(omni, "Omni note Archive.md")!!.contains("- [x] b"))
    }

    @Test
    fun otherOmniFilesAreOnDriveToo() {
        val repo = VaultRepository(context)
        repo.writePath(vault, SettingsSync.PATH, "{}")
        check(drive.text(omni, "omni-settings.json") == "{}")
        check(repo.readPath(vault, SettingsSync.PATH) == "{}")
        repo.writePath(vault, "${VaultText.OMNI_DIR}/Notes/idea.md", "# idea\n")
        val notes = drive.nodes.values.first { it.folder && it.name == "Notes" }
        check(drive.text(notes.id, "idea.md") == "# idea\n")
        repo.load(vault)
        check("omni-settings.json" in repo.namesIn(vault, VaultText.OMNI_DIR))
    }

    @Test
    fun offlineTicksWaitAndGoUpLater() {
        val repo = VaultRepository(context)
        val a = repo.load(vault).tasks.first { it.title == "a" }
        drive.offline = true
        repo.rewriteLine(a) { TaskLine.setDone(it, true, today) }
        val away = repo.load(vault)
        check(away.drive?.online == false)
        check(away.drive?.pending == 1)
        check(away.tasks.first { it.title == "a" }.raw.startsWith("- [x] a"))
        drive.offline = false
        val back = repo.load(vault)
        check(back.drive?.pending == 0)
        check(drive.text(omni, "Omni note.md")!!.startsWith("- [x] a ✅ 2026-10-10"))
    }

    @Test
    fun theSweepWaitsForDrive() {
        val repo = VaultRepository(context)
        repo.load(vault)
        drive.offline = true
        check(repo.sweepDone(vault, LocalDate(2026, 10, 5), today) { false }.isEmpty())
        drive.offline = false
        check(repo.sweepDone(vault, LocalDate(2026, 10, 5), today) { false } == listOf("b"))
        check(drive.text(omni, "Omni note Archive.md")!!.contains("- [x] b"))
    }

    @Test
    fun withoutDriveNothingGoesThere() {
        prefs.edit().putBoolean(DriveLink.KEY_ON, false).commit()
        val repo = VaultRepository(context)
        check(repo.load(vault).drive == null)
        check(repo.load(vault).tasks.isEmpty())
    }
}
