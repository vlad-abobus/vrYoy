package com.vryo.app.renderer

import android.content.Context
import android.util.Size
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vryo.app.utils.splitViewForStereo
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Рендерер для камери - split view для лівого та правого ока (stereo)
 */
class CameraRenderer(
    private val context: Context,
    private val container: ViewGroup,
    private val lifecycleOwner: LifecycleOwner
) {
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var leftEyeView: TextureView? = null
    private var rightEyeView: TextureView? = null

    /**
     * Ініціалізує камеру та створює split view
     */
    fun initialize() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                setupCamera()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupCamera() {
        val provider = cameraProvider ?: return

        // Створюємо два TextureView для лівого та правого ока
        container.removeAllViews()

        leftEyeView = TextureView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        rightEyeView = TextureView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Додаємо обидва view в контейнер
        container.addView(leftEyeView)
        container.addView(rightEyeView)

        // Розділяємо view для стерео (50/50 split)
        splitViewForStereo(leftEyeView!!, rightEyeView!!, container)

        // Налаштування Preview UseCase
        val preview = Preview.Builder()
            .setTargetResolution(Size(1920, 1080)) // Full HD для кращої якості
            .build()
        
        // Встановлюємо surface provider для лівого ока
        preview.setSurfaceProvider(leftEyeView!!.surfaceProvider)
        
        // Для правого ока використовуємо той самий preview
        // У реальному застосунку можна використати окрему камеру для кращого стерео
        // splitViewForStereo вже розділив view на дві половини, тому праве око
        // автоматично показує праву половину того ж самого preview

        // Вибираємо задню камеру
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind use cases before rebinding
            provider.unbindAll()

            // Bind use cases to camera
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Отримує TextureView для лівого ока
     */
    fun getLeftEyeView(): TextureView? = leftEyeView

    /**
     * Отримує TextureView для правого ока
     */
    fun getRightEyeView(): TextureView? = rightEyeView

    /**
     * Зупиняє камеру та очищає ресурси
     */
    fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    /**
     * Перевіряє доступність камери
     */
    fun isAvailable(): Boolean {
        return camera != null
    }
}

