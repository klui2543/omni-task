package app.omnitask.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import app.omnitask.model.ListFolders
import app.omnitask.model.OmniList
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.tr


/**
 * The List folder on the Projects desktop (owner's pick, 2026-10-28): each list is a folder like a project's, with no
 * emoji, and a new folder here is a new list note.
 */
fun LazyListScope.listShelf(state: UiState, onOpen: (OmniList) -> Unit, onCreate: () -> Unit) {
    item(key = "lists-grid") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DeskHead(tr("โฟลเดอร์ย่อย", "Folders inside"))
            TileGrid(
                state.lists.map { l ->
                    val items = state.itemsOf(l)
                    Tile("list:" + l.path, l.name, "${items.count { it.status == Status.DONE }}/${items.size}", folderTint(l.tag), { onOpen(l) })
                } + Tile("list-new", tr("โฟลเดอร์ใหม่", "New folder"), "", C.faint, onCreate, dashed = true, mark = "+"),
            )
        }
    }
}

/**
 * Inside a list, or a folder in it ([folder], "" for the list itself), nesting without limit (owner's pick,
 * 2026-10-28, way ก): how much is done, the folders inside, a quick add line, and the items in this folder.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.listFolderPage(
    list: OmniList, folder: String, state: UiState, vm: TaskViewModel,
    onFolder: (String) -> Unit, onNewFolder: () -> Unit, onOpen: (Task) -> Unit,
) {
    val items = state.itemsOf(list)
    val folders = ListFolders.all(list, items)
    fun under(path: String) = items.filter { ListFolders.inside(path, ListFolders.pathOf(list, it)) }
    val here = under(folder)
    val name = if (folder.isEmpty()) list.name else ListFolders.nameOf(folder)
    item(key = "list-progress") {
        val done = here.count { it.status == Status.DONE }
        Column {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.divider)) {
                val r = if (here.isEmpty()) 0f else done.toFloat() / here.size
                Box(Modifier.fillMaxWidth(r.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(folderTint(ListFolders.tagOf(list, folder))))
            }
            Text(tr("ทำแล้ว $done จาก ${here.size}", "$done of ${here.size} done"), Modifier.padding(top = 6.dp), color = C.muted, fontSize = TS.caption)
        }
    }
    item(key = "list-kids") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DeskHead(tr("โฟลเดอร์ย่อย", "Folders inside"))
            TileGrid(
                ListFolders.children(folders, folder).map { p ->
                    val n = under(p)
                    Tile("list-folder:$p", ListFolders.nameOf(p), "${n.count { it.status == Status.DONE }}/${n.size}", folderTint(ListFolders.tagOf(list, p)), { onFolder(p) })
                } + Tile("list-folder-new", tr("โฟลเดอร์ใหม่", "New folder"), "", C.faint, onNewFolder, dashed = true, mark = "+"),
                small = true,
            )
        }
    }
    item(key = "list-add") {
        Column {
            AddLine(tr("เพิ่มใน $name", "Add to $name")) { vm.addListItem(list, it, folder) }
            var including by remember { mutableStateOf(false) }
            Text(
                tr("ดึงงานที่มีอยู่แล้วเข้ามา", "Add existing tasks"),
                Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, C.accentLine, RoundedCornerShape(14.dp))
                    .clickable { including = true }.padding(vertical = 11.dp),
                color = C.accentText, fontSize = TS.body, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (including) IncludeSheet(list, folder, state, { picked -> vm.includeInList(list, picked, folder); including = false }) { including = false }
        }
    }
    val own = items.filter { ListFolders.pathOf(list, it).equals(folder, ignoreCase = true) }
        .sortedWith(compareBy({ it.status == Status.DONE || it.status == Status.CANCELLED }, { it.title.lowercase() }))
    item(key = "list-own-head") { DeskHead(tr("ในโฟลเดอร์นี้ ${own.size}", "In this folder ${own.size}")) }
    if (own.isEmpty()) {
        item(key = "list-empty") {
            Text(
                if (here.isEmpty()) tr("ยังว่างอยู่ เพิ่มสิ่งแรกได้เลย", "Empty for now. Add the first one.")
                else tr("ไม่มีของตรงนี้ เปิดโฟลเดอร์ย่อยเพื่อดูข้างใน", "Nothing here. Open a folder inside to see what it holds"),
                Modifier.padding(8.dp), color = C.muted, fontSize = TS.body,
            )
        }
    }
    items(own, key = { "item:" + it.key }) { t ->
        val done = t.status == Status.DONE
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card)
                // A tap opens the item; its ring, a button of its own, ticks it.
                .clickable { onOpen(t) }
                .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { vm.toggleDone(t) }, contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(if (done) C.lime else Color.Transparent)
                        .border(2.dp, if (done) C.lime else C.faint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (done) Icon(Ic.check, null, tint = C.onAccent, modifier = Modifier.size(12.dp)) }
            }
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text(
                    t.title, color = if (done) C.muted else C.text, fontSize = TS.body,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                // Tasks pulled in from elsewhere say where they live.
                t.filePath.takeIf { t.list == null }?.substringAfterLast('/')?.removeSuffix(".md")
                    ?.let { Text(it, color = C.faint, fontSize = TS.caption) }
            }
        }
    }
    item(key = "list-file") {
        Text(
            tr(
                "เก็บใน ${list.path} และงานที่ติด #${ListFolders.tagOf(list, folder)} แตะวงกลมเพื่อติ๊ก แตะชื่อเพื่อเปิดดูและแก้",
                "Saved in ${list.path}, plus tasks tagged #${ListFolders.tagOf(list, folder)}. Tap the ring to tick, the name to open",
            ),
            Modifier.padding(start = 4.dp, top = 4.dp), color = C.faint, fontSize = TS.caption,
        )
    }
}

/** Pick any number of open tasks from the vault to put in the open folder of the list; they stay where they are. */
@Composable
private fun IncludeSheet(list: OmniList, folder: String, state: UiState, onAdd: (List<Task>) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val already = state.itemsOf(list).map { it.key }.toSet()
    val pool = state.tasks.filter { it.isOpen && it.key !in already }
    val shown = pool.filter { search.isBlank() || it.title.contains(search.trim(), ignoreCase = true) || it.tags.any { t -> t.contains(search.trim().removePrefix("#"), ignoreCase = true) } }
        .sortedBy { it.title.lowercase() }
    val name = if (folder.isEmpty()) list.name else ListFolders.nameOf(folder)
    SheetFrame(onDismiss) {
        Text(tr("ดึงงานเข้า $name", "Add tasks to $name"), style = MaterialTheme.typography.titleMedium, color = C.text)
        Text(
            tr("งานยังอยู่ที่เดิม แค่ติด #${ListFolders.tagOf(list, folder)} เพิ่ม", "Tasks stay where they are and get #${ListFolders.tagOf(list, folder)}"),
            Modifier.padding(top = 2.dp, bottom = 10.dp), color = C.faint, fontSize = TS.caption,
        )
        Field(search, tr("ค้นชื่องานหรือ #แท็ก", "Search a title or #tag")) { search = it }
        Column(Modifier.padding(top = 10.dp)) {
            if (shown.isEmpty()) Text(tr("ไม่พบงาน", "No tasks found"), Modifier.padding(8.dp), color = C.muted, fontSize = TS.body)
            shown.take(150).forEach { t ->
                val on = t.key in picked
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) C.accentSoft else Color.Transparent)
                        .clickable { picked = if (on) picked - t.key else picked + t.key }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (on) C.accent else Color.Transparent)
                            .border(2.dp, if (on) C.accent else C.faint, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(Ic.check, null, tint = C.onAccent, modifier = Modifier.size(12.dp)) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(t.title, color = C.text, fontSize = TS.body)
                        val sub = t.tags.filterNot { it.startsWith("remind-at-") }.joinToString(" ") { "#$it" }
                        if (sub.isNotEmpty()) Text(sub, color = C.faint, fontSize = TS.caption, maxLines = 1)
                    }
                }
            }
        }
        val n = picked.size
        Text(
            if (n == 0) tr("เลือกงานที่จะเพิ่ม", "Pick tasks to add") else tr("เพิ่ม $n งาน", "Add $n tasks"),
            Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (n > 0) C.accent else C.raised)
                .clickable(enabled = n > 0) { onAdd(pool.filter { it.key in picked }) }.padding(vertical = 13.dp),
            color = if (n > 0) C.onAccent else C.faint, fontSize = TS.body, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
            .border(1.dp, C.cardBorder, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
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


/** The first character as people see it, so a flag or a family emoji stays whole. */
internal fun firstGlyph(text: String): String {
    val t = text.trim()
    if (t.isEmpty()) return ""
    val chars = android.icu.text.BreakIterator.getCharacterInstance()
    chars.setText(t)
    val end = chars.next()
    return if (end > 0) t.substring(0, end) else t
}


/** Asks before a list note goes; [own] items go with it, [pulled] tasks from other notes stay and lose the tag. */
@Composable
fun DeleteListDialog(list: OmniList, own: Int, pulled: Int, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("ลบ ${list.name}?", "Delete ${list.name}?")) },
        text = {
            Text(
                tr("ลบไฟล์ ${list.path.substringAfterLast('/')} พร้อม $own รายการในนั้น", "Deletes ${list.path.substringAfterLast('/')} with its $own items") +
                    (if (pulled > 0) tr("\nงานที่ดึงมาจากโน้ตอื่น $pulled งานจะอยู่ที่เดิม แค่เอา #${list.tag} (และโฟลเดอร์ย่อย) ออก", "\nThe $pulled tasks pulled in from other notes stay there and only lose #${list.tag}") else ""),
                color = C.text2,
            )
        },
        confirmButton = { TextButton(onClick = onDelete) { Text(tr("ลบ", "Delete"), color = C.red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}


/** Asks before a folder inside a list goes: what is in it (and in the folders under it) moves up a level; nothing is deleted. */
@Composable
fun DeleteListFolderDialog(path: String, upName: String, count: Int, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("ลบโฟลเดอร์ ${ListFolders.nameOf(path)}?", "Remove the folder ${ListFolders.nameOf(path)}?")) },
        text = {
            Text(
                if (count == 0) tr("โฟลเดอร์นี้ว่าง", "This folder is empty")
                else tr("ของข้างใน $count รายการ (รวมในโฟลเดอร์ย่อย) จะย้ายไปอยู่ใน $upName ไม่มีรายการไหนถูกลบ", "Its $count items (folders inside too) move to $upName; nothing is deleted"),
                color = C.text2,
            )
        },
        confirmButton = { TextButton(onClick = onDelete) { Text(tr("ลบโฟลเดอร์", "Remove"), color = C.red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("ยกเลิก", "Cancel"), color = C.text2) } },
    )
}

/** A name for a new folder: a new list in the List folder, or a folder inside a list. */
@Composable
fun NewListFolderDialog(where: String, onCreate: (name: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.raised,
        title = { Text(tr("โฟลเดอร์ใหม่ใน $where", "New folder in $where")) },
        text = { Field(name, tr("ชื่อ เช่น ญี่ปุ่น", "Name, e.g. Japan")) { name = it } },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(tr("สร้าง", "Create"), color = if (name.isNotBlank()) C.accent else C.faint)
            }
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
