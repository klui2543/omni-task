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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.omnitask.model.OmniList
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr

/** A list's emoji on a quiet tile. */
@Composable
fun ListIconTile(list: OmniList, size: Int = 44, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3.6).dp)).background(C.raised)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(ListEmoji.of(list.icon), fontSize = (size * 0.5).sp, lineHeight = (size * 0.6).sp) }
}

/** The "รายการ" section under the projects: one card per list note, and a way to make a new one. */
fun LazyListScope.listCards(state: UiState, onOpen: (OmniList) -> Unit, onCreate: () -> Unit) {
    item(key = "lists-head") {
        Text(tr("รายการ", "Lists"), Modifier.padding(start = 4.dp, top = 8.dp), color = C.muted, fontSize = TS.caption)
    }
    items(state.lists, key = { "list:" + it.path }) { l ->
        val items = state.listItems.filter { it.list == l.name }
        val done = items.count { it.status == Status.DONE }
        Card(Modifier.clickable { onOpen(l) }) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ListIconTile(l)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(l.name, style = MaterialTheme.typography.titleMedium, color = C.text)
                    Text(tr("ทำแล้ว $done จาก ${items.size}", "$done of ${items.size} done"), color = C.muted, fontSize = TS.caption)
                }
                Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(14.dp))
            }
        }
    }
    item(key = "lists-new") {
        Text(
            tr("+ สร้างรายการใหม่", "+ New list"),
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF3A3466), RoundedCornerShape(14.dp))
                .clickable(onClick = onCreate).padding(vertical = 12.dp),
            color = C.accentText, fontSize = TS.body, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Inside a list: category chips, a quick add line, and the items (open first, done after). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
fun LazyListScope.listDetail(
    list: OmniList, state: UiState, vm: TaskViewModel, category: String?, onCategory: (String?) -> Unit,
    onOpen: (Task) -> Unit, onIcon: () -> Unit,
) {
    item(key = "list-top") {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListIconTile(list, 40, onIcon)
                Text(
                    tr("แตะไอคอนเพื่อเปลี่ยน", "Tap the icon to change it"),
                    Modifier.padding(start = 12.dp), color = C.faint, fontSize = TS.caption,
                )
            }
            if (list.categories.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(tr("ทั้งหมด", "All"), category == null, { onCategory(null) })
                    list.categories.forEach { c -> Chip(c, category == c, { onCategory(if (category == c) null else c) }) }
                }
            }
            AddLine(
                if (category != null) tr("เพิ่มใน $category", "Add to $category") else tr("เพิ่มใน ${list.name}", "Add to ${list.name}"),
            ) { vm.addListItem(list, it, category) }
        }
    }
    val items = state.listItems.filter { it.list == list.name && (category == null || it.tags.any { t -> t == category }) }
        .sortedWith(compareBy({ it.status == Status.DONE || it.status == Status.CANCELLED }, { it.title.lowercase() }))
    if (items.isEmpty()) {
        item(key = "list-empty") { Text(tr("ยังว่างอยู่ เพิ่มสิ่งแรกได้เลย", "Empty for now. Add the first one."), Modifier.padding(8.dp), color = C.muted, fontSize = TS.body) }
    }
    items(items, key = { "item:" + it.key }) { t ->
        val done = t.status == Status.DONE
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
                .combinedClickable(onClick = { vm.toggleDone(t) }, onLongClick = { onOpen(t) })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(if (done) C.lime else Color.Transparent)
                    .border(2.dp, if (done) C.lime else C.faint, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (done) Icon(Ic.check, null, tint = C.onAccent, modifier = Modifier.size(12.dp)) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    t.title, color = if (done) C.muted else C.text, fontSize = TS.body,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                val cats = t.tags.filter { it in list.categories }
                if (cats.isNotEmpty()) Text(cats.joinToString(", "), color = C.faint, fontSize = TS.caption)
            }
        }
    }
    item(key = "list-file") {
        Text(
            tr("เก็บใน ${list.path} แตะเพื่อติ๊ก กดค้างเพื่อแก้", "Saved in ${list.path}. Tap to tick, long-press to edit"),
            Modifier.padding(start = 4.dp, top = 4.dp), color = C.faint, fontSize = TS.caption,
        )
    }
}

@Composable
private fun AddLine(hint: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun add() {
        if (text.isBlank()) return
        onAdd(text.trim())
        text = ""
    }
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card)
            .border(1.dp, Color(0xFF262B3A), RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
            cursorBrush = SolidColor(C.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(hint, color = C.faint, fontSize = TS.body)
                    inner()
                }
            },
        )
        Box(
            Modifier.padding(start = 8.dp).size(36.dp).clip(RoundedCornerShape(11.dp)).background(C.accent).clickable { add() },
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.plus, tr("เพิ่ม", "Add"), tint = C.onAccent, modifier = Modifier.size(18.dp)) }
    }
}

/** The emoji to choose from, by theme, and a box to type any other; the chosen one is outlined. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconGrid(selected: String, onPick: (String) -> Unit) {
    val current = ListEmoji.of(selected)
    Column {
        ListEmoji.groups.forEach { (label, emoji) ->
            Text(tr(label.first, label.second), Modifier.padding(top = 10.dp, bottom = 6.dp), color = C.faint, fontSize = TS.caption)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                emoji.forEach { e ->
                    val on = e == current
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.accentSoft else C.sunken)
                            .border(1.dp, if (on) C.accent else Color.Transparent, RoundedCornerShape(12.dp)).clickable { onPick(e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 21.sp, lineHeight = 26.sp) }
                }
            }
        }
        var typed by remember { mutableStateOf("") }
        Text(tr("หรือพิมพ์ emoji เอง", "Or type any emoji"), Modifier.padding(top = 12.dp, bottom = 6.dp), color = C.faint, fontSize = TS.caption)
        Field(typed, tr("แตะแล้วเลือกจากแป้นพิมพ์", "Tap and pick from the keyboard")) { text ->
            val first = firstGlyph(text)
            typed = first
            if (first.isNotEmpty() && first.any { it.code > 0x7F }) onPick(first)
        }
    }
}

/** The first character as people see it, so a flag or a family emoji stays whole. */
private fun firstGlyph(text: String): String {
    val t = text.trim()
    if (t.isEmpty()) return ""
    val chars = android.icu.text.BreakIterator.getCharacterInstance()
    chars.setText(t)
    val end = chars.next()
    return if (end > 0) t.substring(0, end) else t
}

@Composable
fun IconPickerDialog(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("เลือกไอคอน", "Pick an icon")) },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) { IconGrid(selected) { onPick(it) } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("ปิด", "Close"), color = C.text2) } },
    )
}

/** Name, icon and categories for a new list note. */
@Composable
fun CreateListDialog(onCreate: (name: String, icon: String, categories: List<String>) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(ListEmoji.DEFAULT) }
    var cats by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("สร้างรายการใหม่", "New list")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Field(name, tr("ชื่อ เช่น หนังสือที่อยากอ่าน", "Name, e.g. Books to read")) { name = it }
                Text(tr("ไอคอน", "Icon"), Modifier.padding(top = 14.dp), color = C.muted, fontSize = TS.caption)
                IconGrid(icon) { icon = it }
                Text(tr("หมวดย่อย คั่นด้วยจุลภาค (ไม่ใส่ก็ได้)", "Categories, comma separated (optional)"), Modifier.padding(top = 14.dp, bottom = 8.dp), color = C.muted, fontSize = TS.caption)
                Field(cats, tr("เช่น นิยาย, ธุรกิจ, สุขภาพ", "e.g. Fiction, Business, Health")) { cats = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, icon, cats.split(',').map { it.trim() }.filter { it.isNotEmpty() }) },
                enabled = name.isNotBlank(),
            ) { Text(tr("สร้าง", "Create"), color = if (name.isNotBlank()) C.accent else C.faint) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}

@Composable
private fun Field(value: String, hint: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = C.text, fontSize = TS.body, fontFamily = AppFont),
        cursorBrush = SolidColor(C.accent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.sunken).padding(14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = C.faint, fontSize = TS.body)
                inner()
            }
        },
    )
}
