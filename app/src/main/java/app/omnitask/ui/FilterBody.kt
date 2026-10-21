package app.omnitask.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.omnitask.model.DateBucket
import app.omnitask.model.Priority
import app.omnitask.model.Status
import app.omnitask.model.TaskKind
import app.omnitask.model.TaskQuery
import app.omnitask.model.label
import app.omnitask.model.tr

/** The tags the quick "me" and "Claude" chips stand for: whose turn a task is. */
const val ME_TAG = "kluido"
const val CLAUDE_TAG = "claudedo"

/** The dates the quick chips cover, so the date section under "more" leaves them out. */
private val QUICK_BUCKETS = setOf(DateBucket.TODAY, DateBucket.OVERDUE)
private val QUICK_TAGS = setOf(ME_TAG, CLAUDE_TAG)

/** Filters chosen beyond the quick chips, for the note on the "more filters" row. */
fun moreInUse(q: TaskQuery): Int =
    (if (q.statuses != TaskQuery.DEFAULT.statuses) 1 else 0) + q.kinds.size + q.lists.size + q.priorities.size + q.notes.size +
        (q.tags - QUICK_TAGS).size + (q.buckets - QUICK_BUCKETS).size

/**
 * The filter in two layers, shared by the Tasks tab and Project Ygdrasil: four quick chips (me, Claude, today,
 * overdue) and the saved filters always show; every other filter waits under "more filters" until [expanded].
 * Chips apply as they are tapped. [extra] adds a page's own choices under "more"; [extraInUse] counts them.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FilterBody(
    q: TaskQuery,
    onChange: (TaskQuery) -> Unit,
    state: UiState,
    vm: TaskViewModel,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onSave: () -> Unit,
    shown: Int,
    onShow: () -> Unit,
    extraInUse: Int = 0,
    hideDone: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    val open = state.allTasks.filter { it.isOpen && it.parent == null }
    fun quickCount(probe: TaskQuery) = open.count { probe.matches(it, state.today) }

    Text(tr("ด่วน", "Quick"), Modifier.padding(top = 4.dp, bottom = 8.dp), color = C.muted, fontSize = TS.caption)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        QuickChip(tr("ฉัน", "Me"), C.tealChip, ME_TAG in q.tags, quickCount(TaskQuery.DEFAULT.copy(tags = setOf(ME_TAG))), Modifier.weight(1f)) {
            onChange(q.copy(tags = q.tags.flip(ME_TAG)))
        }
        QuickChip("Claude", C.accentText, CLAUDE_TAG in q.tags, quickCount(TaskQuery.DEFAULT.copy(tags = setOf(CLAUDE_TAG))), Modifier.weight(1f)) {
            onChange(q.copy(tags = q.tags.flip(CLAUDE_TAG)))
        }
        QuickChip(tr("วันนี้", "Today"), null, DateBucket.TODAY in q.buckets, quickCount(TaskQuery.DEFAULT.copy(buckets = setOf(DateBucket.TODAY))), Modifier.weight(1f)) {
            onChange(q.copy(buckets = q.buckets.flip(DateBucket.TODAY)))
        }
        QuickChip(tr("เลยกำหนด", "Overdue"), null, DateBucket.OVERDUE in q.buckets, quickCount(TaskQuery.DEFAULT.copy(buckets = setOf(DateBucket.OVERDUE))), Modifier.weight(1f)) {
            onChange(q.copy(buckets = q.buckets.flip(DateBucket.OVERDUE)))
        }
    }

    if (state.savedFilters.isNotEmpty()) {
        Text(tr("ตัวกรองที่บันทึกไว้ (กดค้างเพื่อลบ)", "Saved filters (long-press to delete)"), Modifier.padding(top = 14.dp, bottom = 8.dp), color = C.muted, fontSize = TS.caption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.savedFilters.keys.sorted().forEach { name ->
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, C.accentLine, RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = { state.savedFilters[name]?.let { onChange(TaskQuery.decode(it, q)) } },
                            onLongClick = { vm.deleteFilter(name) },
                        ).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, color = C.accentText, fontSize = TS.body)
                }
            }
        }
    }

    // The row that opens the rest, saying how many of the hidden filters are in use.
    val used = moreInUse(q) + extraInUse
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken)
            .clickable { onExpanded(!expanded) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("ตัวกรองเพิ่มเติม", "More filters"), color = C.text, fontSize = TS.body)
            Text(
                if (used > 0) tr("ใช้อยู่ $used อย่าง", "$used in use") else tr("ประเภท วันที่ ความสำคัญ สถานะ Tag", "Type, date, priority, status, tag"),
                color = if (used > 0) C.accentText else C.muted, fontSize = TS.caption,
            )
        }
        Icon(Ic.down, null, tint = C.muted, modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f))
    }

    if (expanded) {
        FilterLabel(tr("ประเภทงาน", "Task type"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskKind.entries.forEach { k -> Chip(k.label, k in q.kinds, { onChange(q.copy(kinds = q.kinds.flip(k))) }) }
            state.listNames.forEach { l -> Chip(l, l in q.lists, { onChange(q.copy(lists = q.lists.flip(l))) }) }
        }
        FilterLabel(tr("วันที่", "Date"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DateBucket.entries.filter { it !in QUICK_BUCKETS }.forEach { b -> Chip(b.label, b in q.buckets, { onChange(q.copy(buckets = q.buckets.flip(b))) }) }
        }
        FilterLabel(tr("ความสำคัญ", "Priority"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Priority.entries.forEach { p -> Chip(p.label, p in q.priorities, { onChange(q.copy(priorities = q.priorities.flip(p))) }, dot = p.tint) }
        }
        FilterLabel(tr("สถานะ", "Status"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Status.entries.forEach { st -> Chip(st.label, st in q.statuses, { onChange(q.copy(statuses = q.statuses.flip(st))) }) }
        }
        val tags = state.tags.filterNot { it.startsWith("remind-at-") || it in QUICK_TAGS }
        if (tags.isNotEmpty() || state.notes.isNotEmpty()) {
            FilterLabel(tr("Tag และโน้ต", "Tags and notes"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { t -> Chip("#$t", t in q.tags, { onChange(q.copy(tags = q.tags.flip(t))) }) }
                state.notes.forEach { n -> Chip(n, n in q.notes, { onChange(q.copy(notes = q.notes.flip(n))) }) }
            }
        }
        if (hideDone) {
            FilterSwitch(
                tr("ซ่อนงานที่เสร็จและยกเลิก", "Hide done and cancelled"),
                tr("ในมุมมอง Kanban, Matrix, Timeline และปฏิทิน", "In Kanban, Matrix, Timeline and calendar"),
                q.hideDone,
            ) { onChange(q.copy(hideDone = !q.hideDone)) }
        }
        extra?.invoke()
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(tr("บันทึกตัวกรองนี้", "Save this filter"), onSave, Modifier.weight(1f))
            PrimaryButton(tr("แสดง $shown งาน", "Show $shown tasks"), onShow, Modifier.weight(1.3f))
        }
    }
}

/** A quick chip: big enough to hit, with how many open tasks it would leave. */
@Composable
private fun QuickChip(label: String, dot: Color?, on: Boolean, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(54.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.text else C.card)
            .border(1.dp, if (on) C.text else C.cardBorder, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(label, color = if (on) C.bg else C.text, fontSize = TS.caption, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Text("$count", color = if (on) C.bg else C.muted, fontSize = TS.micro)
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(text, Modifier.padding(top = 16.dp, bottom = 8.dp), color = C.muted, fontSize = TS.caption)
}

/** A choice that is on or off, as a row with a switch. */
@Composable
fun FilterSwitch(title: String, note: String?, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.sunken)
            .clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = TS.body)
            if (note != null) Text(note, color = C.muted, fontSize = TS.caption)
        }
        OnOff(on)
    }
}

/** The title row of a filter panel: its name and "clear all". */
@Composable
fun FilterHead(onClear: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("กรอง", "Filter"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        trailing()
        Text(
            tr("ล้างทั้งหมด", "Clear all"),
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClear).padding(horizontal = 8.dp, vertical = 12.dp),
            color = C.red, fontSize = TS.body,
        )
    }
}

private fun <T> Set<T>.flip(v: T): Set<T> = if (v in this) this - v else this + v
