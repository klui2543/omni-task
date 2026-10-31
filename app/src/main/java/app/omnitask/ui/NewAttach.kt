package app.omnitask.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.omnitask.data.ImageAttach
import app.omnitask.model.NoteLinks
import app.omnitask.model.tr

/**
 * Linked notes and images picked for a task that is not written yet (owner's pick, 2026-10-30, mock way ข).
 * They live here until the task is added, then go under it as `- [[note]]` and `- ![[image.webp]]`, the same
 * lines the edit page writes. The quick-add sheet and its full page share one of these, so nothing is lost
 * when the owner opens "ตัวเลือกเพิ่มเติม".
 */
class NewAttachments {
    /** Linked notes, as their link text. */
    val links: SnapshotStateList<String> = mutableStateListOf()
    /** WebP bytes, saved to the attachments folder only when the task is added. */
    val images: SnapshotStateList<ByteArray> = mutableStateListOf()
    /** Photos picked but still being read, or waiting on the "large image" choice; adding waits for them. */
    var preparing by mutableIntStateOf(0)
    val isEmpty get() = links.isEmpty() && images.isEmpty() && preparing == 0
}

/**
 * The phone's photo picker for a new task, with the edit page's "large image" choice. Returns the call that
 * opens the picker; the choice dialog shows here when a photo needs it.
 */
@Composable
fun rememberNewImagePicker(vm: TaskViewModel, extras: NewAttachments): () -> Unit {
    var choosing by remember { mutableStateOf<ImageAttach.Prepared?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            extras.preparing++
            vm.prepareImage(uri) { p ->
                when {
                    p == null -> extras.preparing--
                    p.needsChoice -> choosing = p
                    else -> { extras.images.add(p.full); extras.preparing-- }
                }
            }
        }
    }
    choosing?.let { p ->
        AttachChoiceDialog(p) { reduced ->
            choosing = null
            extras.preparing--
            if (reduced != null) extras.images.add(if (reduced) p.reduced ?: p.full else p.full)
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** The vault's note search for a new task; a note already linked is not offered again. */
@Composable
fun NewNotePicker(state: UiState, extras: NewAttachments, onDismiss: () -> Unit) {
    NotePicker(state.notePaths.filterNot { NoteLinks.linkText(it, state.notePaths) in extras.links }, { path ->
        val name = NoteLinks.linkText(path, state.notePaths)
        if (name !in extras.links) extras.links.add(name)
        onDismiss()
    }, onDismiss)
}

/** The images picked so far, each with a ✕, and a tile to add another. */
@Composable
fun NewImageRow(extras: NewAttachments, size: Dp, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        extras.images.toList().forEach { bytes ->
            Box(Modifier.size(size)) {
                val bmp = remember(bytes) { thumbOf(bytes) }
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(C.raised), contentAlignment = Alignment.Center) {
                    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Ic.image, null, tint = C.faint, modifier = Modifier.size(20.dp))
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(3.dp).size(24.dp).clip(CircleShape).background(C.card.copy(alpha = 0.85f))
                        .clickable { extras.images.remove(bytes) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Ic.close, tr("เอารูปออก", "Remove image"), tint = C.text2, modifier = Modifier.size(11.dp)) }
            }
        }
        Column(
            Modifier.size(size).clip(RoundedCornerShape(12.dp)).border(1.dp, C.control, RoundedCornerShape(12.dp)).clickable(onClick = onAdd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Ic.camera, null, tint = C.text2, modifier = Modifier.size(20.dp))
            Text(tr("แนบรูป", "Add image"), color = C.text2, fontSize = TS.caption)
        }
    }
}

/** The notes linked so far, a line each with a ✕. */
@Composable
fun NewLinkRows(state: UiState, extras: NewAttachments, modifier: Modifier = Modifier) {
    Column(modifier) {
        extras.links.toList().forEach { link ->
            val path = NoteLinks.resolve(link, state.notePaths)
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(link.substringAfterLast('/'), color = C.text, fontSize = TS.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        path?.substringBeforeLast('/', "")?.ifEmpty { tr("ราก Vault", "Vault root") } ?: tr("ยังไม่มีโน้ตนี้", "Note does not exist yet"),
                        color = if (path == null) C.amber else C.faint, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                SquareButton(Ic.close, tr("เอาลิงก์ออก", "Remove link"), { extras.links.remove(link) })
            }
        }
    }
}

/** A small preview of picked bytes, made the same way as the edit page's. */
private fun thumbOf(bytes: ByteArray): Bitmap? = ImageAttach.thumbnail(bytes, 240)
