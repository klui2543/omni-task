package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.omnitask.data.ClaudeSettings
import app.omnitask.model.SecretaryAi
import app.omnitask.model.SecretaryGuide
import app.omnitask.model.tr
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

private fun baht(usd: Double, credit: SecretaryAi.Credit) = SecretaryAi.bahtText(credit.baht(usd))

private fun longDay(d: LocalDate) = d.format(SHORT_DATE) + " " + (d.year + 543)

/**
 * The secretary's chat box, laid out like Claude's (owner, 2026-10-26): the text on top; below it the mode (the app's
 * rules, or a Claude model picked here before asking) and send. The prices stay hidden until a question is sent: the
 * confirmation shows them.
 */
@Composable
internal fun ChatBox(
    input: String, onInput: (String) -> Unit, vm: TaskViewModel, bottom: androidx.compose.ui.unit.Dp,
    onSend: () -> Unit, onTalk: () -> Unit, onSettings: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottom)
            .clip(RoundedCornerShape(22.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(22.dp))
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicTextField(
            value = input, onValueChange = onInput,
            modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(end = 6.dp),
            textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
            cursorBrush = SolidColor(C.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            decorationBox = { inner -> Box { if (input.isEmpty()) Text(tr("ถามเลขา", "Ask the secretary"), color = C.faint, fontSize = TS.body); inner() } },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).clickable { menu = true }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val m = vm.deskMode
                    Text(if (m.isAi) m.label + " " + vm.deskEffort.label else m.label, color = if (m.isAi) C.accentText else C.text2, fontSize = TS.body)
                    Icon(Ic.down, null, tint = C.faint, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
                ModeMenu(menu, vm, { menu = false }) { menu = false; onSettings() }
            }
            Spacer(Modifier.weight(1f))
            if (input.isNotBlank()) Box(
                Modifier.size(40.dp).clip(CircleShape).background(C.accent).clickable { onSend() },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.up, tr("ส่ง", "Send"), tint = C.onAccent, modifier = Modifier.size(18.dp)) }
            else Box(
                Modifier.size(40.dp).clip(CircleShape).background(C.raised).clickable { onTalk() },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.mic, tr("คุยเสียงกับ Claude", "Talk to Claude"), tint = C.text2, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** The modes, each with what one everyday question costs at the effort picked; then the effort, then the settings. */
@Composable
private fun ModeMenu(open: Boolean, vm: TaskViewModel, onDismiss: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    DropdownMenu(open, onDismiss, containerColor = C.raised) {
        val credit = remember(vm.aiSettingsVersion) { ClaudeSettings.credit(context) }
        val system = remember { SecretaryAi.system(LocalDate.now()) }
        Column(Modifier.width(300.dp).padding(horizontal = 6.dp)) {
            SecretaryAi.Mode.entries.forEach { m ->
                val on = m == vm.deskMode
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.accentDeep else C.raised)
                        .clickable { vm.pickDeskMode(m) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.label, color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
                        Text(m.note, color = C.muted, fontSize = TS.caption)
                    }
                    val price = if (!m.isAi) tr("ฟรี", "Free") else
                        tr("ราว ", "about ") + baht(SecretaryAi.estimate(m, vm.deskEffort, SecretaryAi.guessInTokens(system, "อยากวิ่ง 30 นาที สัปดาห์นี้")).usd, credit) + tr(" บาท", " baht")
                    Text(price, Modifier.padding(start = 6.dp), color = C.muted, fontSize = TS.caption)
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterEnd) {
                        if (on) Icon(Ic.check, null, tint = C.accentText, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (vm.deskMode.isAi) {
                Text(tr("ความพยายามคิด", "Effort"), Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp), color = C.muted, fontSize = TS.caption)
                Segments(SecretaryAi.Effort.entries.map { it.label }, SecretaryAi.Effort.entries.indexOf(vm.deskEffort)) { vm.pickDeskEffort(SecretaryAi.Effort.entries[it]) }
            }
            Text(
                tr("ตั้งค่า AI และกฎถามตอบ", "AI and rules settings"),
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onSettings).padding(horizontal = 10.dp, vertical = 12.dp),
                color = C.accentText, fontSize = TS.body,
            )
        }
    }
}

@Composable
private fun Segments(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 38.dp).clip(RoundedCornerShape(8.dp)).background(if (on) C.accentDeep else C.sunken).clickable { onPick(i) },
                contentAlignment = Alignment.Center,
            ) { Text(l, color = if (on) C.text else C.muted, fontSize = TS.body, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

/**
 * Before anything goes to Claude: the model and effort, what goes out and what is expected back, in baht, and the
 * credit left before and after. Nothing is sent until "ยืนยันส่ง".
 */
@Composable
internal fun AiConfirmDialog(vm: TaskViewModel) {
    val p = vm.aiPending ?: return
    val context = LocalContext.current
    val credit = remember(vm.aiSettingsVersion, p) { ClaudeSettings.credit(context) }
    val e = p.estimate
    val after = credit.leftUsd - e.usd
    val short = after < 0
    AlertDialog(
        onDismissRequest = vm::aiCancel,
        containerColor = C.raised,
        title = { Text(tr("ใช้ ${p.mode.th} ความพยายาม${p.effort.th}", "Use ${p.mode.en}, ${p.effort.en} effort")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\"${p.question}\"", color = C.text2, fontSize = TS.body)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    // Worked out on the phone (nothing has gone out yet), so every figure is "ราว".
                    CostRow(tr("ส่งไป ราว ${e.inTokens} token", "Out about ${e.inTokens} tokens"), baht(e.inUsd, credit) + tr(" บาท", " baht"))
                    CostRow(tr("ตอบกลับ ราว ${e.outTokens} token", "Back about ${e.outTokens} tokens"), baht(e.outUsd, credit) + tr(" บาท", " baht"))
                    CostRow(tr("ครั้งนี้ ราว", "This question, about"), baht(e.usd, credit) + tr(" บาท", " baht"), strong = true)
                    CostRow(tr("คงเหลือ", "Left"), SecretaryAi.leftText(credit) + tr(" ถึง ", " to ") + SecretaryAi.leftText(credit, after), accent = true)
                    credit.creditExpires?.let { Text(tr("เครดิตโปรโมชั่นหมดอายุ ", "Promotional credit expires ") + longDay(it), color = C.muted, fontSize = TS.caption) }
                }
                if (short) Text(tr("เครดิตคงเหลือไม่พอสำหรับคำถามนี้", "Not enough credit left for this question"), color = C.red, fontSize = TS.caption)
            }
        },
        confirmButton = {
            TextButton(onClick = vm::aiConfirm, enabled = !short) {
                Text(tr("ยืนยันส่ง", "Send"), color = if (!short) C.accent else C.faint, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = vm::aiCancel) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}

@Composable
private fun CostRow(label: String, value: String, strong: Boolean = false, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = if (accent) C.accentText else if (strong) C.text else C.muted, fontSize = TS.caption, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
        Text(value, color = if (accent) C.accentText else C.text, fontSize = TS.caption, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Claude's answer in the chat: what it read, what can be tapped from it, and what it really cost. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiAnswer(t: TaskViewModel.AiTurn, vm: TaskViewModel) {
    val context = LocalContext.current
    val credit = remember(vm.aiSettingsVersion) { ClaudeSettings.credit(context) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t.mode.label + " " + tr("ความพยายาม", "effort ") + t.effort.label, color = C.faint, fontSize = TS.micro)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(C.card).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (t.waiting) { Text(tr("กำลังถาม...", "Asking..."), color = C.muted, fontSize = TS.body); return@Column }
            t.reading?.reply?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.text, fontSize = TS.body) }
            t.error?.let { Text(it, color = C.red, fontSize = TS.body) }
            if (t.done != null) Text(t.done, color = C.lime, fontSize = TS.caption)
            else t.reading?.let { r ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (r.kind) {
                        SecretaryAi.Kind.COMMAND -> if (t.command != null) {
                            AiButton(tr("แสดงเลย", "Show it"), true) { vm.aiRunCommand(t.id) }
                            AiButton(tr("สอนเป็นคำนี้ไว้", "Keep as a taught word"), false) { vm.aiTeach(t.id) }
                        }
                        SecretaryAi.Kind.ADD_TASK -> if (r.task.isNotBlank()) AiButton(tr("เพิ่มงาน: ", "Add: ") + r.task, true) { vm.aiAddTask(r.task) }
                        SecretaryAi.Kind.CLAUDE -> AiButton(tr("ส่งให้ Claude", "Send to Claude"), true) { vm.aiToClaude(t.id) }
                        else -> {}
                    }
                }
                if (r.kind == SecretaryAi.Kind.FIND_TIME) {
                    if (t.slots.isEmpty()) Text(tr("ยังไม่เจอช่องว่างที่พอ", "No free time found yet"), color = C.muted, fontSize = TS.caption)
                    t.slots.forEach { s ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.accentDeep).border(1.dp, C.accentLine, RoundedCornerShape(12.dp))
                                .clickable { vm.aiPickSlot(t.id, s) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("${s.dayLabel} ${"%d:%02d".format(s.start.hour, s.start.minute)} ${tr("ถึง", "to")} ${"%d:%02d".format(s.end.hour, s.end.minute)}", color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
                            Text(s.why, color = C.muted, fontSize = TS.caption)
                        }
                    }
                }
            }
            if (t.inTokens > 0 || t.outTokens > 0) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                CostRow(tr("ส่งไป ${t.inTokens} token", "Out ${t.inTokens} tokens"), baht(t.inUsd, credit) + tr(" บาท", " baht"))
                CostRow(tr("ตอบกลับ ${t.outTokens} token", "Back ${t.outTokens} tokens"), baht(t.outUsd, credit) + tr(" บาท", " baht"))
                CostRow(tr("รวมครั้งนี้", "This question"), baht(t.usd, credit) + tr(" บาท", " baht"), strong = true)
                CostRow(tr("คงเหลือ", "Left"), SecretaryAi.leftText(credit, t.leftUsd), accent = true)
            }
        }
    }
}

@Composable
private fun AiButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(if (primary) C.accent else C.raised)
            .border(1.dp, if (primary) C.accent else C.control, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (primary) C.onAccent else C.text, fontSize = TS.body, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal) }
}

/** The amber line at the top of the chat in the week before the API key runs out (owner, 2026-10-26). */
@Composable
internal fun KeyWarning(vm: TaskViewModel, onSettings: () -> Unit) {
    val context = LocalContext.current
    val expires = remember(vm.aiSettingsVersion) { ClaudeSettings.keyExpires(context) }
    if (ClaudeSettings.key(context) == null) return
    val left = SecretaryAi.keyWarning(expires, LocalDate.now()) ?: return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(C.amberSoft)
            .border(1.dp, C.amber.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).clickable(onClick = onSettings).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            if (left > 0) tr("กุญแจ API หมดอายุในอีก $left วัน (${longDay(expires!!)})", "The API key runs out in $left days (${longDay(expires!!)})")
            else tr("กุญแจ API หมดอายุแล้ว (${longDay(expires!!)})", "The API key has run out (${longDay(expires!!)})"),
            color = C.amber, fontSize = TS.body, fontWeight = FontWeight.SemiBold,
        )
        Text(tr("revoke กุญแจเก่าที่ Claude Console แล้วใส่กุญแจใหม่ แตะเพื่อเปิดตั้งค่า AI", "Revoke the old key in the Claude Console and add a new one; tap for the AI settings"), color = C.text2, fontSize = TS.caption)
    }
}

/** AI and the rules, in the chat (owner, 2026-10-26): the credit, the key and its expiry; what the rules know. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AiSettingsSheet(vm: TaskViewModel, onGuide: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var picking by remember { mutableStateOf<String?>(null) }
    val credit = remember(vm.aiSettingsVersion) { ClaudeSettings.credit(context) }
    val key = remember(vm.aiSettingsVersion) { ClaudeSettings.key(context) }
    val keyExpires = remember(vm.aiSettingsVersion) { ClaudeSettings.keyExpires(context) }
    var newKey by remember { mutableStateOf("") }
    // Read once when the sheet opens, so typing "33." is not undone when the figures around it are read again.
    var quota by remember { mutableStateOf(credit.quotaUsd.toString().removeSuffix(".0")) }
    var rate by remember { mutableStateOf(credit.rate.toString().removeSuffix(".0")) }
    val today = LocalDate.now()

    when (picking) {
        "credit" -> DayDialog(tr("เครดิตหมดอายุ", "Credit expires"), credit.creditExpires ?: today, { ClaudeSettings.setCreditExpires(context, null); vm.aiSettingsChanged() }, { ClaudeSettings.setCreditExpires(context, it); vm.aiSettingsChanged() }) { picking = null }
        "key" -> DayDialog(tr("กุญแจหมดอายุ", "Key expires"), keyExpires ?: today, { ClaudeSettings.setKeyExpires(context, null); vm.aiSettingsChanged() }, { ClaudeSettings.setKeyExpires(context, it); vm.aiSettingsChanged() }) { picking = null }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.raised) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Segments(listOf("AI", tr("กฎถามตอบ", "Rules")), tab) { tab = it }
            if (tab == 0) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CostRow(tr("คงเหลือ", "Left"), SecretaryAi.leftText(credit), strong = true)
                    CostRow(tr("แอปนี้ใช้ไป", "Spent here"), baht(credit.spentUsd, credit) + tr(" บาท", " baht"))
                    SettingRow(tr("เครดิตโปรโมชั่นหมดอายุ", "Promotional credit expires"), credit.creditExpires?.let(::longDay) ?: tr("ตั้งค่า", "Set")) { picking = "credit" }
                    NumberRow(tr("เครดิตตั้งต้น (ดอลลาร์)", "Credit (dollars)"), quota) { v -> quota = v; v.toDoubleOrNull()?.let { ClaudeSettings.setQuota(context, it); vm.aiSettingsChanged() } }
                    NumberRow(tr("บาทต่อ 1 ดอลลาร์", "Baht per dollar"), rate) { v -> rate = v; v.toDoubleOrNull()?.takeIf { it > 0 }?.let { ClaudeSettings.setRate(context, it); vm.aiSettingsChanged() } }
                    Text(
                        tr("เริ่มนับใหม่ (เครดิตก้อนใหม่)", "Start counting again (new credit)"),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { ClaudeSettings.resetSpent(context); vm.aiSettingsChanged() }.padding(vertical = 6.dp),
                        color = C.accentText, fontSize = TS.caption,
                    )
                }
                Text(tr("คิดจากที่แอปนี้ใช้เท่านั้น ถ้าใช้เครดิตเดียวกันที่อื่น ยอดจริงดูที่หน้า Billing ของ Claude Console", "Counted from this app only; the real balance is on the Claude Console's Billing page"), color = C.faint, fontSize = TS.caption)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CostRow(tr("กุญแจ API ของ Anthropic", "Anthropic API key"), key?.let { tr("ใส่แล้ว (ลงท้าย ...", "Set (ends ...") + it.takeLast(4) + ")" } ?: tr("ยังไม่ได้ใส่", "Not set"))
                    // The owner's own date (the console shows it when the key is made); amber with the days left, so it is
                    // seen. No date: amber "ยังไม่ได้ตั้ง", since then nothing can warn before it runs out.
                    if (key != null) SettingRow(
                        tr("กุญแจหมดอายุ", "Key expires"),
                        keyExpires?.let { d ->
                            val left = ChronoUnit.DAYS.between(today, d)
                            longDay(d) + if (left >= 0) tr(" (อีก $left วัน)", " ($left days left)") else tr(" (หมดแล้ว)", " (run out)")
                        } ?: tr("ยังไม่ได้ตั้ง", "Not set"),
                        valueColor = C.amber,
                    ) { picking = "key" }
                    BasicTextField(
                        value = newKey, onValueChange = { newKey = it.trim() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(12.dp),
                        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
                        cursorBrush = SolidColor(C.accent), singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        decorationBox = { inner -> Box { if (newKey.isEmpty()) Text(tr(if (key == null) "วางกุญแจ API ที่นี่" else "วางกุญแจใหม่เพื่อเปลี่ยน", if (key == null) "Paste the API key here" else "Paste a new key to replace it"), color = C.faint, fontSize = TS.body); inner() } },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (newKey.isNotBlank()) AiButton(tr("บันทึกกุญแจ", "Save key"), true) {
                            ClaudeSettings.setKey(context, newKey)
                            // A new key has a new date: the old one is cleared and the picker opens for the owner's.
                            ClaudeSettings.setKeyExpires(context, null)
                            newKey = ""; vm.aiSettingsChanged(); picking = "key"
                        }
                        if (key != null) AiButton(tr("ลบกุญแจออกจากเครื่อง", "Remove the key"), false) { ClaudeSettings.setKey(context, null); ClaudeSettings.setKeyExpires(context, null); vm.pickDeskMode(SecretaryAi.Mode.RULES); vm.aiSettingsChanged() }
                    }
                    Text(tr("กุญแจเก็บในเครื่องนี้เท่านั้น ไม่เข้า vault และไม่ซิงก์ เตือนก่อนหมดอายุ 7 วันที่หน้าเลขา", "Kept on this phone only, never in the vault; a warning shows on the secretary page a week before it runs out"), color = C.faint, fontSize = TS.caption)
                }
                Text(tr("ส่งให้ AI แค่ประโยคที่พิมพ์ วันนี้ และรายชื่อคำสั่งของแอป ไม่ส่งงานทั้งหมด เรื่องยาและผู้ป่วยไม่ส่งให้ AI", "Only the sentence, today's date and the app's commands go out, never your tasks; medicines and patients never go"), color = C.faint, fontSize = TS.caption)
            } else {
                Text(
                    tr(
                        "โหมดเลขาลองตามลำดับ: ชื่อบอลลูน, คำที่สอนไว้, คำในรายการ ถ้าไม่รู้จัก จะเสนอสอนคำ ส่งให้ Claude หรือถามด้วย Haiku (ยืนยันราคาก่อน)",
                        "The rules try, in order: bubble names, taught words, the built-in words; when none fits, teach it, send it to Claude, or ask Haiku (cost confirmed first)",
                    ),
                    color = C.muted, fontSize = TS.caption,
                )
                Text(tr("คำที่สอนไว้ (${vm.guide.words.size})", "Taught words (${vm.guide.words.size})"), color = C.muted, fontSize = TS.caption)
                vm.guide.words.forEach { w ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("\"${w.word}\"", Modifier.weight(1f), color = C.text, fontSize = TS.body)
                        Text(w.action.describe(), color = C.muted, fontSize = TS.caption)
                    }
                }
                AiButton(tr("แก้หรือสอนคำในคู่มือเลขา", "Change or teach words in the guide"), false) { onDismiss(); onGuide() }
                Text(tr("คำในรายการของแอป", "Built-in words"), color = C.muted, fontSize = TS.caption)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecretaryGuide.Builtin.entries.forEach { b ->
                        Text(b.label, Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp), color = C.text2, fontSize = TS.caption)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = C.accentText, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = C.muted, fontSize = TS.caption)
        Text(value, color = valueColor, fontSize = TS.caption)
    }
}

@Composable
private fun NumberRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = C.muted, fontSize = TS.caption)
        BasicTextField(
            value = value, onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
            modifier = Modifier.width(90.dp).clip(RoundedCornerShape(8.dp)).background(C.sunken).padding(horizontal = 10.dp, vertical = 8.dp),
            textStyle = TextStyle(color = C.accentText, fontSize = TS.caption, fontFamily = AppFont),
            cursorBrush = SolidColor(C.accent), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}
