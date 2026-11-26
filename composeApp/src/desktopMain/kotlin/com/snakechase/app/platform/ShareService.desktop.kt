package com.snakechase.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/** Desktop has no share sheet: the card goes on the clipboard (paste into any chat) and into Pictures. */
actual class ShareService {
    actual val actionLabel: String = "Copy & save"

    actual suspend fun shareImage(png: ByteArray, fileName: String, text: String): ShareOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(System.getProperty("user.home"), "Pictures${File.separator}Snake Chase").apply { mkdirs() }
            val file = File(dir, fileName).apply { writeBytes(png) }
            val copied = runCatching {
                val image = ImageIO.read(ByteArrayInputStream(png))
                Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransfer(image), null)
            }.isSuccess
            ShareOutcome.Saved(
                if (copied) "Copied! Paste it into any chat. Also saved in Pictures/Snake Chase." else "Saved in Pictures/Snake Chase.",
            )
        }.getOrElse { ShareOutcome.Failed(it.message ?: "Couldn't save the card") }
    }

    private class ImageTransfer(private val image: java.awt.Image) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
            return image
        }
    }
}

actual fun ImageBitmap.encodePng(): ByteArray = requireNotNull(Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)).bytes
