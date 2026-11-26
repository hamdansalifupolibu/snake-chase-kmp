package com.snakechase.app.platform

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

actual class ShareService(private val context: Context) {
    actual val actionLabel: String = "Share"

    actual suspend fun shareImage(png: ByteArray, fileName: String, text: String): ShareOutcome = runCatching {
        val file = withContext(Dispatchers.IO) {
            // One card at a time in the cache: old ones are deleted, nothing piles up.
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            File(dir, fileName).apply { writeBytes(png) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri(null, uri) // lets the chooser preview the card
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share your Snake Chase card").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        ShareOutcome.Shared
    }.getOrElse { ShareOutcome.Failed(it.message ?: "Couldn't open sharing") }
}

actual fun ImageBitmap.encodePng(): ByteArray = ByteArrayOutputStream().use { out ->
    asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    out.toByteArray()
}
