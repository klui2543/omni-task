package app.omnitask.data

import android.content.Context
import android.util.AtomicFile
import app.omnitask.drive.DriveRest
import app.omnitask.drive.DriveUnavailable
import app.omnitask.drive.HttpResponse
import app.omnitask.drive.HttpTransport
import app.omnitask.drive.OmniDrive
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The phone's link to the Omni folder on Google Drive (docs/drive-direct-plan.md). Once the owner connects it, every
 * read and write of a file in the Omni folder goes to Drive through [OmniDrive], as the web does, so the sync app
 * never has two copies of those files to clash over. Other notes stay on the folder picked with the system picker.
 *
 * One [OmniDrive] serves the whole process (screens, widgets, reminders); [use] takes a lock around each call.
 */
object DriveLink {

    private const val PREFS = "omnitask"
    const val KEY_ON = "drive.on"
    private const val CACHE = "omni-drive.json"

    /** An access token lasts an hour; one is reused for less than that. */
    private const val TOKEN_MS = 45 * 60_000L

    private val lock = Any()
    private var store: OmniDrive? = null
    private var rest: DriveRest? = null

    @Volatile private var token: String? = null
    @Volatile private var tokenAt = 0L

    /** Whether the owner connected the Omni folder on Drive (this phone only). */
    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    /** Runs [block] on the one [OmniDrive], holding the lock. Must not run on the main thread. */
    fun <T> use(context: Context, block: (OmniDrive) -> T): T = synchronized(lock) { block(store(context.applicationContext)) }

    /** Puts a store over a stand-in Drive, for tests; null goes back to the real one. */
    @androidx.annotation.VisibleForTesting
    fun useForTest(store: OmniDrive?) = synchronized(lock) { this.store = store }

    /** Drive itself, for finding the Omni folder while connecting. */
    fun drive(context: Context): DriveRest = synchronized(lock) { rest(context.applicationContext) }

    /** Starts working on the Omni folder [dirId]. */
    fun connect(context: Context, dirId: String) {
        use(context) { it.connect(dirId) }
        prefs(context).edit().putBoolean(KEY_ON, true).apply()
    }

    /** Back to the picked folder for everything; the cache (and any edit not sent yet) is dropped. */
    fun disconnect(context: Context) {
        use(context) { it.disconnect() }
        prefs(context).edit().putBoolean(KEY_ON, false).apply()
    }

    /** What the app asks Google for: the whole Drive, which finding the vault's folders by name needs (the web asks the same). */
    fun request(): AuthorizationRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveRest.SCOPE))).build()

    /**
     * Asks Google for access. The result carries a token, or a screen to show first ([AuthorizationResult.hasResolution]);
     * the screen's answer comes back through [tokenFrom]. Blocks: call it off the main thread.
     */
    fun authorize(context: Context): AuthorizationResult =
        Tasks.await(Identity.getAuthorizationClient(context).authorize(request()), 30, TimeUnit.SECONDS)
            .also { r -> r.accessToken?.let(::remember) }

    /** The token from the answer to Google's sign-in screen. */
    fun tokenFrom(context: Context, data: android.content.Intent?): String? =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken?.also(::remember)

    private fun remember(t: String) {
        token = t
        tokenAt = System.currentTimeMillis()
    }

    /** A token for Drive without showing anything; [DriveUnavailable] with `signIn` when the owner must sign in again. */
    private fun accessToken(context: Context): String {
        token?.takeIf { System.currentTimeMillis() - tokenAt < TOKEN_MS }?.let { return it }
        val result = try {
            authorize(context)
        } catch (e: Exception) {
            // Google answering "sign in first" needs the owner; anything else (no network, a timeout) passes by itself.
            val api = (e as? java.util.concurrent.ExecutionException)?.cause as? ApiException ?: e as? ApiException
            val signIn = api?.statusCode == CommonStatusCodes.SIGN_IN_REQUIRED || api?.statusCode == CommonStatusCodes.RESOLUTION_REQUIRED
            throw DriveUnavailable(signIn = signIn, message = e.message, cause = e)
        }
        if (result.hasResolution()) throw DriveUnavailable(signIn = true, message = "sign-in needed")
        return result.accessToken ?: throw DriveUnavailable(signIn = true, message = "no token")
    }

    /** Drive turned a token down: forget it here and in Google's cache, so the next call gets a new one. */
    private fun refused(context: Context, t: String) {
        token = null
        runCatching { GoogleAuthUtil.clearToken(context, t) }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val http = HttpTransport { r ->
        val type = r.headers["Content-Type"]?.toMediaTypeOrNull()
        val body = r.body?.toRequestBody(type) ?: if (r.method == "GET") null else ByteArray(0).toRequestBody(type)
        val request = Request.Builder().url(r.url)
            .apply { r.headers.forEach { (k, v) -> if (!k.equals("Content-Type", ignoreCase = true)) header(k, v) } }
            .method(r.method, body)
            .build()
        client.newCall(request).execute().use { HttpResponse(it.code, it.body?.string().orEmpty()) }
    }

    private fun rest(context: Context): DriveRest =
        rest ?: DriveRest(http, { accessToken(context) }, { refused(context, it) }).also { rest = it }

    private fun store(context: Context): OmniDrive =
        store ?: OmniDrive(rest(context), readCache(context)) { writeCache(context, it) }.also { store = it }

    private fun cacheFile(context: Context) = AtomicFile(File(context.filesDir, CACHE))

    private fun readCache(context: Context): String? =
        runCatching { cacheFile(context).readFully().toString(Charsets.UTF_8) }.getOrNull()

    private fun writeCache(context: Context, text: String) {
        val file = cacheFile(context)
        val out = file.startWrite()
        try {
            out.write(text.toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
