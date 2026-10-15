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
        val data = MonthData(d(15), d(1), 0, marks, mapOf(d(13) to "วันนวมินทรมหาราช"), setOf(d(15)), d(20), emptyList())
        val bmp = MonthPainter(context, Palettes.graphiteDark).draw(data, 320f, 42f, 5)
        val scale = minOf(context.resources.displayMetrics.density, 2.75f)
        check(bmp.width == (320f * scale).toInt()) { "width ${bmp.width}" }
        check(bmp.height == ((8f + 42f * 5) * scale).toInt()) { "height ${bmp.height}" }
    }

    @Test
    fun aSaveIsReadBackWithoutAVault() {
        context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).edit().remove("vault").commit()
        val saved = MonthMarksStore.save(context, MonthMarks().withStickers(d(15), listOf("🎂")))
        check(saved.savedAt > 0)
        check(MonthMarksStore.local(context) == saved) { "got ${MonthMarksStore.local(context)}" }
    }
}
