package app.omnitask.widget

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.omnitask.data.CalendarReader
import app.omnitask.data.MonthMarksStore
import app.omnitask.model.FrameColor
import app.omnitask.model.MonthMarks
import app.omnitask.ui.Palettes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test
import org.junit.runner.RunWith

/** The month widget's picture and the phone's copy of its frames and emoji. */
@RunWith(AndroidJUnit4::class)
class MonthWidgetTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun d(day: Int) = LocalDate(2026, 10, day)

    @Test
    fun holidayCalendarsAreRecognised() {
        check(CalendarReader.isHolidayCalendar("Holidays in Thailand", "th.th#holiday@group.v.calendar.google.com"))
        check(CalendarReader.isHolidayCalendar("วันหยุดในประเทศไทย", ""))
        check(!CalendarReader.isHolidayCalendar("Work", "me@gmail.com"))
    }

    @Test
    fun thePictureFitsTheGrid() {
        val marks = MonthMarks()
            .withFrame((12..16).map(::d).toSet(), FrameColor.VIOLET, "อบรม Excel")
            .withFrame((23..26).map(::d).toSet(), FrameColor.AMBER, "")
            .withStickers(d(24), listOf("✈️", "🏖️", "📸", "🍜"))
        val data = MonthData(d(15), d(1), 0, marks, mapOf(d(13) to "วันนวมินทรมหาราช"), mapOf(d(15) to listOf(MonthData.Mark("ส่งรายงาน", false))), d(20), emptyList())
        val bmp = MonthPainter(context, Palettes.graphiteDark).draw(data, 320f, 42f, 5)
        val scale = minOf(context.resources.displayMetrics.density, 2.75f)
        check(bmp.width == (320f * scale).toInt()) { "width ${bmp.width}" }
        check(bmp.height == ((8f + 42f * 5) * scale).toInt()) { "height ${bmp.height}" }
    }

    @Test
    fun aTappedDayIsListedFromWhatIsLoaded() {
        val tasks = listOf(
            app.omnitask.data.TaskLine.parse("- [ ] ส่งแบบร่าง 📅 2026-10-22")!!,
            app.omnitask.data.TaskLine.parse("- [ ] ต่อภาษีรถ 📅 2026-10-12")!!,
        )
        val source = MonthSource(d(15), 0, LocalDate(2026, 9, 1), LocalDate(2026, 12, 31), MonthMarks(), tasks, emptyList(), emptyMap())
        // Nothing picked: today with the overdue task, then tomorrow.
        val now = source.view(0, null)
        check(now.agenda.map { it.first } == listOf(d(15), d(16))) { "listed ${now.agenda.map { it.first }}" }
        check(now.agenda[0].second.size == 1 && (now.agenda[0].second[0] as MonthData.Item.Todo).late == 3L)
        // The 22nd picked: only its own work, and it is ringed.
        val picked = source.view(0, d(22))
        check(picked.selected == d(22) && picked.agenda.single().second.size == 1)
        // A tap leaves the grid's work equal, so the widget keeps its picture and only moves the box.
        check(picked.work == now.work && picked.work[d(22)] == listOf(MonthData.Mark("ส่งแบบร่าง", false)))
        // Picking today boxes it and lists today and tomorrow, as nothing picked does; a month away is covered by what was loaded.
        val todayPicked = source.view(0, d(15))
        check(todayPicked.selected == d(15) && todayPicked.agenda.map { it.first } == listOf(d(15), d(16)))
        check(source.covers(1) && !source.covers(6))
    }

    /**
     * W3 and W6 from the tap plan: with 2,000 open tasks and 300 events, picking out a day must stay quick, and the
     * widget's picture must stay small enough that a redraw sends little to the launcher. Runs on the JVM and on the
     * emulator; the limits leave room for a slow emulator.
     */
    @Test
    fun aTapStaysQuickWithALotOfWork() {
        val tasks = (0 until 2000).map { i ->
            app.omnitask.data.TaskLine.parse("- [ ] งาน $i 📅 ${LocalDate(2026, 9, 1).plus(i % 120, kotlinx.datetime.DateTimeUnit.DAY)}")!!
        }
        val events = (0 until 300).map { i ->
            val at = kotlinx.datetime.LocalDateTime(LocalDate(2026, 9, 1).plus(i % 120, kotlinx.datetime.DateTimeUnit.DAY), kotlinx.datetime.LocalTime(9, 0))
            app.omnitask.notify.CalendarEvent(i.toLong(), "นัด $i", at, at, false, 1)
        }
        val source = MonthSource(d(15), 0, LocalDate(2026, 7, 1), LocalDate(2027, 1, 31), MonthMarks(), tasks, events, emptyMap())
        source.view(0, d(20)) // warm up
        val start = System.nanoTime()
        repeat(10) { source.view(0, d(1 + it * 3)) }
        val perTap = (System.nanoTime() - start) / 10 / 1_000_000
        check(perTap < 150) { "picking out a day took $perTap ms" }

        val palette = Palettes.graphiteDark
        val painter = MonthPainter(context, palette, maxScale = 2f, background = android.graphics.Color.BLACK, ring = false)
        val bmp = painter.draw(source.view(0, null), 336f, 42f, 6)
        check(bmp.byteCount < 1_000_000) { "the widget's picture is ${bmp.byteCount} bytes" }
    }

    @Test
    fun aNewSessionReusesTheMonthsLoadedBefore() {
        val source = MonthSource(d(15), 7, LocalDate(2026, 7, 1), LocalDate(2027, 1, 31), MonthMarks(), emptyList(), emptyList(), emptyMap())
        check(MonthSource.reusable(source, 7, 0, d(15)))
        check(MonthSource.reusable(source, 7, 2, d(15)))
        // The vault or the calendar changed, the day turned, or the month is past what was loaded: read again.
        check(!MonthSource.reusable(source, 8, 0, d(15)))
        check(!MonthSource.reusable(source, 7, 0, d(16)))
        check(!MonthSource.reusable(source, 7, 5, d(15)))
        check(!MonthSource.reusable(null, 7, 0, d(15)))
        // Ahead of ‹ ›, the two months either side must be loaded too.
        check(MonthSource.reusable(source, 7, 0, d(15), ahead = true))
        check(!MonthSource.reusable(source, 7, 2, d(15), ahead = true))
    }

    @Test
    fun aSaveIsReadBackWithoutAVault() {
        context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).edit().remove("vault").commit()
        val saved = MonthMarksStore.save(context, MonthMarks().withStickers(d(15), listOf("🎂")))
        check(saved.savedAt > 0)
        check(MonthMarksStore.local(context) == saved) { "got ${MonthMarksStore.local(context)}" }
    }
}
