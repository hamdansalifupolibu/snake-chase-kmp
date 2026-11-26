package com.snakechase.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIViewController

/** iOS: the standard share sheet (Messages, WhatsApp, Instagram, Save Image...). */
actual class ShareService {
    actual val actionLabel: String = "Share"

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual suspend fun shareImage(png: ByteArray, fileName: String, text: String): ShareOutcome = withContext(Dispatchers.Main) {
        runCatching {
            val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
            val image = UIImage.imageWithData(data) ?: error("Couldn't read the card image")
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: error("No screen to share from")
            val top = topOf(root)
            val sheet = UIActivityViewController(activityItems = listOf(text, image), applicationActivities = null)
            // iPad shows the sheet as a popover, which needs an anchor or it crashes.
            sheet.popoverPresentationController?.sourceView = top.view
            top.presentViewController(sheet, animated = true, completion = null)
            ShareOutcome.Shared
        }.getOrElse { ShareOutcome.Failed(it.message ?: "Couldn't open sharing") }
    }

    private fun topOf(vc: UIViewController): UIViewController = vc.presentedViewController?.let(::topOf) ?: vc
}

actual fun ImageBitmap.encodePng(): ByteArray = requireNotNull(Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)).bytes
