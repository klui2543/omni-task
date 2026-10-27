package app.omnitask.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.omnitask.model.TaskKind
import app.omnitask.model.Task
import app.omnitask.model.tr

/**
 * What the secretary waits on Klui to decide (owner's picks, 2026-10-27): its questions, the suggestions, the warnings,
 * people waiting and work for the future. They live in the deck on the secretary's page; Focus only counts them, so
 * both read them from here and the numbers match.
 */
object Decisions {
    /** Warnings hidden with "ซ่อน" until the app is closed, as the old Focus notices were. */
    var hiddenWarnings by mutableStateOf(emptySet<String>())
        private set

    fun hideWarnings(lines: Collection<String>) { hiddenWarnings = hiddenWarnings + lines }

    fun warnings(state: UiState): List<String> = state.brief.warnings.filter { it !in hiddenWarnings }

    /** People waiting, unless the "waiting" kind is hidden in Settings (as its Focus card was). */
    fun waiting(state: UiState): List<Task> = if (TaskKind.WAITING in state.hiddenKinds) emptyList() else state.brief.waiting

    /** Today's work for the future, unless the "future" kind is hidden in Settings (as its Focus card was). */
    fun future(state: UiState): List<Task> = if (TaskKind.FUTURE in state.hiddenKinds) emptyList() else state.brief.future

    /** Each kind with how many are waiting, leaving out the empty ones. [asks] is the number of open questions. */
    fun counts(state: UiState, asks: Int): List<Pair<String, Int>> = listOf(
        tr("เลขาถาม", "Questions") to asks,
        tr("ข้อเสนอ", "Suggestions") to state.brief.suggestions.size,
        tr("เตือน", "Warnings") to warnings(state).size,
        tr("คนรอ", "Waiting") to waiting(state).size,
        tr("อนาคต", "Future") to future(state).size,
    ).filter { it.second > 0 }
}
