package com.vryo.app.controller

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.vryo.app.controller.VoiceController.VoiceCommand.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Контролер для розпізнавання голосових команд (українська та англійська)
 */
class VoiceController(private val context: Context) {
    
    enum class VoiceCommand {
        PAUSE, PLAY, SKIP_FORWARD, SKIP_BACKWARD, VOLUME_UP, VOLUME_DOWN,
        OPEN_YOUTUBE, SAVE_POSITION, 
        NEXT_VIDEO, PREVIOUS_VIDEO, SKIP_AD, REPLAY,
        LIKE, DISLIKE, SUBSCRIBE,
        UNKNOWN
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    private val _isRecognizing = MutableStateFlow(false)
    val isRecognizing: StateFlow<Boolean> = _isRecognizing
    
    private val _lastCommand = MutableStateFlow<Pair<VoiceCommand, String?>>(UNKNOWN to null)
    val lastCommand: StateFlow<Pair<VoiceCommand, String?>> = _lastCommand
    
    // Callbacks
    var onCommandRecognized: ((VoiceCommand, String?) -> Unit)? = null
    
    // Команди українською
    private val ukCommands = mapOf(
        "пауза" to PAUSE,
        "стоп" to PAUSE,
        "зупини" to PAUSE,
        "відтворити" to PLAY,
        "відтвори" to PLAY,
        "грати" to PLAY,
        "вперед" to SKIP_FORWARD,
        "перемотати" to SKIP_FORWARD,
        "перемотай" to SKIP_FORWARD,
        "назад" to SKIP_BACKWARD,
        "перемотай назад" to SKIP_BACKWARD,
        "збільшити гучність" to VOLUME_UP,
        "гучніше" to VOLUME_UP,
        "зменшити гучність" to VOLUME_DOWN,
        "тихіше" to VOLUME_DOWN,
        "відкрити ютуб" to OPEN_YOUTUBE,
        "відкрити youtube" to OPEN_YOUTUBE,
        "зберегти позицію" to SAVE_POSITION,
        "зберегти" to SAVE_POSITION,
        // Нові команди для YouTube
        "наступне відео" to NEXT_VIDEO,
        "наступне" to NEXT_VIDEO,
        "далі" to NEXT_VIDEO,
        "слідуюче відео" to NEXT_VIDEO,
        "попереднє відео" to PREVIOUS_VIDEO,
        "попереднє" to PREVIOUS_VIDEO,
        "назад до відео" to PREVIOUS_VIDEO,
        "пропустити рекламу" to SKIP_AD,
        "пропусти рекламу" to SKIP_AD,
        "скип рекламу" to SKIP_AD,
        "рекламу" to SKIP_AD,
        "перемотати рекламу" to SKIP_AD,
        "відтворити знову" to REPLAY,
        "знову" to REPLAY,
        "повторити" to REPLAY,
        "повтор" to REPLAY,
        "лайк" to LIKE,
        "подобається" to LIKE,
        "поставити лайк" to LIKE,
        "дизлайк" to DISLIKE,
        "не подобається" to DISLIKE,
        "поставити дизлайк" to DISLIKE,
        "підписатися" to SUBSCRIBE,
        "підписка" to SUBSCRIBE,
        "підписатись" to SUBSCRIBE
    )
    
    // Команди англійською
    private val enCommands = mapOf(
        "pause" to PAUSE,
        "stop" to PAUSE,
        "play" to PLAY,
        "skip forward" to SKIP_FORWARD,
        "skip" to SKIP_FORWARD,
        "forward" to SKIP_FORWARD,
        "skip back" to SKIP_BACKWARD,
        "backward" to SKIP_BACKWARD,
        "back" to SKIP_BACKWARD,
        "volume up" to VOLUME_UP,
        "louder" to VOLUME_UP,
        "volume down" to VOLUME_DOWN,
        "quieter" to VOLUME_DOWN,
        "open youtube" to OPEN_YOUTUBE,
        "open" to OPEN_YOUTUBE,
        "save position" to SAVE_POSITION,
        "save" to SAVE_POSITION,
        // Нові команди для YouTube
        "next video" to NEXT_VIDEO,
        "next" to NEXT_VIDEO,
        "previous video" to PREVIOUS_VIDEO,
        "previous" to PREVIOUS_VIDEO,
        "skip ad" to SKIP_AD,
        "skip advertisement" to SKIP_AD,
        "skip ads" to SKIP_AD,
        "replay" to REPLAY,
        "play again" to REPLAY,
        "like" to LIKE,
        "thumbs up" to LIKE,
        "dislike" to DISLIKE,
        "thumbs down" to DISLIKE,
        "subscribe" to SUBSCRIBE
    )
    
    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    /**
     * Починає прослуховування голосових команд
     */
    fun startListening(locale: Locale = Locale.getDefault()) {
        if (speechRecognizer == null || isListening) return
        
        isListening = true
        _isRecognizing.value = true
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toString())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        
        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                Log.d(TAG, "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Можна використовувати для візуального індикатора
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Ignore
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
                _isRecognizing.value = false
                isListening = false
            }

            override fun onError(error: Int) {
                Log.e(TAG, "Recognition error: $error")
                _isRecognizing.value = false
                isListening = false
                
                // Автоматично перезапускаємо прослуховування при помилках
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!isListening) startListening(locale)
                    }, 1000)
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    processResults(it)
                }
                _isRecognizing.value = false
                isListening = false
                
                // Перезапускаємо прослуховування
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!isListening) startListening(locale)
                }, 500)
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let {
                    Log.d(TAG, "Partial result: $it")
                }
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {
                // Ignore
            }
        })
        
        speechRecognizer?.startListening(intent)
    }

    /**
     * Зупиняє прослуховування
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        _isRecognizing.value = false
    }

    /**
     * Обробляє результати розпізнавання
     */
    private fun processResults(results: List<String>) {
        for (result in results) {
            val lowerResult = result.lowercase(Locale.getDefault())
            
            // Спробуємо знайти команду
            val command = recognizeCommand(lowerResult)
            if (command.first != UNKNOWN) {
                _lastCommand.value = command
                onCommandRecognized?.invoke(command.first, command.second)
                Log.d(TAG, "Command recognized: ${command.first}, param: ${command.second}")
                return
            }
        }
    }

    /**
     * Розпізнає команду з тексту
     */
    private fun recognizeCommand(text: String): Pair<VoiceCommand, String?> {
        // Перевіряємо українські команди
        for ((keyword, command) in ukCommands) {
            if (text.contains(keyword, ignoreCase = true)) {
                // Перевіряємо чи є число для skip forward/backward
                val number = extractNumber(text)
                return command to number
            }
        }
        
        // Перевіряємо англійські команди
        for ((keyword, command) in enCommands) {
            if (text.contains(keyword, ignoreCase = true)) {
                val number = extractNumber(text)
                return command to number
            }
        }
        
        // Перевіряємо команду "відкрити YouTube [URL]" або "open youtube [URL]"
        val youtubePattern = "(?:відкрити youtube|open youtube)\\s+(.+)".toRegex(RegexOption.IGNORE_CASE)
        youtubePattern.find(text)?.let {
            return OPEN_YOUTUBE to it.groupValues[1]
        }
        
        // Перевіряємо команду "перемотай на X секунд" або "skip X seconds"
        val skipPattern = "(?:перемотай на|skip)\\s+(\\d+)\\s*(?:секунд|seconds?)".toRegex(RegexOption.IGNORE_CASE)
        skipPattern.find(text)?.let {
            return SKIP_FORWARD to it.groupValues[1]
        }
        
        // Перевіряємо команди пропуску реклами (більш складні варіанти)
        val skipAdPatterns = listOf(
            "пропустити\\s+рекламу".toRegex(RegexOption.IGNORE_CASE),
            "пропусти\\s+рекламу".toRegex(RegexOption.IGNORE_CASE),
            "скип\\s+рекламу".toRegex(RegexOption.IGNORE_CASE),
            "skip\\s+ad".toRegex(RegexOption.IGNORE_CASE),
            "skip\\s+ads".toRegex(RegexOption.IGNORE_CASE),
            "skip\\s+advertisement".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in skipAdPatterns) {
            if (pattern.containsMatchIn(text)) {
                return SKIP_AD to null
            }
        }
        
        return UNKNOWN to null
    }

    /**
     * Виділяє число з тексту
     */
    private fun extractNumber(text: String): String? {
        val numberPattern = "\\d+".toRegex()
        return numberPattern.find(text)?.value
    }

    /**
     * Отримує чи зараз йде прослуховування
     */
    fun isListening(): Boolean = isListening

    /**
     * Очищає ресурси
     */
    fun release() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    companion object {
        private const val TAG = "VoiceController"
    }
}

