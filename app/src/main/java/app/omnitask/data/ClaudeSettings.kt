package app.omnitask.data

import android.content.Context
import app.omnitask.model.SecretaryAi
import kotlinx.datetime.LocalDate

/**
 * The secretary chat's AI settings, kept on this phone only (app-private preferences, never in the vault or the
 * settings file that syncs): the Anthropic key and when it runs out, the credit and what this app spent of it, the
 * baht rate, the effort last picked in the chat box (the model is not kept: the chat starts on the free rules), and
 * what the instructions really cost per model.
 */
object ClaudeSettings {
    private const val PREFS = "omni-ai"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun key(context: Context): String? = prefs(context).getString("claude.key", null)?.takeIf { it.isNotBlank() }

    /**
     * "เลขาเรียนรู้เอง" (plan docs/PLAN_assistant_ml_2026-10-26.md): the secretary chat also reads sentences by the
     * examples taught in Assistant ML.md. Off until the owner turns it on; off, the chat works as it did. The one reader.
     */
    fun learnOn(context: Context): Boolean = prefs(context).getBoolean("secretary.learn", false)

    fun setLearnOn(context: Context, on: Boolean) = prefs(context).edit().putBoolean("secretary.learn", on).apply()

    fun setKey(context: Context, key: String?) {
        prefs(context).edit().apply { if (key.isNullOrBlank()) remove("claude.key") else putString("claude.key", key.trim()) }.apply()
    }

    fun keyExpires(context: Context): LocalDate? = date(context, "claude.keyExpires")

    fun setKeyExpires(context: Context, day: LocalDate?) = setDate(context, "claude.keyExpires", day)

    fun credit(context: Context): SecretaryAi.Credit = SecretaryAi.Credit(
        quotaUsd = double(context, "claude.quotaUsdBits", 100.0),
        spentUsd = double(context, "claude.spentUsdBits", 0.0),
        creditExpires = date(context, "claude.creditExpires"),
        rate = double(context, "claude.rateBits", 33.0),
    )

    fun setQuota(context: Context, usd: Double) = setDouble(context, "claude.quotaUsdBits", usd)

    fun setRate(context: Context, bahtPerUsd: Double) = setDouble(context, "claude.rateBits", bahtPerUsd)

    /**
     * What the fixed part of a question on [mode] (the instructions) really cost in input tokens the last time, so the
     * next confirmation can show a figure close to the real one without sending anything first. Null before the first
     * answer.
     */
    fun baseTokens(context: Context, mode: SecretaryAi.Mode): Long? =
        prefs(context).getLong("claude.base.${mode.name}", -1L).takeIf { it > 0L }

    fun setBaseTokens(context: Context, mode: SecretaryAi.Mode, tokens: Long) {
        if (tokens > 0L) prefs(context).edit().putLong("claude.base.${mode.name}", tokens).apply()
    }

    fun setCreditExpires(context: Context, day: LocalDate?) = setDate(context, "claude.creditExpires", day)

    /** Adds what one call cost to what this app has spent (kept to the full precision of a Double). */
    @Synchronized
    fun addSpent(context: Context, usd: Double) = setDouble(context, "claude.spentUsdBits", credit(context).spentUsd + usd)

    fun effort(context: Context): SecretaryAi.Effort = SecretaryAi.Effort.of(prefs(context).getString("claude.effort", null))

    fun setEffort(context: Context, effort: SecretaryAi.Effort) = prefs(context).edit().putString("claude.effort", effort.name).apply()

    // Money is kept as the bits of a Double, so 33.3 reads back as 33.3 and not 33.29999.
    private fun double(context: Context, name: String, default: Double): Double =
        java.lang.Double.longBitsToDouble(prefs(context).getLong(name, java.lang.Double.doubleToLongBits(default)))

    private fun setDouble(context: Context, name: String, value: Double) =
        prefs(context).edit().putLong(name, java.lang.Double.doubleToLongBits(value)).apply()

    private fun date(context: Context, name: String): LocalDate? =
        prefs(context).getString(name, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun setDate(context: Context, name: String, day: LocalDate?) {
        prefs(context).edit().apply { if (day == null) remove(name) else putString(name, day.toString()) }.apply()
    }
}
