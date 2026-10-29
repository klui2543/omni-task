package app.omnitask.ui

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.omnitask.data.CalendarReader
import app.omnitask.model.tr
import app.omnitask.notify.CalendarEvent
import app.omnitask.time.atZone

/** What a calendar event does wherever it shows: a tap opens it in the calendar app, a long press shows [EventInfoDialog]. */
class EventActions(val open: (CalendarEvent) -> Unit, val inspect: (CalendarEvent) -> Unit)

/**
 * The same event actions on every screen that lists calendar events. The long press shows what the phone's calendar
 * store holds for the event, with Delete for one the phone kept after it was deleted in Google.
 */
@Composable
fun rememberEventActions(vm: TaskViewModel): EventActions {
    val context = LocalContext.current
    var inspecting by remember { mutableStateOf<Pair<CalendarEvent, CalendarReader.EventInfo?>?>(null) }
    var swapping by remember { mutableStateOf<CalendarReader.EventDetails?>(null) }
    var store by remember { mutableStateOf(app.omnitask.model.Swap.Store()) }
    androidx.compose.runtime.LaunchedEffect(inspecting) { if (inspecting != null) store = vm.readSwap() }
    val actions = remember(context) {
        EventActions(
            open = { e ->
                // An event the phone has not synced yet has no row to open, so the calendar app opens at its time.
                val uri = if (e.id < 0) {
                    val ms = e.begin.atZone(if (e.allDay) java.time.ZoneOffset.UTC else java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, ms) }.build()
                } else ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            },
            inspect = { e -> inspecting = e to CalendarReader.inspect(context, e.id) },
        )
    }
    swapping?.let { d -> EventToTaskSheet(d, store, vm) { swapping = null } }
    inspecting?.let { (e, info) ->
        // A single event in a calendar this phone can change can become a task; a repeating one cannot.
        val canSwap = info != null && !info.deleted && !info.recurring && info.calendar?.writable == true && CalendarReader.canWrite(context)
        val wasTask = info != null && app.omnitask.model.Swap.keptTask(store, e.id, e.title, e.begin) != null
        EventInfoDialog(
            e, info,
            swapLabel = when {
                !canSwap -> null
                wasTask -> tr("เปลี่ยนกลับเป็นงาน", "Back to a task")
                else -> tr("เปลี่ยนเป็นงาน", "Make it a task")
            },
            onSwap = {
                inspecting = null
                val d = CalendarReader.details(context, e.id)
                if (d != null) swapping = d else vm.showMessage(tr("อ่านนัดนี้ไม่ได้", "Cannot read this event"))
            },
            canDelete = info != null && !info.deleted && CalendarReader.canWrite(context),
            onOpen = { inspecting = null; actions.open(e) },
            onDelete = {
                inspecting = null
                // The calendar observer reloads the events once the provider changes, so the event goes by itself.
                if (!CalendarReader.delete(context, e.id)) vm.showMessage(tr("ลบนัดไม่ได้", "Cannot delete the event"))
            },
            onDismiss = { inspecting = null },
        )
    }
    return actions
}

/** What the phone's calendar store says about [e]: which calendar it is in and whether it is deleted or waiting to sync. */
@Composable
private fun EventInfoDialog(
    e: CalendarEvent,
    info: CalendarReader.EventInfo?,
    swapLabel: String?,
    onSwap: () -> Unit,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = C.raised,
            title = { Text(tr("ลบนัดนี้ออกจากเครื่อง?", "Delete this event from the phone?")) },
            text = {
                Text(
                    tr(
                        "ใช้กับนัดที่ลบใน Google ไปแล้วแต่ยังค้างในเครื่อง\nถ้านัดนี้ยังอยู่ใน Google จะถูกลบที่ Google ด้วยตอน sync ครั้งหน้า",
                        "For an event already deleted in Google that the phone kept.\nIf it is still in Google, the next sync deletes it there too.",
                    ),
                    color = C.text2,
                )
            },
            confirmButton = { TextButton(onClick = { confirming = false; onDelete() }) { Text(tr("ลบ", "Delete"), color = C.red) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
        )
        return
    }
    fun yes(b: Boolean) = if (b) tr("ใช่", "Yes") else tr("ไม่", "No")
    val lines = if (info == null) {
        listOf(
            tr("ไม่พบนัดนี้ในฐานข้อมูลปฏิทินของเครื่อง (id ${e.id})", "This event is not in the phone's calendar store (id ${e.id})"),
            tr("แต่รายการรอบนัดยังมีอยู่ จึงยังแสดงใน Omni", "but its occurrences are still listed, so Omni still shows it"),
        )
    } else {
        val cal = info.calendar
        listOfNotNull(
            tr("ปฏิทิน: ", "Calendar: ") + (cal?.name?.ifBlank { null } ?: tr("ไม่พบ (id ${info.calendarId})", "Not found (id ${info.calendarId})")),
            cal?.let { tr("บัญชี: ", "Account: ") + it.account.ifBlank { "-" } + if (it.isGoogle) " (Google)" else tr(" (ไม่ใช่ Google)", " (not Google)") },
            cal?.let { tr("แสดงในแอปปฏิทิน: ", "Shown in the calendar app: ") + yes(it.visible) },
            tr("ปิดไว้ใน Omni: ", "Switched off in Omni: ") + yes(info.hiddenInOmni),
            tr("ถูกลบแล้ว: ", "Deleted: ") + yes(info.deleted),
            tr("มีการแก้ที่ยังไม่ sync: ", "Changes waiting to sync: ") + yes(info.dirty),
            tr("เคย sync ขึ้น Google แล้ว: ", "Synced to Google before: ") + yes(info.syncId != null),
            tr("นัดซ้ำ: ", "Recurring: ") + yes(info.recurring) + if (info.recurring) tr(" (นัดซ้ำเปลี่ยนเป็นงานไม่ได้)", " (a repeating event cannot become a task)") else "",
            "id: ${info.id}",
            app.omnitask.data.GoogleCalendarCheck.status(androidx.compose.ui.platform.LocalContext.current),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(e.title.ifBlank { tr("ไม่มีชื่อ", "Untitled") }) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                // Task or event at the top, as on the add sheet; a tap on งาน opens the sheet that confirms it.
                KindToggle(
                    isEvent = true, enabled = swapLabel != null,
                    note = when {
                        info?.recurring == true -> tr("นัดซ้ำเปลี่ยนเป็นงานไม่ได้", "A repeating event cannot become a task")
                        swapLabel == null -> tr("นัดนี้เปลี่ยนเป็นงานไม่ได้ (ปฏิทินนี้แก้จากเครื่องไม่ได้)", "This event cannot become a task (its calendar cannot be changed here)")
                        else -> null
                    },
                    onSwitch = onSwap,
                )
                Text(lines.joinToString("\n"), color = C.text2)
            }
        },
        confirmButton = {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                if (canDelete) TextButton(onClick = { confirming = true }) { Text(tr("ลบ", "Delete"), color = C.red) }
                TextButton(onClick = onDismiss) { Text(tr("ปิด", "Close"), color = C.text2) }
            }
        },
        dismissButton = { TextButton(onClick = onOpen) { Text(tr("เปิดในปฏิทิน", "Open in calendar"), color = C.text2) } },
    )
}
