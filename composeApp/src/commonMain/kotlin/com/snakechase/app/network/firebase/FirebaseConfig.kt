package com.snakechase.app.network.firebase

/**
 * Public Firebase project identifiers. A Firebase *web API key* is not a secret — it only
 * names the project; access is enforced by the Firestore security rules (see firestore.rules).
 */
object FirebaseConfig {
    const val API_KEY = "AIzaSyBG1V_6UkyHe4q4dF42w75DaddoSFVLMbI"
    const val PROJECT_ID = "snake-chase01"

    const val FIRESTORE_ROOT =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents"
    const val DOCUMENT_NAME_PREFIX = "projects/$PROJECT_ID/databases/(default)/documents"

    /** Realtime Database (live duel rooms + quick-match queue). us-central1 default instance. */
    const val RTDB_URL = "https://snake-chase01-default-rtdb.firebaseio.com"

    /** Where the web build is hosted (Firebase Hosting); used in "play with me" share messages. */
    const val WEB_URL = "https://snake-chase01.web.app"
}

/** A failure talking to Firebase, with a message that is safe to show to the player. */
class OnlineException(message: String, cause: Throwable? = null) : Exception(message, cause)
