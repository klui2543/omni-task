package app.omnitask.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConflictCopyTest {

    @Test
    fun syncClashCopiesAreRecognised() {
        assertTrue(VaultText.isConflictCopy("📁 Folder/หลังบ้าน/TaskForge/TaskForge (conflict 2026-10-09-05-55-31).md"))
        assertTrue(VaultText.isConflictCopy("Notes/TaskForge (Conflicted copy 2026-10-09).md"))
        assertTrue(VaultText.isConflictCopy("Notes/TaskForge.sync-conflict-20261009-055531-ABC.md"))
        // DriveSync names the older side this way.
        assertTrue(VaultText.isConflictCopy("📁 Folder/หลังบ้าน/Omni/Omni note (older, before conflict 2026-10-10-18-43-58).md"))
    }

    @Test
    fun ordinaryNotesAreNot() {
        assertFalse(VaultText.isConflictCopy(VaultText.TASK_FILE))
        assertFalse(VaultText.isConflictCopy("📁 Folder/หลังบ้าน/TaskForge/TaskForge Archive.md"))
        assertFalse(VaultText.isConflictCopy("Notes/Conflict resolution (meeting).md"))
        assertFalse(VaultText.isConflictCopy("(conflict) folder/TaskForge.md"))
        assertFalse(VaultText.isConflictCopy("Notes/Book (nonconflict edition).md"))
    }

    @Test
    fun theTaskNoteArchiveAndItsOldTwinAreArchivesNotNotes() {
        assertEquals("📁 Folder/หลังบ้าน/Omni/Omni note.md", VaultText.TASK_FILE)
        assertTrue(Archive.isArchive("📁 Folder/หลังบ้าน/Omni/Omni note Archive.md"))
        assertTrue(Archive.isArchive("📁 Folder/หลังบ้าน/TaskForge/TaskForge Archive.md"))
        assertFalse(Archive.isArchive(VaultText.TASK_FILE))
        assertTrue(VaultText.isConflictCopy("📁 Folder/หลังบ้าน/Omni/Omni note (conflict 2026-10-10-08-00-00).md"))
    }
}
