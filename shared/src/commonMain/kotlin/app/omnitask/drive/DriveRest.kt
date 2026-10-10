package app.omnitask.drive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One HTTP request; [body] is sent as UTF-8. */
class HttpRequest(val method: String, val url: String, val headers: Map<String, String> = emptyMap(), val body: String? = null)

class HttpResponse(val status: Int, val body: String)

/** Sends a request and waits for the answer; throws when the network cannot be reached. The app gives the real one. */
fun interface HttpTransport {
    fun send(request: HttpRequest): HttpResponse
}

/**
 * Drive could not be reached for now: no network, a server error, too many requests, or no sign-in at hand ([signIn]).
 * Work that meets it is kept and tried again later; it never means the file is wrong.
 */
class DriveUnavailable(val signIn: Boolean = false, message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** Drive answered with an error that trying again will not fix (a missing file, no permission). */
class DriveHttpError(val status: Int, message: String) : Exception(message)

/** A file or folder as Drive lists it. */
data class DriveEntry(
    val id: String,
    val name: String,
    val mimeType: String = "",
    val parents: List<String> = emptyList(),
    val version: String = "",
) {
    val isFolder get() = mimeType == DriveRest.FOLDER
}

class Versioned(val text: String, val version: String)

/** The little of Google Drive the app needs, so the logic on top can be tested against a stand-in. */
interface DriveFiles {
    /** Folders with this exact name anywhere in the owner's Drive, not in the trash. */
    fun findFolders(name: String): List<DriveEntry>

    /** Everything directly inside any of [parentIds], not in the trash, with versions. */
    fun childrenOfMany(parentIds: List<String>): List<DriveEntry>

    fun get(id: String): DriveEntry

    fun read(id: String): Versioned

    fun version(id: String): String

    /** Replaces a file's text; returns its new version. */
    fun write(id: String, text: String, mimeType: String): String

    fun create(parentId: String, name: String, mimeType: String, text: String): DriveEntry

    fun createFolder(parentId: String, name: String): String

    /** Moves a file to Drive's trash, where it can still be restored. */
    fun trash(id: String)
}

/**
 * Whether two names are the same to a person. Sync apps store an emoji like 📁 with or without an invisible
 * "show as emoji" mark, so those marks are ignored (as the web's `sameName` does).
 */
fun sameName(a: String, b: String): Boolean {
    fun norm(s: String) = s.filterNot { it == '︎' || it == '️' || it in '​'..'‍' }.trim()
    return norm(a) == norm(b)
}

/** Percent-encodes [s] as UTF-8 for a URL query value. */
fun urlEncode(s: String): String {
    val hex = "0123456789ABCDEF"
    val out = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
            out.append(ch)
        } else {
            out.append('%').append(hex[c shr 4]).append(hex[c and 15])
        }
    }
    return out.toString()
}

/**
 * Drive's REST API over [http], like the web's `drive.ts`. [token] gives an access token or throws [DriveUnavailable]
 * (with `signIn`) when the owner must sign in; after a 401, [refused] is told the token so a fresh one is fetched once.
 */
class DriveRest(
    private val http: HttpTransport,
    private val token: () -> String,
    private val refused: (String) -> Unit = {},
) : DriveFiles {

    private val json = Json { ignoreUnknownKeys = true }

    private fun call(method: String, url: String, headers: Map<String, String> = emptyMap(), body: String? = null): String {
        repeat(2) { attempt ->
            val t = token()
            val res = try {
                http.send(HttpRequest(method, url, headers + ("Authorization" to "Bearer $t"), body))
            } catch (e: DriveUnavailable) {
                throw e
            } catch (e: Exception) {
                throw DriveUnavailable(message = e.message, cause = e)
            }
            when {
                res.status == 401 && attempt == 0 -> refused(t)
                res.status == 401 -> throw DriveUnavailable(signIn = true, message = "Drive 401")
                res.status == 429 || res.status >= 500 -> throw DriveUnavailable(message = "Drive ${res.status}")
                res.status !in 200..299 -> throw DriveHttpError(res.status, "Drive ${res.status}: ${res.body.take(200)}")
                else -> return res.body
            }
        }
        throw DriveUnavailable(signIn = true, message = "Drive 401")
    }

    /** Drive's answer as JSON; an answer that is not (a proxy page, a cut connection) counts as Drive not reached. */
    private fun obj(text: String): JsonObject = try {
        json.parseToJsonElement(text).jsonObject
    } catch (e: Exception) {
        throw DriveUnavailable(message = "Unreadable answer from Drive", cause = e)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun entry(e: JsonElement): DriveEntry {
        val o = e.jsonObject
        return DriveEntry(
            id = o.str("id").orEmpty(),
            name = o.str("name").orEmpty(),
            mimeType = o.str("mimeType").orEmpty(),
            parents = (o["parents"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty(),
            version = o.str("version").orEmpty(),
        )
    }

    private fun list(q: String): List<DriveEntry> {
        val out = ArrayList<DriveEntry>()
        var page = ""
        do {
            val params = "q=${urlEncode(q)}&fields=${urlEncode("nextPageToken,files(id,name,mimeType,parents,version)")}" +
                "&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true" +
                (if (page.isNotEmpty()) "&pageToken=${urlEncode(page)}" else "")
            val res = obj(call("GET", "$API/files?$params"))
            (res["files"] as? JsonArray)?.forEach { out += entry(it) }
            page = res.str("nextPageToken").orEmpty()
        } while (page.isNotEmpty())
        return out
    }

    override fun findFolders(name: String): List<DriveEntry> =
        list("name = ${quote(name)} and mimeType = '$FOLDER' and trashed = false")

    override fun childrenOfMany(parentIds: List<String>): List<DriveEntry> =
        if (parentIds.isEmpty()) emptyList()
        else parentIds.chunked(40).flatMap { ids -> list("(${ids.joinToString(" or ") { "${quote(it)} in parents" }}) and trashed = false") }

    override fun get(id: String): DriveEntry =
        entry(obj(call("GET", "$API/files/${urlEncode(id)}?fields=id,name,mimeType,parents,version&supportsAllDrives=true")))

    override fun version(id: String): String =
        obj(call("GET", "$API/files/${urlEncode(id)}?fields=version&supportsAllDrives=true")).str("version").orEmpty()

    override fun read(id: String): Versioned {
        val version = version(id)
        val text = call("GET", "$API/files/${urlEncode(id)}?alt=media&supportsAllDrives=true")
        return Versioned(text, version)
    }

    override fun write(id: String, text: String, mimeType: String): String =
        obj(
            call(
                "PATCH", "$UPLOAD/files/${urlEncode(id)}?uploadType=media&supportsAllDrives=true&fields=version",
                mapOf("Content-Type" to "$mimeType; charset=UTF-8"), text,
            ),
        ).str("version").orEmpty()

    override fun create(parentId: String, name: String, mimeType: String, text: String): DriveEntry {
        val boundary = "omni" + (100000..999999).random()
        val meta = buildJsonObject {
            put("name", name)
            put("parents", buildJsonArray { add(JsonPrimitive(parentId)) })
            put("mimeType", mimeType)
        }
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: $mimeType; charset=UTF-8\r\n\r\n$text\r\n--$boundary--"
        val res = call(
            "POST", "$UPLOAD/files?uploadType=multipart&supportsAllDrives=true&fields=id,name,mimeType,parents,version",
            mapOf("Content-Type" to "multipart/related; boundary=$boundary"), body,
        )
        return entry(obj(res))
    }

    override fun createFolder(parentId: String, name: String): String {
        val meta = buildJsonObject {
            put("name", name)
            put("parents", buildJsonArray { add(JsonPrimitive(parentId)) })
            put("mimeType", FOLDER)
        }
        val res = call("POST", "$API/files?supportsAllDrives=true&fields=id", mapOf("Content-Type" to "application/json; charset=UTF-8"), meta.toString())
        return obj(res).str("id").orEmpty()
    }

    override fun trash(id: String) {
        call("PATCH", "$API/files/${urlEncode(id)}?supportsAllDrives=true", mapOf("Content-Type" to "application/json; charset=UTF-8"), """{"trashed":true}""")
    }

    companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER = "application/vnd.google-apps.folder"
        const val SCOPE = "https://www.googleapis.com/auth/drive"

        /** A value for Drive's search syntax: single-quoted, with quotes and backslashes escaped. */
        fun quote(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
    }
}
