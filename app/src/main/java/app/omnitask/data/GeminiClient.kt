package app.omnitask.data

import android.content.Context
import app.omnitask.model.Gemini
import app.omnitask.model.tr
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends the assistant's question to Gemini ([Gemini] builds it and reads the answer) with the owner's own API key.
 * The key and the model stay on this phone, in preferences of their own: the "omnitask" ones are copied to
 * omni-settings.json in the vault, and a key must never land in the vault.
 */
object GeminiClient {

    private const val PREFS = "omnitask.gemini"
    private const val KEY_API = "apiKey"
    private const val KEY_MODEL = "model"
    private const val KEY_CHAT = "chat"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // A model that thinks first can take a while before the first byte.
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun key(context: Context): String = prefs(context).getString(KEY_API, null).orEmpty()

    fun model(context: Context): String = prefs(context).getString(KEY_MODEL, null).orEmpty()

    /** Whether typed questions go to Gemini (on until the owner switches back to the rules). */
    fun chat(context: Context): Boolean = prefs(context).getBoolean(KEY_CHAT, true)

    fun setChat(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_CHAT, on).apply()
    }

    fun save(context: Context, key: String, model: String) {
        prefs(context).edit().putString(KEY_API, key.trim()).putString(KEY_MODEL, model.trim()).apply()
    }

    /** Sends [body] and returns Gemini's raw response. Throws [Gemini.Failure] with words for the owner. Not on the main thread. */
    fun send(context: Context, body: String): String {
        val key = key(context).ifEmpty { throw Gemini.Failure(tr("ใส่ API key ของ Gemini ก่อน ที่ ตั้งค่า > Gemini", "Add a Gemini API key first, in Settings > Gemini")) }
        val request = Request.Builder()
            .url(Gemini.endpoint(model(context)))
            .header(Gemini.KEY_HEADER, key)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw Gemini.Failure(Gemini.errorMessage(res.code, text))
                text
            }
        } catch (e: IOException) {
            throw Gemini.Failure(tr("ต่อ Gemini ไม่ได้ ตรวจอินเทอร์เน็ตแล้วลองใหม่", "Cannot reach Gemini. Check the connection and try again"))
        }
    }
}
