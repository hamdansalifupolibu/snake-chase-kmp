package com.snakechase.app.network.firebase

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A change pushed by the Realtime Database stream: [data] now lives at [path] (relative to the listened node). */
data class RtdbEvent(val path: String, val data: JsonElement, val isPatch: Boolean = false)

/** The slice of Firebase Realtime Database used for live duel rooms. Faked in tests. */
interface RealtimeDb {
    suspend fun put(path: String, value: JsonElement)

    /** Appends under [path] with a server-generated, chronologically ordered key; returns the key. */
    suspend fun push(path: String, value: JsonElement): String

    /** Writes only if nothing exists at [path] yet; false if it was already taken. */
    suspend fun putIfAbsent(path: String, value: JsonElement): Boolean
    suspend fun get(path: String): JsonElement?
    suspend fun delete(path: String)

    /** Live changes under [path] until the flow is cancelled. The first event is the current value. */
    fun listen(path: String): Flow<RtdbEvent>

    /**
     * Keys of the children of [path] whose (numeric) value is at most [max], up to [limit] of them.
     * The default filters client-side (fine for fakes); the real client asks the server via an index.
     */
    suspend fun keysWithValueAtMost(path: String, max: Long, limit: Int): List<String> = (get(path) as? JsonObject).orEmpty()
        .filter { (_, v) -> (v as? JsonPrimitive)?.longOrNull?.let { it <= max } == true }
        .keys.take(limit)
}

/** Realtime Database over REST + Server-Sent Events — no SDK, so it runs on every Kotlin target. */
class RealtimeDbClient(
    private val http: HttpClient,
    private val auth: FirebaseAuthClient,
    private val baseUrl: String = FirebaseConfig.RTDB_URL,
) : RealtimeDb {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun url(path: String) = "$baseUrl/${path.trim('/')}.json?auth=${auth.token()}"

    override suspend fun put(path: String, value: JsonElement) {
        call {
            // print=silent: the server answers 204 instead of echoing the value back.
            http.put(url(path) + "&print=silent") {
                contentType(ContentType.Application.Json)
                setBody(value.toString())
            }
        }.checked()
    }

    override suspend fun push(path: String, value: JsonElement): String {
        val body = call {
            http.post(url(path)) {
                contentType(ContentType.Application.Json)
                setBody(value.toString())
            }
        }.checked()
        return json.parseToJsonElement(body).jsonObject.getValue("name").jsonPrimitive.content
    }

    override suspend fun putIfAbsent(path: String, value: JsonElement): Boolean {
        // RTDB conditional REST write: "null_etag" matches only a node that does not exist.
        val response = call {
            http.put(url(path)) {
                header("if-match", "null_etag")
                contentType(ContentType.Application.Json)
                setBody(value.toString())
            }
        }
        if (response.status == HttpStatusCode.PreconditionFailed) return false
        // Rules reject writes to an occupied slot too (e.g. a second guest) — same meaning.
        // (A 401 is a sign-in problem, not a taken slot: checked() reports it.)
        if (response.status == HttpStatusCode.Forbidden) return false
        response.checked()
        return true
    }

    override suspend fun get(path: String): JsonElement? {
        val body = call { http.get(url(path)) }.checked()
        return json.parseToJsonElement(body).takeUnless { it is JsonNull }
    }

    override suspend fun delete(path: String) {
        call { http.delete(url(path)) }.checked()
    }

    override suspend fun keysWithValueAtMost(path: String, max: Long, limit: Int): List<String> {
        // Server-side query; needs `.indexOn: ".value"` on [path] in database.rules.json.
        val query = "&orderBy=%22%24value%22&endAt=$max&limitToFirst=$limit"
        val body = call { http.get(url(path) + query) }.checked()
        return (json.parseToJsonElement(body) as? JsonObject)?.keys?.toList().orEmpty()
    }

    override fun listen(path: String): Flow<RtdbEvent> = flow {
        http.prepareGet(url(path)) {
            header("Accept", "text/event-stream")
            // A stream lives as long as the room; RTDB sends keep-alives every ~30 s.
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = 90_000
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                // An expired sign-in: drop it so the reconnect signs in again.
                if (response.status == HttpStatusCode.Unauthorized) auth.invalidate()
                throw describe(response.status, response.bodyAsText())
            }
            val channel = response.bodyAsChannel()
            var event = ""
            var data = StringBuilder()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.startsWith("event:") -> event = line.substringAfter(':').trim()
                    line.startsWith("data:") -> data.append(line.substringAfter(':').trim())
                    line.isEmpty() -> {
                        if (event == "auth_revoked") {
                            // The token expired mid-stream: end it; the listener reconnects signed in again.
                            auth.invalidate()
                            throw OnlineException("Sign-in expired, reconnecting.")
                        }
                        when (event) {
                            "put", "patch" -> {
                                val payload = json.parseToJsonElement(data.toString()).jsonObject
                                emit(
                                    RtdbEvent(
                                        path = payload["path"]?.jsonPrimitive?.content ?: "/",
                                        data = payload["data"] ?: JsonNull,
                                        isPatch = event == "patch",
                                    ),
                                )
                            }
                            "cancel" -> throw OnlineException("Lost access to the room.")
                            // The token expired: end the stream so the caller re-listens with a fresh one.
                            "auth_revoked" -> {
                                auth.invalidate()
                                return@execute
                            }
                        }
                        event = ""
                        data = StringBuilder()
                    }
                }
            }
        }
    }

    /**
     * Runs a request (which builds its URL, and so its token, each time). A 401 means the sign-in
     * expired, e.g. the laptop slept through the hour and the browser's clock paused with it:
     * sign in again and retry once, instead of failing the duel.
     */
    private suspend fun call(request: suspend () -> HttpResponse): HttpResponse {
        suspend fun attempt(): HttpResponse = try {
            request()
        } catch (e: OnlineException) {
            throw e
        } catch (e: Exception) {
            throw OnlineException("No connection to the online arena.", e)
        }
        val first = attempt()
        if (first.status != HttpStatusCode.Unauthorized) return first
        auth.invalidate()
        return attempt()
    }

    private suspend fun HttpResponse.checked(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) throw describe(status, body)
        return body
    }

    private fun describe(status: HttpStatusCode, body: String): OnlineException = when {
        status == HttpStatusCode.NotFound -> OnlineException("Realtime Database is not created yet.")
        status == HttpStatusCode.Unauthorized || "Permission denied" in body ->
            OnlineException("The online arena refused the request (security rules).")
        else -> OnlineException("Online arena error (${status.value}).")
    }
}
