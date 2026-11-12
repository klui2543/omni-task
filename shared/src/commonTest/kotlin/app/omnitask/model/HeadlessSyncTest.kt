package app.omnitask.model

import app.omnitask.model.HeadlessSync.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeadlessSyncTest {

    @Test
    fun exitZeroWithNoInternalErrorIsOk() {
        assertEquals(Outcome.Ok, HeadlessSync.outcome(-1, null, 0, ""))
        assertEquals(Outcome.Ok, HeadlessSync.outcome(null, "", 0, null))
    }

    @Test
    fun noResultAtAllIsNeverOk() {
        assertEquals(Outcome.Failed("no result"), HeadlessSync.outcome(null, null, null, null))
        assertEquals(Outcome.Failed("no result"), HeadlessSync.outcome(-1, null, null, null))
    }

    @Test
    fun termuxRefusingOtherAppsIsNotAllowed() {
        val msg = "RUN_COMMAND service requires `allow-external-apps` property to be set to `true` in `~/.termux/termux.properties` file."
        assertEquals(Outcome.NotAllowed, HeadlessSync.outcome(2, msg, null, null))
        // The words alone are enough, whatever the code.
        assertEquals(Outcome.NotAllowed, HeadlessSync.outcome(null, msg, null, null))
        assertEquals(Outcome.NotAllowed, HeadlessSync.outcome(-1, msg, 1, ""))
    }

    @Test
    fun missingScriptIsNotSetUp() {
        assertEquals(Outcome.NotSetUp, HeadlessSync.outcome(-1, null, 127, "bash: omni-sync.sh: command not found\n"))
        assertEquals(Outcome.NotSetUp, HeadlessSync.outcome(-1, null, 127, null))
        assertEquals(Outcome.NotSetUp, HeadlessSync.outcome(1, "No such file or directory", null, null))
        assertEquals(
            Outcome.NotSetUp,
            HeadlessSync.outcome(1, "The executable file not found at path \"${HeadlessSync.SCRIPT}\".", null, null),
        )
        assertEquals(
            Outcome.NotSetUp,
            HeadlessSync.outcome(-1, null, 1, "bash: ${HeadlessSync.SCRIPT}: No such file or directory"),
        )
    }

    @Test
    fun aMissingVaultFileInsideARunIsAFailureNotAMissingSetup() {
        val stderr = "syncing...\nENOENT: no such file or directory, open 'x.md'\n"
        assertEquals(Outcome.Failed("ENOENT: no such file or directory, open 'x.md'"), HeadlessSync.outcome(-1, null, 1, stderr))
    }

    @Test
    fun otherExitsFailWithTheLastLineOfStderr() {
        val stderr = "Connecting\nError: not logged in\n\n  \n"
        assertEquals(Outcome.Failed("Error: not logged in"), HeadlessSync.outcome(-1, null, 1, stderr))
        // timeout's exit when ob runs past its limit, with nothing on stderr.
        assertEquals(Outcome.Failed("exit 124"), HeadlessSync.outcome(-1, null, 124, ""))
        assertEquals(Outcome.Failed("exit 2"), HeadlessSync.outcome(null, null, 2, null))
    }

    @Test
    fun anInternalErrorFailsWithItsMessage() {
        assertEquals(Outcome.Failed("Termux crashed"), HeadlessSync.outcome(3, "Termux crashed", 0, ""))
        assertEquals(Outcome.Failed("error 3"), HeadlessSync.outcome(3, null, null, null))
    }

    @Test
    fun aNewRunIsDueOnlyAfterTheGap() {
        val last = 1_000_000L
        assertTrue(HeadlessSync.due(0L, last), "the first run is always due")
        assertFalse(HeadlessSync.due(last, last), "not again at once")
        assertFalse(HeadlessSync.due(last, last + HeadlessSync.MIN_GAP_MS - 1), "not inside the gap")
        assertTrue(HeadlessSync.due(last, last + HeadlessSync.MIN_GAP_MS), "due once the gap has passed")
        assertTrue(HeadlessSync.due(last, last - 5_000L), "due when the clock went back")
    }

    @Test
    fun theLockHeldByAnotherRunIsBusyNotAFailure() {
        assertEquals(Outcome.Busy, HeadlessSync.outcome(-1, null, 75, "omni-sync: another sync is running\n"))
        assertEquals(Outcome.Busy, HeadlessSync.outcome(null, null, 75, null))
        // Termux's own error still wins over the script's exit.
        assertEquals(Outcome.Failed("Termux crashed"), HeadlessSync.outcome(3, "Termux crashed", 75, ""))
    }

    @Test
    fun aRunMarkedAsRunningBlocksANewOneUntilItIsStale() {
        val since = 5_000_000L
        assertTrue(HeadlessSync.free(0L, since), "nothing running")
        assertFalse(HeadlessSync.free(since, since), "one just started")
        assertFalse(HeadlessSync.free(since, since + HeadlessSync.TIMEOUT_MS - 1), "still inside the wait")
        assertTrue(HeadlessSync.free(since, since + HeadlessSync.TIMEOUT_MS), "stale: its answer is lost")
        assertTrue(HeadlessSync.free(since, since - 1_000L), "the clock went back")
    }

    @Test
    fun theNewWayIsOnlyForTheSyncedFolder() {
        assertTrue(HeadlessSync.isSyncedFolder("OmniVault"))
        assertTrue(HeadlessSync.isSyncedFolder("OmniVault\uFE0F"), "an invisible emoji mark does not count")
        assertFalse(HeadlessSync.isSyncedFolder(null))
        assertFalse(HeadlessSync.isSyncedFolder("Klui"))
        assertFalse(HeadlessSync.isSyncedFolder("omnivault"))
        assertFalse(HeadlessSync.isSyncedFolder("OmniVault 2"))
    }

    @Test
    fun obsidianLinksNameTheVaultExceptForTheSyncedFolder() {
        assertEquals("Klui", HeadlessSync.obsidianVault("Klui"))
        assertEquals(null, HeadlessSync.obsidianVault("OmniVault"))
        assertEquals(null, HeadlessSync.obsidianVault(null))
    }

    @Test
    fun aLeaveRunWaitsOnlyForTheShortGapBetweenLeaves() {
        val last = 7_000_000L
        assertTrue(HeadlessSync.leaveDue(0L, last), "the first leave is always due")
        assertFalse(HeadlessSync.leaveDue(last, last), "a second pause at once is one burst")
        assertFalse(HeadlessSync.leaveDue(last, last + HeadlessSync.LEAVE_GAP_MS - 1), "inside the burst gap")
        assertTrue(HeadlessSync.leaveDue(last, last + HeadlessSync.LEAVE_GAP_MS), "due after the burst gap")
        // Far inside the resume gap, which a leave run does not use.
        assertTrue(HeadlessSync.leaveDue(last, last + 5_000L), "a leave 5 s later still starts")
        assertTrue(HeadlessSync.leaveDue(last, last - 1_000L), "due when the clock went back")
    }

    @Test
    fun aLeaveRunGetsTheLongerWait() {
        assertEquals(HeadlessSync.LEAVE_TIMEOUT_MS, HeadlessSync.timeoutMs(leave = true))
        assertEquals(HeadlessSync.TIMEOUT_MS, HeadlessSync.timeoutMs(leave = false))
        assertTrue(HeadlessSync.LEAVE_TIMEOUT_MS >= (150L + 170L) * 1000L, "covers the script's wait and its sync")
    }

    @Test
    fun theSyncedFolderSaysWhyItIsNotSyncing() {
        assertEquals(HeadlessSync.NotReady.NO_TERMUX, HeadlessSync.notReady("OmniVault", termuxInstalled = false, permissionGranted = false))
        assertEquals(HeadlessSync.NotReady.NO_TERMUX, HeadlessSync.notReady("OmniVault", termuxInstalled = false, permissionGranted = true))
        assertEquals(HeadlessSync.NotReady.NO_PERMISSION, HeadlessSync.notReady("OmniVault", termuxInstalled = true, permissionGranted = false))
        assertEquals(null, HeadlessSync.notReady("OmniVault", termuxInstalled = true, permissionGranted = true))
        // Any other folder: nothing to say, the way it always was.
        assertEquals(null, HeadlessSync.notReady("Klui", termuxInstalled = false, permissionGranted = false))
        assertEquals(null, HeadlessSync.notReady(null, termuxInstalled = true, permissionGranted = false))
    }
}
