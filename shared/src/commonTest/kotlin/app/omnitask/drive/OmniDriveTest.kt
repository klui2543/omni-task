package app.omnitask.drive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OmniDriveTest {

    private val drive = FakeDrive()
    private val vault = drive.folder("Vault", "root")
    private val omni = run {
        val folder = drive.folder("📁 Folder", vault)
        val back = drive.folder("หลังบ้าน", folder)
        drive.folder("Omni", back)
    }
    private val note = drive.file("Omni note.md", omni, "- [ ] a\n- [ ] b\n")
    private var saved: String? = null
    private fun store(state: String? = saved) = OmniDrive(drive, state) { saved = it }.also { if (!it.connected) it.connect(omni) }

    @Test
    fun locatesTheOmniFolderByItsParents() {
        // A folder named Omni elsewhere is not it.
        drive.folder("Omni", vault)
        assertEquals(omni, OmniDrive.locate(drive, "📁 Folder/หลังบ้าน/Omni", "Vault"))
    }

    @Test
    fun locatePrefersTheVaultWithTheSameName() {
        val copy = drive.folder("Vault backup", "root")
        val f = drive.folder("📁 Folder", copy)
        val b = drive.folder("หลังบ้าน", f)
        drive.folder("Omni", b)
        assertEquals(omni, OmniDrive.locate(drive, "📁 Folder/หลังบ้าน/Omni", "Vault"))
    }

    @Test
    fun locateIgnoresTheEmojiMark() {
        drive.nodes.values.first { it.name == "📁 Folder" }.name = "📁️ Folder"
        assertEquals(omni, OmniDrive.locate(drive, "📁 Folder/หลังบ้าน/Omni", null))
    }

    @Test
    fun refreshDownloadsOnlyWhatChanged() {
        val notes = drive.folder("Notes", omni)
        drive.file("x.md", notes, "x")
        drive.file("picture.webp", omni, "")
        val s = store()
        val first = s.refresh()
        assertTrue(first.online)
        assertEquals(setOf("Omni note.md", "Notes/x.md"), first.texts.keys)
        assertEquals(2, drive.downloads)
        s.refresh()
        assertEquals(2, drive.downloads)
        drive.change(note, "- [x] a\n- [ ] b\n")
        assertEquals("- [x] a\n- [ ] b\n", s.refresh().texts["Omni note.md"])
        assertEquals(3, drive.downloads)
        assertTrue("picture.webp" in s.namesIn(""))
    }

    @Test
    fun editWritesOnDrive() {
        val s = store()
        s.edit("Omni note.md") { it!!.replace("- [ ] a", "- [x] a") }
        assertEquals("- [x] a\n- [ ] b\n", drive.textOf(omni, "Omni note.md"))
        assertTrue(s.pendingPaths.isEmpty())
    }

    @Test
    fun aChangeMadeBetweenReadAndWriteIsKept() {
        val s = store()
        // The web ticks b right after the phone read the note: the phone's edit starts over on the new text.
        drive.afterRead = { drive.change(note, "- [ ] a\n- [x] b\n") }
        s.edit("Omni note.md") { it!!.replace("- [ ] a", "- [x] a") }
        assertEquals("- [x] a\n- [x] b\n", drive.textOf(omni, "Omni note.md"))
    }

    @Test
    fun offlineEditIsKeptAndJoinedLater() {
        val s = store()
        s.refresh()
        drive.offline = true
        s.edit("Omni note.md") { it!!.replace("- [ ] a", "- [x] a") }
        assertEquals(listOf("Omni note.md"), s.pendingPaths)
        assertFalse(s.refresh().online)
        assertEquals("- [x] a\n- [ ] b\n", s.read("Omni note.md"))
        // Meanwhile the web adds a task.
        drive.offline = false
        drive.change(note, "- [ ] a\n- [ ] b\n- [ ] web\n")
        drive.offline = true
        s.edit("Omni note.md") { it!! + "- [ ] phone\n" }
        drive.offline = false
        val back = s.refresh()
        assertTrue(back.online)
        assertEquals("- [x] a\n- [ ] b\n- [ ] web\n- [ ] phone\n", drive.textOf(omni, "Omni note.md"))
        assertEquals(drive.textOf(omni, "Omni note.md"), back.texts["Omni note.md"])
        assertTrue(s.pendingPaths.isEmpty())
        assertTrue(s.takeLost().isEmpty())
    }

    @Test
    fun pendingEditsSurviveARestart() {
        store().refresh()
        drive.offline = true
        store().edit("Omni note.md") { it!!.replace("- [ ] b", "- [x] b") }
        drive.offline = false
        store().refresh()
        assertEquals("- [ ] a\n- [x] b\n", drive.textOf(omni, "Omni note.md"))
    }

    @Test
    fun aClashKeepsDriveAndReportsThePhoneLines() {
        val s = store()
        s.refresh()
        drive.offline = true
        s.edit("Omni note.md") { it!!.replace("- [ ] a", "- [ ] a phone") }
        drive.offline = false
        drive.change(note, "- [ ] a web\n- [ ] b\n")
        s.refresh()
        assertEquals("- [ ] a web\n- [ ] b\n", drive.textOf(omni, "Omni note.md"))
        val lost = s.takeLost()
        assertEquals(listOf("- [ ] a phone"), lost.single().lines)
        assertTrue(s.takeLost().isEmpty())
    }

    @Test
    fun aFileMadeOfflineIsCreatedWithItsFolder() {
        val s = store()
        s.refresh()
        drive.offline = true
        s.edit("Notes/idea.md") { "# idea\n" }
        drive.offline = false
        s.refresh()
        val notes = drive.nodes.values.first { it.folder && it.name == "Notes" }
        assertEquals("# idea\n", drive.textOf(notes.id, "idea.md"))
    }

    @Test
    fun editCreatesAMissingFile() {
        val s = store()
        s.edit("omni-settings.json") { old -> assertNull(old); "{}" }
        assertEquals("{}", drive.textOf(omni, "omni-settings.json"))
    }

    @Test
    fun nothingChangedWritesNothing() {
        val s = store()
        val version = drive.nodes.getValue(note).version
        assertNull(s.edit("Omni note.md") { it })
        assertNull(s.edit("Omni note.md") { null })
        assertEquals(version, drive.nodes.getValue(note).version)
    }

    @Test
    fun anErrorFromTheEditPassesThrough() {
        val s = store()
        assertFailsWith<IllegalStateException> { s.edit("Omni note.md") { error("line gone") } }
        drive.offline = true
        assertFailsWith<IllegalStateException> { s.edit("Omni note.md") { error("line gone") } }
        assertTrue(s.pendingPaths.isEmpty())
    }

    @Test
    fun readIsFreshOnlineAndCachedOffline() {
        val s = store()
        assertEquals("- [ ] a\n- [ ] b\n", s.read("Omni note.md"))
        drive.change(note, "changed")
        assertEquals("changed", s.read("Omni note.md"))
        drive.offline = true
        assertEquals("changed", s.read("Omni note.md"))
        assertNull(s.read("missing.md"))
    }

    @Test
    fun deleteTrashesTheFile() {
        val s = store()
        s.refresh()
        s.delete("Omni note.md")
        assertNull(drive.textOf(omni, "Omni note.md"))
        assertNull(s.read("Omni note.md"))
    }

    @Test
    fun aFileMovedAwayIsLookedUpAgain() {
        val s = store()
        s.refresh()
        drive.nodes.getValue(note).trashed = true
        val again = drive.file("Omni note.md", omni, "- [ ] new\n")
        s.edit("Omni note.md") { it!! + "- [ ] more\n" }
        assertEquals("- [ ] new\n- [ ] more\n", drive.nodes.getValue(again).text)
    }

    @Test
    fun disconnectDropsTheCache() {
        val s = store()
        s.refresh()
        s.disconnect()
        assertFalse(s.connected)
        assertTrue(s.state.files.isEmpty())
    }
}
