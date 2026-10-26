package app.omnitask.data

import android.content.Context
import app.omnitask.model.SecretaryAi
import kotlinx.datetime.LocalDate

/**
 * The secretary chat's AI settings, kept on this phone only (app-private preferences, never in the vault or the
 * settings file that syncs): the Anthropic key and when it runs out, the credit and what this app spent of it, the
 * baht rate, and the model and effort last picked in the chat box.
 */
object ClaudeSettings {
    private const val PREFS = "omni-ai"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun key(context: Context): String? = prefs(context).getString("claude.key", null)?.takeIf { it.isNotBlank() }

    fun setKey(context: Context, key: String?) {
        prefs(context).edit().apply { if (key.isNullOrBlank()) remove("claude.key") else putString("claude.key", key.trim()) }.apply()
    }

    fun keyExpires(context: Context): LocalDate? = date(context, "claude.keyExpires")

    fun setKeyExpires(context: Context, day: LocalDate?) = setDate(context, "claude.keyExpires", day)

    fun credit(context: Context): SecretaryAi.Credit {
        val p = prefs(context)
        return SecretaryAi.Credit(
            quotaUsd = p.getFloat("claude.quotaUsd", 100f).toDouble(),
            spentUsd = java.lang.Double.longBitsToDouble(p.getLong("claude.spentUsdBits", java.lang.Double.doubleToLongBits(0.0))),
            creditExpires = date(context, "claude.creditExpires"),
            rate = p.getFloat("claude.rate", 33f).toDouble(),
        )
    }

    fun setQuota(context: Context, usd: Double) = prefs(context).edit().putFloat("claude.quotaUsd", usd.toFloat()).apply()

    fun setRate(context: Context, bahtPerUsd: Double) = prefs(context).edit().putFloat("claude.rate", bahtPerUsd.toFloat()).apply()

    fun setCreditExpires(context: Context, day: LocalDate?) = setDate(context, "claude.creditExpires", day)

    /** Adds what one call cost to what this app has spent (kept to the full precision of a Double). */
    @Synchronized
    fun addSpent(context: Context, usd: Double) {
        val now = credit(context).spentUsd + usd
        prefs(context).edit().putLong("claude.spentUsdBits", java.lang.Double.doubleToLongBits(now)).apply()
    }

    /** Starts counting again (a new credit): what was spent goes back to zero. */
    fun resetSpent(context: Context) = prefs(context).edit().remove("claude.spentUsdBits").apply()

    fun mode(context: Context): SecretaryAi.Mode = SecretaryAi.Mode.of(prefs(context).getString("claude.mode", null))

    fun setMode(context: Context, mode: SecretaryAi.Mode) = prefs(context).edit().putString("claude.mode", mode.name).apply()

    fun effort(context: Context): SecretaryAi.Effort = SecretaryAi.Effort.of(prefs(context).getString("claude.effort", null))

    fun setEffort(context: Context, effort: SecretaryAi.Effort) = prefs(context).edit().putString("claude.effort", effort.name).apply()

    private fun date(context: Context, name: String): LocalDate? =
        prefs(context).getString(name, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun setDate(context: Context, name: String, day: LocalDate?) {
        prefs(context).edit().apply { if (day == null) remove(name) else putString(name, day.toString()) }.apply()
    }
}
