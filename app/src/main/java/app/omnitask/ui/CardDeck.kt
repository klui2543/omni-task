package app.omnitask.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.omnitask.model.Appearance

/** One card of a [CardDeck]: its name in the corner, and what it shows. */
class DeckCard(
    val key: String,
    val label: String,
    val labelColor: Color? = null,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * Several cards in one plain card (owner's pick, 2026-10-28, "like the first waiting card"): the card stays still,
 * its inside swipes sideways to the next one, the head names the one shown with "n/all" on the right and dots sit
 * under it. Every page has the same height, so the page does not jump. One card has no count or dots; an empty
 * deck draws nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardDeck(cards: List<DeckCard>, height: Dp, modifier: Modifier = Modifier) {
    if (cards.isEmpty()) return
    val n = cards.size
    // Kept by the card's key, so a card dropping out (nothing left in it) does not move the deck to another one.
    var currentKey by rememberSaveable { mutableStateOf(cards.first().key) }
    val pager = rememberPagerState(cards.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)) { n }
    val latest by rememberUpdatedState(cards)
    LaunchedEffect(cards.map { it.key }) {
        val i = cards.indexOfFirst { it.key == currentKey }
        if (i >= 0 && i != pager.currentPage) pager.scrollToPage(i)
    }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { p -> latest.getOrNull(p)?.let { currentKey = it.key } } }
    val shown = pager.currentPage.coerceIn(0, n - 1)
    val card = cards[shown]
    Card(modifier) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.label, Modifier.weight(1f), color = card.labelColor ?: C.accentText, fontSize = TS.body,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (n > 1) Text("${shown + 1}/$n", color = C.muted, fontSize = TS.caption)
            }
            HorizontalPager(
                pager, Modifier.padding(top = 8.dp).fillMaxWidth().height(height * Appearance.factor),
                pageSpacing = 14.dp, verticalAlignment = Alignment.Top,
            ) { i ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) { cards.getOrNull(i)?.content?.invoke(this) }
            }
            if (n > 1) Row(
                Modifier.padding(top = 8.dp).fillMaxWidth().height(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                repeat(n) { i -> Box(Modifier.size(5.dp).clip(CircleShape).background(if (i == shown) C.text2 else C.control)) }
            }
        }
    }
}

/** The actions along the bottom of a deck card, small words on the right; put the main one last. */
@Composable
fun ColumnScope.DeckButtons(content: @Composable RowScope.() -> Unit) {
    Box(Modifier.weight(1f))
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/** One action of a deck card: words in the accent, the main one ([filled]) in bold, as "ข้ามวันนี้" was on the first cards. */
@Composable
fun RowScope.DeckButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        text, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        color = C.accentText, fontSize = TS.body, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
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
