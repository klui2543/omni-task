package app.omnitask.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.omnitask.model.tr

/** The Gemini key and its settings, for the Settings page. */
class AiSettings(
    val ai: AiUi,
    val onKey: (String?) -> Unit,
    val onTest: () -> Unit,
    val onAutoBrief: (Boolean) -> Unit,
)

/**
 * "ผู้ช่วย AI (Gemini)": whether a key is in (only its last four characters show), entering, testing and removing it,
 * whether the brief is read on opening the assistant, and where the key goes.
 */
@Composable
internal fun AiKeyGroup(s: AiSettings) {
    val ai = s.ai
    var entering by remember { mutableStateOf(false) }
    if (entering) AiKeyDialog({ s.onKey(it); entering = false }) { entering = false }
    SettingsGroup(tr("ผู้ช่วย AI (Gemini)", "AI assistant (Gemini)")) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text("API key", color = C.text, fontSize = TS.body)
                Text(
                    if (ai.hasKey) tr("ใส่แล้ว ลงท้ายด้วย ${ai.keyTail}", "Added, ends in ${ai.keyTail}") else tr("ยังไม่ได้ใส่ ผู้ช่วยใช้แบบเดิม (ไม่มี AI)", "Not added: the assistant works without AI"),
                    color = if (ai.hasKey) C.tealChip else C.muted, fontSize = TS.caption,
                )
            }
            if (ai.hasKey) GhostButton(tr("เปลี่ยน", "Change"), { entering = true }) else PrimaryButton(tr("ใส่ key", "Add key"), { entering = true })
        }
        if (ai.hasKey) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(tr("ทดสอบการเชื่อมต่อ", "Test the connection"), color = C.text, fontSize = TS.body)
                val t = ai.test
                Text(
                    when {
                        ai.testing -> tr("กำลังทดสอบ...", "Testing...")
                        t == null -> tr("ยังไม่ได้ทดสอบ", "Not tested yet")
                        else -> t.second
                    },
                    color = when { t == null || ai.testing -> C.muted; t.first -> C.tealChip; else -> C.red }, fontSize = TS.caption,
                )
            }
            GhostButton(tr("ทดสอบ", "Test"), { if (!ai.testing) s.onTest() })
        }
        Row(Modifier.fillMaxWidth().clickable { s.onAutoBrief(!ai.autoBrief) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(tr("สรุปให้เมื่อเปิดหน้าผู้ช่วย", "Sum up when the assistant opens"), color = C.text, fontSize = TS.body)
                Text(tr("อ่านงานใหม่ไม่เกินชั่วโมงละครั้ง เพื่อประหยัด", "At most once an hour, to keep it cheap"), color = C.muted, fontSize = TS.caption)
            }
            OnOff(ai.autoBrief)
        }
        Text(
            tr(
                "รับ key ได้ที่ aistudio.google.com/apikey ควรเปิดแบบจ่ายเงิน (ราว 70 ถึง 250 บาทต่อเดือน) เพราะแบบฟรี Google อาจนำข้อมูลงานไปฝึก AI",
                "Get a key at aistudio.google.com/apikey. Turn on billing (a few dollars a month): on the free tier Google may use your tasks to train its AI.",
            ),
            Modifier.padding(top = 4.dp), color = C.muted, fontSize = TS.caption,
        )
        Text(
            tr("key เก็บในเครื่องนี้เท่านั้น ไม่ซิงก์ลง vault และไม่ส่งไปที่อื่นนอกจาก Google", "The key stays on this phone: it is not synced to the vault and goes nowhere but Google."),
            Modifier.padding(top = 6.dp, bottom = if (ai.hasKey) 0.dp else 12.dp), color = C.muted, fontSize = TS.caption,
        )
        if (ai.hasKey) TextButton(onClick = { s.onKey(null) }, modifier = Modifier.padding(bottom = 4.dp)) {
            Text(tr("ลบ key", "Remove key"), color = C.red, fontSize = TS.body)
        }
    }
}

/** Typing or pasting the key. It is hidden as it is typed, like a password. */
@Composable
private fun AiKeyDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("ใส่ Gemini API key", "Gemini API key")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("คัดลอกจาก aistudio.google.com/apikey แล้ววางที่นี่", "Copy it from aistudio.google.com/apikey and paste it here"), color = C.text2, fontSize = TS.caption)
                OutlinedTextField(
                    value = text, onValueChange = { text = it.trim() }, singleLine = true,
                    placeholder = { Text("AIza...", color = C.faint) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = C.text, unfocusedTextColor = C.text, focusedBorderColor = C.accent, unfocusedBorderColor = C.control, cursorColor = C.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
                    clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.let { text = it }
                }) { Text(tr("วางจากคลิปบอร์ด", "Paste"), color = C.accentText) }
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text) }, enabled = text.isNotBlank()) { Text(tr("บันทึก", "Save"), color = C.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}
