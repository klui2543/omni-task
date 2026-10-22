package app.omnitask.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import app.omnitask.time.*
import java.time.ZoneId
import kotlinx.datetime.LocalDateTime

/**
 * A plain report of every calendar on the phone and how many of its events Omni can see, written to the vault so
 * Claude can read why a calendar's events do not show (a shared calendar showed in Google Calendar but not in Omni).
 * Counts and settings only: no event titles. Temporary, while that is looked into.
 */
object CalendarCheck {
    const val PATH = "${app.omnitask.data.VaultText.OMNI_DIR}/ตรวจปฏิทิน.md"

    fun report(context: Context): String? {
        if (!CalendarReader.hasPermission(context)) return null
        val r = context.contentResolver
        val zone = ZoneId.systemDefault()
        val from = LocalDateTime.now().minusDays(30).atZone(zone).toInstant().toEpochMilli()
        val to = LocalDateTime.now().plusDays(60).atZone(zone).toInstant().toEpochMilli()

        // Every row of the Instances table in the window, by calendar, before any filter of Omni's.
        val instances = HashMap<Long, Int>()
        val instancesVisible = HashMap<Long, Int>()
        runCatching {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
            r.query(uri, arrayOf(CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.VISIBLE), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    instances[id] = (instances[id] ?: 0) + 1
                    if (c.getInt(1) == 1) instancesVisible[id] = (instancesVisible[id] ?: 0) + 1
                }
            }
        }
        // Event rows of any date, deleted ones apart.
        val events = HashMap<Long, Int>()
        val deleted = HashMap<Long, Int>()
        runCatching {
            r.query(CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.DELETED), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    if (c.getInt(1) == 1) deleted[id] = (deleted[id] ?: 0) + 1 else events[id] = (events[id] ?: 0) + 1
                }
            }
        }
        val shown = CalendarReader.events(context, LocalDateTime.now().minusDays(30), LocalDateTime.now().plusDays(60)).groupingBy { it.calendarId }.eachCount()
        val gone = GoogleCalendarCheck.gone(context)
        val hidden = CalendarReader.hiddenIds(context)

        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS,
        )
        val out = StringBuilder()
        out.append("# ตรวจปฏิทิน\n\n")
        out.append("แอป Omni เขียนไฟล์นี้เองตอนเปิดแอป ให้ Claude อ่านว่าทำไมปฏิทินบางอันไม่ขึ้น มีแต่จำนวน ไม่มีชื่อนัด\n\n")
        out.append("เวลา: ${LocalDateTime.now().toString().take(19)}\n")
        out.append("ช่วงที่นับนัด: 30 วันก่อน ถึง 60 วันหน้า\n")
        out.append("นัดที่ Omni ซ่อนเพราะ Google ลบแล้ว: ${gone.size}\n")
        out.append("สถานะเช็กกับ Google: ${GoogleCalendarCheck.status(context) ?: "-"}\n\n")
        out.append("| id | ชื่อ | บัญชี | ชนิด | เจ้าของ | สิทธิ์ | แสดงในเครื่อง | ซิงค์ | ปิดใน Omni | นัดทั้งหมด | ถูกลบ | นัดในช่วง | ในช่วงที่แสดง | Omni แสดง |\n")
        out.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n")
        runCatching {
            r.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val access = when (c.getInt(5)) {
                        CalendarContract.Calendars.CAL_ACCESS_NONE -> "none"
                        CalendarContract.Calendars.CAL_ACCESS_FREEBUSY -> "freebusy"
                        CalendarContract.Calendars.CAL_ACCESS_READ -> "read"
                        CalendarContract.Calendars.CAL_ACCESS_RESPOND -> "respond"
                        CalendarContract.Calendars.CAL_ACCESS_OVERRIDE -> "override"
                        CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR -> "contributor"
                        CalendarContract.Calendars.CAL_ACCESS_EDITOR -> "editor"
                        CalendarContract.Calendars.CAL_ACCESS_OWNER -> "owner"
                        CalendarContract.Calendars.CAL_ACCESS_ROOT -> "root"
                        else -> c.getInt(5).toString()
                    }
                    val cells = listOf(
                        id.toString(), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "", access,
                        if (c.getInt(6) == 1) "ใช่" else "ไม่", if (c.getInt(7) == 1) "ใช่" else "ไม่", if (id in hidden) "ใช่" else "ไม่",
                        (events[id] ?: 0).toString(), (deleted[id] ?: 0).toString(), (instances[id] ?: 0).toString(),
                        (instancesVisible[id] ?: 0).toString(), (shown[id] ?: 0).toString(),
                    )
                    out.append("| ").append(cells.joinToString(" | ") { it.replace("|", "/") }).append(" |\n")
                }
            }
        }.onFailure { out.append("\nอ่านรายการปฏิทินไม่ได้: ${it.message}\n") }
        return out.toString()
    }
}
