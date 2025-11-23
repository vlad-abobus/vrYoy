package com.vryo.app.controller

import android.view.MotionEvent
import android.view.View
import com.vryo.app.model.OverlayWindowData
import com.vryo.app.model.Quaternion
import com.vryo.app.model.Vector3
import com.vryo.app.renderer.Overlay3D
import kotlinx.coroutines.*
import kotlin.math.sqrt

/**
 * Контролер для обробки вхідних подій: gaze, dwell, drag, resize
 */
class InputController(
    private val overlay3D: Overlay3D
) {
    // Gaze tracking
    private var gazePosition = Vector3(0f, 0f, 0f) // Екранні координати прицілу
    private var lastGazeUpdate = 0L
    
    // Dwell tracking
    private var dwellStartTime = 0L
    private var dwellTargetWindow: OverlayWindowData? = null
    private val DWELL_DURATION_MS = 900L // 900ms для активації
    
    // Drag tracking
    private var isDragging = false
    private var draggedWindow: OverlayWindowData? = null
    private var grabDepth = 0f
    private var dragStartPosition = Vector3()
    
    // Resize tracking
    private var isResizing = false
    private var resizeStartDepth = 0f
    private var resizeStartScale = 1f
    
    // Button state
    private var isButtonPressed = false
    private var lastButtonPressTime = 0L
    private val DOUBLE_TAP_THRESHOLD_MS = 300L
    
    // Callbacks
    var onWindowSelected: ((OverlayWindowData) -> Unit)? = null
    var onWindowDeselected: (() -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Оновлює позицію gaze (прицілу) на основі обертання голови
     */
    fun updateGaze(
        headRotation: Quaternion,
        headPosition: Vector3,
        screenWidth: Int,
        screenHeight: Int
    ) {
        val now = System.currentTimeMillis()
        val forward = calculateForwardVector(headRotation)
        
        // Проєктуємо forward vector на екран (центр екрану за замовчуванням)
        val centerWorld = Vector3(0f, 0f, -2f)
        val projected = overlay3D.projectToScreen(
            centerWorld,
            headRotation,
            0f, // без eye offset для gaze
            screenWidth,
            screenHeight
        )
        
        gazePosition = Vector3(
            projected.x,
            projected.y,
            0f
        )
        lastGazeUpdate = now
        
        // Перевіряємо dwell
        checkDwell(headRotation, screenWidth, screenHeight)
    }

    /**
     * Обчислює forward vector з quaternion
     */
    private fun calculateForwardVector(rotation: Quaternion): Vector3 {
        // Forward vector = quaternion * (0, 0, -1)
        val x = 2f * (rotation.x * rotation.z - rotation.w * rotation.y)
        val y = 2f * (rotation.y * rotation.z + rotation.w * rotation.x)
        val z = 1f - 2f * (rotation.x * rotation.x + rotation.y * rotation.y)
        return Vector3(x, y, -z).normalize()
    }

    /**
     * Перевіряє чи gaze потрапляє на вікно та обчислює dwell time
     */
    private fun checkDwell(
        headRotation: Quaternion,
        screenWidth: Int,
        screenHeight: Int
    ) {
        // Реалізація перевірки перетину gaze з вікнами
        // Поки що спрощена версія
    }

    /**
     * Перевіряє чи gaze перетинається з вікном
     */
    fun isGazeHittingWindow(
        window: OverlayWindowData,
        headRotation: Quaternion,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        val windowCenter = Vector3(
            window.positionX,
            window.positionY,
            window.positionZ
        )
        
        val projected = overlay3D.projectToScreen(
            windowCenter,
            headRotation,
            0f,
            screenWidth,
            screenHeight
        )
        
        // Перевіряємо чи gaze в межах вікна (припускаємо розмір вікна)
        val windowSize = 0.5f * window.scale // приблизна ширина вікна в world space
        val dx = gazePosition.x - projected.x
        val dy = gazePosition.y - projected.y
        val distance = sqrt(dx * dx + dy * dy)
        
        return distance < windowSize * 100f // поріг у пікселях
    }

    /**
     * Обробка натискання кнопки (Cardboard tap або контролер)
     */
    fun onButtonDown(
        windows: List<OverlayWindowData>,
        headRotation: Quaternion,
        headPosition: Vector3,
        screenWidth: Int,
        screenHeight: Int
    ) {
        val now = System.currentTimeMillis()
        
        // Перевірка на подвійний тап
        if (now - lastButtonPressTime < DOUBLE_TAP_THRESHOLD_MS) {
            onDoubleTap?.invoke()
            lastButtonPressTime = 0
            return
        }
        lastButtonPressTime = now
        
        isButtonPressed = true
        
        // Знаходимо вікно під gaze
        val hitWindow = windows.firstOrNull { window ->
            window.isVisible && isGazeHittingWindow(window, headRotation, screenWidth, screenHeight)
        }
        
        if (hitWindow != null) {
            // Починаємо drag
            isDragging = true
            draggedWindow = hitWindow
            grabDepth = hitWindow.positionZ
            dragStartPosition = Vector3(hitWindow.positionX, hitWindow.positionY, hitWindow.positionZ)
            onWindowSelected?.invoke(hitWindow)
            
            // Також починаємо resize mode
            isResizing = true
            resizeStartDepth = headPosition.z
            resizeStartScale = hitWindow.scale
        }
    }

    /**
     * Обробка відпускання кнопки
     */
    fun onButtonUp() {
        if (isDragging || isResizing) {
            isDragging = false
            isResizing = false
            draggedWindow = null
            onWindowDeselected?.invoke()
        }
        isButtonPressed = false
    }

    /**
     * Оновлює позицію вікна при drag
     */
    fun updateDrag(
        headRotation: Quaternion,
        headPosition: Vector3,
        forwardVector: Vector3
    ) {
        if (!isDragging || draggedWindow == null) return
        
        // Обчислюємо нову позицію на основі ray проєкції
        val cameraOrigin = headPosition
        val newPosition = overlay3D.projectRayToDepth(
            cameraOrigin,
            forwardVector,
            grabDepth
        )
        
        // Плавне переміщення з lerp
        draggedWindow?.let { window ->
            val currentPos = Vector3(window.positionX, window.positionY, window.positionZ)
            val targetPos = Vector3(newPosition.x, newPosition.y, grabDepth)
            val lerped = overlay3D.lerpVector(currentPos, targetPos, 0.3f) // 30% lerp для плавності
            
            window.positionX = lerped.x
            window.positionY = lerped.y
        }
    }

    /**
     * Оновлює масштаб вікна при resize
     */
    fun updateResize(
        headPosition: Vector3,
        resizeSensitivity: Float = 2.0f
    ) {
        if (!isResizing || draggedWindow == null) return
        
        val deltaZ = resizeStartDepth - headPosition.z // рух вперед = збільшення
        val scaleDelta = deltaZ * resizeSensitivity
        
        draggedWindow?.let { window ->
            val newScale = (resizeStartScale + scaleDelta).coerceIn(0.5f, 3.0f)
            window.scale = newScale
        }
    }

    /**
     * Отримує поточну позицію gaze
     */
    fun getGazePosition(): Vector3 = gazePosition

    /**
     * Отримує чи зараз виконується drag
     */
    fun isDragging(): Boolean = isDragging

    /**
     * Отримує чи зараз виконується resize
     */
    fun isResizing(): Boolean = isResizing

    /**
     * Очищає ресурси
     */
    fun release() {
        coroutineScope.cancel()
        isDragging = false
        isResizing = false
        draggedWindow = null
        dwellTargetWindow = null
    }
}

