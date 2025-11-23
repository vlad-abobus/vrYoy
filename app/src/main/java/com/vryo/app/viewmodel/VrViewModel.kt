package com.vryo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vryo.app.controller.VoiceController
import com.vryo.app.model.OverlayWindowData
import com.vryo.app.utils.Persistence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel для VR додатку
 * Керує станом: режими, вікна, обрані елементи
 */
class VrViewModel : ViewModel() {
    
    enum class ViewMode {
        CAMERA_ONLY,        // Тільки камера
        CAMERA_YOUTUBE,     // Камера + YouTube
        YOUTUBE_ONLY        // Тільки YouTube
    }
    
    // State flows
    private val _viewMode = MutableStateFlow(ViewMode.CAMERA_YOUTUBE)
    val viewMode: StateFlow<ViewMode> = _viewMode
    
    private val _overlayWindows = MutableStateFlow<List<OverlayWindowData>>(emptyList())
    val overlayWindows: StateFlow<List<OverlayWindowData>> = _overlayWindows
    
    private val _selectedWindowId = MutableStateFlow<String?>(null)
    val selectedWindowId: StateFlow<String?> = _selectedWindowId
    
    private val _hudVisible = MutableStateFlow(false)
    val hudVisible: StateFlow<Boolean> = _hudVisible
    
    // Voice controller (буде ініціалізований в MainActivity)
    var voiceController: VoiceController? = null
    
    init {
        // Завантажуємо збережені вікна при старті
        // viewModelScope.launch { loadWindows() }
    }

    /**
     * Перемикає режим відображення
     */
    fun toggleViewMode() {
        val nextMode = when (_viewMode.value) {
            ViewMode.CAMERA_ONLY -> ViewMode.CAMERA_YOUTUBE
            ViewMode.CAMERA_YOUTUBE -> ViewMode.YOUTUBE_ONLY
            ViewMode.YOUTUBE_ONLY -> ViewMode.CAMERA_ONLY
        }
        _viewMode.value = nextMode
    }

    /**
     * Додає нове YouTube вікно
     */
    fun addYoutubeWindow(url: String? = null): OverlayWindowData {
        val window = OverlayWindowData(
            id = UUID.randomUUID().toString(),
            positionX = 0f,
            positionY = 0f,
            positionZ = -2.0f,
            scale = 1.0f,
            url = url,
            isVisible = true
        )
        
        val currentWindows = _overlayWindows.value.toMutableList()
        currentWindows.add(window)
        _overlayWindows.value = currentWindows
        
        return window
    }

    /**
     * Видаляє вікно
     */
    fun removeWindow(windowId: String) {
        val currentWindows = _overlayWindows.value.toMutableList()
        currentWindows.removeAll { it.id == windowId }
        _overlayWindows.value = currentWindows
    }

    /**
     * Оновлює вікно
     */
    fun updateWindow(window: OverlayWindowData) {
        val currentWindows = _overlayWindows.value.toMutableList()
        val index = currentWindows.indexOfFirst { it.id == window.id }
        if (index >= 0) {
            currentWindows[index] = window
            _overlayWindows.value = currentWindows
        }
    }

    /**
     * Обирає вікно
     */
    fun selectWindow(windowId: String?) {
        _selectedWindowId.value = windowId
    }

    /**
     * Перемикає видимість HUD
     */
    fun toggleHud() {
        _hudVisible.value = !_hudVisible.value
    }

    // Callback для доступу до YoutubeRenderer (встановлюється з MainActivity)
    var youtubeRendererCallback: ((String, (com.vryo.app.renderer.YoutubeRenderer) -> Unit) -> Unit)? = null

    /**
     * Обробляє голосову команду
     */
    fun handleVoiceCommand(command: VoiceController.VoiceCommand, parameter: String?) {
        val selectedWindowId = _selectedWindowId.value
        
        when (command) {
            VoiceController.VoiceCommand.PAUSE -> {
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.getPlayer(windowId)?.pause()
                    }
                }
            }
            VoiceController.VoiceCommand.PLAY -> {
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.getPlayer(windowId)?.play()
                    }
                }
            }
            VoiceController.VoiceCommand.SKIP_FORWARD -> {
                val seconds = parameter?.toIntOrNull() ?: 10
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.getPlayer(windowId)?.let { player ->
                            try {
                                val currentTime = 0f // Заглушка, якщо API не підтримує
                                player.seekTo(currentTime + seconds)
                            } catch (e: Exception) {
                                // Якщо не вдалося
                            }
                        }
                    }
                }
            }
            VoiceController.VoiceCommand.SKIP_BACKWARD -> {
                val seconds = parameter?.toIntOrNull() ?: 10
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.getPlayer(windowId)?.let { player ->
                            try {
                                // currentTime може не бути доступним в усіх версіях API
                                // Спробуємо отримати поточний час
                                val currentTime = 0f // Заглушка, якщо API не підтримує
                                player.seekTo(kotlin.math.max(0f, currentTime - seconds))
                            } catch (e: Exception) {
                                // Якщо не вдалося отримати поточний час
                                player.seekTo(0f)
                            }
                        }
                    }
                }
            }
            VoiceController.VoiceCommand.VOLUME_UP -> {
                // Збільшити гучність системи (потребує AudioManager)
            }
            VoiceController.VoiceCommand.VOLUME_DOWN -> {
                // Зменшити гучність системи (потребує AudioManager)
            }
            VoiceController.VoiceCommand.OPEN_YOUTUBE -> {
                parameter?.let { url ->
                    addYoutubeWindow(url)
                } ?: addYoutubeWindow()
            }
            VoiceController.VoiceCommand.SAVE_POSITION -> {
                // Зберігаємо позиції всіх вікон
                viewModelScope.launch {
                    saveWindows()
                }
            }
            // Нові команди для YouTube
            VoiceController.VoiceCommand.NEXT_VIDEO -> {
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.playNextVideo(windowId)
                    }
                }
            }
            VoiceController.VoiceCommand.PREVIOUS_VIDEO -> {
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.playPreviousVideo(windowId)
                    }
                }
            }
            VoiceController.VoiceCommand.SKIP_AD -> {
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.skipAd(windowId)
                    }
                }
            }
            VoiceController.VoiceCommand.REPLAY -> {
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.replay(windowId)
                    }
                }
            }
            VoiceController.VoiceCommand.LIKE -> {
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.likeVideo(windowId)
                    }
                }
            }
            VoiceController.VoiceCommand.DISLIKE -> {
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.dislikeVideo(windowId)
                    }
                }
            }
            VoiceController.VoiceCommand.SUBSCRIBE -> {
                selectedWindowId?.let { windowId ->
                    youtubeRendererCallback?.invoke(windowId) { renderer ->
                        renderer.subscribeToChannel(windowId)
                    }
                }
            }
            VoiceController.VoiceCommand.UNKNOWN -> {
                // Невідома команда
            }
        }
    }

    /**
     * Зберігає вікна
     */
    private suspend fun saveWindows() {
        // Persistence.saveWindows(context, _overlayWindows.value)
        // Потрібен context, тому буде викликано з MainActivity
    }

    /**
     * Завантажує збережені вікна
     */
    fun loadWindows(context: android.content.Context) {
        viewModelScope.launch {
            val windows = com.vryo.app.utils.Persistence.loadWindows(context)
            _overlayWindows.value = windows
        }
    }

    /**
     * Зберігає вікна (викликається з MainActivity)
     */
    fun saveWindows(context: android.content.Context) {
        viewModelScope.launch {
            com.vryo.app.utils.Persistence.saveWindows(context, _overlayWindows.value)
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceController?.release()
    }
}

