package app.omnitask.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.omnitask.model.Appearance
import app.omnitask.model.tr
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** One card of a [CardDeck]: its name in the corner, and what it shows. */
class DeckCard(
    val key: String,
    val label: String,
    val labelColor: Color? = null,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * Cards stacked in one place (owner's pick, 2026-10-27): one shows at a time, the edges of the next two peek out under
 * it, and "‹ n/all ›" in its top right corner steps through them. Swiping the card sideways does the same. Every card
 * has the same height, so the page does not jump when the card changes. A deck of one card has no arrows or edges;
 * an empty deck draws nothing.
 */
@Composable
fun CardDeck(cards: List<DeckCard>, height: Dp, modifier: Modifier = Modifier) {
    if (cards.isEmpty()) return
    val n = cards.size
    // Kept by the card's key, so a card dropping out (nothing left in it) does not move the deck to another one.
    var currentKey by rememberSaveable { mutableStateOf(cards.first().key) }
    val index = cards.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
    fun go(step: Int) { currentKey = cards[((index + step) % n + n) % n].key }
    val card = cards[index]
    val tall = height * Appearance.factor
    val edge = 14.dp
    val behind = minOf(n - 1, 2)
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val away = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val far = with(density) { 72.dp.toPx() }

    Box(modifier.fillMaxWidth().height(tall + edge * behind)) {
        // The cards behind, deepest first: only their lower edge shows. Tapping one brings it to the front.
        for (depth in behind downTo 1) {
            val target = (index + depth) % n
            val inset = if (depth == 1) 8.dp else 18.dp
            Box(
                Modifier.padding(start = inset, end = inset, top = tall - edge).fillMaxWidth().height(edge * (depth + 1))
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(if (depth == 1) C.raised else C.sunken)
                    .border(1.dp, C.accentLine, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .clickable(onClickLabel = cards[target].label) { currentKey = cards[target].key },
            )
        }
        Column(
            Modifier.fillMaxWidth().height(tall)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .graphicsLayer { rotationZ = offset.value / far * 1.5f }
                .clip(RoundedCornerShape(14.dp)).background(C.accentDeep).border(1.dp, C.accentLine, RoundedCornerShape(14.dp))
                .draggable(
                    rememberDraggableState { d -> scope.launch { offset.snapTo(offset.value + d) } },
                    Orientation.Horizontal,
                    enabled = n > 1,
                    onDragStopped = { velocity ->
                        val dx = offset.value
                        if (abs(dx) > far || abs(velocity) > 1200f) {
                            val left = if (abs(dx) > far) dx < 0 else velocity < 0
                            offset.animateTo(if (left) -away else away, tween(160))
                            go(if (left) 1 else -1)
                            offset.snapTo(0f)
                        } else {
                            offset.animateTo(0f, tween(160))
                        }
                    },
                )
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.label, Modifier.weight(1f), color = card.labelColor ?: C.accentText, fontSize = TS.caption,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (n > 1) Row(
                    Modifier.clip(RoundedCornerShape(9.dp)).background(C.card).border(1.dp, C.accentLine, RoundedCornerShape(9.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = 34.dp, height = 30.dp).clickable(onClickLabel = tr("ใบก่อน", "Previous")) { go(-1) }, contentAlignment = Alignment.Center) {
                        Icon(Ic.back, tr("ใบก่อน", "Previous"), tint = C.text2, modifier = Modifier.size(14.dp))
                    }
                    Text("${index + 1}/$n", Modifier.widthIn(min = 28.dp), color = C.muted, fontSize = TS.caption, maxLines = 1)
                    Box(Modifier.size(width = 34.dp, height = 30.dp).clickable(onClickLabel = tr("ใบต่อไป", "Next")) { go(1) }, contentAlignment = Alignment.Center) {
                        Icon(Ic.next, tr("ใบต่อไป", "Next"), tint = C.text2, modifier = Modifier.size(14.dp))
                    }
                }
            }
            card.content(this)
        }
    }
}

/** The buttons along the bottom of a deck card: a filled one (wider) and quiet ones. */
@Composable
fun ColumnScope.DeckButtons(content: @Composable RowScope.() -> Unit) {
    Box(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun RowScope.DeckButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.then(if (filled) Modifier.weight(1f) else Modifier).heightIn(min = 38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (filled) C.accent else C.raised).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (filled) C.onAccent else C.text2, fontSize = TS.body,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of small counted labels on a deck card ("เลขาถาม 3"). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DeckCounts(counts: List<Pair<String, Int>>) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        counts.forEach { (label, count) ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(C.card).border(1.dp, C.accentLine, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = C.text2, fontSize = TS.caption)
                Text("$count", Modifier.padding(start = 5.dp), color = C.text, fontSize = TS.caption, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
