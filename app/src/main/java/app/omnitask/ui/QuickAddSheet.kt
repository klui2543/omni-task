package app.omnitask.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.omnitask.model.Lang
import app.omnitask.model.Priority
import app.omnitask.model.QuickAdd
import app.omnitask.model.tr
import kotlinx.coroutines.delay

/** Add a task in one line; day words, times, #tags and ! marks are read out of the sentence. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(state: UiState, vm: TaskViewModel, voice: Boolean, onAskAssistant: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val draft = QuickAdd.parse(text, state.today)
    val focus = remember { FocusRequester() }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && heard != null) text = (text.trim() + " " + heard).trim()
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
        vm.quickAdd(draft)
        onDismiss()
    }
    // Focus (and the keyboard) only after the sheet has finished opening; asking earlier is the likeliest
    // cause of the crash on the owner's phone.
    LaunchedEffect(Unit) {
        if (voice) {
            startListening()
        } else {
            delay(350)
            runCatching { focus.requestFocus() }
        }
    }

    SheetFrame(onDismiss) {
        Text(tr("เพิ่มงาน", "New task"), style = MaterialTheme.typography.titleMedium, color = C.text)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
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

        // What the sentence was read as, so a wrong guess is visible before saving.
        val chips = buildList {
            draft.due?.let { add(if (it == state.today) tr("วันนี้", "Today") else it.format(SHORT_DATE)) }
            draft.time?.let { add(tr("เตือน ", "Remind ") + "%02d:%02d".format(it.hour, it.minute)) }
            draft.tags.forEach { add("#$it") }
            if (draft.priority != Priority.NONE) add(draft.priority.label)
        }
        if (chips.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { Pill(it, C.accentSoft, C.accentText) }
            }
        }
        Text(
            tr("วันนี้ พรุ่งนี้ วันจันทร์ 25/10 เวลา 9:00 #tag และ ! !! !!! สำหรับความสำคัญ", "today, tomorrow, monday, 25/10, 9:00, #tag, and ! !! !!! for priority"),
            Modifier.padding(top = 10.dp), color = C.faint, fontSize = TS.caption,
        )

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Ic.mic, tr("พูด", "Speak"), C.raised, C.text2) { startListening() }
            RoundIcon(Ic.spark, tr("ถามผู้ช่วยว่าควรทำตอนไหน", "Ask the assistant when to do it"), C.accentSoft, C.accentText) {
                if (text.isNotBlank()) { onAskAssistant(text.trim()); onDismiss() }
            }
            Box(Modifier.weight(1f))
            PrimaryButton(tr("เพิ่มงาน", "Add task"), { add() })
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = fg, modifier = Modifier.size(19.dp))
    }
}
