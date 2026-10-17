package app.omnitask.model

/**
 * Events the phone still holds after they were deleted in Google. On some phones (ColorOS) the calendar store stops
 * hearing about deletions made elsewhere, while the Google Calendar app reads Google itself and shows them gone.
 * Omni lists what Google still has and leaves out the phone's rows that are not in that list.
 */
object CalendarGone {

    /** One event row on the phone that came from Google: [syncId] is Google's id for it. */
    data class Row(val eventId: Long, val calendar: String, val syncId: String)

    /**
     * The rows of [rows] that Google no longer has. [live] holds the ids Google listed, with the series id of each
     * repeating event's rounds. A changed round of a series is stored as "seriesId_time", so it also counts while
     * its series is live.
     */
    fun gone(rows: List<Row>, live: Set<String>): Set<Long> =
        rows.filter { it.syncId !in live && it.syncId.substringBefore('_') !in live }.map { it.eventId }.toSet()
}
