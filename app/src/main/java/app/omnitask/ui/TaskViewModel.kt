package app.omnitask.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.omnitask.data.TaskLine
import app.omnitask.data.TaskLine.DateField
import app.omnitask.data.VaultRepository
import app.omnitask.model.DateBucket
import app.omnitask.model.Priority
import app.omnitask.model.Task
import app.omnitask.model.UrgentRule
import app.omnitask.model.bucket
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The filter set shared by the List and Eisenhower views. */
data class Filters(
    val bucket: DateBucket? = null,
    val tag: String? = null,
    val note: String? = null,
    val showDone: Boolean = false,
)

data class UiState(
    val vault: Uri? = null,
    val loading: Boolean = false,
    val tasks: List<Task> = emptyList(),
    val filters: Filters = Filters(),
    val urgentRule: UrgentRule = UrgentRule.THIS_WEEK,
    val today: LocalDate = LocalDate.now(),
    val message: String? = null,
) {
    val tags get() = tasks.flatMap { it.tags }.distinct().sorted()
    val notes get() = tasks.map { it.noteName }.distinct().sorted()

    /** Everything but the date filter, so the date tabs can show a count each. */
    val scoped
        get() = tasks.filter { t ->
            (filters.showDone || t.isOpen) &&
                (filters.tag == null || filters.tag in t.tags) &&
                (filters.note == null || filters.note == t.noteName)
        }

    val visible get() = scoped.filter { filters.bucket == null || it.bucket(today) == filters.bucket }
}

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = VaultRepository(app)
    private val prefs = app.getSharedPreferences("omnitask", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            vault = prefs.getString(KEY_VAULT, null)?.let(Uri::parse),
            urgentRule = prefs.getString(KEY_URGENT, null)
                ?.let { name -> UrgentRule.entries.firstOrNull { it.name == name } }
                ?: UrgentRule.THIS_WEEK,
        )
    )
    val state: StateFlow<UiState> = _state

    fun setVault(uri: Uri) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString(KEY_VAULT, uri.toString()).apply()
        _state.update { it.copy(vault = uri) }
        reload()
    }

    fun setUrgentRule(rule: UrgentRule) {
        prefs.edit().putString(KEY_URGENT, rule.name).apply()
        _state.update { it.copy(urgentRule = rule) }
    }

    fun setFilters(filters: Filters) = _state.update { it.copy(filters = filters) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun reload() {
        val vault = _state.value.vault ?: return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.loadTasks(vault) } }
            _state.update {
                it.copy(
                    loading = false,
                    today = LocalDate.now(),
                    tasks = result.getOrDefault(it.tasks),
                    message = result.exceptionOrNull()?.let { e -> "อ่านตู้โน้ตไม่ได้: ${e.message}" } ?: it.message,
                )
            }
        }
    }

    fun toggleDone(task: Task) {
        // Completing a repeating task must also create its next occurrence, which arrives in phase 2.
        if (task.recurrence != null && task.isOpen) {
            _state.update { it.copy(message = "งานวนซ้ำยังติ๊กในแอปนี้ไม่ได้ (ช่วง 2) ติ๊กใน TaskForge ไปก่อน") }
            return
        }
        edit(task) { TaskLine.setDone(it, task.isOpen, LocalDate.now()) }
    }

    fun setPriority(task: Task, priority: Priority) = edit(task) { TaskLine.setPriority(it, priority) }

    fun setDate(task: Task, field: DateField, value: LocalDate?) = edit(task) { TaskLine.setDate(it, field, value) }

    private fun edit(task: Task, transform: (String) -> String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.rewriteLine(task, transform) } }
            result.exceptionOrNull()?.let { e ->
                val text = if (e is VaultRepository.ConflictException) {
                    "ไฟล์ถูกแก้จากที่อื่น โหลดใหม่แล้ว ลองอีกครั้ง"
                } else {
                    "บันทึกไม่ได้: ${e.message}"
                }
                _state.update { it.copy(message = text) }
            }
            reload()
        }
    }

    private companion object {
        const val KEY_VAULT = "vault"
        const val KEY_URGENT = "urgentRule"
    }
}
