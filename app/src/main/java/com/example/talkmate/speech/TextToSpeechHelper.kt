package com.example.talkmate.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.*

class TextToSpeechHelper(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val _statusChannel = Channel<TTSStatus>(Channel.BUFFERED)
    val statusFlow: Flow<TTSStatus> = _statusChannel.receiveAsFlow()

    sealed class TTSStatus {
        object Initializing : TTSStatus()
        object Ready : TTSStatus()
        object Speaking : TTSStatus()
        object Done : TTSStatus()
        data class Error(val message: String) : TTSStatus()
    }

    init {
        _statusChannel.trySend(TTSStatus.Initializing)
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        when (status) {
            TextToSpeech.SUCCESS -> {
                tts?.let { ttsInstance ->
                    // Set language to default locale
                    val result = ttsInstance.setLanguage(Locale.getDefault())

                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        _statusChannel.trySend(TTSStatus.Error("Language not supported"))
                        return
                    }

                    // Configure TTS settings
                    ttsInstance.setPitch(1.0f)
                    ttsInstance.setSpeechRate(0.9f)

                    // Set utterance progress listener
                    ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _statusChannel.trySend(TTSStatus.Speaking)
                        }

                        override fun onDone(utteranceId: String?) {
                            _statusChannel.trySend(TTSStatus.Done)
                        }

                        override fun onError(utteranceId: String?) {
                            _statusChannel.trySend(TTSStatus.Error("Speech synthesis failed"))
                        }
                    })

                    isInitialized = true
                    _statusChannel.trySend(TTSStatus.Ready)
                }
            }
            else -> {
                _statusChannel.trySend(TTSStatus.Error("Text-to-speech initialization failed"))
            }
        }
    }

    fun speak(text: String, utteranceId: String = "tts_${System.currentTimeMillis()}") {
        if (!isInitialized) {
            _statusChannel.trySend(TTSStatus.Error("TTS not initialized"))
            return
        }

        if (text.isBlank()) {
            _statusChannel.trySend(TTSStatus.Error("No text to speak"))
            return
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _statusChannel.trySend(TTSStatus.Done)
    }

    fun pause() {
        tts?.stop()
    }

    fun isCurrentlySpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.1f, 3.0f))
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.1f, 2.0f))
    }

    fun getAvailableLanguages(): Set<Locale>? {
        return tts?.availableLanguages
    }

    fun setLanguage(locale: Locale): Boolean {
        tts?.let { ttsInstance ->
            val result = ttsInstance.setLanguage(locale)
            return result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }
        return false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _statusChannel.close()
    }
}