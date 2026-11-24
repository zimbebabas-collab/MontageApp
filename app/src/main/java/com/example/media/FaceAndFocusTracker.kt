package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.example.model.VideoClip
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max

/**
 * Automatically detects the primary speaker's face (using ML Kit Face Detection)
 * or falls back to a visual edge-saliency key-point centroid per clip, mapped into
 * the center-cropped 9:16 coordinate space so close-up zooms lock onto the face/subject
 * instead of blindly zooming into the dead center of the frame.
 */
object FaceAndFocusTracker {

    data class TrackResult(
        val focusX: Float,
        val focusY: Float,
        val faceDetected: Boolean
    )

    private const val TARGET_ASPECT = 9f / 16f

    suspend fun trackClipsFocus(
        context: Context,
        videoUri: Uri,
        clips: List<VideoClip>
    ): List<VideoClip> = withContext(Dispatchers.IO) {
        if (clips.isEmpty()) return@withContext clips

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.08f)
            .build()
        val detector = FaceDetection.getClient(options)
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, videoUri)
            clips.map { clip ->
                val sampleTimes = listOf(
                    clip.startMs + (clip.durationMs * 0.25f).toLong(),
                    clip.startMs + (clip.durationMs * 0.55f).toLong()
                )
                var bestFace: TrackResult? = null
                var fallbackSaliency: TrackResult? = null

                for (tsMs in sampleTimes) {
                    val bmp = fetchLowResFrame(retriever, tsMs) ?: continue
                    try {
                        val detected = detectFaceInBitmap(detector, bmp)
                        if (detected != null) {
                            bestFace = detected
                            break
                        } else if (fallbackSaliency == null) {
                            fallbackSaliency = detectSaliencyKeyPoint(bmp)
                        }
                    } finally {
                        bmp.recycle()
                    }
                }

                val chosen = bestFace ?: fallbackSaliency ?: TrackResult(0.50f, 0.35f, false)
                clip.copy(
                    focusX = chosen.focusX.coerceIn(0.18f, 0.82f),
                    focusY = chosen.focusY.coerceIn(0.16f, 0.80f),
                    hasDetectedFace = chosen.faceDetected
                )
            }
        } catch (_: Throwable) {
            clips
        } finally {
            runCatching { detector.close() }
            runCatching { retriever.release() }
        }
    }

    private fun fetchLowResFrame(retriever: MediaMetadataRetriever, ms: Long): Bitmap? {
        val us = ms.coerceAtLeast(0L) * 1000L
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    us,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    360,
                    640
                )
            } else {
                retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        }.getOrNull()
    }

    private suspend fun detectFaceInBitmap(
        detector: com.google.mlkit.vision.face.FaceDetector,
        bitmap: Bitmap
    ): TrackResult? = suspendCancellableCoroutine { cont ->
        val input = InputImage.fromBitmap(bitmap, 0)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val primary = pickPrimaryFace(faces)
                if (primary == null) {
                    if (cont.isActive) cont.resume(null)
                    return@addOnSuccessListener
                }
                val bw = bitmap.width.toFloat().coerceAtLeast(1f)
                val bh = bitmap.height.toFloat().coerceAtLeast(1f)

                // Use eyes/nose landmarks when available so zoom centers accurately on the face
                val leftEye = primary.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val rightEye = primary.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                val nose = primary.getLandmark(FaceLandmark.NOSE_BASE)?.position

                val rawX: Float
                val rawY: Float
                if (leftEye != null && rightEye != null) {
                    rawX = ((leftEye.x + rightEye.x) * 0.5f) / bw
                    rawY = (((leftEye.y + rightEye.y) * 0.5f) + (nose?.y ?: ((leftEye.y + rightEye.y) * 0.5f))) * 0.5f / bh
                } else {
                    val box = primary.boundingBox
                    rawX = box.exactCenterX() / bw
                    rawY = (box.top + box.height() * 0.42f) / bh
                }

                val mapped = mapSourcePointToCenterCropped9x16(rawX, rawY, bw, bh)
                if (cont.isActive) {
                    cont.resume(TrackResult(mapped.first, mapped.second, faceDetected = true))
                }
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    private fun pickPrimaryFace(faces: List<Face>): Face? {
        if (faces.isEmpty()) return null
        return faces.maxByOrNull { face ->
            val b = face.boundingBox
            b.width().toLong() * b.height().toLong()
        }
    }

    /**
     * Visual key-point / high-contrast saliency centroid when no human face is visible
     * (e.g. cars, gaming action, pets, products).
     */
    private fun detectSaliencyKeyPoint(bitmap: Bitmap): TrackResult {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return TrackResult(0.50f, 0.42f, false)

        val stepX = max(2, w / 28)
        val stepY = max(2, h / 36)

        var weightedX = 0.0
        var weightedY = 0.0
        var totalWeight = 0.0

        var y = stepY
        while (y < h - stepY) {
            var x = stepX
            while (x < w - stepX) {
                val c = bitmap.getPixel(x, y)
                val r = bitmap.getPixel(x + stepX, y)
                val d = bitmap.getPixel(x, y + stepY)

                val lumC = luma(c)
                val grad = abs(lumC - luma(r)) + abs(lumC - luma(d))
                val sat = saturation(c) * 90f
                val energy = grad + sat

                if (energy > 34f) {
                    val weight = (energy * energy).toDouble()
                    weightedX += (x.toDouble() / w.toDouble()) * weight
                    weightedY += (y.toDouble() / h.toDouble()) * weight
                    totalWeight += weight
                }
                x += stepX
            }
            y += stepY
        }

        if (totalWeight <= 1.0) return TrackResult(0.50f, 0.42f, false)
        val rawX = (weightedX / totalWeight).toFloat().coerceIn(0.15f, 0.85f)
        val rawY = (weightedY / totalWeight).toFloat().coerceIn(0.15f, 0.82f)
        val mapped = mapSourcePointToCenterCropped9x16(rawX, rawY, w.toFloat(), h.toFloat())
        return TrackResult(mapped.first, mapped.second, faceDetected = false)
    }

    private fun mapSourcePointToCenterCropped9x16(
        srcNormX: Float,
        srcNormY: Float,
        srcW: Float,
        srcH: Float
    ): Pair<Float, Float> {
        val srcAspect = srcW / srcH.coerceAtLeast(1f)
        return if (srcAspect > TARGET_ASPECT) {
            // Source is wider than 9:16 (e.g. 16:9): left/right sides are cropped out
            val visibleWidthFraction = TARGET_ASPECT / srcAspect
            val cropStart = (1f - visibleWidthFraction) / 2f
            val mappedX = ((srcNormX - cropStart) / visibleWidthFraction).coerceIn(0.15f, 0.85f)
            mappedX to srcNormY.coerceIn(0.15f, 0.82f)
        } else {
            // Source is taller than 9:16: top/bottom are cropped out
            val visibleHeightFraction = srcAspect / TARGET_ASPECT
            val cropStart = (1f - visibleHeightFraction) / 2f
            val mappedY = ((srcNormY - cropStart) / visibleHeightFraction).coerceIn(0.15f, 0.82f)
            srcNormX.coerceIn(0.15f, 0.85f) to mappedY
        }
    }

    private fun luma(pixel: Int): Float {
        return 0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)
    }

    private fun saturation(pixel: Int): Float {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        return if (mx == 0) 0f else (mx - mn).toFloat() / mx.toFloat()
    }
}
