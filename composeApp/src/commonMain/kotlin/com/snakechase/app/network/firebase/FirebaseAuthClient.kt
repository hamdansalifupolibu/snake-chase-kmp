package com.snakechase.app.network.firebase

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Anonymous Firebase Authentication over the REST API — no SDK, so it runs on every
 * Kotlin target. The player never sees a login: the first online action creates an
 * anonymous account; its refresh token is persisted so the same uid survives restarts.
 */
class FirebaseAuthClient(
    private val http: HttpClient,
    private val settings: Settings,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private var idToken: String? = null

    /** Wall-clock expiry (epoch ms). Not a monotonic clock: browsers pause that while a laptop sleeps. */
    private var expiresAtMs: Long? = null

    /** Firebase uid of this install, or null before the first sign-in. */
    var uid: String? = settings.getStringOrNull(KEY_UID)
        private set

    /** A valid ID token, signing in or refreshing first if needed. */
    suspend fun token(): String = mutex.withLock {
        val current = idToken
        val expiry = expiresAtMs
        if (current != null && expiry != null && nowMs() < expiry) return current

        val refresh = settings.getStringOrNull(KEY_REFRESH)
        if (refresh != null) {
            // A revoked/invalid refresh token falls through to a fresh anonymous account.
            runCatching { return refreshWith(refresh) }
        }
        signUpAnonymously()
    }

    private suspend fun signUpAnonymously(): String {
        val response = try {
            http.post("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${FirebaseConfig.API_KEY}") {
                contentType(ContentType.Application.Json)
                setBody("""{"returnSecureToken":true}""")
            }
        } catch (e: Exception) {
            throw OnlineException("No connection to the online arena.", e)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val hint = when {
                "CONFIGURATION_NOT_FOUND" in body -> "Firebase Authentication is not set up (console → Authentication → Get started)."
                "ADMIN_ONLY_OPERATION" in body || "OPERATION_NOT_ALLOWED" in body ->
                    "Anonymous sign-in is disabled in the Firebase console."
                else -> "Sign-in failed (${response.status.value})."
            }
            throw OnlineException(hint)
        }
        val obj = json.parseToJsonElement(body).jsonObject
        return store(
            idToken = obj.getValue("idToken").jsonPrimitive.content,
            refreshToken = obj.getValue("refreshToken").jsonPrimitive.content,
            uid = obj.getValue("localId").jsonPrimitive.content,
            expiresInSec = obj["expiresIn"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600,
        )
    }

    private suspend fun refreshWith(refreshToken: String): String {
        val response = http.submitForm(
            url = "https://securetoken.googleapis.com/v1/token?key=${FirebaseConfig.API_KEY}",
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            },
        )
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw OnlineException("Session refresh failed.")
        val obj = json.parseToJsonElement(body).jsonObject
        return store(
            idToken = obj.getValue("id_token").jsonPrimitive.content,
            refreshToken = obj.getValue("refresh_token").jsonPrimitive.content,
            uid = obj.getValue("user_id").jsonPrimitive.content,
            expiresInSec = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600,
        )
    }

    private fun store(idToken: String, refreshToken: String, uid: String, expiresInSec: Long): String {
        this.idToken = idToken
        // Refresh a minute early so a request never goes out with a token about to expire.
        this.expiresAtMs = nowMs() + (expiresInSec - 60).coerceAtLeast(30) * 1000
        this.uid = uid
        settings.putString(KEY_REFRESH, refreshToken)
        settings.putString(KEY_UID, uid)
        return idToken
    }

    /** Signs in if needed and returns this install's uid. */
    suspend fun requireUid(): String {
        token()
        return uid ?: throw OnlineException("Sign-in did not return a player id.")
    }

    /** Forces the next call to fetch a new token (after a 401 from Firestore). */
    fun invalidate() {
        idToken = null
        expiresAtMs = null
    }

    private fun nowMs(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val KEY_REFRESH = "firebase_refresh_token"
        const val KEY_UID = "firebase_uid"
    }
}
