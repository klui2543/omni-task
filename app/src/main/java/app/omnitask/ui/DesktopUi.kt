package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Projects page as a desktop (owner's pick, 2026-10-26, mock way 1): folders as icons, the pinned ones on top,
 * the rest in the folder "อื่นๆ", the lists in the folder "List".
 */
data class Tile(
    val key: String,
    val name: String,
    val sub: String,
    val tint: Color,
    val onClick: () -> Unit,
    val pinned: Boolean = false,
    val late: Int = 0,
    val dashed: Boolean = false,
    val dim: Boolean = false,
    val mark: String? = null,
    /** A short note on the folder's corner, such as "ใหม่ 2" for the secretary's proposals inside. */
    val badge: String? = null,
)

/** Folder icons three to a row, like a phone's home screen. */
@Composable
fun TileGrid(tiles: List<Tile>, small: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t -> Box(Modifier.weight(1f)) { FolderTile(t, small) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FolderTile(t: Tile, small: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = t.onClick).alpha(if (t.dim) 0.5f else 1f).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            FolderGlyph(t.tint, if (small) 52.dp else 64.dp, dashed = t.dashed)
            t.mark?.let {
                Text(it, Modifier.align(Alignment.Center).offset(y = 5.dp), color = Color.Black.copy(alpha = 0.55f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            if (t.pinned) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(22.dp).clip(CircleShape).background(C.card),
                contentAlignment = Alignment.Center,
            ) { PinMark() }
            if (t.late > 0) Box(
                Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-4).dp).widthIn(min = 18.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(C.red)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Text("${t.late}", color = C.bg, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            t.badge?.let { b ->
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(C.amber)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(b, color = C.bg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
        Text(t.name, Modifier.padding(top = 4.dp), color = C.text, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(t.sub, color = if (t.late > 0) C.red else C.faint, fontSize = TS.micro, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** A small heading with a hairline after it, between groups of folders. */
@Composable
fun DeskHead(title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = C.muted, fontSize = TS.caption)
        Box(Modifier.padding(start = 8.dp).weight(1f).height(1.dp).background(C.divider))
    }
}

/** Where a folder sits, every level a tap back: `โปรเจกต์ › Siriraj › ตารางเวรเภสัช`. */
@Composable
fun FolderCrumbs(path: String, onHome: () -> Unit, onOpen: (String) -> Unit) {
    val parts = path.split('/')
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("โปรเจกต์", "Projects"), Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onHome).padding(horizontal = 2.dp), color = C.accentText, fontSize = TS.caption)
        parts.forEachIndexed { i, name ->
            Text(" › ", color = C.faint, fontSize = TS.caption)
            val to = parts.take(i + 1).joinToString("/")
            if (i == parts.lastIndex) Text(name, color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Text(name, Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpen(to) }.padding(horizontal = 2.dp), color = C.accentText, fontSize = TS.caption, maxLines = 1)
        }
    }
}

private fun tr(th: String, en: String) = app.omnitask.model.tr(th, en)

/**
 * A lone tag or a label opened from the folder "อื่นๆ" (mock way 1: a tile opens, a label shows its tasks): its open
 * tasks, and the way to make it a project, which a tap did at once before.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TagTasksSheet(
    state: UiState,
    tag: String,
    canMakeProject: Boolean,
    onOpen: (app.omnitask.model.Task) -> Unit,
    onToggle: (app.omnitask.model.Task) -> Unit,
    onMakeProject: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tasks = state.tasks.filter { t -> t.isOpen && t.parent == null && t.tags.any { it.equals(tag, ignoreCase = true) || it.startsWith("$tag/", ignoreCase = true) } }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.raised,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("#$tag", color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
            Text(tr("งานค้าง ${tasks.size} งาน", "${tasks.size} open"), color = C.muted, fontSize = TS.caption)
            tasks.forEachIndexed { i, t ->
                if (i > 0) Divider(start = 48.dp)
                TaskRow(t, state.today, { onToggle(t) }, { onOpen(t) })
            }
            if (canMakeProject) Text(
                tr("ทำเป็นโปรเจกต์", "Make it a project"),
                Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(C.card).clickable(onClick = onMakeProject).padding(horizontal = 14.dp, vertical = 10.dp),
                color = C.accentText, fontSize = TS.body,
            )
        }
    }
}
