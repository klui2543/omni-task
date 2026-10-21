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

    /** Whether [model] turned the thinking setting down before. */
    fun noThinking(context: Context, model: String): Boolean = prefs(context).getBoolean("gemini.noThinking.$model", false)

    fun setNoThinking(context: Context, model: String) = prefs(context).edit().putBoolean("gemini.noThinking.$model", true).apply()

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
     * [AiAssist] to read. When the model is not found, picks the newest Flash-Lite (or Flash) from Google's list
     * once and retries.
     */
    fun generate(body: String, key: String = AiKey.get(context) ?: throw AiAssist.Failure(NO_KEY)): String {
        // A model that does not take the thinking setting says so once; after that it is left out, so no call is made twice.
        val model = AiKey.model(context)
        var first = post(model, if (AiKey.noThinking(context, model)) AiAssist.withoutThinking(body) else body, key)
        if (first.first == 400 && first.second.contains("thinking", ignoreCase = true)) {
            AiKey.setNoThinking(context, model)
            first = post(model, AiAssist.withoutThinking(body), key)
        }
        if (first.first != 404) return check(first)
        val newest = list(key)?.let(AiAssist::fallbackModel)?.takeIf { it != model } ?: return check(first)
        AiKey.setModel(context, newest)
        return check(post(newest, body, key))
    }

    /** A tiny call that proves the key works; returns the model it used. */
    fun test(key: String): String {
        val started = System.currentTimeMillis()
        generate("""{"contents":[{"role":"user","parts":[{"text":"ตอบคำว่า ok"}]}],"generationConfig":{"thinkingConfig":{"thinkingLevel":"minimal"}}}""", key)
        val seconds = (System.currentTimeMillis() - started) / 100 / 10.0
        return AiKey.model(context) + tr(", ตอบใน $seconds วินาที", ", answered in $seconds s")
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

    /**
     * Vectors for [texts] (documents to keep, or a [query] to look them up with), from Gemini's embedding model;
     * the next model is tried when one is not offered.
     */
    fun embed(texts: List<String>, query: Boolean, key: String = AiKey.get(context) ?: throw AiAssist.Failure(NO_KEY)): List<FloatArray> {
        var last: Pair<Int, String>? = null
        for (model in EMBED_MODELS) {
            val body = app.omnitask.model.AiMemory.embedRequest(model, texts, query)
            val request = Request.Builder().url("$base/models/$model:batchEmbedContents").header("x-goog-api-key", key)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            val r = try {
                client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                throw AiAssist.Failure(NO_NET)
            }
            if (r.first == 404) { last = r; continue }
            val vectors = app.omnitask.model.AiMemory.parseEmbeddings(check(r))
            if (vectors.size != texts.size) throw AiAssist.Failure(tr("แปลงบันทึกเป็นตัวเลขไม่ครบ", "Some notes were not embedded"))
            return vectors
        }
        return app.omnitask.model.AiMemory.parseEmbeddings(check(last ?: (404 to "")))
    }

    /** Every text model this key can use, grouped fast to smart. */
    fun models(key: String = AiKey.get(context) ?: throw AiAssist.Failure(NO_KEY)): List<AiAssist.Model> {
        val text = list(key) ?: throw AiAssist.Failure(tr("อ่านรายชื่อรุ่นจาก Google ไม่ได้", "Could not read the model list from Google"))
        return AiAssist.models(text)
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
        /** Embedding models, newest first. */
        val EMBED_MODELS = listOf("gemini-embedding-001", "text-embedding-004")

        val NO_KEY get() = tr("ยังไม่ได้ใส่ Gemini API key ใส่ได้ที่ ตั้งค่า > ผู้ช่วย AI", "No Gemini API key yet. Add one in Settings > AI assistant")
        val BAD_KEY get() = tr("API key ใช้ไม่ได้ ตรวจที่ ตั้งค่า > ผู้ช่วย AI", "The API key does not work. Check it in Settings > AI assistant")
        val QUOTA get() = tr("ใช้ Gemini ครบโควตาแล้ว รอสักครู่ หรือเปิดแบบจ่ายเงินใน Google AI Studio", "Gemini quota used up. Wait a while, or turn on billing in Google AI Studio")
        val SLOW get() = tr("Gemini ตอบช้าเกินไป ลองกดอ่านใหม่อีกครั้ง", "Gemini took too long. Try again")
        val NO_NET get() = tr("ต่อ Gemini ไม่ได้ ตรวจอินเทอร์เน็ต", "Cannot reach Gemini. Check the internet connection")
    }
}
