package app.omnitask.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import androidx.glance.GlanceId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.omnitask.data.TaskLine
import app.omnitask.model.CellStyle
import app.omnitask.model.FrameColor
import app.omnitask.model.MonthMarks
import app.omnitask.notify.CalendarEvent
import app.omnitask.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Draws the month widget as the launcher would, from the real Glance code, with a made-up month of work, and keeps
 * the pictures (ci/device-tests.sh copies them out) so a change to how it looks can be seen before it reaches a phone.
 * Android emulator only.
 */
@RunWith(AndroidJUnit4::class)
class WidgetShotsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()
    private val out get() = File(context.filesDir, "widget-shots").apply { mkdirs() }

    @After
    fun after() {
        MonthSource.sample = null
    }

    /** This month with a workout every morning, a busy week with a frame, two holidays and an overdue task. */
    private fun sample(style: CellStyle): MonthSource {
        val today = LocalDate.now()
        fun day(n: Int) = LocalDate(today.year, today.monthNumber, n)
        fun at(d: LocalDate, h: Int) = LocalDateTime(d, LocalTime(h, 0))
        val tasks = listOf(
            "- [ ] ส่งใบเสนอราคา 📅 ${day(6)}", "- [ ] นัดช่างแอร์ 📅 ${day(6)}",
            "- [ ] ส่งงานตัดต่อ 📅 ${day(10)}", "- [ ] ตอบอีเมล 📅 ${day(10)}", "- [ ] สรุปยอด 📅 ${day(10)}",
            "- [ ] ซื้อของขวัญ 📅 ${day(14)}", "- [ ] เตรียมสไลด์ 📅 ${day(16)}",
            "- [ ] ส่งแบบร่างหน้าเว็บ 📅 ${day(20)}", "- [ ] รีวิวงานทีม 📅 ${day(20)}", "- [ ] โทรหาบัญชี 📅 ${day(20)}",
            "- [ ] ทดสอบแอป 📅 ${day(21)}", "- [ ] แก้บั๊ก widget 📅 ${day(21)}", "- [ ] ส่ง build 📅 ${day(21)}",
            "- [ ] เปิดตัวเวอร์ชันใหม่ 📅 ${day(22)}", "- [ ] ตอบคอมเมนต์ 📅 ${day(22)}", "- [ ] อัปเดตเอกสาร 📅 ${day(22)}",
            "- [ ] จ่ายบัตรเครดิต 📅 ${day(27)}", "- [ ] ต่อภาษีรถ 📅 ${today.minusDays(3)}",
            "- [ ] ส่งรายงานไตรมาส 📅 $today",
        ).mapNotNull { TaskLine.parse(it) }
        val first = day(1).minusDays(7)
        val routine = (0 until 45).map { i -> first.plusDays(i.toLong()).let { CalendarEvent(1000L + i, "ออกกำลังกาย", at(it, 6), at(it, 7)) } }
        val events = routine + listOf(
            CalendarEvent(1, "ประชุมลูกค้า", at(day(8), 14), at(day(8), 15)),
            CalendarEvent(2, "ประชุมทีม", at(day(22), 10), at(day(22), 11)),
            CalendarEvent(3, "นัดหมอฟัน", at(day(26), 9), at(day(26), 10)),
            CalendarEvent(4, "คุยงานกับพี่เอ", at(today, 13), at(today, 14)),
        )
        val marks = MonthMarks()
            .withFrame((20..22).map(::day).toSet(), FrameColor.AMBER, "สปรินต์")
            .withStickers(day(9), listOf("✈️"))
            .withStickers(day(22), listOf("🚀", "🎉"))
        val holidays = mapOf(day(13) to "วันหยุด", day(23) to "วันหยุด")
        return MonthSource(today, 1, first.minusDays(60), first.plusDays(120), marks, tasks, events, holidays, style)
    }

    @OptIn(ExperimentalGlanceApi::class)
    private fun shot(name: String, style: CellStyle, night: Boolean, picked: Int?, size: DpSize) {
        MonthSource.sample = sample(style)
        val conf = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val themed = context.createConfigurationContext(conf)
        val today = LocalDate.now()
        val state = mutablePreferencesOf().also { p ->
            if (picked != null) p[MonthWidget.DAY] = LocalDate(today.year, today.monthNumber, picked).toString()
            p[MonthWidget.TOUCHED] = System.currentTimeMillis()
        }
        val start = System.nanoTime()
        val views = runBlocking { MonthWidget().compose(themed, size = size, state = state) }
        // How long drawing the widget takes, which is what a tap waits for now (see MonthWidget.push).
        File(out, "timings.txt").appendText("$name ${(System.nanoTime() - start) / 1_000_000} ms\n")
        val density = themed.resources.displayMetrics.density
        val w = (size.width.value * density).toInt()
        val h = (size.height.value * density).toInt()
        var bmp: Bitmap? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = FrameLayout(themed)
            val view = views.apply(themed, parent)
            parent.addView(view, FrameLayout.LayoutParams(w, h))
            repeat(2) {
                parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, w, h)
            }
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                Canvas(b).apply { drawColor(if (night) 0xFF2A2622.toInt() else 0xFFD9D4CC.toInt()); parent.draw(this) }
            }
        }
        File(out, "$name.png").outputStream().use { checkNotNull(bmp).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * The composition kept for taps (MonthLive) redraws when a tap sets the widget's place in memory, quickly and
     * without Glance's session. Writes how long each redraw took next to the pictures.
     */
    @Test
    fun aTapRedrawsTheKeptComposition() = runBlocking<Unit> {
        MonthSource.sample = sample(CellStyle.TINT)
        // A widget id no launcher has, as Glance makes for previews.
        val id = Class.forName("androidx.glance.appwidget.AppWidgetUtilsKt").getMethod("createFakeAppWidgetId").invoke(null) as GlanceId
        val widgetId = MonthLive.key(context, id)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 360)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 360)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 480)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 480)
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(360f, 480f)))
        }
        val drawn = Channel<Long>(Channel.UNLIMITED)
        val job = MonthLive.keep(context, id, widgetId, options) { drawn.trySend(System.nanoTime()) }
        try {
            withTimeout(20_000) { drawn.receive() }
            val today = LocalDate.now()
            val times = (21..25).map { day ->
                while (drawn.tryReceive().isSuccess) Unit
                val start = System.nanoTime()
                MonthLive.set(widgetId, app.omnitask.model.MonthNav(picked = LocalDate(today.year, today.monthNumber, day), touchedAt = System.currentTimeMillis()))
                // As a tap does: the session hears of it and draws at its quick rate.
                GlanceSessions.session(widgetId)?.let { GlanceSessions.updateGlance(it) }
                (withTimeout(5_000) { drawn.receive() } - start) / 1_000_000
            }
            File(out, "timings.txt").appendText("kept composition, taps ${times.joinToString(" ")} ms\n")
        } finally {
            job.cancel()
            MonthLive.navs.remove(widgetId)
        }
    }

    @Test
    fun drawTheMonthWidget() {
        val big = DpSize(360.dp, 480.dp)
        for (style in CellStyle.entries) for (night in listOf(true, false)) {
            val tag = "${style.key}-${if (night) "dark" else "light"}"
            shot("$tag-today", style, night, null, big)
            shot("$tag-day22", style, night, 22, big)
        }
        shot("tint-dark-small", CellStyle.TINT, true, null, DpSize(360.dp, 400.dp))
        shot("bars-dark-small", CellStyle.BARS, true, null, DpSize(360.dp, 400.dp))
        check(out.listFiles().orEmpty().count { it.extension == "png" } >= 10)
    }
}
