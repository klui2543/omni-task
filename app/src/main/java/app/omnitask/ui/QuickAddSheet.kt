package app.omnitask.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.omnitask.data.CalendarReader
import app.omnitask.model.Lang
import app.omnitask.model.fetchTitle
import app.omnitask.model.Priority
import app.omnitask.model.Status
import app.omnitask.model.QuickAdd
import app.omnitask.model.tr
import kotlinx.coroutines.delay
import app.omnitask.time.*

/** Add a task in one line; day words, times, #tags and ! marks are read out of the sentence. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(state: UiState, vm: TaskViewModel, voice: Boolean, status: Status = Status.TODO, initial: String = "", link: String? = null, onAskAssistant: (String) -> Unit, onDismiss: () -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val text = field.text
    fun setText(value: String) { field = TextFieldValue(value, TextRange(value.length)) }
    val draft = QuickAdd.parse(text, state.today)
    val context = LocalContext.current
    // "ลงปฏิทิน": the same sentence goes to a calendar as an event instead of the vault as a task.
    var toCalendar by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf(60) }
    val calendars = state.calendars.filter { it.writable && it.visible }
    var chosen by remember { mutableStateOf(CalendarReader.addTo(context)) }
    val calendar = calendars.firstOrNull { it.id == chosen } ?: calendars.firstOrNull { it.isGoogle && it.name == it.account }
        ?: calendars.firstOrNull { it.isGoogle } ?: calendars.firstOrNull()
    val canWrite = CalendarReader.canWrite(context)
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.calendarChanged() }
    // "#" can send the item to a list note (and one of its categories) instead of TaskForge.
    var target by remember { mutableStateOf<QuickAdd.HashPick.ToList?>(null) }
    val token = QuickAdd.hashToken(text)
    val tags = remember(state.tasks) { state.tasks.flatMap { it.tags }.filter { !it.startsWith("remind-at-") }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val picks = token?.let { QuickAdd.hashPicks(it, state.lists, tags) }.orEmpty()
    fun pick(p: QuickAdd.HashPick) {
        val before = text.dropLast(token!!.length + 1).trimEnd()
        when (p) {
            is QuickAdd.HashPick.ToList -> { target = p; setText(if (before.isEmpty()) "" else "$before ") }
            is QuickAdd.HashPick.ToTag -> setText((if (before.isEmpty()) "" else "$before ") + "#${p.tag} ")
        }
    }
    val focus = remember { FocusRequester() }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && heard != null) setText((text.trim() + " " + heard).trim())
    }
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (Lang.english) "en-US" else "th-TH")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, tr("พูดงานที่จะเพิ่ม", "Say the task to add"))
        try { listen.launch(intent) } catch (_: ActivityNotFoundException) { }
    }
    fun add() {
        if (draft.title.isBlank()) return
        if (toCalendar) {
            val c = calendar ?: return
            if (!canWrite) return
            CalendarReader.setAddTo(context, c.id)
            // No time typed: an all-day event.
            vm.addEvent(c.id, draft.title, draft.due ?: state.today, draft.time, minutes)
            onDismiss()
            return
        }
        val t = target
        if (t != null) vm.addListItem(t.list, draft.title, t.category, link) else vm.quickAdd(draft, status, link)
        onDismiss()
    }
    // Focus (and the keyboard) only after the sheet has finished opening; asking earlier is the likeliest
    // cause of the crash on the owner's phone.
    // A shared link without a title: read the page's title, unless something was typed meanwhile.
    LaunchedEffect(link) {
        if (link != null && initial.isBlank()) {
            val title = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.omnitask.model.Shared.fetchTitle(link) }
            if (title != null && field.text.isBlank()) setText(title)
        }
    }
    LaunchedEffect(Unit) {
        if (voice) {
            startListening()
        } else {
            delay(350)
            runCatching { focus.requestFocus() }
        }
    }

    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (toCalendar) tr("เพิ่มนัด", "New event") else tr("เพิ่มงาน", "New task"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = C.text)
            Chip(tr("ลงปฏิทิน", "To calendar"), toCalendar, { toCalendar = !toCalendar; if (toCalendar) target = null })
        }
        BasicTextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                .background(C.sunken).padding(14.dp).focusRequester(focus),
            textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
            cursorBrush = SolidColor(C.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(tr("เช่น ส่งรายงาน พรุ่งนี้ 9:00 #รอ/พี่เอ", "e.g. Send report tomorrow 9:00 #waiting/Ann"), color = C.faint, fontSize = TS.body)
                    inner()
                }
            },
        )

        // Lists, their categories and known tags for the "#word" being typed.
        if (picks.isNotEmpty() && !toCalendar) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                picks.forEach { p ->
                    val label = when (p) {
                        is QuickAdd.HashPick.ToList -> ListEmoji.of(p.list.icon) + " " + p.list.name + (p.category?.let { " / $it" } ?: "")
                        is QuickAdd.HashPick.ToTag -> "#" + p.tag
                    }
                    Text(
                        label,
                        Modifier.clip(RoundedCornerShape(16.dp)).background(if (p is QuickAdd.HashPick.ToList) C.accentSoft else C.raised)
                            .clickable { pick(p) }.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (p is QuickAdd.HashPick.ToList) C.accentText else C.text2, fontSize = TS.body, maxLines = 1,
                    )
                }
            }
        }
        target?.let { t ->
            Row(
                Modifier.padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(C.accentSoft).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("เพิ่มลงใน ", "Add to ") + ListEmoji.of(t.list.icon) + " " + t.list.name + (t.category?.let { " / $it" } ?: ""),
                    color = C.accentText, fontSize = TS.body,
                )
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { target = null }, contentAlignment = Alignment.Center) {
                    Icon(Ic.close, tr("ไม่ใส่ลง List", "Not to a list"), tint = C.accentText, modifier = Modifier.size(12.dp))
                }
            }
            // A list item has no date; a category is picked from the chips.
            if (t.category == null && t.list.categories.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.list.categories.forEach { c ->
                        Text(
                            c, Modifier.clip(RoundedCornerShape(16.dp)).background(C.raised).clickable { target = t.copy(category = c) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = C.text2, fontSize = TS.body,
                        )
                    }
                }
            }
        }

        // A shared link rides along as the task's details.
        link?.let { Text(tr("ลิงก์ในรายละเอียด: ", "Link in details: ") + it, Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }

        // What the sentence was read as, so a wrong guess is visible before saving.
        fun hm(t: kotlinx.datetime.LocalTime) = "%02d:%02d".format(t.hour, t.minute)
        val chips = if (target != null) emptyList() else if (toCalendar) buildList {
            val day = draft.due ?: state.today
            add(if (day == state.today) tr("วันนี้", "Today") else day.format(SHORT_DATE))
            add(draft.time?.let { hm(it) + tr(" ถึง ", " to ") + hm(it.plusMinutes(minutes.toLong())) } ?: tr("ทั้งวัน", "All day"))
        } else buildList {
            draft.due?.let { add(if (it == state.today) tr("วันนี้", "Today") else it.format(SHORT_DATE)) }
            draft.time?.let { add(tr("เตือน ", "Remind ") + hm(it)) }
            draft.tags.forEach { add("#$it") }
            if (draft.priority != Priority.NONE) add(draft.priority.label)
        }
        if (chips.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { Pill(it, C.accentSoft, C.accentText) }
            }
        }
        if (toCalendar) {
            // How long, for an event with a time; which calendar it goes to.
            if (draft.time != null) {
                Row(Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 90, 120, 180).forEach { m -> Chip(length(m), minutes == m, { minutes = m }) }
                }
            }
            when {
                !canWrite -> Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("ต้องอนุญาตให้แอปเขียนปฏิทินก่อน", "Allow the app to write to your calendars first"), Modifier.weight(1f), color = C.text2, fontSize = TS.body)
                    GhostButton(tr("อนุญาต", "Allow"), { allow.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) })
                }
                calendars.isEmpty() -> Text(
                    tr("ไม่พบปฏิทินที่เพิ่มนัดได้ในเครื่อง\nเปิดซิงค์ปฏิทินของบัญชี Google ในตั้งค่าเครื่อง", "No calendar on this phone takes new events.\nTurn on calendar sync for your Google account in the phone's settings."),
                    Modifier.padding(top = 10.dp), color = C.text2, fontSize = TS.body,
                )
                calendars.size > 1 -> FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    calendars.forEach { c -> Chip(c.name.ifBlank { c.account }, c.id == calendar?.id, { chosen = c.id }) }
                }
            }
        }
        // What tasks like this one usually came with before: tapping one adds its words to the sentence.
        val hints = remember(draft, target, toCalendar) { if (target != null || toCalendar) emptyList() else vm.quickHints(draft) }
        if (hints.isNotEmpty()) {
            Text(tr("เคยใส่กับงานแบบนี้", "Usual for tasks like this"), Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.caption)
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hints.forEach { h ->
                    Text(
                        "+ " + h.label,
                        Modifier.clip(RoundedCornerShape(16.dp)).background(C.raised)
                            .clickable { setText(text.trimEnd() + " " + h.words + " ") }.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = C.text2, fontSize = TS.body, maxLines = 1,
                    )
                }
            }
        }
        Text(
            if (toCalendar) tr("วันนี้ พรุ่งนี้ วันจันทร์ 25/10 เวลา 14:00 ไม่ใส่เวลาจะลงเป็นทั้งวัน", "today, tomorrow, monday, 25/10, 14:00; no time makes it all day")
            else tr("วันนี้ พรุ่งนี้ วันจันทร์ 25/10 เวลา 9:00 #tag ! !! !!! สำหรับความสำคัญ และ # แล้วเลือก List", "today, tomorrow, monday, 25/10, 9:00, #tag, ! !! !!! for priority, and # to pick a list"),
            Modifier.padding(top = 10.dp), color = C.faint, fontSize = TS.caption,
        )

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Ic.mic, tr("พูด", "Speak"), C.raised, C.text2) { startListening() }
            RoundIcon(Ic.spark, tr("ถามผู้ช่วยว่าควรทำตอนไหน", "Ask the assistant when to do it"), C.accentSoft, C.accentText) {
                if (text.isNotBlank()) { onAskAssistant(text.trim()); onDismiss() }
            }
            Box(Modifier.weight(1f))
            PrimaryButton(
                when {
                    toCalendar -> tr("ลงปฏิทิน", "Add to calendar")
                    target != null -> tr("เพิ่มลง List", "Add to list")
                    else -> tr("เพิ่มงาน", "Add task")
                },
                { add() },
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = fg, modifier = Modifier.size(19.dp))
    }
}

private fun length(m: Int) = when {
    m < 60 -> tr("$m นาที", "$m min")
    m % 60 == 0 -> tr("${m / 60} ชม.", "${m / 60} h")
    else -> tr("${m / 60} ชม. ${m % 60} นาที", "${m / 60} h ${m % 60} min")
}
