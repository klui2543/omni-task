package app.omnitask.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The phone app's dark colours, so the two read as one app. */
object W {
    val text = Color(0xFFF2F2F3)
    val muted = Color(0xFF9A9AA0)
    val lime = Color(0xFFA6D96A)
    val red = Color(0xFFE06C6C)
    val raised = Color(0xFF2F2F33)
}

@Composable
fun Label(text: String, color: Color = W.text, size: Int = 14, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, textAlign = TextAlign.Center))
}

@Composable
fun Pill(text: String, color: Color) {
    Label(text, color, 12, modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
fun RoundButton(text: String, background: Color, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clip(CircleShape).background(background).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Label(text, W.text, 12, FontWeight.Medium)
    }
}

/** 4:52 for 292 minutes. */
fun hm(minutes: Long) = "%d:%02d".format(minutes / 60, minutes % 60)

val H: DateTimeFormatter = DateTimeFormatter.ofPattern("HH")
val M: DateTimeFormatter = DateTimeFormatter.ofPattern("mm")

/** Today, Tomorrow, or the weekday and date for a day further on. */
@Composable
fun dayWord(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
}

/** How long until [at], like "7 hr 20 min", for the line under the alarm. */
@Composable
fun until(at: LocalDateTime): String {
    val minutes = ChronoUnit.MINUTES.between(LocalDateTime.now(), at).coerceAtLeast(0) + 1
    return stringResource(R.string.hours_minutes, minutes / 60, minutes % 60)
}
