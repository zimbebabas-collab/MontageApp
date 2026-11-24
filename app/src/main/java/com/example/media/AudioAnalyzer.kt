package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.model.AudioAnalysisResult
import com.example.model.AudioPeak
import com.example.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object AudioAnalyzer {

    private const val WINDOW_MS = 50L

    suspend fun analyzeVideo(
        context: Context,
        uri: Uri,
        silenceThreshold: Float,
        minSilenceMs: Long
    ): AudioAnalysisResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 1080
        var height = 1920
        var rotation = 0

        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1080
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1920
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
        } finally {
            runCatching { retriever.release() }
        }

        if (durationMs <= 0L) {
            durationMs = 5000L
        }

        val envelope = extractRmsEnvelope(context, uri, durationMs, WINDOW_MS)
        val peaks = detectPeaks(envelope, WINDOW_MS)
        val clips = sliceClipsFromEnvelope(
            envelope = envelope,
            durationMs = durationMs,
            windowMs = WINDOW_MS,
            silenceThreshold = silenceThreshold,
            minSilenceMs = minSilenceMs
        )

        AudioAnalysisResult(
            durationMs = durationMs,
            width = width,
            height = height,
            rotation = rotation,
            rmsEnvelope = envelope,
            windowMs = WINDOW_MS,
            peaks = peaks,
            clips = clips
        )
    }

    private fun extractRmsEnvelope(
        context: Context,
        uri: Uri,
        durationMs: Long,
        windowMs: Long
    ): FloatArray {
        val totalBuckets = max(1, ceil(durationMs.toDouble() / windowMs.toDouble()).toInt())
        val sumSq = DoubleArray(totalBuckets)
        val counts = IntArray(totalBuckets)

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                return FloatArray(totalBuckets) { 0.45f }
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return FloatArray(totalBuckets) { 0.45f }
            var sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val timeoutUs = 10_000L

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val ptsUs = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inIndex, 0, sampleSize, ptsUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }

                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val sampleCount = shortBuf.remaining()
                            val startMs = bufferInfo.presentationTimeUs / 1000L
                            val framesPerMs = (sampleRate * max(1, channelCount)) / 1000.0

                            // Stride by 4 samples for fast analysis without losing accuracy
                            val step = max(1, channelCount * 2)
                            var idx = 0
                            while (idx < sampleCount) {
                                val s = shortBuf.get(idx).toDouble() / 32768.0
                                val sampleMs = startMs + (idx / max(1.0, framesPerMs)).toLong()
                                val bucket = (sampleMs / windowMs).toInt().coerceIn(0, totalBuckets - 1)
                                sumSq[bucket] += s * s
                                counts[bucket]++
                                idx += step
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            return FloatArray(totalBuckets) { 0.45f }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }

        val rawRms = FloatArray(totalBuckets)
        var maxRms = 0.001f
        for (i in 0 until totalBuckets) {
            if (counts[i] > 0) {
                val rms = sqrt(sumSq[i] / counts[i]).toFloat()
                rawRms[i] = rms
                if (rms > maxRms) maxRms = rms
            }
        }

        // Normalize to 0f..1f with gentle perceptual lift so quieter speech still shows clearly
        return FloatArray(totalBuckets) { i ->
            val norm = (rawRms[i] / maxRms).coerceIn(0f, 1f)
            sqrt(norm.toDouble()).toFloat().coerceIn(0f, 1f)
        }
    }

    fun detectPeaks(envelope: FloatArray, windowMs: Long = WINDOW_MS): List<AudioPeak> {
        if (envelope.isEmpty()) return emptyList()
        val avg = envelope.average().toFloat()
        val peakThreshold = max(0.58f, avg + 0.18f).coerceAtMost(0.88f)
        val peaks = mutableListOf<AudioPeak>()
        var lastPeakMs = -1000L

        for (i in envelope.indices) {
            val v = envelope[i]
            val prev = if (i > 0) envelope[i - 1] else 0f
            val next = if (i < envelope.lastIndex) envelope[i + 1] else 0f
            val tsMs = i * windowMs
            if (v >= peakThreshold && v >= prev && v >= next && (tsMs - lastPeakMs) >= 650L) {
                peaks.add(AudioPeak(timestampMs = tsMs, intensity = v))
                lastPeakMs = tsMs
            }
        }
        return peaks
    }

    fun sliceClipsFromEnvelope(
        envelope: FloatArray,
        durationMs: Long,
        windowMs: Long = WINDOW_MS,
        silenceThreshold: Float,
        minSilenceMs: Long = 300L,
        minClipMs: Long = 650L
    ): List<VideoClip> {
        if (durationMs <= 0L) return emptyList()
        if (envelope.isEmpty()) {
            return listOf(
                VideoClip(
                    id = UUID.randomUUID().toString(),
                    startMs = 0L,
                    endMs = durationMs,
                    peakIntensity = 0.6f,
                    label = "Часть 1"
                )
            )
        }

        val minSilentBuckets = max(2, (minSilenceMs / windowMs).toInt())
        val padMs = 85L // Small breathing margin around speech cuts so words aren't clipped

        val activeRanges = mutableListOf<Pair<Long, Long>>()
        var inSpeech = false
        var speechStartIdx = 0
        var silentRun = 0

        for (i in envelope.indices) {
            val loud = envelope[i] >= silenceThreshold
            if (loud) {
                if (!inSpeech) {
                    inSpeech = true
                    speechStartIdx = i
                }
                silentRun = 0
            } else if (inSpeech) {
                silentRun++
                if (silentRun >= minSilentBuckets) {
                    val endIdx = i - silentRun
                    val startMs = max(0L, speechStartIdx * windowMs - padMs)
                    val endMs = min(durationMs, (endIdx + 1) * windowMs + padMs)
                    if (endMs - startMs >= minClipMs) {
                        activeRanges.add(startMs to endMs)
                    }
                    inSpeech = false
                    silentRun = 0
                }
            }
        }

        if (inSpeech) {
            val endIdx = envelope.lastIndex - silentRun
            val startMs = max(0L, speechStartIdx * windowMs - padMs)
            val endMs = min(durationMs, (endIdx + 1) * windowMs + padMs)
            if (endMs - startMs >= minClipMs) {
                activeRanges.add(startMs to endMs)
            }
        }

        // Merge overlapping padded ranges
        val merged = mutableListOf<Pair<Long, Long>>()
        for (range in activeRanges) {
            if (merged.isEmpty()) {
                merged.add(range)
            } else {
                val last = merged.last()
                if (range.first <= last.second + 120L) {
                    merged[merged.lastIndex] = last.first to max(last.second, range.second)
                } else {
                    merged.add(range)
                }
            }
        }

        // If the video had continuous audio without pauses, split long segments at local low-energy dips
        // so the user still gets dynamic highlight parts for TikTok/Reels
        val refined = if (merged.size == 1 && (merged[0].second - merged[0].first) > 9000L) {
            splitLongSegmentAtDips(merged[0].first, merged[0].second, envelope, windowMs)
        } else if (merged.isEmpty()) {
            // Fallback if threshold was higher than the whole audio
            listOf(0L to durationMs)
        } else {
            merged
        }

        return refined.mapIndexed { index, (s, e) ->
            val startB = (s / windowMs).toInt().coerceIn(0, envelope.lastIndex)
            val endB = (e / windowMs).toInt().coerceIn(startB, envelope.lastIndex)
            var peak = 0f
            for (b in startB..endB) {
                if (envelope[b] > peak) peak = envelope[b]
            }
            val shouldCloseUp = (index % 2 == 1) || (index > 0 && peak >= 0.84f)
            VideoClip(
                id = UUID.randomUUID().toString(),
                startMs = s,
                endMs = e,
                peakIntensity = peak,
                label = "Часть ${index + 1}",
                isCloseUp = shouldCloseUp
            )
        }
    }

    private fun splitLongSegmentAtDips(
        startMs: Long,
        endMs: Long,
        envelope: FloatArray,
        windowMs: Long
    ): List<Pair<Long, Long>> {
        val targetChunkMs = 4500L
        val result = mutableListOf<Pair<Long, Long>>()
        var cursor = startMs
        while (endMs - cursor > targetChunkMs + 1500L) {
            val idealSplit = cursor + targetChunkMs
            val searchStart = ((idealSplit - 1200L) / windowMs).toInt().coerceIn(0, envelope.lastIndex)
            val searchEnd = ((idealSplit + 1200L) / windowMs).toInt().coerceIn(searchStart, envelope.lastIndex)
            var minIdx = searchStart
            var minVal = Float.MAX_VALUE
            for (i in searchStart..searchEnd) {
                if (envelope[i] < minVal) {
                    minVal = envelope[i]
                    minIdx = i
                }
            }
            val splitMs = (minIdx * windowMs).coerceIn(cursor + 1200L, endMs - 1000L)
            result.add(cursor to splitMs)
            cursor = splitMs
        }
        result.add(cursor to endMs)
        return result
    }
}
