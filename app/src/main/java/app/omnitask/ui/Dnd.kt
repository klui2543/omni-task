package app.omnitask.ui

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Long-press starts a drag carrying the task key; a plain tap still opens the task. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.taskDragSource(key: String, tint: Color, onTap: () -> Unit): Modifier =
    dragAndDropSource(drawDragDecoration = { drawRoundRect(tint.copy(alpha = 0.45f), cornerRadius = CornerRadius(10.dp.toPx())) }) {
        detectTapGestures(
            onTap = { onTap() },
            onLongPress = { startTransfer(DragAndDropTransferData(ClipData.newPlainText("task", key))) },
        )
    }

/** A drop zone for dragged tasks. The returned state is true while a task hovers over it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTaskDrop(onDrop: (String) -> Unit): Pair<Modifier, MutableState<Boolean>> {
    val hovering = remember { mutableStateOf(false) }
    val drop by rememberUpdatedState(onDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hovering.value = true }
            override fun onExited(event: DragAndDropEvent) { hovering.value = false }
            override fun onEnded(event: DragAndDropEvent) { hovering.value = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering.value = false
                val key = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString() ?: return false
                drop(key)
                return true
            }
        }
    }
    val modifier = Modifier.dragAndDropTarget(
        shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
        target = target,
    )
    return modifier to hovering
}
