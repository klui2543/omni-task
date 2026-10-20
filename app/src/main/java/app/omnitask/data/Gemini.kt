package app.omnitask.data

import android.content.Context
import app.omnitask.model.AiAssist
import app.omnitask.model.tr
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The owner's Gemini API key and settings. They live in a preferences file of their own, so the settings sync never
 * carries the key into the vault, and the app does not allow backups, so it stays on this phone.
 */
object AiKey {
    private const val PREFS = "omni-ai"
    private const val KEY = "gemini.key"
    private const val MODEL = "gemini.model"
    private const val AUTO = "gemini.autoBrief"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): String? = prefs(context).getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun set(context: Context, key: String?) {
        prefs(context).edit().apply { if (key.isNullOrBlank()) remove(KEY).remove(MODEL) else putString(KEY, key.trim()) }.apply()
    }

    /** The model in use: the default alias, or the one picked from Google's list when the alias is not offered. */
    fun model(context: Context): String = prefs(context).getString(MODEL, null) ?: AiAssist.DEFAULT_MODEL

    fun setModel(context: Context, model: String) = prefs(context).edit().putString(MODEL, model).apply()

    /** Whether the brief is read again when the assistant page opens (at most once an hour). */
    fun autoBrief(context: Context): Boolean = prefs(context).getBoolean(AUTO, true)

    fun setAutoBrief(context: Context, on: Boolean) = prefs(context).edit().putBoolean(AUTO, on).apply()

    /** The last four characters, to show which key is in without showing the key. */
    fun tail(key: String) = key.takeLast(4)
}

/** Calls Gemini's generateContent with the owner's key. Blocking: call it off the main thread. */
class Gemini(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    private val base = "https://generativelanguage.googleapis.com/v1beta"

    /**
     * Sends [body] (from [AiAssist.briefRequest] or [AiAssist.chatRequest]) and returns the raw response for
     * [AiAssist] to read. When the model is not found, picks the newest Flash from Google's list once and retries.
     */
    fun generate(body: String, key: String = AiKey.get(context) ?: throw AiAssist.Failure(NO_KEY)): String {
        var first = post(AiKey.model(context), body, key)
        // A model that does not take the thinking setting says so: ask again without it.
        if (first.first == 400 && first.second.contains("thinking", ignoreCase = true)) first = post(AiKey.model(context), AiAssist.withoutThinking(body), key)
        if (first.first != 404) return check(first)
        val newest = list(key)?.let(AiAssist::newestFlash) ?: return check(first)
        AiKey.setModel(context, newest)
        return check(post(newest, body, key))
    }

    /** A tiny call that proves the key works; returns the model it used. */
    fun test(key: String): String {
        generate("""{"contents":[{"role":"user","parts":[{"text":"ตอบคำว่า ok"}]}]}""", key)
        return AiKey.model(context)
    }

    private fun post(model: String, body: String, key: String): Pair<Int, String> {
        val request = Request.Builder()
            .url("$base/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: java.io.InterruptedIOException) {
            // A read or call timeout: the connection worked, the answer was slow. A connect timeout is the network.
            if (e.message?.contains("connect", ignoreCase = true) == true) throw AiAssist.Failure(NO_NET)
            throw AiAssist.Failure(SLOW)
        } catch (e: IOException) {
            throw AiAssist.Failure(NO_NET + (e.message?.let { " ($it)" } ?: ""))
        }
    }

    private fun list(key: String): String? = runCatching {
        val request = Request.Builder().url("$base/models?pageSize=200").header("x-goog-api-key", key).get().build()
        client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    /** The body when it worked, else a [AiAssist.Failure] that says what to do. */
    private fun check(r: Pair<Int, String>): String {
        val (code, text) = r
        if (code in 200..299) return text
        val said = runCatching { AiAssist.answerText(text) }.exceptionOrNull()?.message
        throw AiAssist.Failure(
            when (code) {
                400 -> if (said?.contains("API key", ignoreCase = true) == true) BAD_KEY else said ?: tr("Gemini ไม่รับคำขอ ($code)", "Gemini refused the request ($code)")
                401, 403 -> BAD_KEY
                429 -> QUOTA
                in 500..599 -> tr("Gemini ขัดข้องชั่วคราว ลองใหม่อีกครั้ง", "Gemini is having trouble. Try again")
                else -> said ?: tr("Gemini ตอบว่าผิดพลาด ($code)", "Gemini returned an error ($code)")
            },
        )
    }

    companion object {
        val NO_KEY get() = tr("ยังไม่ได้ใส่ Gemini API key ใส่ได้ที่ ตั้งค่า > ผู้ช่วย AI", "No Gemini API key yet. Add one in Settings > AI assistant")
        val BAD_KEY get() = tr("API key ใช้ไม่ได้ ตรวจที่ ตั้งค่า > ผู้ช่วย AI", "The API key does not work. Check it in Settings > AI assistant")
        val QUOTA get() = tr("ใช้ Gemini ครบโควตาแล้ว รอสักครู่ หรือเปิดแบบจ่ายเงินใน Google AI Studio", "Gemini quota used up. Wait a while, or turn on billing in Google AI Studio")
        val SLOW get() = tr("Gemini ตอบช้าเกินไป ลองกดอ่านใหม่อีกครั้ง", "Gemini took too long. Try again")
        val NO_NET get() = tr("ต่อ Gemini ไม่ได้ ตรวจอินเทอร์เน็ต", "Cannot reach Gemini. Check the internet connection")
    }
}
