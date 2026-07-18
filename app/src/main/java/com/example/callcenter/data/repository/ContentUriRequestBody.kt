package com.example.callcenter.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * An OkHttp [RequestBody] that streams a `content://` file straight from the
 * [android.content.ContentResolver] instead of buffering it into a ByteArray.
 *
 * This avoids loading a large recording (the backend accepts up to ~100 MiB)
 * fully into the heap, which would OOM on low-RAM devices. OkHttp may call
 * [writeTo] more than once (e.g. on retry), so a fresh InputStream is opened
 * each time.
 */
class ContentUriRequestBody(
    private val context: Context,
    private val uri: Uri,
    mimeType: String,
) : RequestBody() {

    private val mediaType: MediaType? = mimeType.toMediaTypeOrNull()

    override fun contentType(): MediaType? = mediaType

    /** Best-effort content length from the resolver; -1 (unknown → chunked) if unavailable. */
    override fun contentLength(): Long = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
            } ?: -1L
    } catch (_: Exception) {
        -1L
    }

    override fun writeTo(sink: BufferedSink) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("Couldn't open the selected file.")
        input.source().use { source -> sink.writeAll(source) }
    }
}
