package app.omnitask.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.DateBucket
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.bucket
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val TH = Locale("th")
val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", TH)

/** Stroke icons drawn from 24×24 path data, matching the mockups. */
object Ic {
    private fun icon(name: String, vararg paths: String, width: Float = 1.9f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    addPathNodes(it), stroke = SolidColor(Color.White), strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val focus = icon("focus", "M12 3.5a8.5 8.5 0 1 0 0.01 0Z", "M12 7.5a4.5 4.5 0 1 0 0.01 0Z")
    val tasks = icon("tasks", "M9 6h11M9 12h11M9 18h11", "M4.5 6h0.01M4.5 12h0.01M4.5 18h0.01")
    val views = icon("views", "M4 4h7v7H4Z", "M13 4h7v7h-7Z", "M4 13h7v7H4Z", "M13 13h7v7h-7Z")
    val folder = icon("folder", "M3.5 7.5a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2Z")
    val spark = icon("spark", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8Z")
    val plus = icon("plus", "M12 5v14M5 12h14", width = 2.2f)
    val search = icon("search", "M11 4.5a6.5 6.5 0 1 0 0.01 0Z", "M16 16l4 4")
    val filter = icon("filter", "M4 5h16l-6 7.5V19l-4-2v-4.5Z")
    val camera = icon("camera", "M4 8h3l2-2.5h6L17 8h3v11H4Z", "M12 9.5a3.5 3.5 0 1 0 0.01 0Z")
    val image = icon("image", "M3.5 5h17v14h-17Z", "M3.5 16l5-5 4 4 3-3 5 5")
    val lock = icon("lock", "M5 11h14v9H5Z", "M8 11V8a4 4 0 0 1 8 0v3")
    val check = icon("check", "M5 12.5l4.5 4.5L19 7.5", width = 2.8f)
    val close = icon("close", "M6 6l12 12M18 6L6 18", width = 2f)
    val back = icon("back", "M15 6l-6 6 6 6", width = 2f)
    val next = icon("next", "M9 6l6 6-6 6", width = 2f)
    val send = icon("send", "M5 12h14M13 6l6 6-6 6", width = 2.2f)
    val bell = icon("bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15Z", "M10 20.5h4")
    val refresh = icon("refresh", "M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7")
    val flag = icon("flag", "M5 21V4h11l-2 4 2 4H5")
    val calendar = icon("calendar", "M3.5 5h17v15h-17Z", "M3.5 10h17M8 3v4M16 3v4")
    val link = icon("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
    val share = icon("share", "M12 4v11", "M7 9l5-5 5 5", "M5 14v5h14v-5")
    val folderOpen = icon("folderOpen", "M3.5 7.5a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v1", "M3.5 18.5l2.5-8h15l-2.5 8Z")
}

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = C.card, border: Color = C.cardBorder, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = color, border = BorderStroke(1.dp, border)) {
        Column(content = content)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, color = fg, fontSize = TS.caption, lineHeight = 16.sp, maxLines = 1)
    }
}

/** The round check: priority colour while open, filled lime when done, half-filled while in progress. */
@Composable
fun TaskCheck(task: Task, onClick: () -> Unit, size: Dp = 20.dp) {
    val done = task.status == Status.DONE
    val ring = when {
        done -> C.lime
        task.status == Status.CANCELLED -> C.faint
        else -> task.priority.tint
    }
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(if (done) C.lime else if (task.status == Status.IN_PROGRESS) ring.copy(alpha = 0.28f) else Color.Transparent)
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Ic.check, "เสร็จแล้ว", tint = C.onAccent, modifier = Modifier.size(size * 0.6f))
        }
    }
}

@Composable
fun ProgressRing(ratio: Float, size: Dp, stroke: Dp, color: Color = C.lime, label: String? = null, labelSize: Int = 13) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(C.control, 0f, 360f, false, tl, arc, style = Stroke(w))
            if (ratio > 0f) drawArc(color, -90f, 360f * ratio.coerceIn(0f, 1f), false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        if (label != null) Text(label, fontSize = labelSize.sp, fontWeight = FontWeight.Medium, color = C.text)
    }
}

@Composable
fun SectionHead(title: String, color: Color = C.text2, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = color, style = MaterialTheme.typography.titleSmall)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(1.dp).background(C.divider))
        if (trailing != null) Text(trailing, color = C.muted, fontSize = TS.caption)
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(C.card).border(1.dp, C.cardBorder, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) C.accent else Color.Transparent).clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) C.onAccent else C.muted, fontSize = TS.body, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, dot: Color? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (selected) C.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) C.accent else C.control, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, color = if (selected) C.text else C.text2, fontSize = TS.body, maxLines = 1)
    }
}

@Composable
fun SquareButton(icon: ImageVector, description: String, onClick: () -> Unit, filled: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
            .background(if (filled) C.accent else C.raised)
            .border(1.dp, if (filled) C.accent else C.cardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (filled) C.onAccent else C.text2, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.accent) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(color).clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = C.onAccent, fontWeight = FontWeight.Medium, fontSize = TS.body, textAlign = TextAlign.Center) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, C.control, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = C.text2, fontSize = TS.body, textAlign = TextAlign.Center) }
}

@Composable
fun OnOff(checked: Boolean) {
    Box(
        Modifier.width(44.dp).height(26.dp).clip(CircleShape).background(if (checked) C.accent else C.control).padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(20.dp).clip(CircleShape).background(if (checked) C.onAccent else C.muted)) }
}

data class Meta(val text: String, val bg: Color, val fg: Color, val icon: ImageVector? = null)

/** The small labels under a task title. */
fun metaOf(t: Task, today: LocalDate, blockedBy: String? = null, compact: Boolean = false): List<Meta> = buildList {
    val open = t.isOpen
    if (blockedBy != null) add(Meta("รอ $blockedBy", C.amberSoft, C.amber, Ic.lock))
    t.due?.let { d ->
        val late = open && d < today
        val text = when {
            d == today -> "ครบวันนี้"
            late -> "เลย ${d.format(SHORT_DATE)}"
            else -> "ครบ ${d.format(SHORT_DATE)}"
        }
        add(Meta(text, if (late) C.redSoft else C.raised, if (late) C.red else C.text2))
    }
    if (t.due == null || !compact) t.scheduled?.let { d ->
        add(Meta(if (d == today) "นัดวันนี้" else "นัด ${d.format(SHORT_DATE)}", C.raised, C.text2))
    }
    if (t.status == Status.IN_PROGRESS) add(Meta("กำลังทำ", C.accentSoft, C.accentText))
    t.reminderTime?.let { add(Meta("%02d:%02d".format(it.hour, it.minute), C.raised, C.text2, Ic.bell)) }
    if (t.recurrence != null) add(Meta("↻ ${t.recurrence}", C.raised, C.text2))
    if (!compact) {
        val project = Projects.projectOf(t)
        t.tags.filterNot { it.startsWith("remind-at-") }.forEach { tag ->
            if (tag == project) add(Meta(tag, C.tealSoft, C.tealChip)) else add(Meta("#$tag", C.accentSoft, C.accentText))
        }
    }
    if (t.attachments.isNotEmpty()) add(Meta(t.attachments.size.toString(), C.raised, C.text2, Ic.image))
    if (t.links.isNotEmpty()) add(Meta(t.links.size.toString(), C.raised, C.text2, Ic.link))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    task: Task,
    today: LocalDate,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    blockedBy: String? = null,
    compact: Boolean = false,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val done = !task.isOpen
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.Top) {
        TaskCheck(task, onToggle)
        Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(top = 11.dp, bottom = 10.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done || blockedBy != null) C.muted else C.text,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = if (compact) 2 else 4,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = metaOf(task, today, blockedBy, compact)
            if (meta.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    meta.forEach { Pill(it.text, it.bg, it.fg, it.icon) }
                }
            }
        }
        if (trailing != null) Row(Modifier.padding(top = 4.dp), content = trailing)
    }
}

@Composable
fun Divider(start: Dp = 0.dp) {
    Box(Modifier.fillMaxWidth().padding(start = start).height(1.dp).background(C.divider))
}

fun DateBucket.isLate() = this == DateBucket.OVERDUE

fun Task.isLate(today: LocalDate) = isOpen && bucket(today) == DateBucket.OVERDUE
