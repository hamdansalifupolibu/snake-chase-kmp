package com.snakechase.app.network.firebase

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/**
 * A Firestore document as plain Kotlin values: String, Long, Double, Boolean, Instant,
 * nested Map<String, Any?> or null. [updateTime] is the optimistic-concurrency token.
 */
data class FsDoc(val id: String, val fields: Map<String, Any?>, val updateTime: String)

/** Marker value: "set this field to the server's commit time" (REQUEST_TIME transform). */
object ServerTimestamp

/** The small slice of Firestore the game needs. Faked in tests. */
interface FirestoreDocs {
    /** Creates a new document; fails if [id] already exists. */
    suspend fun create(collection: String, id: String, fields: Map<String, Any?>): FsDoc

    suspend fun get(collection: String, id: String): FsDoc?

    /**
     * Updates only [fields]. With [ifUpdateTime], the write applies only if nobody changed the
     * document since that version was read; returns false when that precondition fails.
     */
    suspend fun update(collection: String, id: String, fields: Map<String, Any?>, ifUpdateTime: String?): Boolean

    /** Equality-only filters (no composite index needed), optional single-field ordering. */
    suspend fun query(
        collection: String,
        equals: Map<String, Any>,
        orderByDesc: String? = null,
        limit: Int = 20,
        /** Only documents whose [orderByDesc] field is at most this (null = no bound). */
        maxValue: Long? = null,
    ): List<FsDoc>

    companion object {
        private const val ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        fun newId(): String = (1..20).map { ID_CHARS[Random.nextInt(ID_CHARS.length)] }.joinToString("")
    }
}

/** Firestore over its REST API with an anonymous-auth bearer token. */
class FirestoreClient(
    private val http: HttpClient,
    private val auth: FirebaseAuthClient,
) : FirestoreDocs {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(collection: String, id: String, fields: Map<String, Any?>): FsDoc {
        val updateTime = commit(collection, id, fields, mask = null, precondition = buildJsonObject { put("exists", false) })
            ?: throw OnlineException("That challenge already exists.")
        return FsDoc(id, fields.filterValues { it !is ServerTimestamp }, updateTime)
    }

    override suspend fun get(collection: String, id: String): FsDoc? {
        val response = authed { http.get("${FirebaseConfig.FIRESTORE_ROOT}/$collection/$id") { it(this) } }
        if (response.status == HttpStatusCode.NotFound) return null
        val body = response.checked()
        return FirestoreCodec.decodeDocument(json.parseToJsonElement(body).jsonObject)
    }

    override suspend fun update(collection: String, id: String, fields: Map<String, Any?>, ifUpdateTime: String?): Boolean {
        val precondition = ifUpdateTime?.let { t -> buildJsonObject { put("updateTime", t) } }
            ?: buildJsonObject { put("exists", true) }
        return commit(collection, id, fields, mask = fields.keys.toList(), precondition = precondition) != null
    }

    override suspend fun query(
        collection: String,
        equals: Map<String, Any>,
        orderByDesc: String?,
        limit: Int,
        maxValue: Long?,
    ): List<FsDoc> {
        val structuredQuery = buildJsonObject {
            putJsonArray("from") { add(buildJsonObject { put("collectionId", collection) }) }
            val bound = if (maxValue != null && orderByDesc != null) {
                // An upper bound on the sort field: Firestore allows it without a composite index.
                listOf(
                    buildJsonObject {
                        putJsonObject("fieldFilter") {
                            putJsonObject("field") { put("fieldPath", orderByDesc) }
                            put("op", "LESS_THAN_OR_EQUAL")
                            put("value", FirestoreCodec.encodeValue(maxValue))
                        }
                    },
                )
            } else {
                emptyList()
            }
            if (equals.isNotEmpty() || bound.isNotEmpty()) {
                val filters = equals.map { (field, value) ->
                    buildJsonObject {
                        putJsonObject("fieldFilter") {
                            putJsonObject("field") { put("fieldPath", field) }
                            put("op", "EQUAL")
                            put("value", FirestoreCodec.encodeValue(value))
                        }
                    }
                } + bound
                put(
                    "where",
                    if (filters.size == 1) {
                        filters.first()
                    } else {
                        buildJsonObject {
                            putJsonObject("compositeFilter") {
                                put("op", "AND")
                                put("filters", JsonArray(filters))
                            }
                        }
                    },
                )
            }
            if (orderByDesc != null) {
                putJsonArray("orderBy") {
                    add(
                        buildJsonObject {
                            putJsonObject("field") { put("fieldPath", orderByDesc) }
                            put("direction", "DESCENDING")
                        },
                    )
                }
            }
            put("limit", limit)
        }
        val body = authed {
            http.post("${FirebaseConfig.FIRESTORE_ROOT}:runQuery") {
                it(this)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("structuredQuery", structuredQuery) }.toString())
            }
        }.checked()
        // runQuery streams one object per result; entries without "document" are progress markers.
        return json.parseToJsonElement(body).jsonArray.mapNotNull { row ->
            row.jsonObject["document"]?.jsonObject?.let(FirestoreCodec::decodeDocument)
        }
    }

    /** One atomic write via :commit. Returns the new updateTime, or null if the precondition failed. */
    private suspend fun commit(
        collection: String,
        id: String,
        fields: Map<String, Any?>,
        mask: List<String>?,
        precondition: JsonObject,
    ): String? {
        val plain = fields.filterValues { it !is ServerTimestamp }
        val serverTimeFields = fields.filterValues { it is ServerTimestamp }.keys
        val write = buildJsonObject {
            putJsonObject("update") {
                put("name", "${FirebaseConfig.DOCUMENT_NAME_PREFIX}/$collection/$id")
                put("fields", FirestoreCodec.encodeFields(plain))
            }
            if (mask != null) {
                putJsonObject("updateMask") {
                    put("fieldPaths", buildJsonArray { plain.keys.forEach { add(JsonPrimitive(it)) } })
                }
            }
            if (serverTimeFields.isNotEmpty()) {
                put(
                    "updateTransforms",
                    buildJsonArray {
                        serverTimeFields.forEach { f ->
                            add(
                                buildJsonObject {
                                    put("fieldPath", f)
                                    put("setToServerValue", "REQUEST_TIME")
                                },
                            )
                        }
                    },
                )
            }
            put("currentDocument", precondition)
        }
        val response = authed {
            http.post("${FirebaseConfig.FIRESTORE_ROOT}:commit") {
                it(this)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { putJsonArray("writes") { add(write) } }.toString())
            }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // Lost an optimistic-concurrency race, or create() on an existing id: not an error.
            if ("FAILED_PRECONDITION" in body ||
                "ALREADY_EXISTS" in body ||
                "ABORTED" in body ||
                response.status == HttpStatusCode.Conflict
            ) {
                return null
            }
            throw describe(response.status, body)
        }
        val result = json.parseToJsonElement(body).jsonObject
        return result["writeResults"]?.jsonArray?.firstOrNull()?.jsonObject?.get("updateTime")?.jsonPrimitive?.content
            ?: result["commitTime"]?.jsonPrimitive?.content
            ?: ""
    }

    /** Runs a request with the bearer token; on 401 refreshes once and retries. */
    private suspend fun authed(request: suspend (HttpRequestBuilder.() -> Unit) -> HttpResponse): HttpResponse {
        suspend fun attempt(): HttpResponse {
            val token = auth.token()
            return try {
                request { header("Authorization", "Bearer $token") }
            } catch (e: Exception) {
                throw OnlineException("No connection to the online arena.", e)
            }
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
        status == HttpStatusCode.Forbidden || "PERMISSION_DENIED" in body ->
            OnlineException("The online arena refused the request (security rules).")
        "NOT_FOUND" in body && "database" in body.lowercase() ->
            OnlineException("Firestore database is not created yet.")
        else -> OnlineException("Online arena error (${status.value}).")
    }
}

/** Plain Kotlin values ⇄ Firestore's typed JSON values. Pure, so it is unit-tested. */
object FirestoreCodec {

    fun encodeFields(fields: Map<String, Any?>): JsonObject = JsonObject(fields.mapValues { (_, v) -> encodeValue(v) })

    fun encodeValue(value: Any?): JsonElement = when (value) {
        null -> buildJsonObject { put("nullValue", JsonNull) }
        is String -> buildJsonObject { put("stringValue", value) }
        is Boolean -> buildJsonObject { put("booleanValue", value) }
        is Int -> buildJsonObject { put("integerValue", value.toString()) }
        is Long -> buildJsonObject { put("integerValue", value.toString()) }
        is Float -> buildJsonObject { put("doubleValue", value.toDouble()) }
        is Double -> buildJsonObject { put("doubleValue", value) }
        is Instant -> buildJsonObject { put("timestampValue", value.toString()) }
        is Map<*, *> -> buildJsonObject {
            putJsonObject("mapValue") {
                @Suppress("UNCHECKED_CAST")
                put("fields", encodeFields(value as Map<String, Any?>))
            }
        }
        else -> throw IllegalArgumentException("Unsupported Firestore value: ${value::class.simpleName}")
    }

    fun decodeDocument(doc: JsonObject): FsDoc {
        val name = doc.getValue("name").jsonPrimitive.content
        val fields = doc["fields"]?.jsonObject?.let(::decodeFields) ?: emptyMap()
        return FsDoc(
            id = name.substringAfterLast('/'),
            fields = fields,
            updateTime = doc["updateTime"]?.jsonPrimitive?.content ?: "",
        )
    }

    fun decodeFields(fields: JsonObject): Map<String, Any?> = fields.mapValues { (_, v) -> decodeValue(v.jsonObject) }

    fun decodeValue(v: JsonObject): Any? {
        val (type, raw) = v.entries.firstOrNull() ?: return null
        return when (type) {
            "stringValue" -> raw.jsonPrimitive.content
            "integerValue" -> raw.jsonPrimitive.content.toLong()
            "doubleValue" -> raw.jsonPrimitive.content.toDouble()
            "booleanValue" -> raw.jsonPrimitive.content.toBoolean()
            "timestampValue" -> Instant.parse(raw.jsonPrimitive.content)
            "mapValue" -> raw.jsonObject["fields"]?.jsonObject?.let(::decodeFields) ?: emptyMap<String, Any?>()
            "nullValue" -> null
            else -> null // arrays/bytes/geo are not used by the game
        }
    }
}
