package com.example.media

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID

class AdVoiceoverSynthesizer(private val context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile
    private var isReady: Boolean = false
    private val readyDeferred = CompletableDeferred<Boolean>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ru = Locale("ru", "RU")
                val res = tts?.setLanguage(ru)
                isReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                if (!isReady) {
                    tts?.setLanguage(Locale.getDefault())
                    isReady = true
                }
                tts?.setSpeechRate(1.06f)
            } else {
                isReady = false
            }
            if (!readyDeferred.isCompleted) {
                readyDeferred.complete(isReady)
            }
        }
    }

    fun speakPreview(text: String) {
        if (text.isBlank() || !isReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ad_preview_${UUID.randomUUID()}")
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    suspend fun synthesizeToWav(text: String): String? {
        if (text.isBlank()) return null
        val initialized = withTimeoutOrNull(4000L) { readyDeferred.await() } ?: isReady
        if (!initialized) return null

        val outDir = File(context.cacheDir, "tts").apply { mkdirs() }
        val outFile = File(outDir, "ad_tts_${System.currentTimeMillis()}.wav")
        val utteranceId = "tts_export_${UUID.randomUUID()}"
        val done = CompletableDeferred<Boolean>()

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && !done.isCompleted) {
                    done.complete(true)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && !done.isCompleted) {
                    done.complete(false)
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && !done.isCompleted) {
                    done.complete(false)
                }
            }
        })

        val params = Bundle()
        val res = tts?.synthesizeToFile(text, params, outFile, utteranceId)
        if (res != TextToSpeech.SUCCESS) return null

        val ok = withTimeoutOrNull(12_000L) { done.await() } == true
        return if (ok && outFile.exists() && outFile.length() > 64L) {
            outFile.absolutePath
        } else {
            null
        }
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
