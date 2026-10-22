package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.tr
import app.omnitask.time.*
import kotlinx.coroutines.delay

/**
 * The voice note sheet: recording (time and a stop button, nothing read back), Gemini writing it down, then the
 * tasks it heard to check before they go to the no-project tray. Shown whenever one of those is going on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSheet(v: TaskViewModel.VoiceUi, vm: TaskViewModel) {
    val recording = v.recording
    val review = v.review
    if (recording == null && !v.busy && review == null) return
    ModalBottomSheet(
        // While recording the sheet stays; swiping a check away leaves the recording waiting on the focus page.
        onDismissRequest = { if (review != null) vm.closeVoiceReview() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { recording == null }),
        containerColor = C.card, contentColor = C.text, scrimColor = Color(0x99000000),
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            when {
                recording != null -> Recording(v, recording.since, vm)
                v.busy -> Text(
                    tr("กำลังเก็บตกและจัดรายการงาน...", "Catching what was missed and tidying the list..."),
                    Modifier.padding(vertical = 32.dp).align(Alignment.CenterHorizontally), color = C.text2, fontSize = TS.body,
                )
                review != null -> Review(review, vm)
            }
        }
    }
}

@Composable
private fun Recording(v: TaskViewModel.VoiceUi, since: Long, vm: TaskViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val secs = ((now - since) / 1000).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(C.red))
            Text(tr("กำลังอัด", "Recording"), Modifier.padding(start = 10.dp).weight(1f), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text("%02d:%02d".format(secs / 60, secs % 60), color = C.text, fontSize = TS.title, fontWeight = FontWeight.Medium)
        }
        // The words heard so far, newest at the end; a few seconds behind the voice, as each piece is written down.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.bg).border(1.dp, C.cardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(tr("ได้ยินว่า", "Heard"), color = C.muted, fontSize = TS.micro)
            Text(
                when {
                    v.words.isNotBlank() -> "..." + v.words.takeLast(160)
                    v.sending > 0 -> tr("กำลังฟัง...", "Listening...")
                    else -> tr("พูดงานได้เลย คำจะขึ้นทุกครั้งที่หยุดพูดสั้นๆ", "Go ahead. The words show each time you pause")
                },
                color = if (v.words.isNotBlank()) C.text2 else C.muted, fontSize = TS.body, maxLines = 4,
            )
            if (v.unsent > 0) Text(
                tr("ส่งไม่ได้ ${v.unsent} ช่วง จะลองอีกครั้งตอนหยุด", "${v.unsent} piece(s) not sent; tried again when you stop"),
                color = C.amber, fontSize = TS.caption,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("งานที่จับได้ ${v.live.size}", "Tasks heard: ${v.live.size}"), color = C.text, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
            Text(tr("  อัปเดตเองระหว่างพูด แตะเพื่อแก้", "  Updates as you speak. Tap to fix"), Modifier.weight(1f), color = C.muted, fontSize = TS.caption, maxLines = 1)
        }
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            v.live.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (item.fresh) C.accentSoft else C.bg)
                        .border(1.dp, if (item.fresh) C.accentLine else C.cardBorder, RoundedCornerShape(12.dp))
                        .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        item.text, { vm.editLive(item.id, it) }, Modifier.weight(1f).padding(vertical = 10.dp),
                        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont), cursorBrush = SolidColor(C.accent),
                    )
                    if (item.fresh) Text(
                        tr("เพิ่งจับได้", "Just heard"), Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).background(C.card).padding(horizontal = 8.dp),
                        color = C.accentText, fontSize = TS.caption,
                    )
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = tr("ไม่เอางานนี้", "Leave this one out")) { vm.removeLive(item.id) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ic.close, tr("ไม่เอางานนี้", "Leave this one out"), tint = C.muted, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("ทิ้งทั้งหมด", "Throw all away"),
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.discardRecording() }.padding(horizontal = 14.dp, vertical = 14.dp),
                color = C.muted, fontSize = TS.body,
            )
            Box(Modifier.weight(1f))
            Row(
                Modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(C.red)
                    .clickable(onClickLabel = tr("หยุดอัด", "Stop recording")) { vm.stopVoice() }.padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(C.bg))
                Text(tr("หยุดและตรวจ", "Stop and check"), Modifier.padding(start = 10.dp), color = C.bg, fontSize = TS.body, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            tr("กดหยุดแล้ว Gemini อ่านทั้งหมดอีกรอบ เก็บตกงานที่ขาด ปิดจอได้ ยังอัดต่อ", "When you stop, Gemini reads it all once more. The screen can go off"),
            Modifier.align(Alignment.CenterHorizontally), color = C.muted, fontSize = TS.caption,
        )
    }
}

@Composable
private fun Review(review: TaskViewModel.VoiceReview, vm: TaskViewModel) {
    // Edits and removals stay here until saved; a new recording brings a new list.
    val texts = remember(review.dir) { mutableStateListOf(*review.items.map { it.text }.toTypedArray()) }
    val dups = remember(review.dir) { mutableStateListOf(*review.items.map { it.dup }.toTypedArray()) }
    val minutes = (review.seconds + 59) / 60
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (texts.isEmpty()) tr("ไม่ได้ยินงานในเสียงนี้", "No tasks heard in this recording")
            else tr("ได้ ${texts.size} งานจากเสียง $minutes นาที", "${texts.size} tasks from $minutes min of speech"),
            color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold,
        )
        Text(tr("แตะเพื่อแก้คำ ไม่ใส่วันที่และโปรเจกต์ให้ จัดทีหลังในถาด", "Tap to fix a word. No dates or projects are set; place them later in the tray"), color = C.muted, fontSize = TS.caption)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            texts.forEachIndexed { i, t ->
                val dup = dups.getOrNull(i)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.bg)
                        .border(1.dp, if (dup != null) C.amber.copy(alpha = 0.5f) else C.cardBorder, RoundedCornerShape(12.dp))
                        .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            t, { texts[i] = it }, Modifier.weight(1f).padding(vertical = 8.dp),
                            textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont), cursorBrush = SolidColor(C.accent),
                        )
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = tr("ไม่เอางานนี้", "Leave this one out")) { texts.removeAt(i); dups.removeAt(i) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Ic.close, tr("ไม่เอางานนี้", "Leave this one out"), tint = C.muted, modifier = Modifier.size(16.dp)) }
                    }
                    if (dup != null) Text(
                        tr("อาจซ้ำกับงานที่มีอยู่แล้ว: ${dup.title}", "May repeat a task you have: ${dup.title}") +
                            (dup.created?.let { tr(" (ค้างตั้งแต่ ${it.format(SHORT_DATE)})", " (open since ${it.format(SHORT_DATE)})") } ?: ""),
                        Modifier.padding(bottom = 6.dp, end = 10.dp), color = C.amber, fontSize = TS.caption, maxLines = 2,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(tr("ทิ้งทั้งหมด", "Throw all away"), { vm.dropVoice(review.dir) }, Modifier.weight(1f))
            if (texts.any { it.isNotBlank() }) PrimaryButton(
                tr("บันทึก ${texts.count { it.isNotBlank() }} งานลงถาด", "Save ${texts.count { it.isNotBlank() }} to the tray"),
                { vm.saveVoice(texts.toList()) }, Modifier.weight(2f),
            )
        }
        Text(tr("บันทึกแล้วลบไฟล์เสียงทิ้ง", "The recording is deleted once saved"), Modifier.align(Alignment.CenterHorizontally), color = C.muted, fontSize = TS.caption)
    }
}

/**
 * On the focus page: recordings not written down yet (no network, or the check was closed). The sound is kept on the
 * phone until it is written down or thrown away.
 */
@Composable
fun VoiceWaitingCard(v: TaskViewModel.VoiceUi, vm: TaskViewModel) {
    val file = v.waiting.firstOrNull() ?: return
    if (v.recording != null || v.busy || v.review != null) return
    val at = app.omnitask.data.VoiceRec.since(file)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.amberSoft)
            .border(1.dp, C.amber.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(C.amber))
            Text(
                tr("มีเสียงที่ยังไม่ได้ถอด ${v.waiting.size} ไฟล์", "${v.waiting.size} recording(s) not written down yet"),
                Modifier.padding(start = 8.dp), color = C.amber, fontSize = TS.body, fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            listOfNotNull(
                at?.let { tr("อัดเมื่อ ${Secretary.whenText(it)}", "Recorded ${Secretary.whenText(it)}") },
                v.failed,
                tr("เสียงยังเก็บอยู่ในเครื่อง ไม่หาย", "The sound is kept on this phone"),
            ).joinToString("\n"),
            color = C.text2, fontSize = TS.body,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(tr("ทิ้ง", "Throw away"), { vm.dropVoice(file) }, Modifier.weight(1f))
            PrimaryButton(tr("ถอดเสียงอีกครั้ง", "Write it down again"), { vm.transcribe(file) }, Modifier.weight(2f), color = C.amber)
        }
    }
}
