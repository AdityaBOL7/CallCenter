package com.example.callcenter.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Downscales + recompresses a picked image to a small JPEG suitable for an
 * avatar. Phone cameras produce 5–20 MB photos; a profile picture only needs
 * ~512px, which JPEG-compresses to well under 200 KB — so the backend's 10 MB
 * limit is never hit and no valid photo is ever rejected for size.
 *
 * Memory-safe: the source is decoded with inSampleSize so even a 108 MP image
 * never loads full-resolution into the heap (which would OOM on low-RAM phones).
 */
object ImageCompressor {

    private const val MAX_EDGE = 512      // longest side of the output, px
    private const val JPEG_QUALITY = 85   // 0..100

    /**
     * Read [uri], produce a compressed JPEG byte array. Returns null if the
     * image can't be decoded (corrupt / unsupported / gone). Runs blocking work;
     * call from a background dispatcher.
     */
    fun compressToJpeg(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver

        // Pass 1: bounds only, so we can compute a safe downsample factor
        // without decoding the full bitmap into memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        // Honour the EXIF orientation so portrait selfies aren't uploaded sideways.
        val rotated = applyExifRotation(context, uri, decoded)

        // Scale the long edge down to exactly MAX_EDGE (never up).
        val scaled = scaleLongEdge(rotated, MAX_EDGE)
        if (scaled !== rotated) rotated.recycle()

        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            scaled.recycle()
            out.toByteArray()
        }
    }

    /** Largest power-of-two sample size that keeps both edges ≥ [maxEdge]. */
    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxEdge && h / 2 >= maxEdge) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleLongEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longest
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
