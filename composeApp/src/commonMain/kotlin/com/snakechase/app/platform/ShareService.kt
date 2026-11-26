package com.snakechase.app.platform

import androidx.compose.ui.graphics.ImageBitmap

/** What happened when the player shared a card; the share page shows it as a status line. */
sealed interface ShareOutcome {
    /** The system share sheet opened (Android, iOS, Web Share). */
    data object Shared : ShareOutcome

    /** No share sheet here: the card was copied and/or saved instead. [note] says where. */
    data class Saved(val note: String) : ShareOutcome

    data object Cancelled : ShareOutcome

    data class Failed(val reason: String) : ShareOutcome
}

/**
 * Sends a share card the way each platform does it best: the share sheet on Android and iOS,
 * Web Share (or a download) in the browser, and clipboard + Pictures folder on desktop.
 */
expect class ShareService {
    /** Label for the main button, e.g. "Share" or "Copy & save". */
    val actionLabel: String

    suspend fun shareImage(png: ByteArray, fileName: String, text: String): ShareOutcome
}

/** PNG bytes of a captured card (Android uses its Bitmap encoder, the rest use Skia). */
expect fun ImageBitmap.encodePng(): ByteArray
