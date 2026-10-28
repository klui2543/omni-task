package app.omnitask.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.omnitask.model.Branches
import app.omnitask.model.ListFolders
import app.omnitask.model.OmniList
import app.omnitask.model.Priority
import app.omnitask.model.Projects
import app.omnitask.model.Status
import app.omnitask.model.Task
import app.omnitask.model.TreeMove
import app.omnitask.model.Ygdrasil
import app.omnitask.model.tr

/** The scale a tree too big for the screen opens at: small enough to see a lot, big enough to read. */
private const val READABLE = 0.62f

/**
 * Project Ygdrasil: every project in one tree that fills the screen, from the trunk through each project (a world)
 * and its branches to the tasks, with the tasks that wait for one another tied across. Drag to move, pinch or the
 * buttons to zoom; tap a world or branch for its actions underneath; tap a task to tick it, long-press to open it.
 * The filter and the folded limbs come from the Projects tab, so they stay while it is open.
 */
/**
 * The tree set inside another page (the assistant's): that page owns the chips and the picked task. A tap picks a
 * task instead of opening it, nothing can be ticked or carried, tasks that do not match are [dim], the assistant's
 * [picks] get a ring in the accent, each task shows whose turn it is ([mine]: true for the owner, false for Claude)
 * and how long it has sat ([stale]), and the dashed wait lines say "รอ".
 */
class YgdrasilEmbed(
    val dim: (Task) -> Boolean,
    val picks: Set<String>,
    val selKey: String?,
    val mine: (Task) -> Boolean,
    val stale: (Task) -> Int?,
    val onSelect: (Task?) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YgdrasilScreen(
    state: UiState,
    vm: TaskViewModel,
    filter: Ygdrasil.Filter,
    onFilter: (Ygdrasil.Filter) -> Unit,
    folded: Set<String>,
    onFolded: (Set<String>) -> Unit,
    onOpen: (Task) -> Unit,
    onOpenProject: (String) -> Unit,
    onBack: () -> Unit,
    embed: YgdrasilEmbed? = null,
    onOpenList: ((OmniList, String) -> Unit)? = null,
    onCreateList: (() -> Unit)? = null,
) {
    val tree = remember(state.tasks, state.parked, state.branchStates, state.today, state.starred, state.projectOrder, filter) {
        Ygdrasil.grow(state.tasks + state.parked, state.branchStates, state.today, filter, state.starred, state.projectOrder)
    }
    // Opened, every project folds but those with work in the chosen reach (work past its 📅 date or its own ⏳ target always opens one); the
    // owner's own folds then hold until the reach changes or the tree is opened again. A folded project tells its next task.
    val reach = state.treeReach
    val notes = remember(tree, reach, state.today, embed) { if (embed == null) Ygdrasil.urgencies(tree, reach, state.today) else emptyMap() }
    var applied by rememberSaveable { mutableStateOf<String?>(null) }
    val auto = embed == null && applied != reach.name
    val shownFolded = if (auto) Ygdrasil.autoFolded(tree, reach, state.today, folded) else folded
    SideEffect { if (auto) { applied = reach.name; onFolded(shownFolded) } }
    // Work outside the reach stays on the tree but faint, so what the reach is about stands out.
    val lit = remember(tree, reach, state.today, embed) { if (embed == null) Ygdrasil.reachTags(tree, reach, state.today) else null }
    fun faint(t: Task) = lit != null && !Ygdrasil.inReach(t, reach, state.today)
    fun faint(tag: String) = lit != null && tag !in lit && !Ygdrasil.isInbox(tag) && !isListTag(tag)
    // The tray of tasks with no project, under the tree, and the task whose new home is being picked.
    var straySort by remember { mutableStateOf(Ygdrasil.StraySort.DUE) }
    // The lists as one more limb before the tray (owner's pick, 2026-10-26, Ygdrasil mock way 1); left out while filtering.
    val listsShown = onOpenList != null && filter.query.text.isBlank() && !filter.starredOnly && !filter.blockedOnly
    // A task shows in one place only (owner, 2026-10-27): in its project if it has one, else on its list, else with
    // the tasks that have no project.
    val listTags = if (listsShown) state.lists.map { it.tag } else emptyList()
    val strays = remember(state.tasks, state.today, state.projectTags, filter, straySort, listTags) { Ygdrasil.strays(state.tasks, state.today, filter, straySort, listTags) }
    val strayCount = strays.sumOf { it.tasks.size }
    val inboxGroups = remember(strays) { Ygdrasil.inboxGroups(strays) }
    var trayOpen by rememberSaveable { mutableStateOf(false) }
    // The tasks with no project are one more (pretend) project at the end of the tree, drawn like the others.
    val grown = remember(tree, strays, embed, state.lists, state.listItems, listsShown) {
        if (embed == null) withListLimb(Ygdrasil.withInbox(tree, strays), if (listsShown) state.lists else emptyList()) { l ->
            state.itemsOf(l).filter { it.isOpen && !it.todayCopy && it.parent == null && (it.list != null || Projects.projectOf(it) == null) }
        } else tree
    }
    // Desktop pins are the stars; before any star every project is pinned.
    val pinned = if (state.starred.isEmpty()) tree.worlds.map { it.node.tag }.toSet() else state.starred
    val layout = remember(grown, shownFolded, notes) { Ygdrasil.layout(grown, shownFolded, notes = notes) }
    LaunchedEffect(strayCount) { if (strayCount == 0) trayOpen = false }
    var picking by remember { mutableStateOf<Task?>(null) }
    var trayHeight by remember { mutableStateOf(0) }
    var boxAt by remember { mutableStateOf(Offset.Zero) }
    var selTag by rememberSaveable { mutableStateOf("") }
    val sel = layout.spots.firstOrNull { it.kind == Ygdrasil.Kind.LIMB && it.limb!!.node.tag == selTag }

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var touched by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    // A tree too big for the screen opens readable, from the trunk, instead of shrunk until its text cannot be read;
    // "Fit" shows all of it, and the tree keeps that way until the owner moves it.
    var whole by remember { mutableStateOf(false) }
    fun place() {
        if (box.width == 0) return
        val pad = 24f * density
        val s = minOf(1.2f, (box.width - pad * 2) / (layout.width * density), (box.height - pad * 2) / (layout.height * density))
        val trunkY = layout.spots.firstOrNull { it.kind == Ygdrasil.Kind.TRUNK }?.y ?: (layout.height / 2f)
        if (!whole && s < READABLE) {
            scale = READABLE
            pan = Offset(pad, box.height / 2f - trunkY * density * READABLE)
        } else {
            val k = s.coerceIn(0.15f, 2.5f)
            scale = k
            pan = Offset((box.width - layout.width * density * k) / 2f, (box.height - layout.height * density * k) / 2f)
        }
        touched = false
    }
    fun fit() { whole = true; place() }
    fun zoomBy(f: Float, at: Offset) {
        val s = (scale * f).coerceIn(0.15f, 2.5f)
        pan = at - (at - pan) * (s / scale)
        scale = s
        touched = true
    }
    // Until the owner moves it, the tree keeps fitting the screen as filters and folds change its size.
    LaunchedEffect(layout.width, layout.height, box) { if (!touched) place() }
    // Zoomed far out, tasks show as their rings only.
    val far by remember { derivedStateOf { scale < 0.45f } }

    // A task, project or branch carried across the tree (held, then dragged): where it is, in dp, and the project or
    // branch (or the trunk, "") under it. A task let go on another branch moves there, and let go where it was opens;
    // a project or branch let go on another goes under it, and on the trunk becomes a project of its own.
    var carry by remember { mutableStateOf<Carry?>(null) }
    var carryAt by remember { mutableStateOf(Offset.Zero) }
    var carryMoved by remember { mutableStateOf(false) }
    // Set when a press lifts a card, so letting it go where it was does not also fold or open it.
    val lifted = remember { booleanArrayOf(false) }
    val spots by rememberUpdatedState(layout.spots)
    fun targetAt(p: Offset): String? = spots.firstOrNull { s ->
        (s.kind == Ygdrasil.Kind.LIMB || s.kind == Ygdrasil.Kind.TRUNK) && p.x in s.x..(s.x + s.w) && p.y in (s.y - s.h / 2)..(s.y + s.h / 2)
    }?.let { it.limb?.node?.tag ?: "" }?.takeUnless { isListTag(it) }
    /** Where the carried thing would go if let go now, or null. A task can go to "no project yet"; a project or branch cannot. */
    fun goesTo(c: Carry, to: String?): String? = when {
        to == null -> null
        // A task with no project let go on the no-tag group or the whole of "no project yet" stays as it is.
        c.task != null -> to.takeIf { it.isNotEmpty() && it != c.from && !(c.stray && Ygdrasil.isInbox(it) && inboxGroups[it] == null) }
        else -> to.takeIf { !Ygdrasil.isInbox(it) && Branches.graftTag(c.from, it) != null }
    }
    val dropTag = carry?.let { goesTo(it, targetAt(carryAt)) }
    // A task held near a list (owner's pick, 2026-10-27, mock round 2): the nearest list within about half a finger
    // takes it, since a list's card is small on a zoomed-out tree; let go, the task moves into the list (owner, 2026-10-27).
    fun listAt(p: Offset): Pair<OmniList, String>? {
        val reach = 30f / scale.coerceAtLeast(0.2f)
        return spots.filter { it.kind == Ygdrasil.Kind.LIMB && it.limb!!.node.tag.startsWith("$LIST_LIMB/") }
            .map { s -> s to Offset(maxOf(s.x - p.x, 0f, p.x - s.x - s.w), maxOf(s.y - s.h / 2 - p.y, 0f, p.y - s.y - s.h / 2)).getDistance() }
            .filter { it.second < reach }.minByOrNull { it.second }
            ?.let { (s, _) -> listPlace(state.lists, s.limb!!.node.tag) }
    }
    val listDrop = carry?.takeIf { it.task != null && carryMoved && targetAt(carryAt) == null }?.let { c -> listAt(carryAt)?.takeIf { limbTagOf(it) != c.from } }
    // A folded List limb under a carried task: it springs open like a folded project, so its lists can take it.
    fun listRootAt(p: Offset): String? = spots.firstOrNull { s ->
        s.kind == Ygdrasil.Kind.LIMB && s.limb!!.node.tag == LIST_LIMB && p.x in s.x..(s.x + s.w) && p.y in (s.y - s.h / 2)..(s.y + s.h / 2)
    }?.let { LIST_LIMB }
    val baseConfig = LocalViewConfiguration.current
    val quickHold = remember(baseConfig) { object : ViewConfiguration by baseConfig { override val longPressTimeoutMillis: Long get() = HOLD_MS } }
    // Folded branches opened for a moment while something is held over them; folded again when it is let go.
    var sprung by remember { mutableStateOf(setOf<String>()) }
    val foldedNow by rememberUpdatedState(folded)
    fun unspring() {
        if (sprung.isNotEmpty()) onFolded(foldedNow + sprung)
        sprung = emptySet()
    }
    fun drop() {
        val c = carry ?: return
        val to = goesTo(c, targetAt(carryAt))
        // Only after a real drag: a task held and let go where it was still opens.
        val list = if (c.task != null && carryMoved && targetAt(carryAt) == null) listAt(carryAt)?.takeIf { limbTagOf(it) != c.from } else null
        unspring()
        // A task moves for real, to one place only (owner's pick, 2026-10-27, way ก).
        val dest = when {
            c.task == null -> null
            list != null -> TreeMove.To.InList(ListFolders.tagOf(list.first, list.second))
            to == null -> null
            Ygdrasil.isInbox(to) -> TreeMove.To.NoHome(inboxGroups[to])
            else -> TreeMove.To.Branch(to)
        }
        when {
            dest != null -> vm.moveInTree(c.task!!, dest)
            c.task != null && !carryMoved -> if (c.stray) picking = c.task else onOpen(c.task)
            c.task == null && to != null -> vm.graftBranch(c.from, to)
        }
        carry = null
    }
    /** Lifts [what] when held, and carries it until let go. */
    fun Modifier.carries(what: Carry, top: Float, left: Float): Modifier = pointerInput(what, top, left) {
        detectDragGesturesAfterLongPress(
            onDragStart = { o ->
                lifted[0] = true
                carry = what
                carryMoved = false
                carryAt = Offset(left + o.x / density, top + o.y / density)
            },
            onDrag = { change, amount ->
                change.consume()
                carryAt += Offset(amount.x / density, amount.y / density)
                if (amount.getDistance() > 0f) carryMoved = true
            },
            onDragEnd = { drop() },
            onDragCancel = { unspring(); carry = null },
        )
    }
    val hovered = carry?.let { c -> targetAt(carryAt) ?: if (c.task != null) listRootAt(carryAt) else null }
    // Held over a folded project or branch for a moment, it opens to show its branches.
    LaunchedEffect(hovered, carry != null) {
        val tag = hovered ?: return@LaunchedEffect
        if (carry == null || tag !in folded) return@LaunchedEffect
        delay(600)
        sprung = sprung + tag
        onFolded(foldedNow - tag)
    }
    // Held near the top of the tree, or just above the tray, the tree slides to bring more of it in reach; not while
    // over a project or branch, so the one under the finger stays put. The finger stays where it is on the screen.
    LaunchedEffect(carry != null) {
        while (carry != null) {
            delay(16)
            if (targetAt(carryAt) != null || (carry?.task != null && listAt(carryAt) != null)) continue
            val k = density * scale
            val y = carryAt.y * k + pan.y
            val bottom = box.height - (if (trayOpen) trayHeight else 0)
            val edge = 48f * density
            val dy = when {
                y < edge && y > -20f * density -> 8f * density
                y > bottom - edge && y < bottom + 8f * density -> -8f * density
                else -> 0f
            }
            if (dy != 0f) {
                pan += Offset(0f, dy)
                carryAt -= Offset(0f, dy / k)
                touched = true
            }
        }
    }
    // A task from the tray: its place on the screen turned into a place on the tree.
    val strayDrag = StrayDrag(
        onStart = { t, root ->
            carry = Carry(t, "", t.title, stray = true)
            carryMoved = false
            carryAt = (root - boxAt - pan) / (density * scale)
        },
        onDrag = { amount ->
            carryAt += amount / (density * scale)
            if (amount.getDistance() > 0f) carryMoved = true
        },
        onEnd = { drop() },
        onCancel = { unspring(); carry = null },
    )
    picking?.let { t ->
        HomePickerDialog(
            t, Projects.looseTag(t), tree.worlds.flatMap { w -> w.flatten().map { it.node.tag } },
            onDismiss = { picking = null },
        ) { tag -> picking = null; vm.homeTask(t, tag) }
    }

    var naming by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    naming?.let { (title, done) -> MapNameDialog(title, { done(it); naming = null }) { naming = null } }
    // The filter sits under the tree, leaving it in sight: half height with the quick chips, or the whole page.
    var filtering by rememberSaveable { mutableStateOf(false) }
    var filterFull by rememberSaveable { mutableStateOf(false) }

    val dock = if (embed == null) LocalDockClearance.current else 0.dp
    if (embed == null) FullPage()
    Column(Modifier.fillMaxSize().padding(bottom = dock)) {
        if (embed == null) Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SquareButton(Ic.back, tr("กลับ", "Back"), onBack)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Project Ygdrasil", style = MaterialTheme.typography.headlineSmall, color = C.text, maxLines = 1)
                Text(
                    tr("${tree.worlds.size} โปรเจกต์, ${tree.matched} งาน", "${tree.worlds.size} projects, ${tree.matched} tasks") +
                        if (tree.links.isNotEmpty()) tr(", งานที่รอกัน ${tree.links.size} เส้น", ", ${tree.links.size} waits") else "",
                    color = C.muted, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (strayCount > 0) Row(
                Modifier.padding(end = 8.dp).height(40.dp).clip(RoundedCornerShape(12.dp)).background(C.card)
                    .border(1.dp, C.cardBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        filtering = false
                        if (Ygdrasil.INBOX in foldedNow) onFolded(foldedNow - Ygdrasil.INBOX)
                        layout.spots.firstOrNull { it.limb?.node?.tag == Ygdrasil.INBOX }?.let { s ->
                            pan = Offset(24f * density - s.x * density * scale, 120f * density - (s.y - s.h / 2) * density * scale); touched = true
                        }
                    }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("ยังไม่มีบ้าน", "No project"), color = C.text2, fontSize = TS.caption, maxLines = 1)
                Text(
                    "$strayCount", Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).background(C.amberSoft).padding(horizontal = 7.dp),
                    color = C.amber, fontSize = TS.caption, fontWeight = FontWeight.SemiBold,
                )
            }
            val changed = filter.changed
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (changed > 0) C.accent else C.card)
                    .border(1.dp, if (changed > 0) C.accent else C.cardBorder, RoundedCornerShape(12.dp))
                    .clickable { filtering = !filtering; filterFull = false }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.filter, null, tint = if (changed > 0) C.onAccent else C.text2, modifier = Modifier.size(16.dp))
                Text(
                    tr("ตัวกรอง", "Filter") + if (changed > 0) " ($changed)" else "",
                    Modifier.padding(start = 6.dp), color = if (changed > 0) C.onAccent else C.text2, fontSize = TS.caption,
                )
            }
        }
        if (!(filtering && filterFull)) Box(Modifier.weight(1f).fillMaxWidth()) {
        Box(
            Modifier.fillMaxSize().clipToBounds().background(C.sunken)
                .onSizeChanged { box = it }
                .onGloballyPositioned { boxAt = it.positionInRoot() }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, move, zoom, _ ->
                        pan += move
                        touched = true
                        if (zoom != 1f) zoomBy(zoom, centroid)
                    }
                },
        ) {
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = pan.x; translationY = pan.y; scaleX = scale; scaleY = scale
                }.size(layout.width.dp, layout.height.dp),
            ) {
                val line = C.control
                val faintLine = C.cardBorder
                val linkColor = C.muted
                val acrossColor = C.amber
                Canvas(Modifier.fillMaxSize()) {
                    layout.edges.forEach { e ->
                        val path = Path().apply {
                            moveTo(e.x1.dp.toPx(), e.y1.dp.toPx())
                            cubicTo(e.cx1.dp.toPx(), e.cy1.dp.toPx(), e.cx2.dp.toPx(), e.cy2.dp.toPx(), e.x2.dp.toPx(), e.y2.dp.toPx())
                        }
                        when (e.kind) {
                            Ygdrasil.EdgeKind.BRANCH -> {
                                val parked = e.state == Branches.State.PARKED
                                drawPath(
                                    path, if (parked) line else (e.state ?: Branches.State.ACTIVE).color.copy(alpha = 0.8f),
                                    style = Stroke(1.8.dp.toPx(), pathEffect = if (parked) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null),
                                )
                            }
                            Ygdrasil.EdgeKind.TASK -> drawPath(path, faintLine, style = Stroke(1.2.dp.toPx()))
                            Ygdrasil.EdgeKind.LINK -> drawPath(
                                path, linkColor.copy(alpha = 0.7f),
                                style = Stroke(1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))),
                            )
                            Ygdrasil.EdgeKind.LINK_ACROSS -> drawPath(
                                path, acrossColor.copy(alpha = 0.9f),
                                style = Stroke(1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()))),
                            )
                        }
                    }
                }
                // Inside the assistant the wait lines say what they mean.
                if (embed != null) layout.edges.filter { it.kind == Ygdrasil.EdgeKind.LINK || it.kind == Ygdrasil.EdgeKind.LINK_ACROSS }.forEach { e ->
                    val mx = (e.x1 + 3 * e.cx1 + 3 * e.cx2 + e.x2) / 8f
                    val my = (e.y1 + 3 * e.cy1 + 3 * e.cy2 + e.y2) / 8f
                    Text(
                        tr("รอ", "waits"),
                        Modifier.offset((mx + 4).dp, (my - 9).dp).clip(RoundedCornerShape(6.dp)).background(C.sunken).padding(horizontal = 3.dp),
                        color = if (e.kind == Ygdrasil.EdgeKind.LINK_ACROSS) C.amber else C.text2, fontSize = TS.micro, fontWeight = FontWeight.SemiBold,
                    )
                }
                CompositionLocalProvider(LocalViewConfiguration provides quickHold) {
                layout.spots.forEach { s ->
                    when (s.kind) {
                        Ygdrasil.Kind.TRUNK -> Row(
                            Modifier.offset(s.x.dp, (s.y - s.h / 2).dp).width(s.w.dp).height(s.h.dp)
                                .clip(RoundedCornerShape(12.dp)).background(C.accent)
                                .then(if (sel == null) Modifier.border(2.dp, C.accentText, RoundedCornerShape(12.dp)) else Modifier)
                                .then(if (carry != null && dropTag == "") Modifier.border(2.dp, C.amber, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable { selTag = "" }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Ygdrasil", Modifier.weight(1f), color = C.onAccent, fontSize = TS.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            // The tasks on the tree; list items (some also under their project) are not counted again.
                            Text("${grown.worlds.filter { !isListTag(it.node.tag) }.sumOf { it.matched }}", color = C.onAccent, fontSize = TS.caption)
                        }
                        Ygdrasil.Kind.LIMB -> {
                            val n = s.limb!!.node
                            val world = n.path.isEmpty()
                            // The pretend project of tasks with no project: amber outline, opens and folds, never moved or picked.
                            val inbox = Ygdrasil.isInbox(n.tag)
                            val listLimb = isListTag(n.tag)
                            val list = if (listLimb && !world) listPlace(state.lists, n.tag)?.takeIf { it.second.isEmpty() }?.first else null
                            val picked = sel === s
                            val target = carry != null && (dropTag == n.tag || (listDrop != null && limbTagOf(listDrop) == n.tag))
                            Row(
                                Modifier.offset(s.x.dp, (s.y - s.h / 2).dp).width(s.w.dp).height(s.h.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(if (target) C.amberSoft else if (inbox) C.bg else if (world) C.accentSoft else C.card)
                                    .border(
                                        if (picked || target || (inbox && world)) 1.5.dp else 1.dp,
                                        if (target) C.amber else if (inbox) C.amber.copy(alpha = if (world) 1f else 0.5f) else if (picked) C.accent else C.cardBorder, RoundedCornerShape(10.dp),
                                    )
                                    .alpha(if (n.state == Branches.State.PARKED) 0.5f else if (carry?.task == null && carry?.from == n.tag) 0.35f else if (faint(n.tag)) 0.35f else 1f)
                                    .then(
                                        if (embed != null) Modifier
                                        else if (listLimb) Modifier.pointerInput(n.tag, s.folded) {
                                            detectTapGestures(onTap = { selTag = n.tag; if (world) onFolded(if (s.folded) foldedNow - n.tag else foldedNow + n.tag) })
                                        }
                                        else if (inbox) Modifier.pointerInput(n.tag, s.folded) {
                                            detectTapGestures(onTap = { onFolded(if (s.folded) foldedNow - n.tag else foldedNow + n.tag) })
                                        }
                                        else Modifier
                                            // A tap picks it and folds or opens it, like the No project card (owner, 2026-10-27).
                                            .pointerInput(n.tag, s.folded, n.children.size, n.all.size) {
                                                detectTapGestures(onPress = { lifted[0] = false }, onTap = {
                                                    selTag = n.tag
                                                    if (lifted[0]) Unit
                                                    else if (s.folded) onFolded(foldedNow - n.tag) else if (n.children.isNotEmpty() || n.all.isNotEmpty()) onFolded(foldedNow + n.tag)
                                                })
                                            }
                                            .carries(Carry(null, n.tag, if (world) "#" + n.name else n.name), s.y - s.h / 2, s.x),
                                    )
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Folders (owner's pick, 2026-10-26): a branch's folder keeps its state's colour; a project's has its own, and a pin when pinned.
                                // Lists and their folders have no emoji (owner, 2026-10-28).
                                if (listLimb && !world) FolderGlyph(folderTint(n.tag), 13.dp)
                                else if (!world) FolderGlyph(n.state.color, 13.dp)
                                else if (listLimb) FolderGlyph(C.accent, 16.dp, Modifier.padding(end = 7.dp))
                                else if (!inbox) FolderGlyph(folderTint(n.tag), 16.dp, Modifier.padding(end = 7.dp))
                                val note = notes[n.tag]
                                if (world && !s.folded && note != null && note.opens) Column(Modifier.weight(1f)) {
                                    // Why it opened on its own, inside the card (the owner's pick, mock ค).
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(n.name, Modifier.weight(1f), color = C.accentText, fontSize = TS.caption, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (n.all.isNotEmpty()) Text("${n.done}/${n.all.size}", color = C.muted, fontSize = TS.micro)
                                    }
                                    Box(Modifier.padding(top = 3.dp)) { TreeBadges(note, reach) }
                                } else if (world && s.folded && note != null && s.w > Ygdrasil.NODE_W) Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            n.name, Modifier.weight(1f, fill = false).padding(end = 6.dp), color = C.accentText, fontSize = TS.caption,
                                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        TreeBadges(note, reach)
                                    }
                                    note.next?.let {
                                        Text(tr("ถัดไป: ${it.title}", "Next: ${it.title}"), Modifier.padding(top = 2.dp), color = C.muted, fontSize = TS.micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                } else Text(
                                    if (inbox && world) tr("ยังไม่มีบ้าน", "No project yet") else if (listLimb && world) "List" else n.name, Modifier.weight(1f).padding(start = if (world) 0.dp else 7.dp),
                                    color = if (inbox) C.amber else if (world) C.accentText else C.text, fontSize = TS.caption,
                                    fontWeight = if (world) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                if (world && !s.folded && note != null && note.opens) Unit
                                else if (listLimb && world) {
                                    Text(tr("${n.children.size} รายการ", "${n.children.size} lists"), color = C.muted, fontSize = TS.micro)
                                } else if (s.folded) {
                                    Text("+${s.count}", Modifier.clip(RoundedCornerShape(8.dp)).background(C.raised).padding(horizontal = 6.dp, vertical = 1.dp), color = C.text2, fontSize = TS.micro)
                                } else if (n.all.isNotEmpty()) {
                                    Text("${n.done}/${n.all.size}", color = C.muted, fontSize = TS.micro)
                                } else if (list != null) {
                                    val items = state.itemsOf(list)
                                    Text("${items.count { !it.isOpen }}/${items.size}", color = C.muted, fontSize = TS.micro)
                                }
                            }
                            if (world && !inbox && !listLimb && n.tag in pinned) PinMark(Modifier.offset((s.x + s.w - 16).dp, (s.y - s.h / 2 + 2).dp))
                            // A thin bar along the card's bottom edge: the share of its open work past due, then past target.
                            val note = notes[n.tag]
                            val open = n.all.size - n.done
                            if (world && note != null && note.opens && open > 0 && (note.late > 0 || note.targetLate > 0)) Row(
                                Modifier.offset((s.x + 1).dp, (s.y + s.h / 2 - 4).dp).width((s.w - 2).dp).height(3.dp),
                            ) {
                                if (note.late > 0) Box(Modifier.weight(note.late.toFloat()).fillMaxHeight().background(C.red))
                                if (note.targetLate > 0) Box(Modifier.weight(note.targetLate.toFloat()).fillMaxHeight().background(C.amber))
                                val rest = open - note.late - note.targetLate
                                if (rest > 0) Box(Modifier.weight(rest.toFloat()).fillMaxHeight().background(C.cardBorder))
                            }
                        }
                        Ygdrasil.Kind.TASK -> {
                            val t = s.task!!
                            val from = s.limb!!.node.tag
                            val done = t.status == Status.DONE
                            val someday = Ygdrasil.isSomeday(t)
                            val aiPick = embed?.picks?.contains(t.key) == true
                            val chosen = embed?.selKey == t.key
                            Row(
                                Modifier.offset(s.x.dp, (s.y - s.h / 2).dp).width(s.w.dp).height(s.h.dp).clip(RoundedCornerShape(8.dp))
                                    .alpha(if (carry?.task == t) 0.35f else if (embed?.dim?.invoke(t) == true) 0.25f else if (faint(t) && !isListTag(from)) 0.35f else if (someday) 0.6f else 1f)
                                    .then(if (chosen) Modifier.background(C.accentSoft) else Modifier)
                                    .then(if (aiPick) Modifier.border(2.dp, C.accent, RoundedCornerShape(8.dp)) else Modifier)
                                    .then(
                                        if (embed != null) Modifier.pointerInput(t, chosen) { detectTapGestures(onTap = { embed.onSelect(if (chosen) null else t) }) }
                                        // A tap opens it (its ring ticks it); held, the task lifts and follows the finger
                                        // (the drag consumes the touch, so neither the tap nor the map's own drag sees it).
                                        // A task with no project: a tap picks its home, held it is dragged up onto a project.
                                        else if (Ygdrasil.isInbox(from)) Modifier.pointerInput(t) { detectTapGestures(onTap = { picking = t }) }.carries(Carry(t, from, t.title, stray = true), s.y - s.h / 2, s.x)
                                        else Modifier.pointerInput(t) { detectTapGestures(onTap = { onOpen(t) }) }.carries(Carry(t, from, t.title), s.y - s.h / 2, s.x),
                                    )
                                    .padding(end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // The ring is a button of its own, a little larger than it looks (only a mark inside the assistant).
                                Box(Modifier.size(28.dp).clip(CircleShape).then(if (embed == null) Modifier.clickable { vm.toggleDone(t, inTree = true) } else Modifier), contentAlignment = Alignment.Center) {
                                    Box(
                                        Modifier.size(15.dp).clip(CircleShape).background(if (done) C.lime else C.sunken)
                                            .border(1.5.dp, if (done) C.lime else priorityTint(t.priority), CircleShape),
                                    )
                                }
                                if (!far) Text(
                                    t.title, Modifier.padding(start = 2.dp).weight(1f, fill = false), color = if (done || someday) C.faint else C.text2, fontSize = TS.caption,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (done) TextDecoration.LineThrough else null,
                                )
                                if (!far && embed != null && !someday) {
                                    Box(Modifier.padding(start = 5.dp).size(7.dp).clip(CircleShape).background(if (embed.mine(t)) C.tealChip else C.accentText))
                                    embed.stale(t)?.let { d ->
                                        Text(tr("$d วัน", "${d}d"), Modifier.padding(start = 5.dp), color = C.amber, fontSize = TS.micro, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                }
                                if (!far && someday) Text(
                                    tr("พักไว้", "Someday"),
                                    Modifier.padding(start = 4.dp).clip(RoundedCornerShape(8.dp)).background(C.raised).padding(horizontal = 6.dp, vertical = 1.dp),
                                    color = C.muted, fontSize = TS.micro, maxLines = 1,
                                )
                            }
                        }
                        Ygdrasil.Kind.MORE -> if (!far) Text(
                            tr("และอีก ${s.count} งาน", "and ${s.count} more"),
                            Modifier.offset((s.x + 6).dp, (s.y - 9).dp), color = C.faint, fontSize = TS.caption,
                        )
                    }
                }
                // What is carried, under the finger, saying where it would go.
                carry?.let { c ->
                    Column(
                        // Centred on the finger (owner, 2026-10-27).
                        Modifier.offset((carryAt.x - 100).dp, (carryAt.y - 22).dp).width(200.dp).clip(RoundedCornerShape(10.dp))
                            .background(C.card).border(1.5.dp, C.accent, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(c.label, color = C.text, fontSize = TS.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                listDrop != null -> (if (listDrop.second.isEmpty()) listDrop.first.name else ListFolders.nameOf(listDrop.second)).let { tr("ย้ายไป $it", "Move to $it") }
                                dropTag == null && c.stray -> tr("ลากขึ้นไปวางบนกิ่ง", "Drag it up onto a branch")
                                dropTag == null -> if (c.task != null) tr("ลากไปวางบนกิ่ง", "Drop it on a branch") else tr("ลากไปวางบนโปรเจกต์ กิ่ง หรือลำต้น", "Drop it on a project, branch or the trunk")
                                c.task != null && Ygdrasil.isInbox(dropTag) -> tr("ย้ายไป ยังไม่มีบ้าน", "Move to No project yet")
                                c.task != null -> tr("ย้ายไป #$dropTag", "Move to #$dropTag")
                                dropTag.isEmpty() -> tr("แยกเป็นโปรเจกต์ของตัวเอง", "Make it a project of its own")
                                else -> tr("ไปไว้ใต้ #$dropTag", "Put it under #$dropTag")
                            },
                            color = C.muted, fontSize = TS.micro, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                }
            }
            if (tree.worlds.isEmpty()) {
                Text(tr("ไม่มีงานตรงกับตัวกรอง", "Nothing matches the filter"), Modifier.align(Alignment.Center), color = C.muted, fontSize = TS.body)
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(C.card)
                    .border(1.dp, C.cardBorder, RoundedCornerShape(12.dp)).padding(3.dp),
            ) {
                val mid = Offset(box.width / 2f, box.height / 2f)
                // Undo and redo sit on top of the zoom keys, always there (owner, 2026-10-27).
                if (embed == null) {
                    UndoKey("↶", tr("เลิกทำ", "Undo"), state.treeUndo.isNotEmpty(), vm::treeUndo)
                    UndoKey("↷", tr("ทำซ้ำ", "Redo"), state.treeRedo.isNotEmpty(), vm::treeRedo)
                    Box(Modifier.padding(horizontal = 4.dp, vertical = 2.dp).width(32.dp).height(1.dp).background(C.cardBorder))
                }
                ZoomKey("+", tr("ซูมเข้า", "Zoom in")) { zoomBy(1.25f, mid) }
                ZoomKey("−", tr("ซูมออก", "Zoom out")) { zoomBy(0.8f, mid) }
                ZoomKey(tr("พอดี", "Fit"), tr("พอดีจอ", "Fit")) { fit() }
            }
            if (embed == null) UndoNote(state.treeUndo, state.treeRedo, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 72.dp, bottom = 12.dp))
        }
        // The tray rises over the bottom of the tree, which still moves above it; low while a task is carried up.
        if (trayOpen && strayCount > 0) StrayTray(
            strays, state.today, straySort, { straySort = it }, { trayOpen = false }, { picking = it },
            { tag -> vm.setProject(tag, true) }, strayDrag,
            Modifier.align(Alignment.BottomCenter).fillMaxHeight(if (carry != null) 0.22f else 0.46f).onSizeChanged { trayHeight = it.height },
            fresh = remember(state.tasks) { vm.freshVoiceTitles() },
        )
        }

        if (embed != null) Unit
        else if (filtering) {
            YgdrasilFilterDock(
                state, vm, filter, onFilter, tree.matched, filterFull, { filterFull = it }, { filtering = false; filterFull = false },
                if (filterFull) Modifier.weight(1f) else Modifier.height(380.dp),
            )
        } else {
        // The trunk or the picked world or branch: what to do with it.
        Column(Modifier.fillMaxWidth().background(C.card).padding(horizontal = 14.dp, vertical = 12.dp).then(if (dock == 0.dp) Modifier.navigationBarsPadding() else Modifier)) {
            val node = sel?.limb?.node
            if (node == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ygdrasil", Modifier.weight(1f), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold)
                    Text(tr("ทุกโปรเจกต์ ${tree.matched} งาน", "${tree.matched} tasks in all"), color = C.faint, fontSize = TS.caption)
                }
                Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapButton(tr("กางทั้งหมด", "Unfold all")) { onFolded(emptySet()) }
                    MapButton(tr("พับทุกโปรเจกต์", "Fold every project")) { onFolded(grown.worlds.map { it.node.tag }.toSet()) }
                    MapButton(if (filter.links) tr("ซ่อนเส้นงานที่รอกัน", "Hide wait lines") else tr("แสดงเส้นงานที่รอกัน", "Show wait lines")) { onFilter(filter.copy(links = !filter.links)) }
                }
                Text(tr("กางเองเมื่อมีงานถึง", "Open on their own with work up to"), Modifier.padding(top = 10.dp), color = C.muted, fontSize = TS.caption)
                Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Ygdrasil.Reach.TODAY, Ygdrasil.Reach.WEEK, Ygdrasil.Reach.MONTH, Ygdrasil.Reach.LATE).forEach { r ->
                        MapButton(r.label, filled = r == reach) { vm.setTreeReach(r) }
                    }
                }
                Text(
                    tr(
                        "แตะโปรเจกต์หรือกิ่งเพื่อจัดการ เส้นประสีส้มคืองานที่รองานในโปรเจกต์อื่น งานที่เลยกำหนด (📅) หรือเลยเป้าตัวเอง (⏳) กางโปรเจกต์เสมอ",
                        "Tap a project or branch. Orange dashes: a task waiting on another project. Work past its 📅 date or its own ⏳ target always opens its project",
                    ),
                    Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption,
                )
            } else if (isListTag(node.tag)) {
                // The lists (owner's pick, mock round 2): the List card makes a list or folds; a list or a folder in it opens.
                val place = listPlace(state.lists, node.tag)
                val list = place?.first
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FolderGlyph(if (place != null) folderTint(node.tag) else C.accent, 18.dp)
                    Text(
                        place?.let { (l, f) -> if (f.isEmpty()) l.name else ListFolders.nameOf(f) } ?: "List",
                        Modifier.weight(1f).padding(start = 8.dp), color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (place != null) state.itemsOf(place.first).filter { ListFolders.inside(place.second, ListFolders.pathOf(place.first, it)) }
                            .let { tr("ทำแล้ว ${it.count { t -> !t.isOpen }} จาก ${it.size}", "${it.count { t -> !t.isOpen }} of ${it.size} done") }
                        else tr("${state.lists.size} รายการ", "${state.lists.size} lists"),
                        color = C.faint, fontSize = TS.caption,
                    )
                }
                Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (place != null) MapButton(if (place.second.isEmpty()) tr("เปิดรายการ", "Open list") else tr("เปิดโฟลเดอร์", "Open folder"), filled = true) { onOpenList?.invoke(place.first, place.second) }
                    else {
                        if (onCreateList != null) MapButton(tr("+ รายการ", "+ List"), filled = true) { onCreateList() }
                        MapButton(if (node.tag in folded) tr("กาง", "Unfold") else tr("พับ", "Fold")) { onFolded(if (node.tag in folded) folded - node.tag else folded + node.tag) }
                    }
                    MapButton(tr("ปิด", "Close")) { selTag = "" }
                }
                Text(
                    if (list != null) tr("ลากงานมาวางบนรายการนี้ = ย้ายงานมาอยู่ในรายการนี้ (ออกจากโปรเจกต์เดิม)", "Drop a task here to move it into this list (it leaves its project)")
                    else tr("ลากงานไปวางบนรายการไหน งานนั้นจะย้ายไปอยู่ในรายการนั้น", "Drop a task on a list to move it into that list"),
                    Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FolderGlyph(if (node.path.isNotEmpty()) node.state.color else folderTint(node.tag), 18.dp)
                    Text(
                        node.name, Modifier.weight(1f).padding(start = 8.dp),
                        color = C.text, fontSize = TS.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text("#" + node.tag + if (node.path.isNotEmpty()) ", " + node.state.label else "", color = C.faint, fontSize = TS.caption, maxLines = 1)
                }
                Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapButton(tr("+ งาน", "+ Task"), filled = true) { naming = tr("งานใหม่ใน ${node.name}", "New task in ${node.name}") to { title: String -> vm.addBranchTask(node, title) } }
                    MapButton(tr("+ กิ่ง", "+ Branch")) { naming = tr("กิ่งใหม่ใต้ ${node.name}", "New branch under ${node.name}") to { name: String -> vm.addEmptyBranch(node, name) } }
                    MapButton(if (node.tag in folded) tr("กาง", "Unfold") else tr("พับ", "Fold")) { onFolded(if (node.tag in folded) folded - node.tag else folded + node.tag) }
                    MapButton(tr("เปิด Mind map", "Open mind map")) { onOpenProject(node.project) }
                }
                Text(tr("แตะงานเพื่อติ๊ก กดค้างแล้วลากไปวางบนโปรเจกต์หรือกิ่งอื่นเพื่อย้าย ปล่อยที่เดิมเพื่อเปิด", "Tap a task to tick it. Hold and drag it onto another project or branch to move it, or let go to open it"), Modifier.padding(top = 8.dp), color = C.muted, fontSize = TS.caption)
            }
        }
        }
    }
}

/** What is carried across the tree: a task ([task] set), or a project or branch; [from] is its tag. */
private data class Carry(val task: Task?, val from: String, val label: String, val stray: Boolean = false)

/** Tasks, projects and branches lift after this short a hold (the system's long press is slower). */
private const val HOLD_MS = 250L

/** The tick ring's edge carries the priority, as the task rows do. */
private fun priorityTint(p: Priority) = when (p) {
    Priority.HIGHEST -> C.red
    Priority.HIGH -> C.amber
    Priority.MEDIUM -> C.blue
    else -> C.faint
}

/**
 * The tree's filter under the tree: the task list's two-layer filter, and the tree's own choices under "more". Half
 * height keeps the tree in sight and changing as chips are tapped; [full] gives it the page.
 */
@Composable
private fun YgdrasilFilterDock(
    state: UiState,
    vm: TaskViewModel,
    filter: Ygdrasil.Filter,
    onFilter: (Ygdrasil.Filter) -> Unit,
    shown: Int,
    full: Boolean,
    onFull: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    var naming by remember { mutableStateOf(false) }
    if (naming) NameDialog(tr("ตั้งชื่อตัวกรอง", "Name this filter"), { vm.saveFilter(it, filter.query); naming = false }) { naming = false }
    val extraInUse = listOf(filter.blockedOnly, filter.starredOnly, filter.parked, !filter.someday).count { it }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(C.card)
            .border(1.dp, C.cardBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
    ) {
        // The grip: a tap switches between half and full.
        Box(Modifier.fillMaxWidth().height(24.dp).clickable { onFull(!full) }, contentAlignment = Alignment.Center) {
            Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(C.control))
        }
        Row(Modifier.padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("กรอง", "Filter"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = C.text)
            Text(
                if (full) tr("ย่อ", "Shrink") else tr("เต็มจอ", "Full screen"),
                Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, C.control, RoundedCornerShape(16.dp)).clickable { onFull(!full) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = C.text2, fontSize = TS.caption,
            )
            Text(
                tr("ล้าง", "Clear"),
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { onFilter(Ygdrasil.Filter(links = filter.links)) }.padding(horizontal = 10.dp, vertical = 12.dp),
                color = C.red, fontSize = TS.body,
            )
            SquareButton(Ic.close, tr("ปิดตัวกรอง", "Close filter"), onClose)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
            // The search reads titles and details, as on the Tasks tab.
            DialogField(filter.query.text, tr("ค้นชื่องานหรือรายละเอียด", "Search titles and details")) { onFilter(filter.copy(query = filter.query.copy(text = it))) }
            FilterBody(
                filter.query, { onFilter(filter.copy(query = it)) }, state, vm,
                expanded = full, onExpanded = onFull, onSave = { naming = true }, shown = shown, onShow = { onFull(false) },
                extraInUse = extraInUse,
            ) {
                Text(tr("เฉพาะ Ygdrasil", "Ygdrasil only"), Modifier.padding(top = 18.dp), color = C.accentText, fontSize = TS.caption, fontWeight = FontWeight.SemiBold)
                FilterSwitch(tr("ติดรองานอื่น", "Waiting on other work"), tr("เฉพาะงานที่รองานอื่นที่ยังไม่เสร็จ", "Only tasks waiting on unfinished work"), filter.blockedOnly) { onFilter(filter.copy(blockedOnly = !filter.blockedOnly)) }
                FilterSwitch(tr("แสดงงานที่พักไว้", "Show someday work"), tr("งาน #สักวัน เป็นสีจางพร้อมป้าย", "#สักวัน work, greyed with a label"), filter.someday) { onFilter(filter.copy(someday = !filter.someday)) }
                FilterSwitch(tr("รวมกิ่งที่พับเก็บ", "Parked branches"), null, filter.parked) { onFilter(filter.copy(parked = !filter.parked)) }
                FilterSwitch(tr("เฉพาะโปรเจกต์ติดดาว", "Starred projects only"), null, filter.starredOnly) { onFilter(filter.copy(starredOnly = !filter.starredOnly)) }
            }
        }
    }
}

/** On the Projects tab, above the project cards: the way into Project Ygdrasil. */
@Composable
fun YgdrasilEntry(onClick: () -> Unit) {
    Card(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Project Ygdrasil", color = C.text, fontSize = TS.body, fontWeight = FontWeight.Medium)
                Text(
                    tr("ทุกโปรเจกต์ในต้นไม้เดียว กรองงานได้ เห็นงานที่รอกันข้ามโปรเจกต์", "Every project in one tree, filtered, with waits across projects"),
                    color = C.muted, fontSize = TS.caption,
                )
            }
            Icon(Ic.next, null, tint = C.faint, modifier = Modifier.size(14.dp))
        }
    }
}

/** Why a project opened on its own, or what it holds while folded: past due, due within the reach, past the owner's target. */
@Composable
private fun TreeBadges(u: Ygdrasil.Urgency, reach: Ygdrasil.Reach) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (u.late > 0) TreeBadge(tr("เลยกำหนด ${u.late}", "${u.late} past due"), C.red)
        if (u.targetLate > 0) TreeBadge(tr("เลยเป้า ${u.targetLate}", "${u.targetLate} past target"), C.amber)
        if (u.within > 0) TreeBadge("${reach.label} ${u.within}", C.accentText)
    }
}

@Composable
private fun TreeBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    // A dot and bold text in the colour itself, no tinted box: the box was nearly the card's colour and hid it.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, Modifier.padding(start = 4.dp), color = color, fontSize = TS.micro, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The tag of the pretend limb that holds the lists on the tree; never written to a task. */
private const val LIST_LIMB = "~List"

private fun isListTag(tag: String) = tag == LIST_LIMB || tag.startsWith("$LIST_LIMB/")

private fun listTagOf(l: OmniList) = "$LIST_LIMB/${l.name.replace('/', ' ')}"

/** The limb of a list, or of a folder in it (`~List/Bucket list/เที่ยว`). */
private fun limbTagOf(place: Pair<OmniList, String>) = listTagOf(place.first) + if (place.second.isEmpty()) "" else "/" + place.second

/** The list, and the folder in it ("" for the list itself), that a limb on the tree stands for. */
private fun listPlace(lists: List<OmniList>, tag: String): Pair<OmniList, String>? {
    if (!tag.startsWith("$LIST_LIMB/")) return null
    val rest = tag.removePrefix("$LIST_LIMB/")
    val list = lists.firstOrNull { listTagOf(it) == tag || tag.startsWith(listTagOf(it) + "/") } ?: return null
    return list to rest.removePrefix(list.name.replace('/', ' ')).removePrefix("/")
}

/**
 * [tree] with the lists as one more limb before the tray of tasks with no project: one branch per list, a branch under
 * it for each folder holding open [items] (nesting without limit, owner's pick 2026-10-28), the items as leaves.
 */
private fun withListLimb(tree: Ygdrasil.Tree, lists: List<OmniList>, items: (OmniList) -> List<Task>): Ygdrasil.Tree {
    if (lists.isEmpty()) return tree
    val kids = lists.map { l ->
        val mine = items(l)
        val folders = ListFolders.all(l, mine).filter { f -> mine.any { ListFolders.inside(f, ListFolders.pathOf(l, it)) } }
        fun build(path: String): Ygdrasil.Limb {
            val own = mine.filter { ListFolders.pathOf(l, it).equals(path, ignoreCase = true) }
            val all = mine.filter { ListFolders.inside(path, ListFolders.pathOf(l, it)) }
            val under = ListFolders.children(folders, path).map { build(it) }
            val nodePath = l.name.replace('/', ' ') + if (path.isEmpty()) "" else "/$path"
            return Ygdrasil.Limb(Branches.Node(LIST_LIMB, nodePath, Branches.State.ACTIVE, own, all, under.map { it.node }), own, under)
        }
        build("")
    }
    val limb = Ygdrasil.Limb(Branches.Node(LIST_LIMB, "", Branches.State.ACTIVE, emptyList(), emptyList(), kids.map { it.node }), emptyList(), kids)
    val at = tree.worlds.indexOfFirst { Ygdrasil.isInbox(it.node.tag) }.let { if (it < 0) tree.worlds.size else it }
    return tree.copy(worlds = tree.worlds.take(at) + limb + tree.worlds.drop(at))
}

/**
 * Which task Ygdrasil's ↶ (or ↷, when nothing is left to undo) acts on next, bottom left. It shows again after each
 * tick, move, undo or redo and goes after 30 seconds; the buttons on the zoom panel stay (owner, 2026-10-27).
 */
@Composable
private fun UndoNote(undo: List<TaskViewModel.UndoStep>, redo: List<TaskViewModel.UndoStep>, modifier: Modifier = Modifier) {
    fun word(s: TaskViewModel.UndoStep) = when (s.kind) {
        TaskViewModel.UndoStep.Kind.DONE -> tr("ติ๊ก ${s.title}", "tick ${s.title}")
        TaskViewModel.UndoStep.Kind.OPENED -> tr("เปิดคืน ${s.title}", "reopen ${s.title}")
        TaskViewModel.UndoStep.Kind.MOVED -> tr("ย้าย ${s.title}", "move ${s.title}")
    }
    val what = undo.lastOrNull()?.let { tr("ย้อน: ", "Undo: ") + word(it) } ?: redo.lastOrNull()?.let { tr("ทำซ้ำ: ", "Redo: ") + word(it) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(undo.size, redo.size, undo.lastOrNull(), redo.lastOrNull()) {
        shown = what != null
        kotlinx.coroutines.delay(30_000)
        shown = false
    }
    if (shown && what != null) Text(
        what,
        modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(12.dp)).background(C.raised).border(1.dp, C.cardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = C.muted, fontSize = TS.micro, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun UndoKey(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = if (enabled) C.text else C.faint.copy(alpha = 0.45f), fontSize = TS.title) }
}
