package com.watchout.core.speech

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gere a síntese de voz (TTS) com preferência para Português de Portugal.
 * Alertas críticos interrompem falas menos importantes.
 */
class SpeechManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    var isEnabled = true
    private var currentSpeechRate = 1.0f
    private val completionCallbacks = ConcurrentHashMap<String, () -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SpeechManager"
        const val UTTERANCE_ALERT = "utterance_alert"
        const val UTTERANCE_INFO = "utterance_info"
    }

    fun initialize(onReady: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("pt", "PT"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback para Português do Brasil se pt-PT não estiver disponível
                    val fallback = tts?.setLanguage(Locale("pt", "BR"))
                    isReady = fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
                    Log.w(TAG, "pt-PT não disponível. A usar pt-BR como fallback.")
                } else {
                    isReady = true
                }
                tts?.setSpeechRate(currentSpeechRate)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let(completionCallbacks::remove)
                    }
                    override fun onDone(utteranceId: String?) {
                        val callback = utteranceId?.let(completionCallbacks::remove) ?: return
                        mainHandler.post(callback)
                    }
                })
                onReady(isReady)
            } else {
                Log.e(TAG, "Falha na inicialização do TTS: $status")
                onReady(false)
            }
        }
    }

    /** Interrompe a fala atual e fala o texto imediatamente (para alertas críticos). */
    fun speakUrgent(text: String, onDone: (() -> Unit)? = null) {
        if (!isEnabled || !isReady) return
        val params = Bundle()
        completionCallbacks.clear()
        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) completionCallbacks[utteranceId] = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /** Adiciona o texto à fila de fala (para informações não urgentes). */
    fun speak(text: String) {
        if (!isEnabled || !isReady) return
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        completionCallbacks.clear()
    }
}
