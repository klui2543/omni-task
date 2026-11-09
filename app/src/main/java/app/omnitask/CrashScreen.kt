package app.omnitask

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The last crash report, shown on its own before the app loads anything, so a crash that repeats on every launch
 * still leaves a report the owner can send. Plain colours and Thai only: the app's theme and language are not loaded.
 */
@Composable
fun CrashScreen(report: String, onOpen: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().background(Color(0xFF16181D)).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("แอปปิดไปเองเมื่อครั้งก่อน", color = Color.White, fontSize = 20.sp)
        Text("กดคัดลอกและส่ง แล้ววางให้ Claude จะได้แก้ตรงจุด", color = Color(0xFFB8BCC6), fontSize = 14.sp)
        Text(
            report,
            Modifier.weight(1f).fillMaxWidth().background(Color(0xFF22252C)).verticalScroll(rememberScrollState()).padding(10.dp),
            color = Color(0xFFD6D9E0), fontSize = 11.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                clipboard.setText(AnnotatedString(report))
                runCatching {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "ส่งรายงาน"),
                    )
                }
            }) { Text("คัดลอกและส่ง") }
            OutlinedButton(onClick = onOpen) { Text("เปิดแอปต่อ", color = Color.White) }
        }
    }
}
