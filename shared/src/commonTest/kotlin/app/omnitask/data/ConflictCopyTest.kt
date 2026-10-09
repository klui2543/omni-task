package app.omnitask.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConflictCopyTest {

    @Test
    fun syncClashCopiesAreRecognised() {
        assertTrue(VaultText.isConflictCopy("📁 Folder/หลังบ้าน/TaskForge/TaskForge (conflict 2026-10-09-05-55-31).md"))
        assertTrue(VaultText.isConflictCopy("Notes/TaskForge (Conflicted copy 2026-10-09).md"))
        assertTrue(VaultText.isConflictCopy("Notes/TaskForge.sync-conflict-20261009-055531-ABC.md"))
    }

    @Test
    fun ordinaryNotesAreNot() {
        assertFalse(VaultText.isConflictCopy(VaultText.TASK_FILE))
        assertFalse(VaultText.isConflictCopy("📁 Folder/หลังบ้าน/TaskForge/TaskForge Archive.md"))
        assertFalse(VaultText.isConflictCopy("Notes/Conflict resolution (meeting).md"))
        assertFalse(VaultText.isConflictCopy("(conflict) folder/TaskForge.md"))
    }
}
