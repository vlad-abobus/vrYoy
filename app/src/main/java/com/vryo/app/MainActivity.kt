package com.vryo.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vryo.app.controller.InputController
import com.vryo.app.controller.VoiceController
import com.vryo.app.model.OverlayWindowData
import com.vryo.app.model.Vector3
import com.vryo.app.renderer.CameraRenderer
import com.vryo.app.renderer.Overlay3D
import com.vryo.app.renderer.YoutubeRenderer
import com.vryo.app.utils.PermissionsHelper
import com.vryo.app.utils.SensorHelper
import com.vryo.app.viewmodel.VrViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Головна Activity для VR додатку
 * Інтегрує всі компоненти: камера, YouTube overlay, 3D проєктування, input, voice
 */
class MainActivity : AppCompatActivity() {
    
    private val viewModel: VrViewModel by viewModels()
    
    // Компоненти
    private lateinit var cameraRenderer: CameraRenderer
    private lateinit var youtubeRenderer: YoutubeRenderer
    private lateinit var overlay3D: Overlay3D
    private lateinit var inputController: InputController
    private lateinit var voiceController: VoiceController
    private lateinit var sensorHelper: SensorHelper
    
    // UI елементи
    private lateinit var cameraContainer: FrameLayout
    private lateinit var overlayContainer: FrameLayout
    private lateinit var hudContainer: LinearLayout
    private lateinit var hudTime: TextView
    private lateinit var hudBattery: TextView
    private lateinit var hudVideo: TextView
    private lateinit var gazeIndicator: View
    private lateinit var statusText: TextView
    private lateinit var controllerHint: TextView
    
    // Оновлення UI
    private val updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private val UPDATE_INTERVAL_MS = 16L // ~60 FPS
    
    // Battery receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (scale > 0) (level * 100 / scale) else -1
            updateBatteryLevel(batteryPct)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        checkPermissions()
    }
    
    private fun initializeViews() {
        cameraContainer = findViewById(R.id.cameraContainer)
        overlayContainer = findViewById(R.id.overlayContainer)
        hudContainer = findViewById(R.id.hudContainer)
        hudTime = findViewById(R.id.hudTime)
        hudBattery = findViewById(R.id.hudBattery)
        hudVideo = findViewById(R.id.hudVideo)
        gazeIndicator = findViewById(R.id.gazeIndicator)
        statusText = findViewById(R.id.statusText)
        controllerHint = findViewById(R.id.controllerHint)
    }
    
    private fun checkPermissions() {
        if (!PermissionsHelper.hasCameraPermission(this)) {
            PermissionsHelper.requestCameraPermission(this)
            return
        }
        
        if (!PermissionsHelper.hasAudioPermission(this)) {
            PermissionsHelper.requestAudioPermission(this)
            return
        }
        
        initializeComponents()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PermissionsHelper.CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    checkPermissions()
                } else {
                    showPermissionDialog(
                        getString(R.string.camera_permission_title),
                        getString(R.string.camera_permission_message)
                    )
                }
            }
            PermissionsHelper.AUDIO_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    checkPermissions()
                } else {
                    showPermissionDialog(
                        getString(R.string.audio_permission_title),
                        getString(R.string.audio_permission_message)
                    )
                }
            }
        }
    }
    
    private fun showPermissionDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                checkPermissions()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                finish()
            }
            .show()
    }
    
    private fun initializeComponents() {
        // Ініціалізуємо компоненти
        overlay3D = Overlay3D()
        cameraRenderer = CameraRenderer(this, cameraContainer, this)
        youtubeRenderer = YoutubeRenderer(this, overlayContainer)
        inputController = InputController(overlay3D)
        voiceController = VoiceController(this)
        sensorHelper = SensorHelper(this)
        
        // Налаштування ViewModel
        viewModel.voiceController = voiceController
        viewModel.youtubeRendererCallback = { windowId, action ->
            action(youtubeRenderer)
        }
        
        // Callbacks для InputController
        inputController.onWindowSelected = { window ->
            viewModel.selectWindow(window.id)
        }
        inputController.onWindowDeselected = {
            viewModel.selectWindow(null)
        }
        inputController.onDoubleTap = {
            viewModel.toggleViewMode()
            showStatus("Mode: ${viewModel.viewMode.value}")
        }
        
        // Callbacks для VoiceController
        voiceController.onCommandRecognized = { command, parameter ->
            viewModel.handleVoiceCommand(command, parameter)
            showStatus("Voice: $command ${parameter ?: ""}")
        }
        
        // Запускаємо компоненти
        cameraRenderer.initialize()
        sensorHelper.start()
        voiceController.startListening()
        
        // Завантажуємо збережені вікна
        viewModel.loadWindows(this)
        
        // Підписуємось на зміни стану
        subscribeToViewModel()
        
        // Запускаємо оновлення UI
        startUpdateLoop()
        
        // Реєструємо battery receiver
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        // Показуємо підказку про контролер
        controllerHint.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            controllerHint.visibility = View.GONE
        }, 5000)
    }
    
    private fun subscribeToViewModel() {
        lifecycleScope.launch {
            viewModel.viewMode.collect { mode ->
                updateViewMode(mode)
            }
        }
        
        lifecycleScope.launch {
            viewModel.overlayWindows.collect { windows ->
                updateOverlayWindows(windows)
            }
        }
        
        lifecycleScope.launch {
            viewModel.hudVisible.collect { visible ->
                hudContainer.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }
        
        lifecycleScope.launch {
            viewModel.selectedWindowId.collect { windowId ->
                // Оновлюємо візуальний стан обраного вікна
            }
        }
    }
    
    private fun updateViewMode(mode: VrViewModel.ViewMode) {
        when (mode) {
            VrViewModel.ViewMode.CAMERA_ONLY -> {
                cameraContainer.visibility = View.VISIBLE
                overlayContainer.visibility = View.GONE
            }
            VrViewModel.ViewMode.CAMERA_YOUTUBE -> {
                cameraContainer.visibility = View.VISIBLE
                overlayContainer.visibility = View.VISIBLE
            }
            VrViewModel.ViewMode.YOUTUBE_ONLY -> {
                cameraContainer.visibility = View.GONE
                overlayContainer.visibility = View.VISIBLE
            }
        }
    }
    
    private fun updateOverlayWindows(windows: List<OverlayWindowData>) {
        // Видаляємо видалені вікна
        val windowIds = windows.map { it.id }.toSet()
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until overlayContainer.childCount) {
            val view = overlayContainer.getChildAt(i)
            val tag = view.tag as? String
            if (tag != null && tag !in windowIds) {
                viewsToRemove.add(view)
            }
        }
        viewsToRemove.forEach { view ->
            val id = view.tag as? String
            overlayContainer.removeView(view)
            id?.let { youtubeRenderer.removeYoutubeWindow(it) }
        }
        
        // Додаємо/оновлюємо вікна
        windows.forEach { window ->
            var existingView: View? = null
            for (i in 0 until overlayContainer.childCount) {
                val view = overlayContainer.getChildAt(i)
                if (view.tag == window.id) {
                    existingView = view
                    break
                }
            }
            
            if (existingView == null) {
                val view = youtubeRenderer.createYoutubeWindow(window)
                overlayContainer.addView(view)
            } else {
                // Оновлюємо transform
                updateWindowTransform(existingView, window)
            }
        }
    }
    
    private fun updateWindowTransform(view: View, window: OverlayWindowData) {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val headRotation = sensorHelper.headRotation.value
        val eyeOffset = Overlay3D.INTEROCULAR_DISTANCE / 2f
        
        // Оновлюємо через YoutubeRenderer
        youtubeRenderer.updateWindowTransform(view, window, FloatArray(16), eyeOffset)
    }
    
    private fun startUpdateLoop() {
        updateRunnable = object : Runnable {
            override fun run() {
                updateFrame()
                updateHandler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
        updateHandler.post(updateRunnable!!)
    }
    
    private fun updateFrame() {
        // Оновлюємо gaze
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val headRotation = sensorHelper.headRotation.value
        val headPosition = sensorHelper.headPosition.value
        
        inputController.updateGaze(headRotation, headPosition, screenWidth, screenHeight)
        
        // Оновлюємо позицію gaze indicator
        val gazePos = inputController.getGazePosition()
        (gazeIndicator.layoutParams as? FrameLayout.LayoutParams)?.apply {
            leftMargin = gazePos.x.toInt() - gazeIndicator.width / 2
            topMargin = gazePos.y.toInt() - gazeIndicator.height / 2
        }
        
        // Оновлюємо drag/resize
        if (inputController.isDragging()) {
            val forward = sensorHelper.getForwardVector()
            inputController.updateDrag(headRotation, headPosition, forward)
        }
        if (inputController.isResizing()) {
            inputController.updateResize(headPosition)
        }
        
        // Оновлюємо всі вікна
        viewModel.overlayWindows.value.forEach { window ->
            var view: View? = null
            for (i in 0 until overlayContainer.childCount) {
                val v = overlayContainer.getChildAt(i)
                if (v.tag == window.id) {
                    view = v
                    break
                }
            }
            view?.let { updateWindowTransform(it, window) }
        }
        
        // Оновлюємо HUD
        updateHud()
    }
    
    private fun updateHud() {
        // Час
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        hudTime.text = getString(R.string.hud_time, timeFormat.format(Date()))
        
        // Відео інформація
        viewModel.selectedWindowId.value?.let { windowId ->
            val window = viewModel.overlayWindows.value.firstOrNull { it.id == windowId }
            window?.url?.let { url ->
                hudVideo.text = getString(R.string.hud_video, url.take(30))
            }
        }
    }
    
    private fun updateBatteryLevel(level: Int) {
        if (level >= 0) {
            hudBattery.text = getString(R.string.hud_battery, level)
        }
    }
    
    private fun showStatus(message: String) {
        statusText.text = message
        statusText.visibility = View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            statusText.visibility = View.GONE
        }, 2000)
    }
    
    // Обробка подвійного тапу на екрані (fallback для Cardboard button)
    private var lastTapTime = 0L
    private val DOUBLE_TAP_TIMEOUT = 300L
    
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                // Подвійний тап
                viewModel.toggleViewMode()
                showStatus("Mode: ${viewModel.viewMode.value}")
            } else {
                // Одиночний тап
                inputController.onButtonDown(
                    viewModel.overlayWindows.value,
                    sensorHelper.headRotation.value,
                    sensorHelper.headPosition.value,
                    resources.displayMetrics.widthPixels,
                    resources.displayMetrics.heightPixels
                )
            }
            lastTapTime = currentTime
        } else if (event?.action == MotionEvent.ACTION_UP) {
            inputController.onButtonUp()
        }
        return true
    }
    
    override fun onPause() {
        super.onPause()
        updateRunnable?.let { updateHandler.removeCallbacks(it) }
        sensorHelper.stop()
        voiceController.stopListening()
    }
    
    override fun onResume() {
        super.onResume()
        sensorHelper.start()
        voiceController.startListening()
        startUpdateLoop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { updateHandler.removeCallbacks(it) }
        unregisterReceiver(batteryReceiver)
        cameraRenderer.release()
        youtubeRenderer.release()
        inputController.release()
        voiceController.release()
        sensorHelper.stop()
        viewModel.saveWindows(this)
    }
}

