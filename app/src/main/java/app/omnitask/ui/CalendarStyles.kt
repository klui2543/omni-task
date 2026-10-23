package app.omnitask.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.omnitask.model.tr

/**
 * Whose calendar an event is from, at a glance: each of the phone's calendars can have one of a few quiet colours and a
 * short label (such as "แฟน"), set in the calendar choice. Kept on this phone only, like which calendars are shown,
 * since calendar ids differ from phone to phone. Compose state, so every screen follows a change at once.
 */
object CalendarStyles {
    data class Style(val color: Int?, val label: String)

    /** Quiet colours that sit with the app's themes; the index is what is stored. */
    val PALETTE = listOf(
        Color(0xFF9DB4D3), Color(0xFFD49AAE), Color(0xFF7FAE93), Color(0xFFC4A56A),
        Color(0xFFB3A6D9), Color(0xFFD9906C), Color(0xFF7FB8BF), Color(0xFF8E8E96),
    )

    private const val PREFS = "omni-calendar-style"
    private val styles = mutableStateMapOf<Long, Style>()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            val id = k.toLongOrNull() ?: return@forEach
            val text = v as? String ?: return@forEach
            styles[id] = Style(text.substringBefore('|').toIntOrNull(), text.substringAfter('|', ""))
        }
    }

    fun of(calendarId: Long): Style? = styles[calendarId]

    /** The colour set for [calendarId], or null for the app's usual event colour. */
    fun color(calendarId: Long): Color? = styles[calendarId]?.color?.let { PALETTE.getOrNull(it) }

    fun label(calendarId: Long): String? = styles[calendarId]?.label?.takeIf { it.isNotBlank() }

    fun set(context: Context, calendarId: Long, style: Style?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (style == null || (style.color == null && style.label.isBlank())) {
            styles.remove(calendarId)
            prefs.remove(calendarId.toString())
        } else {
            styles[calendarId] = style
            prefs.putString(calendarId.toString(), "${style.color ?: ""}|${style.label.replace('|', ' ')}")
        }
        prefs.apply()
    }
}

/** A calendar's colour dot, tappable to open [CalendarStyleEditor]. */
@Composable
fun CalendarDot(calendarId: Long, onClick: () -> Unit) {
    val c = CalendarStyles.color(calendarId)
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(c ?: C.raised).border(1.dp, if (c == null) C.control else c, CircleShape)
            .clickable(onClickLabel = tr("ตั้งสีและป้ายชื่อ", "Set colour and label"), onClick = onClick),
    )
}

/** The label and colour of one calendar, under its row in the calendar choice. */
@Composable
fun CalendarStyleEditor(context: Context, calendarId: Long) {
    val now = CalendarStyles.of(calendarId)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(C.bg).border(1.dp, C.cardBorder, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("ป้ายชื่อ (แสดงข้างนัด)", "Label (shown beside events)"), color = C.muted, fontSize = TS.caption)
        BasicTextField(
            now?.label.orEmpty(),
            { CalendarStyles.set(context, calendarId, CalendarStyles.Style(now?.color, it.take(12))) },
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont), cursorBrush = SolidColor(C.accent), singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (now?.label.isNullOrEmpty()) Text(tr("เช่น แฟน, เวร, วันหยุด", "e.g. Partner, Shifts, Holidays"), color = C.faint, fontSize = TS.body)
                    inner()
                }
            },
        )
        Text(tr("สี", "Colour"), color = C.muted, fontSize = TS.caption)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CalendarStyles.PALETTE.forEachIndexed { i, color ->
                val on = now?.color == i
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(color).border(2.dp, if (on) C.text else Color.Transparent, CircleShape)
                        .clickable { CalendarStyles.set(context, calendarId, CalendarStyles.Style(if (on) null else i, now?.label.orEmpty())) },
                )
            }
        }
        if (now != null) Text(
            tr("ใช้สีเดิมของแอป", "Use the app's usual colour"),
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { CalendarStyles.set(context, calendarId, null) }.padding(vertical = 4.dp),
            color = C.accentText, fontSize = TS.caption,
        )
        Box(Modifier.height(0.dp))
    }
}
