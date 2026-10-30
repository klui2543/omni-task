package app.omnitask.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.omnitask.model.DayFill
import app.omnitask.model.LookAhead
import app.omnitask.model.tr
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * The secretary looking eight weeks ahead (owner's pick ก, mock look-ahead v2, 2026-10-30): Settings switch "เลขามองไกล
 * 8 สัปดาห์", off at first; this is its one reader. On, today's plan reads the weeks ahead, and the today page shows
 * short tags whose reason shows on a tap.
 */
object LookAheadMode {
    private const val PREFS = "omni-lookahead"
    private const val ON = "on"
    var on by mutableStateOf(false)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ON, false)
    }

    fun setOn(context: Context, value: Boolean) {
        on = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ON, value).apply()
    }
}

@Composable
fun LookAheadSetting(context: Context) {
    LaunchedEffect(Unit) { LookAheadMode.load(context) }
    SettingSwitch(
        tr("เลขามองไกล 8 สัปดาห์", "The secretary looks 8 weeks ahead"),
        tr(
            "เปิด: ตารางวันนี้ดูเวรและวันว่าง 8 สัปดาห์ข้างหน้าด้วย มีป้ายสั้นบอกเหตุ แตะป้ายเพื่อดูเหตุผล  ปิด: แบบเดิม",
            "On: today's plan also reads the shifts and free days of the next 8 weeks, with short tags (tap one for why). Off: as before",
        ),
        LookAheadMode.on,
    ) { LookAheadMode.setOn(context, it) }
}

/** The colours of a tag, as in the mock: want violet, tight red, light green, plain grey; null is the row's kind (accent). */
private fun toneColors(tone: DayFill.Tone?): Pair<Color, Color> {
    val dark = Palettes.current.dark
    return when (tone) {
        DayFill.Tone.WANT -> if (dark) Color(0xFFB39DD3) to Color(0xFF231D30) else Color(0xFF6A4FA3) to Color(0xFFEEE8F7)
        DayFill.Tone.HEAVY -> C.red to C.redSoft
        DayFill.Tone.LIGHT -> C.lime to C.limeSoft
        null -> C.accentText to C.accentSoft
    }
}

/** A short tag; a tap shows or hides its reason (the caller keeps which one is open). */
@Composable
fun HintPill(label: String, tone: DayFill.Tone?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (fg, bg) = toneColors(tone)
    Text(
        label,
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        color = fg, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** The look-ahead for the week card: the weeks, and what each whole free day ahead would hold. Nothing is written. */
class LookAheadView(val weeks: List<LookAhead.Week>, val booked: Map<LocalDate, List<DayFill.Placed>>)

/** Counted for the days from tomorrow to [last]; a free day is planned as it would be then (rule 6 of the mock). */
fun lookAheadView(input: DayFill.Input, last: LocalDate): LookAheadView {
    val today = input.now.date
    val days = generateSequence(today.plusDays(1)) { it.plusDays(1) }.takeWhile { it <= last }
    return LookAheadView(LookAhead.weeks(input), days.associateWith { LookAhead.booked(input, it) }.filterValues { it.isNotEmpty() })
}

/** "หุ้น 2 ชม., ระบบป้าย 45 นาที": what a free day ahead holds, as the mock's week card says it. */
fun bookedText(placed: List<DayFill.Placed>): String = placed.joinToString(", ") { p ->
    val m = p.len
    val len = if (m >= 60) tr("${m / 60} ชม.", "${m / 60} h") + if (m % 60 != 0) tr(" ${m % 60} นาที", " ${m % 60} min") else "" else tr("$m นาที", "$m min")
    p.item.title + " " + len
}
