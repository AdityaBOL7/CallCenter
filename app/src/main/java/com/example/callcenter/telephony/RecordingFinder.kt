package com.example.callcenter.telephony

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * Scans the agent's configured call-recording folder (a persisted SAF tree URI)
 * for the newest audio file created after the call started, so the disposition
 * screen can auto-attach it instead of the agent browsing manually.
 *
 * Best-effort: returns null if no folder is set, permission was lost, or nothing
 * matches. The agent can always Browse to override.
 */
object RecordingFinder {

    data class Found(val uri: Uri, val name: String)

    private val AUDIO_EXTS = setOf("mp3", "m4a", "wav", "amr", "ogg", "3gp", "aac", "opus", "wma")

    /**
     * Find the newest audio file in [treeUriString] modified at/after [sinceMillis].
     * Pass sinceMillis = 0 to just take the newest audio file in the folder.
     */
    fun findLatest(context: Context, treeUriString: String, sinceMillis: Long): Found? {
        if (treeUriString.isBlank()) return null
        return try {
            val treeUri = Uri.parse(treeUriString)
            // Confirm we still hold read permission on the tree (grants can be revoked).
            val hasGrant = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isReadPermission
            }
            if (!hasGrant) {
                Log.w(TAG, "no persisted read permission on recording folder")
                return null
            }

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )

            var bestUri: Uri? = null
            var bestName: String? = null
            var bestTime = Long.MIN_VALUE

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val mime = c.getString(mimeIdx).orEmpty()
                    val name = c.getString(nameIdx).orEmpty()
                    val modified = if (c.isNull(modIdx)) 0L else c.getLong(modIdx)
                    if (!isAudio(mime, name)) continue
                    if (modified < sinceMillis) continue
                    if (modified > bestTime) {
                        bestTime = modified
                        bestName = name
                        val docId = c.getString(idIdx)
                        bestUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    }
                }
            }

            val uri = bestUri
            val name = bestName
            if (uri != null && name != null) {
                Log.d(TAG, "auto-found recording: $name")
                Found(uri, name)
            } else {
                Log.d(TAG, "no matching recording since $sinceMillis")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "recording scan failed", e)
            null
        }
    }

    private fun isAudio(mime: String, name: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTS
    }

    private const val TAG = "Bol7Rec"
}
