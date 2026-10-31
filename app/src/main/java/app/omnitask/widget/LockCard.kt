package app.omnitask.widget

/**
 * What the lock-screen card shows of today: the Today widget's rows without what is already over (events that ended,
 * finished work), cut to [MAX] rows with "อีก N" for the rest (owner, 2026-10-31: the widget's look, today only,
 * at most 4 rows; mock lock-v4, way ก). The red now line stays while nothing before it was cut.
 */
internal object LockCard {
    const val MAX = 4

    data class Card(val rows: List<TodayWeek.Row>, val more: Int)

    fun card(day: TodayWeek.Day, max: Int = MAX): Card {
        val out = ArrayList<TodayWeek.Row>()
        var shown = 0
        var more = 0
        day.rows.forEach { r ->
            when {
                r is TodayWeek.Row.Event && r.past -> Unit
                r is TodayWeek.Row.Todo && !r.task.isOpen -> Unit
                r is TodayWeek.Row.Now -> if (more == 0) out += r
                shown < max -> { out += r; shown++ }
                else -> more++
            }
        }
        return Card(out, more)
    }
}
