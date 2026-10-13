package app.omnitask.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.tr
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.time.format.DateTimeFormatter
import app.omnitask.time.*

/** The next [time] to come: today while it is still ahead, else tomorrow, as an alarm clock does. */
fun nextAlarm(time: LocalTime, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
    val at = now.date.atTime(time)
    return if (at > now) at else now.date.plusDays(1).atTime(time)
}

/** Today, Tomorrow, or the weekday and date of a day further on. */
fun alarmDay(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> tr("วันนี้", "today")
    today.plusDays(1) -> tr("พรุ่งนี้", "tomorrow")
    else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", TH))
}

/** How long until [at], like "7 ชม. 20 นาที". */
fun untilAlarm(at: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
    val tz = TimeZone.currentSystemDefault()
    val minutes = ((at.toInstant(tz) - now.toInstant(tz)).inWholeMinutes + 1).coerceAtLeast(0)
    return tr("${minutes / 60} ชม. ${minutes % 60} นาที", "${minutes / 60} hr ${minutes % 60} min")
}

/**
 * The watch alarm, set like a clock's: the time on the phone's own dial (hours, then minutes), and the day. Until a
 * day is picked by hand it follows the time, today while it is still ahead and else tomorrow; a date further on comes
 * from the calendar. [isSet] adds a button to turn the alarm off.
 */
@Composable
fun AlarmDialog(initial: LocalDateTime, isSet: Boolean, onSet: (LocalDateTime) -> Unit, onOff: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var at by remember { mutableStateOf(initial) }
    var pickedDay by remember { mutableStateOf(initial != nextAlarm(initial.time)) }
    val today = LocalDate.now()
    val passed = at <= LocalDateTime.now()
    fun setDay(day: LocalDate) {
        at = day.atTime(at.time)
        pickedDay = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("ตั้งปลุกที่นาฬิกา", "Watch alarm")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    at.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                        pickSystemTime(context, at.time) { t -> at = if (pickedDay) at.date.atTime(t) else nextAlarm(t) }
                    }.padding(horizontal = 4.dp),
                    color = C.text, fontSize = 48.sp, fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(tr("วันนี้", "Today"), at.date == today, { setDay(today) })
                    Chip(tr("พรุ่งนี้", "Tomorrow"), at.date == today.plusDays(1), { setDay(today.plusDays(1)) })
                    val later = at.date > today.plusDays(1)
                    Chip(if (later) alarmDay(at.date, today) else tr("เลือกวัน", "Pick a day"), later, {
                        android.app.DatePickerDialog(
                            context, android.R.style.Theme_DeviceDefault_Dialog,
                            { _, y, m, d -> setDay(LocalDate(y, m + 1, d)) },
                            at.date.year, at.date.monthNumber - 1, at.date.dayOfMonth,
                        ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
                    })
                }
                Text(
                    if (passed) tr("เวลานี้ผ่านไปแล้ว เลือกเวลาหรือวันใหม่", "That time has passed: pick another time or day")
                    else tr("ปลุก${alarmDay(at.date, today)} อีก ${untilAlarm(at)}", "Rings ${alarmDay(at.date, today)}, in ${untilAlarm(at)}"),
                    color = if (passed) C.red else C.muted, fontSize = TS.caption,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(at); onDismiss() }, enabled = !passed) {
                Text(tr("ตั้งปลุก", "Set alarm"), color = if (passed) C.faint else C.accent)
            }
        },
        dismissButton = {
            Row {
                if (isSet) TextButton(onClick = { onOff(); onDismiss() }) { Text(tr("ปิดปลุก", "Turn off"), color = C.red) }
                TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) }
            }
        },
    )
}
