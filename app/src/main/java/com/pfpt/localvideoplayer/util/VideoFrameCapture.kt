package com.pfpt.localvideoplayer.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object VideoFrameCapture {

    fun captureAndSave(
        context: Context,
        videoUri: Uri,
        positionMs: Long,
    ): Boolean {
        val bitmap = captureFrame(context, videoUri, positionMs) ?: return false
        return try {
            saveBitmap(context, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun captureFrame(context: Context, videoUri: Uri, positionMs: Long): Bitmap? {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, videoUri)
                val timeUs = (positionMs.coerceAtLeast(0L) * 1000L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        1920,
                        1080,
                    ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }
            }
        }.getOrNull()
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap): Boolean {
        val fileName = "LumenPlay_${System.currentTimeMillis()}.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LumenPlay")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                } == true
            }.getOrDefault(false)
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            written
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "LumenPlay",
            )
            if (!dir.exists() && !dir.mkdirs()) return false
            val file = File(dir, fileName)
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            true
        }
    }
}
