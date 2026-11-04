package app.omnitask.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.omnitask.data.TaskLine
import app.omnitask.data.VaultRepository
import app.omnitask.model.NextThing
import app.omnitask.notify.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import app.omnitask.time.*

/**
 * The one thing next (owner's pick ก, 2026-11-03, mock six-pains screen 3): what was put off today with "ไม่ใช่ตอนนี้"
 * or ticked from the lock-screen card, kept on the phone for today only, so the card and the today page show the one
 * after it. Read and written from here alone.
 */
object NextThingSkips {
    private const val PREFS = "omni-nextthing"
    private const val DAY = "day"
    private const val KEYS = "keys"

    fun of(context: Context, today: LocalDate): Set<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.getString(DAY, null) == today.toString()) p.getStringSet(KEYS, emptySet()).orEmpty() else emptySet()
    }

    fun add(context: Context, key: String, today: LocalDate) {
        val keys = of(context, today) + key
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(DAY, today.toString()).putStringSet(KEYS, keys).apply()
    }

    /** The picks for today, the ones put off left out. */
    fun ranked(context: Context, tasks: List<app.omnitask.model.Task>, today: LocalDate) = NextThing.ranked(tasks, today, of(context, today))
}

/** The card's two buttons on the one thing next: "ทำแล้ว" ticks the task in the vault, "ไม่ใช่ตอนนี้" puts it off for today. */
class LockNextReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val today = LocalDate.now()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == DONE) {
                    val raw = intent.getStringExtra(EXTRA_RAW)
                    val task = Scheduler.loadVaultTasks(context).firstOrNull { it.key == key && it.raw == raw }
                    if (task != null && task.isOpen) {
                        val wrote = runCatching { VaultRepository(context).rewriteLine(task) { TaskLine.setDone(it, true, today) } }
                        wrote.exceptionOrNull()?.let { WidgetLog.add("lockcard: could not tick the next thing: ${it.message}") }
                    } else WidgetLog.add("lockcard: the next thing changed before it was ticked")
                }
                // Ticked or put off, the card moves on to the one after it at once.
                NextThingSkips.add(context, key, today)
                LockCardNotifier.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val DONE = "app.omnitask.NEXT_DONE"
        const val SKIP = "app.omnitask.NEXT_SKIP"
        const val EXTRA_KEY = "key"
        const val EXTRA_RAW = "raw"
    }
}
