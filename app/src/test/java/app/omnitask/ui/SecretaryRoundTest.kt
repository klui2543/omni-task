package app.omnitask.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Which secretary round the phone's copy of the book should already hold, and when the phone counts as behind. */
class SecretaryRoundTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun expectedRoundIsTheLatestFinishedOneToday() {
        // 10:00 Bangkok (03:00 UTC): 08:07 Bangkok (01:07 UTC) has had its grace.
        assertEquals(ms("2026-10-26T01:07:00Z"), Secretary.expectedRound(ms("2026-10-26T03:00:00Z")))
        // 11:20 Bangkok: the 11:07 round is still inside its grace, so 08:07 is the one expected.
        assertEquals(ms("2026-10-26T01:07:00Z"), Secretary.expectedRound(ms("2026-10-26T04:20:00Z")))
        // 11:40 Bangkok: 11:07 is expected now.
        assertEquals(ms("2026-10-26T04:07:00Z"), Secretary.expectedRound(ms("2026-10-26T04:40:00Z")))
    }

    @Test
    fun beforeTheFirstRoundYesterdaysLastOneIsExpected() {
        // 07:00 Bangkok on the 26th: yesterday's 23:07 Bangkok (16:07 UTC on the 25th).
        assertEquals(ms("2026-10-25T16:07:00Z"), Secretary.expectedRound(ms("2026-10-26T00:00:00Z")))
        // 01:00 Bangkok, after midnight: still yesterday's 23:07.
        assertEquals(ms("2026-10-25T16:07:00Z"), Secretary.expectedRound(ms("2026-10-25T18:00:00Z")))
    }

    @Test
    fun behindWhenTheBookIsOlderThanTheExpectedRound() {
        val now = ms("2026-10-26T03:00:00Z") // 10:00 Bangkok
        // The owner's case on 26 Oct: the phone still held 24 Oct 08:10.
        assertTrue(Secretary.behind(ms("2026-10-24T01:10:00Z"), now))
        // The 08:07 round wrote its marker at 08:10: up to date.
        assertFalse(Secretary.behind(ms("2026-10-26T01:10:46Z"), now))
        // No marker in the book at all: unknown, so the app does not send the owner to Obsidian.
        assertFalse(Secretary.behind(null, now))
    }

    @Test
    fun nightIsNotBehindAfterTheLastRound() {
        // 03:00 Bangkok: the book from last night's 23:07 round is all there is.
        assertFalse(Secretary.behind(ms("2026-10-25T16:09:03Z"), ms("2026-10-25T20:00:00Z")))
    }
}
