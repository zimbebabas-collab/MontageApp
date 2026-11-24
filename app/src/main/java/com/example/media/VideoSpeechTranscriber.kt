package com.example.media

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Automatically transcribes Russian speech directly from the uploaded video's audio track
 * using Android 13+ on-device SpeechRecognizer EXTRA_AUDIO_SOURCE (16kHz mono PCM pipe),
 * without requiring cloud API keys or manual re-speaking.
 */
object VideoSpeechTranscriber {

    sealed interface Result {
        data class Success(val text: String) : Result
        data class UnsupportedOrEmpty(val reasonRu: String) : Result
    }

    private const val TARGET_RATE = 16000

    suspend fun transcribeVideoAudioRussian(
        context: Context,
        videoUri: Uri,
        maxDurationMs: Long = 35_000L
    ): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return Result.UnsupportedOrEmpty(
                "Авто-распознавание прямо из видеофайла доступно на Android 13+. Используйте кнопку микрофона (ru-RU) или вставьте текст ниже."
            )
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return Result.UnsupportedOrEmpty(
                "Системный сервис распознавания речи недоступен. Введите текст вручную."
            )
        }

        val mono16kPcm = withContext(Dispatchers.IO) {
            decodeVideoAudioTo16kMonoPcm(context, videoUri, maxDurationMs)
        }
        if (mono16kPcm == null || mono16kPcm.size < 3200) {
            return Result.UnsupportedOrEmpty(
                "В видео не найдена чёткая голосовая дорожка. Введите текст или надиктуйте голосом."
            )
        }

        val pipes = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
            ?: return Result.UnsupportedOrEmpty("Не удалось открыть аудиопоток для распознавания.")
        val readEnd = pipes[0]
        val writeEnd = pipes[1]

        val transcriptDeferred = CompletableDeferred<String?>()
        val errorDeferred = CompletableDeferred<String?>()

        // Create and start SpeechRecognizer strictly on Main thread
        val recognizer = withContext(Dispatchers.Main) {
            val sr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            val accumulated = StringBuilder()
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (!transcriptDeferred.isCompleted) {
                        val partial = accumulated.toString().trim()
                        if (partial.isNotEmpty()) {
                            transcriptDeferred.complete(partial)
                        } else {
                            errorDeferred.complete("Код распознавания: $error")
                            transcriptDeferred.complete(null)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotEmpty()) {
                        if (accumulated.isNotEmpty()) accumulated.append(" ")
                        accumulated.append(best)
                    }
                    if (!transcriptDeferred.isCompleted) {
                        transcriptDeferred.complete(accumulated.toString().trim().ifEmpty { null })
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    if (p.isNotEmpty() && p.length > accumulated.length) {
                        accumulated.clear()
                        accumulated.append(p)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readEnd)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, TARGET_RATE)
            }

            runCatching { sr.startListening(intent) }.onFailure {
                errorDeferred.complete(it.localizedMessage)
                transcriptDeferred.complete(null)
            }
            sr
        }

        // Stream decoded video PCM into pipe on IO dispatcher
        val writerJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { out ->
                    val chunkSize = 3200 // 100ms of 16kHz 16-bit mono PCM
                    var pos = 0
                    while (pos < mono16kPcm.size && !transcriptDeferred.isCompleted) {
                        val len = min(chunkSize, mono16kPcm.size - pos)
                        out.write(mono16kPcm, pos, len)
                        out.flush()
                        pos += len
                        // Pace gently so Android SpeechRecognizer processes every word accurately
                        Thread.sleep(35L)
                    }
                }
            }
        }

        val recognized = withTimeoutOrNull(22_000L) { transcriptDeferred.await() }
        writerJob.cancel()

        withContext(Dispatchers.Main) {
            runCatching {
                recognizer.stopListening()
                recognizer.destroy()
            }
        }
        runCatching { readEnd.close() }
        runCatching { writeEnd.close() }

        return if (!recognized.isNullOrBlank()) {
            Result.Success(recognized)
        } else {
            Result.UnsupportedOrEmpty(
                "Речь из видеофайла не распозналась локально — нажмите «Надиктовать (ru-RU)» или вставьте текст вручную (облачное ИИ-распознавание требует Firebase)."
            )
        }
    }

    private fun decodeVideoAudioTo16kMonoPcm(
        context: Context,
        uri: Uri,
        maxDurationMs: Long
    ): ByteArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val out = ByteArrayOutputStream()
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    trackFormat = fmt
                    break
                }
            }
            if (trackIndex < 0 || trackFormat == null) return null

            extractor.selectTrack(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            var srcRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcCh = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val maxUs = maxDurationMs * 1000L

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val size = extractor.readSampleData(inBuf, 0)
                            val pts = extractor.sampleTime
                            if (size < 0 || pts > maxUs) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 5_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            srcRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            srcCh = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                    outIdx >= 0 -> {
                        val buf = codec.getOutputBuffer(outIdx)
                        if (buf != null && info.size > 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val raw = ByteArray(info.size)
                            buf.get(raw)
                            out.write(downsampleToMono16k(raw, srcRate, srcCh))
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            return out.toByteArray()
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun downsampleToMono16k(raw: ByteArray, srcRate: Int, srcCh: Int): ByteArray {
        val safeCh = max(1, srcCh)
        val safeRate = max(8000, srcRate)
        val totalShorts = raw.size / 2
        val srcFrames = totalShorts / safeCh
        if (srcFrames <= 0) return ByteArray(0)

        val srcShorts = ShortArray(totalShorts)
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(srcShorts)

        val ratio = safeRate.toDouble() / TARGET_RATE.toDouble()
        val dstFrames = max(1, (srcFrames / ratio).roundToInt())
        val dstBytes = ByteArray(dstFrames * 2)
        val dstBuf = ByteBuffer.wrap(dstBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until dstFrames) {
            val pos = min((i * ratio).toInt(), srcFrames - 1)
            val base = pos * safeCh
            val mono = if (safeCh >= 2 && base + 1 < srcShorts.size) {
                ((srcShorts[base].toInt() + srcShorts[base + 1].toInt()) / 2).toShort()
            } else {
                srcShorts[base]
            }
            dstBuf.putShort(mono)
        }
        return dstBytes
    }
}
