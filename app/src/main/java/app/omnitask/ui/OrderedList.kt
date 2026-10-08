package app.omnitask.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr

/**
 * Tasks in the order to do them, numbered 1, 2, 3: subtasks inside a task, or a project's tasks.
 * The handle drags a row; neighbours swap once it passes their middle, and [onReorder] gets the new order
 * when the finger lifts. The first open task is marked as the next one.
 */
@Composable
fun OrderedTaskList(
    tasks: List<Task>,
    onToggle: (Task) -> Unit,
    onOpen: (Task) -> Unit,
    onReorder: (List<Task>) -> Unit,
    meta: (Task) -> String,
    locked: (Task) -> Boolean = { false },
) {
    var order by remember(tasks.map { it.raw }) { mutableStateOf(tasks) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // Row heights vary with the meta line, so each row reports its own.
    val heights = remember { HashMap<String, Int>() }
    val next = order.firstOrNull { it.isOpen }

    Column {
        order.forEachIndexed { i, t ->
            key(t.key) {
                val lifted = dragKey == t.key
                val done = t.status == Status.DONE
                val isLocked = !done && locked(t)
                if (i > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).zIndex(if (lifted) 1f else 0f)
                        .onSizeChanged { heights[t.key] = it.height }
                        .graphicsLayer { translationY = if (lifted) offset else 0f }
                        .background(if (lifted) C.raised else Color.Transparent)
                        .clickable { onOpen(t) }.padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(width = 28.dp, height = 44.dp).pointerInput(t.key) {
                            detectDragGestures(
                                onDragStart = { dragKey = t.key; offset = 0f },
                                onDragEnd = {
                                    if (order.map { it.raw } != tasks.map { it.raw }) onReorder(order)
                                    dragKey = null; offset = 0f
                                },
                                onDragCancel = { order = tasks; dragKey = null; offset = 0f },
                            ) { change, amount ->
                                change.consume()
                                offset += amount.y
                                val at = order.indexOfFirst { it.key == t.key }
                                val below = order.getOrNull(at + 1)
                                val above = order.getOrNull(at - 1)
                                val belowH = below?.let { heights[it.key] } ?: 0
                                val aboveH = above?.let { heights[it.key] } ?: 0
                                if (below != null && offset > belowH / 2f) {
                                    order = order.toMutableList().also { it[at] = below; it[at + 1] = t }
                                    offset -= belowH
                                } else if (above != null && offset < -aboveH / 2f) {
                                    order = order.toMutableList().also { it[at] = above; it[at - 1] = t }
                                    offset += aboveH
                                }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ic.grip, tr("ลากเพื่อเรียง", "Drag to reorder"), tint = C.faint, modifier = Modifier.size(16.dp)) }

                    // The number: violet for the one to do next, green once done.
                    val isNext = t == next
                    Box(
                        Modifier.padding(start = 2.dp).size(24.dp).clip(CircleShape)
                            .background(if (isNext) C.accent else if (done) C.limeSoft else C.raised),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", color = if (isNext) C.onAccent else if (done) C.lime else C.text2, fontSize = TS.caption, fontWeight = FontWeight.Medium)
                    }

                    Box(
                        Modifier.padding(start = 6.dp).size(32.dp).clip(CircleShape).clickable { onToggle(t) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(if (done) C.lime else Color.Transparent)
                                .border(2.dp, if (done) C.lime else if (isLocked) C.control else C.faint, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (done) Icon(Ic.check, null, tint = C.onAccent, modifier = Modifier.size(12.dp)) }
                    }

                    Column(Modifier.weight(1f).padding(start = 6.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(
                            t.title, color = if (done || isLocked) C.muted else C.text, fontSize = TS.body,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                        )
                        val line = meta(t)
                        if (line.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLocked) Icon(Ic.lock, null, tint = C.amber, modifier = Modifier.padding(end = 4.dp).size(12.dp))
                                Text(
                                    line, color = if (isLocked) C.amber else if (isNext) C.accentText else C.faint,
                                    fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Icon(Ic.next, null, tint = C.faint, modifier = Modifier.padding(start = 8.dp).width(14.dp))
                }
            }
        }
    }
}
