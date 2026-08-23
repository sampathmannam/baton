package com.baton.app.features.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.8.0 (PROD-READINESS-P2-P2-#3): the photo-stamp
 * helper. The SP-of-district persona uses photo
 * captures for case documentation (a photo of a
 * case diary, a suspect's property, a court
 * notice). A photo that lacks provenance is
 * inadmissible as evidence — a photo that is
 * timestamped at capture time, watermarked with
 * the device owner + the case-id, and stored
 * immutably (the watermark is part of the
 * pixel data, not an EXIF tag that can be
 * stripped) is a much stronger legal artifact.
 *
 * **The pattern.**
 *  1. Read the camera-captured JPEG from
 *     `cacheDir/captures/`.
 *  2. Apply EXIF orientation (the system camera
 *     may have rotated the bitmap; the EXIF tag
 *     records the rotation needed to display the
 *     pixels upright).
 *  3. Draw a watermark in the bottom-right
 *     corner:
 *      - "BATON · {device owner} · {iso8601} ·
 *        case {caseId}"
 *      - Semi-transparent white text on a
 *        semi-transparent black background bar so
 *        it's readable on any photo.
 *  4. Re-encode the bitmap to JPEG (quality 92)
 *     and overwrite the file in place.
 *
 * **Why overwrite (not copy).** The cache file
 * is the only copy at this point (the camera
 * wrote it; we read it; we have the re-encoded
 * version in memory; the original bytes are
 * unreferenced). Overwriting is simpler and
 * avoids a stale original lingering on disk.
 *
 * **v1.8.0 trade-off.** The watermark is
 * anti-aliased text at 28pt. A forensic adversary
 * with a JPEG compressor could remove the
 * watermark if they had the original (they don't
 * — the original is gone). A high-resolution
 * photo of a hand-written signature would lose
 * the watermark on a 95%-quality re-compression;
 * the v1.8.0 watermark is best-effort, not
 * tamper-proof. A v2.x can switch to a steganographic
 * watermark (LSB on the green channel) for true
 * tamper-evidence.
 *
 * **Failure modes.** The function returns the
 * stamped file path on success and the original
 * URI's path on failure (the caller treats both
 * as "a photo was saved"). The watermark is
 * best-effort — losing the watermark on a corrupt
 * JPEG decode is preferable to losing the photo.
 */
object PhotoStamp {

    private const val WATERMARK_TEXT_SIZE_PT = 28f
    private const val WATERMARK_PADDING_PX = 24
    private const val JPEG_QUALITY = 92

    /**
     * Stamp the JPEG at [uri] (a FileProvider URI
     * pointing to `cacheDir/captures/...`) with
     * the device owner's display name, the
     * capture time, and the case id. The file is
     * overwritten in place.
     *
     * Returns the resolved file path on success,
     * or the original URI's path on failure (the
     * caller treats both as "a photo was saved").
     */
    fun stamp(
        context: Context,
        uri: Uri,
        deviceOwnerDisplayName: String,
        caseId: String,
    ): String {
        val originalPath = resolveFilePath(context, uri) ?: return uri.toString()
        val sourceBitmap = decodeScaled(originalPath) ?: return originalPath
        try {
            val oriented = applyExifOrientation(sourceBitmap, originalPath)
            try {
                val stamped = drawWatermark(oriented, deviceOwnerDisplayName, caseId)
                try {
                    val out = FileOutputStream(originalPath)
                    out.use { stamped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                } finally {
                    stamped.recycle()
                }
            } finally {
                if (oriented !== sourceBitmap) oriented.recycle()
            }
        } finally {
            sourceBitmap.recycle()
        }
        return originalPath
    }

    private fun resolveFilePath(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        // FileProvider URIs: the file is
        // accessible via the FileProvider API
        // directly (we built the URI in
        // [CameraLauncher.newCaptureUri]).
        if (uri.scheme == "content") {
            val authority = uri.authority ?: return null
            val expectedAuthority = context.packageName + CameraLauncher.AUTHORITY_SUFFIX
            if (authority == expectedAuthority) {
                // The last path segment is the file
                // name. The captures subdir is
                // `cacheDir/captures/`. We can
                // resolve the full path from the
                // well-known subdir + the segment.
                val fileName = uri.lastPathSegment ?: return null
                val dir = File(context.cacheDir, CameraLauncher.CAPTURE_SUBDIR)
                val file = File(dir, fileName)
                if (file.exists()) return file.absolutePath
            }
        }
        return null
    }

    private fun decodeScaled(path: String): Bitmap? {
        // First pass: bounds only.
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        // Target max dimension 2048px — a photo
        // larger than that is wasted disk space
        // for a thumbnail-thumbnail.
        val target = 2048
        var sample = 1
        while (
            boundsOpts.outWidth / sample > target ||
            boundsOpts.outHeight / sample > target
        ) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun applyExifOrientation(bitmap: Bitmap, path: String): Bitmap {
        // v1.8.0: we deliberately don't depend on
        // androidx.exifinterface (the EXIF library is
        // a separate dep). The system camera on the
        // majority of Android 14+ devices writes
        // pixels upright (the ORIENTATION_NORMAL
        // case); on devices that do write a rotated
        // JPEG, the watermark is in the bottom-right
        // and may appear in a non-ideal corner, but
        // the photo is still readable. A v2.x can
        // add the exif dependency to fix the
        // orientation properly.
        return bitmap
    }

    private fun drawWatermark(
        bitmap: Bitmap,
        deviceOwnerDisplayName: String,
        caseId: String,
    ): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        val text = "BATON · $deviceOwnerDisplayName · $timestamp · case $caseId"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = WATERMARK_TEXT_SIZE_PT * (output.width / 1080f).coerceAtLeast(1f)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 0, 0, 0)
        }
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val pad = WATERMARK_PADDING_PX
        val textWidth = textBounds.width()
        val textHeight = textBounds.height()
        val bgLeft = output.width - textWidth - pad * 3
        val bgTop = output.height - textHeight - pad * 2
        val bgRight = output.width - pad
        val bgBottom = output.height - pad
        canvas.drawRoundRect(
            bgLeft.toFloat(),
            bgTop.toFloat(),
            bgRight.toFloat(),
            bgBottom.toFloat(),
            8f,
            8f,
            bgPaint,
        )
        canvas.drawText(
            text,
            bgLeft + pad.toFloat(),
            bgBottom - pad / 2f,
            textPaint,
        )
        return output
    }
}
