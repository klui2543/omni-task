package app.omnitask.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.omnitask.model.Branches
import app.omnitask.model.Task
import app.omnitask.model.tr

@Composable
fun DialogField(value: String, hint: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
        cursorBrush = SolidColor(C.accent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = C.faint, fontSize = TS.body)
                inner()
            }
        },
    )
}

/** Renaming a project shows what will change before anything is written. */
@Composable
fun RenameProjectDialog(old: String, counts: Pair<Int, Int>, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(old) }
    val clean = name.trim().removePrefix("#").replace(Regex("""\s+"""), "-")
    val ok = clean.isNotEmpty() && clean != old && '/' !in clean
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("เปลี่ยนชื่อโปรเจกต์", "Rename project")) },
        text = {
            Column {
                DialogField(name, tr("ชื่อใหม่", "New name")) { name = it }
                Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.sunken).padding(12.dp)) {
                    Row {
                        Text("#$old", Modifier.weight(1f), color = C.muted, fontSize = TS.caption)
                        Text("#$clean", color = C.accentText, fontSize = TS.caption)
                    }
                    Row(Modifier.padding(top = 4.dp)) {
                        Text("#$old/…", Modifier.weight(1f), color = C.muted, fontSize = TS.caption)
                        Text("#$clean/…", color = C.accentText, fontSize = TS.caption)
                    }
                }
                Text(
                    tr("แก้ ${counts.second} บรรทัดงานใน ${counts.first} ไฟล์ รวมทุก branch ลำดับงาน ดาว และสถานะ branch ย้ายตามไปด้วย", "Changes ${counts.second} task lines in ${counts.first} files, branches included; order, star and branch states move along"),
                    Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption,
                )
                if ('/' in clean) Text(tr("ชื่อโปรเจกต์มี / ไม่ได้", "A project name cannot contain /"), Modifier.padding(top = 6.dp), color = C.red, fontSize = TS.caption)
            }
        },
        confirmButton = { TextButton(onClick = { onRename(clean) }, enabled = ok) { Text(tr("เปลี่ยนชื่อ", "Rename"), color = if (ok) C.accent else C.faint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}
