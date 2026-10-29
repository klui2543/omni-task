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

    /** One event as Google lists it: [id] is the round's own id, [seriesId] the id of its series when it repeats. */
    data class Listed(val id: String, val seriesId: String? = null)

    /**
     * The events of [listed] the phone holds no row for yet: added on Google (on the web, another device) while the
     * phone's calendar store has not synced, which on ColorOS can wait until the Google Calendar app is opened.
     * [phone] holds Google's ids of the phone's rows, deleted ones too, so an event deleted on the phone and not yet
     * sent up does not come back. A repeating event's round counts as held when its series is, and a changed round
     * stored as "seriesId_time" counts as its own id.
     */
    fun <T> notOnPhone(listed: List<T>, phone: Set<String>, key: (T) -> Listed): List<T> = listed.filter { item ->
        val k = key(item)
        k.id !in phone && k.id.substringBefore('_') !in phone && (k.seriesId == null || k.seriesId !in phone)
    }
}
