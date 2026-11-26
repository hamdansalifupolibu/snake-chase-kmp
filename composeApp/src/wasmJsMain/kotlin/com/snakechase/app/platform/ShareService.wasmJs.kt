package com.snakechase.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.await
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.Promise

/**
 * The browser shares the card with the Web Share API where it can (phones, Edge, Safari) and
 * otherwise downloads it and copies the text. The PNG crosses into JavaScript as one base64
 * data URL: a single interop call instead of a million byte-by-byte ones.
 */
actual class ShareService {
    actual val actionLabel: String = "Share"

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun shareImage(png: ByteArray, fileName: String, text: String): ShareOutcome = runCatching {
        val dataUrl = "data:image/png;base64," + Base64.encode(png)
        when (val r = jsShareCard(dataUrl, fileName, text).await<JsString>().toString()) {
            "shared" -> ShareOutcome.Shared
            "cancelled" -> ShareOutcome.Cancelled
            "downloaded" -> ShareOutcome.Saved("Card downloaded and the message copied: post it anywhere!")
            else -> ShareOutcome.Failed(r)
        }
    }.getOrElse { ShareOutcome.Failed(it.message ?: "Couldn't share from this browser") }
}

private fun jsShareCard(dataUrl: String, fileName: String, text: String): Promise<JsString> = js(
    """
    (async () => {
        const blob = await (await fetch(dataUrl)).blob();
        const file = new File([blob], fileName, { type: 'image/png' });
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
            try {
                await navigator.share({ files: [file], text: text });
                return 'shared';
            } catch (e) {
                if (e && e.name === 'AbortError') return 'cancelled';
            }
        }
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        try { await navigator.clipboard.writeText(text); } catch (e) {}
        return 'downloaded';
    })()
    """,
)

actual fun ImageBitmap.encodePng(): ByteArray = requireNotNull(Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)).bytes
