package app.omnitask.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import app.omnitask.model.QuickAddChips
import app.omnitask.model.Folders
import app.omnitask.model.tr
import kotlinx.coroutines.delay
import app.omnitask.time.*
import kotlinx.datetime.LocalDate

/** Add a task in one line; day words, times, #tags and ! marks are read out of the sentence. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(state: UiState, vm: TaskViewModel, voice: Boolean, status: Status = Status.TODO, initial: String = "", link: String? = null, onAskAssistant: (String) -> Unit, onDismiss: () -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val text = field.text
    fun setText(value: String) { field = TextFieldValue(value, TextRange(value.length)) }
    val draft = QuickAdd.parse(text, state.today)
    fun hm(t: kotlinx.datetime.LocalTime) = "%02d:%02d".format(t.hour, t.minute)
    val context = LocalContext.current
    // "ลงปฏิทิน" beside "เพิ่มงาน": the same sentence goes to a calendar as an event. Both sit where the thumb
    // already is after typing; the length and the calendar keep their last choice and only open when asked.
    val eventPick = remember { QuickEventState() }
    var chosen by remember { mutableStateOf(CalendarReader.addTo(context)) }
    fun pickCalendar(all: List<CalendarReader.Calendar>): CalendarReader.Calendar? {
        val calendars = all.filter { it.writable && it.visible }
        return calendars.firstOrNull { it.id == chosen } ?: calendars.firstOrNull { it.isGoogle && it.name == it.account }
            ?: calendars.firstOrNull { it.isGoogle } ?: calendars.firstOrNull()
    }
    val calendars = state.calendars.filter { it.writable && it.visible }
    val calendar = pickCalendar(state.calendars)
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
    fun addEvent(c: CalendarReader.Calendar?) {
        if (draft.title.isBlank()) return
        if (c == null) {
            vm.showMessage(tr("ไม่พบปฏิทินที่เพิ่มนัดได้ในเครื่อง เปิดซิงค์ปฏิทินของบัญชี Google ในตั้งค่าเครื่อง", "No calendar on this phone takes new events: turn on calendar sync for your Google account"))
            return
        }
        CalendarReader.setAddTo(context, c.id)
        // No time typed: an all-day event, unless a time was picked in the rows.
        val ev = quickEvent(draft, eventPick, state.today)
        vm.addEvent(c.id, draft.title, ev.start, ev.end, ev.allDay)
        onDismiss()
    }
    // The first time, the phone asks for calendar access; once given, the event goes in without a second tap.
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.calendarChanged()
        if (CalendarReader.canWrite(context)) addEvent(pickCalendar(CalendarReader.calendars(context)))
        else vm.showMessage(tr("ลงปฏิทินไม่ได้ ยังไม่ได้อนุญาตให้เข้าถึงปฏิทิน", "Cannot add the event: calendar access was not allowed"))
    }
    fun toCalendar() {
        if (draft.title.isBlank()) return
        if (CalendarReader.canWrite(context)) addEvent(calendar ?: pickCalendar(CalendarReader.calendars(context)))
        else allow.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }
    // Linked notes and images for the new task (owner's pick, 2026-10-30, way ข): kept here until it is added,
    // and handed to the full page so they are not lost there.
    val extras = remember { NewAttachments() }
    val pickImage = rememberNewImagePicker(vm, extras)
    var linking by remember { mutableStateOf(false) }
    if (linking) NewNotePicker(state, extras) { linking = false }

    // Task or event is picked first, so one button saves it and the two can no longer be mixed up (owner, 2026-10-24).
    // A typed time range (9:00-11:00) or day range picks the event until the owner picks for himself.
    var pickedEvent by remember { mutableStateOf<Boolean?>(null) }
    val autoEvent = draft.endTime != null || draft.endDue != null
    val asEvent = target == null && (pickedEvent ?: autoEvent)
    fun add() {
        if (draft.title.isBlank()) return
        val t = target
        if (t != null && (extras.images.isNotEmpty() || extras.links.isNotEmpty())) {
            vm.showMessage(tr("ของในลิสต์เก็บรูปและโน้ตไม่ได้ จึงไม่ได้แนบไป", "List items cannot hold images or notes, so they were left out"))
        }
        if (t != null) vm.addListItem(t.list, draft.title, t.category, link) else vm.quickAdd(draft, status, link, extras.links.toList(), extras.images.toList())
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

    // The folder chip's picker: a folder goes into the sentence as its tag, a list sends the item there.
    var choosingFolder by remember { mutableStateOf(false) }
    if (choosingFolder) FolderPicker(
        state,
        current = if (target == null) Folders.ofTags(draft.tags) else null,
        labels = null,
        onLabel = null,
        onPick = { f -> vm.notePickedFolder(f); target = null; setText(QuickAddChips.withFolder(text, f, state.today)); choosingFolder = false },
        onDismiss = { choosingFolder = false },
        lists = state.lists,
        currentList = target?.let { it.list to it.category },
        // Lists are plain folders now (owner, 2026-10-28): they show here only until their notes are changed.
        onPickList = if (state.lists.isEmpty()) null else { l, c -> target = QuickAdd.HashPick.ToList(l, c); choosingFolder = false },
        onCreateList = null,
        listSize = { state.itemsOf(it).size },
        listFolders = { state.listFolders(it) },
    )

    // The full-screen page for what one sentence can't say; it starts from what was typed so far.
    var full by remember { mutableStateOf(false) }
    if (full) AddDetailPage(state, vm, text, status, link, extras, startAsEvent = asEvent, onClose = { full = false }, onSaved = { full = false; onDismiss() })

    SheetFrame(onDismiss) {
        if (target == null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(false to tr("งาน", "Task"), true to tr("นัด", "Event")).forEach { (event, name) ->
                    val on = asEvent == event
                    Row(
                        Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) C.accentDeep else C.sunken)
                            .clickable { pickedEvent = event },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) {
                        if (event) Icon(Ic.calendar, null, tint = if (on) C.text else C.muted, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                        Text(name, color = if (on) C.text else C.muted, fontSize = TS.body, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
            if (pickedEvent == null && autoEvent) Text(
                tr("เลือกนัดให้ เพราะพิมพ์ช่วงเวลา แตะ งาน ถ้าไม่ใช่", "Event picked because a time range was typed. Tap Task if not"),
                Modifier.padding(top = 8.dp), color = C.accentText, fontSize = TS.caption,
            )
        } else Text(tr("เพิ่มลง List", "Add to a list"), style = MaterialTheme.typography.titleMedium, color = C.text)
        BasicTextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                .background(C.sunken).padding(14.dp).focusRequester(focus),
            textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
            cursorBrush = SolidColor(C.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (asEvent) toCalendar() else add() }),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(tr("เช่น ส่งรายงาน พรุ่งนี้ 9:00 #รอ/พี่เอ", "e.g. Send report tomorrow 9:00 #waiting/Ann"), color = C.faint, fontSize = TS.body)
                    inner()
                }
            },
        )

        // Day, time and folder chips under the box (owner's pick, 2026-10-28, mock way ก): a tap opens the phone's
        // own calendar or clock, or the folder picker; what is picked is written into the sentence as if typed.
        if (!asEvent) {
            val list = target
            val folder = if (list == null) Folders.ofTags(draft.tags) else null
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fun day(d: LocalDate) = when (d) { state.today -> tr("วันนี้", "Today"); state.today.plusDays(1) -> tr("พรุ่งนี้", "Tomorrow"); else -> d.format(SHORT_DATE) }
                AddChip(
                    { Icon(Ic.calendar, null, tint = it, modifier = Modifier.size(15.dp)) },
                    draft.due?.takeIf { list == null }?.let { d -> day(d) + (draft.endDue?.let { tr(" ถึง ", " to ") + day(it) } ?: "") }, tr("วันที่", "Date"), enabled = list == null,
                    // A reminder needs its day, so taking the day out takes the time with it.
                    onClear = { setText(QuickAddChips.withoutTime(QuickAddChips.withoutDue(text, state.today), state.today)) },
                ) { pickSystemDate(context, draft.due ?: state.today) { setText(QuickAddChips.withDue(text, it, state.today, Lang.english)) } }
                AddChip(
                    { Icon(Ic.clock, null, tint = it, modifier = Modifier.size(15.dp)) },
                    draft.time?.takeIf { list == null }?.let { t -> draft.endTime?.let { hm(t) + tr(" ถึง ", " to ") + hm(it) } ?: (tr("เวลา ", "Time ") + hm(t)) }, tr("เวลา", "Time"), enabled = list == null,
                    onClear = { setText(QuickAddChips.withoutTime(text, state.today)) },
                ) { pickSystemTime(context, draft.time ?: kotlinx.datetime.LocalTime(9, 0)) { setText(QuickAddChips.withTime(text, it, state.today)) } }
                AddChip(
                    { FolderGlyph(if (list != null) C.accent else if (folder != null) folderTint(folder) else it, 16.dp) },
                    list?.let { it.category?.substringAfterLast('/') ?: it.list.name } ?: folder?.substringAfterLast('/'),
                    tr("โฟลเดอร์", "Folder"),
                    onClear = { if (list != null) target = null else setText(QuickAddChips.withFolder(text, null, state.today)) },
                ) { choosingFolder = true }
                // A list item is one line, so images and notes go only with a task.
                if (list == null) {
                    AddChip(
                        { Icon(Ic.image, null, tint = it, modifier = Modifier.size(15.dp)) },
                        extras.images.size.takeIf { it > 0 }?.let { tr("รูป $it", "$it images") }, tr("รูป", "Image"),
                        onClear = { extras.images.clear() },
                    ) { pickImage() }
                    AddChip(
                        { Icon(Ic.notes, null, tint = it, modifier = Modifier.size(15.dp)) },
                        extras.links.size.takeIf { it > 0 }?.let { n -> if (n == 1) extras.links.first().substringAfterLast('/') else tr("โน้ต $n", "$n notes") }, tr("โน้ต", "Note"),
                        onClear = { extras.links.clear() },
                    ) { linking = true }
                }
            }
            if (list == null && extras.images.isNotEmpty()) NewImageRow(extras, 64.dp, pickImage, Modifier.padding(top = 10.dp))
            if (list == null && extras.links.size > 1) NewLinkRows(state, extras, Modifier.padding(top = 6.dp))
            if (list != null) Text(
                tr("ของในลิสต์ไม่มีวันที่และเวลา แตะ ✕ ที่ชิปลิสต์ ถ้าอยากให้เป็นงานปกติ", "List items have no date or time. Tap ✕ on the list chip for a normal task"),
                Modifier.padding(top = 8.dp), color = C.accentText, fontSize = TS.caption,
            )
        }

        // Lists, their categories and known tags for the "#word" being typed.
        if (picks.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                picks.forEach { p ->
                    val label = when (p) {
                        is QuickAdd.HashPick.ToList -> p.list.name + (p.category?.let { " › " + folderTrail(it) } ?: "")
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
            // A list item has no date; a folder of the list is picked from the chips, one level at a time.
            val inside = app.omnitask.model.ListFolders.children(state.listFolders(t.list), t.category ?: "")
            if (inside.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    inside.forEach { c ->
                        Text(
                            app.omnitask.model.ListFolders.nameOf(c), Modifier.clip(RoundedCornerShape(16.dp)).background(C.raised).clickable { target = t.copy(category = c) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = C.text2, fontSize = TS.body,
                        )
                    }
                }
            }
        }

        // A shared link rides along as the task's details.
        link?.let { Text(tr("ลิงก์ในรายละเอียด: ", "Link in details: ") + it, Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }

        // What the sentence was read as, so a wrong guess is visible before saving.
        // A task shows its day, time and folder on the chips above; the rest (labels, priority) is read out here.
        val chips = if (target != null) emptyList() else buildList {
            fun day(d: LocalDate) = if (d == state.today) tr("วันนี้", "Today") else d.format(SHORT_DATE)
            if (asEvent) {
                draft.due?.let { d -> add(day(d) + (draft.endDue?.let { tr(" ถึง ", " to ") + day(it) } ?: "")) }
                draft.time?.let { t -> add(draft.endTime?.let { hm(t) + tr(" ถึง ", " to ") + hm(it) } ?: (tr("เวลา ", "Time ") + hm(t))) }
            }
            val folder = if (asEvent) null else Folders.ofTags(draft.tags)
            draft.tags.filter { it != folder }.forEach { add("#$it") }
            if (draft.priority != Priority.NONE) add(draft.priority.label)
        }
        if (chips.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { Pill(it, C.accentSoft, C.accentText) }
            }
        }
        // What tasks like this one usually came with before: tapping one adds its words to the sentence.
        val hints = remember(draft, target) { if (target != null) emptyList() else vm.quickHints(draft) }
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
            tr("วันนี้ พรุ่งนี้ วันจันทร์ 25/10 เวลา 9:00 ช่วง 9:00-11:00 หรือ 20-22/10 #tag ! !! !!! สำหรับความสำคัญ และ # แล้วเลือก List", "today, tomorrow, monday, 25/10, 9:00, ranges like 9:00-11:00 or 20-22/10, #tag, ! !! !!! for priority, and # to pick a list"),
            Modifier.padding(top = 10.dp), color = C.faint, fontSize = TS.caption,
        )

        // Where and when "ลงปฏิทิน" would put it, laid out like Google Calendar's new-event sheet.
        if (asEvent) {
            QuickEventRows(draft, eventPick, state.today, calendars, calendar, onCalendar = { chosen = it }, onMore = { full = true })
        } else if (target == null) {
            // A task gets the same way into the full page (date kind, start day, repeat, details, subtasks).
            Box(Modifier.padding(top = 6.dp)) { MoreOptionsRow { full = true } }
        }

        // One button: what it saves follows the choice at the top.
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Ic.mic, tr("พูด", "Speak"), C.raised, C.text2) { startListening() }
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(C.accent)
                    .clickable { if (asEvent) toCalendar() else add() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when { target != null -> tr("เพิ่มลง List", "Add to list"); asEvent -> tr("ลงปฏิทิน", "Add to calendar"); else -> tr("เพิ่มงาน", "Add task") },
                    color = C.onAccent, fontSize = TS.body, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * A chip under the new-task box: grey with its [name] until something is picked, then tinted with the [value]
 * and a ✕ to take it out. A disabled chip (a list item has no day) is faded and does nothing.
 */
@Composable
private fun AddChip(
    icon: @Composable (Color) -> Unit,
    value: String?,
    name: String,
    enabled: Boolean = true,
    onClear: () -> Unit,
    onClick: () -> Unit,
) {
    val set = value != null
    val fg = if (set) C.accentText else C.text2
    Row(
        Modifier.alpha(if (enabled) 1f else 0.45f).heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp))
            .background(if (set) C.accentSoft else Color.Transparent)
            .border(1.dp, if (set) C.accentLine else C.control, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(start = 12.dp, end = if (set) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon(fg)
        Text(value ?: name, color = fg, fontSize = TS.body, fontWeight = if (set) FontWeight.Medium else FontWeight.Normal, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        if (set) Box(Modifier.size(28.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClear), contentAlignment = Alignment.Center) {
            Icon(Ic.close, tr("เอา$name ออก", "Clear ${name.lowercase()}"), tint = fg, modifier = Modifier.size(11.dp))
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = fg, modifier = Modifier.size(19.dp))
    }
}
