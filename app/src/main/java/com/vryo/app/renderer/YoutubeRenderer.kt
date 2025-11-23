package com.vryo.app.renderer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.vryo.app.R
import com.vryo.app.model.OverlayWindowData

/**
 * Рендерер для YouTube overlay вікна
 * Створює та керує YouTube player view для 3D overlay
 */
class YoutubeRenderer(
    private val context: Context,
    private val container: ViewGroup
) {
    private val youtubePlayers = mutableMapOf<String, YouTubePlayer>()
    private val youtubeViews = mutableMapOf<String, YouTubePlayerView>()

    /**
     * Створює нове YouTube overlay вікно
     */
    fun createYoutubeWindow(
        windowData: OverlayWindowData,
        videoId: String? = null
    ): View {
        val inflater = LayoutInflater.from(context)
        val overlayView = inflater.inflate(
            R.layout.overlay_window_layout,
            container,
            false
        ) as FrameLayout

        val youtubePlayerView = overlayView.findViewById<YouTubePlayerView>(
            R.id.youtubePlayerView
        )

        // Налаштування прозорості та рамки
        overlayView.background.alpha = (0.9f * 255).toInt()
        overlayView.elevation = 8f

        // Ініціалізуємо YouTube player
        youtubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                youtubePlayers[windowData.id] = youTubePlayer
                youtubeViews[windowData.id] = youtubePlayerView

                // Відтворюємо відео якщо ID вказано
                videoId?.let {
                    youTubePlayer.loadVideo(it, 0f)
                } else {
                    // Або використовуємо URL з windowData
                    windowData.url?.let { url ->
                        extractVideoId(url)?.let { id ->
                            youTubePlayer.loadVideo(id, 0f)
                        }
                    }
                }
            }

            override fun onStateChange(
                youTubePlayer: YouTubePlayer,
                state: PlayerConstants.PlayerState
            ) {
                // Можна додати обробку змін стану
            }
        })

        // Зберігаємо ID вікна як tag для подальшої ідентифікації
        overlayView.tag = windowData.id

        return overlayView
    }

    /**
     * Оновлює позицію та розмір вікна
     */
    fun updateWindowTransform(
        view: View,
        windowData: OverlayWindowData,
        projectionMatrix: FloatArray,
        eyeOffset: Float
    ) {
        // Використовуємо простіші перетворення через View properties
        // У реальному застосунку тут буде OpenGL проєктування
        
        val layoutParams = view.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

        // Обчислюємо 2D позицію з 3D координат
        val screenX = windowData.positionX * 100f // Пікселі
        val screenY = windowData.positionY * 100f
        val scale = windowData.scale

        layoutParams.width = (400 * scale).toInt()
        layoutParams.height = (225 * scale).toInt() // 16:9 aspect ratio
        layoutParams.leftMargin = screenX.toInt()
        layoutParams.topMargin = screenY.toInt()

        view.layoutParams = layoutParams
        view.alpha = if (windowData.isVisible) 1f else 0f

        // Застосовуємо обертання (спрощена версія)
        view.rotationX = windowData.rotationX
        view.rotationY = windowData.rotationY
        view.rotation = windowData.rotationZ
    }

    /**
     * Видаляє YouTube вікно
     */
    fun removeYoutubeWindow(windowId: String) {
        youtubePlayers[windowId]?.pause()
        youtubePlayers.remove(windowId)
        youtubeViews[windowId]?.let { view ->
            view.release()
            youtubeViews.remove(windowId)
        }
    }

    /**
     * Отримує YouTube player для вікна
     */
    fun getPlayer(windowId: String): YouTubePlayer? {
        return youtubePlayers[windowId]
    }

    /**
     * Відтворює наступне відео в плейлисті
     */
    fun playNextVideo(windowId: String) {
        youtubePlayers[windowId]?.let { player ->
            // YouTube IFrame API не підтримує прямий перехід до наступного відео
            // Але можна спробувати через playNextVideo() якщо доступно
            try {
                // Для YouTube IFrame player потрібно використовувати JavaScript ін'єкцію
                // Тимчасово використовуємо обхідний шлях
                player.playNextVideo()
            } catch (e: Exception) {
                // Якщо не підтримується, можна показати повідомлення
            }
        }
    }

    /**
     * Відтворює попереднє відео в плейлисті
     */
    fun playPreviousVideo(windowId: String) {
        youtubePlayers[windowId]?.let { player ->
            try {
                player.playPreviousVideo()
            } catch (e: Exception) {
                // Якщо не підтримується
            }
        }
    }

    /**
     * Пропускає рекламу (якщо можливо)
     */
    fun skipAd(windowId: String) {
        youtubePlayers[windowId]?.let { player ->
            // YouTube IFrame API обмежує можливість пропуску реклами
            // Але можна спробувати через seekTo (перемотати далі)
            // Або використати playNextVideo() якщо реклама в плейлисті
            try {
                // Спробуємо перемотати на 5 секунд вперед
                // currentTime може не бути доступним, тому використовуємо загальний підхід
                player.playNextVideo() // Якщо реклама - може перейти до наступного
            } catch (e: Exception) {
                // Альтернатива: перемотати вперед (якщо currentTime доступний)
                try {
                    player.seekTo(5f)
                } catch (e2: Exception) {
                    // Якщо не вдалося
                }
            }
        }
    }

    /**
     * Повторює поточне відео
     */
    fun replay(windowId: String) {
        youtubePlayers[windowId]?.let { player ->
            try {
                player.seekTo(0f)
                player.play()
            } catch (e: Exception) {
                // Якщо не вдалося
            }
        }
    }

    /**
     * Поставить лайк (якщо можливо через API)
     */
    fun likeVideo(windowId: String) {
        youtubePlayers[windowId]?.let { player ->
            // YouTube IFrame API не підтримує пряме керування лайками
            // Потрібна авторизація через YouTube Data API
            // Тимчасово це залишаємо для майбутньої реалізації
        }
    }

    /**
     * Поставить дизлайк (якщо можливо через API)
     */
    fun dislikeVideo(windowId: String) {
        youtubePlayers[windowId]?.let { player ->
            // Аналогічно likeVideo - потребує YouTube Data API
        }
    }

    /**
     * Підписатися на канал (якщо можливо через API)
     */
    fun subscribeToChannel(windowId: String) {
        youtubePlayers[windowId]?.let { player ->
            // Потребує YouTube Data API з авторизацією
        }
    }

    /**
     * Виділяє video ID з YouTube URL
     */
    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            "(?:youtube\\.com\\/watch\\?v=|youtu\\.be\\/)([a-zA-Z0-9_-]{11})".toRegex(),
            "youtube\\.com\\/embed\\/([a-zA-Z0-9_-]{11})".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Очищає всі ресурси
     */
    fun release() {
        youtubePlayers.values.forEach { it.pause() }
        youtubeViews.values.forEach { it.release() }
        youtubePlayers.clear()
        youtubeViews.clear()
    }
}

