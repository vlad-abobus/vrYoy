package com.vryo.app.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import com.vryo.app.model.Quaternion
import com.vryo.app.model.Vector3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Helper клас для обробки датчиків руху (gyroscope, accelerometer, rotation vector)
 * Конвертує дані в rotation matrix та quaternion для 3D проєктування
 */
class SensorHelper(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // Rotation Vector sensor (найточніший для VR)
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    
    // Матриця обертання для камери (16 floats)
    private val rotationMatrix = FloatArray(16)
    private val orientationAngles = FloatArray(3)
    
    // StateFlow для підписок на зміни
    private val _headRotation = MutableStateFlow(Quaternion.identity())
    val headRotation: StateFlow<Quaternion> = _headRotation
    
    private val _headPosition = MutableStateFlow(Vector3(0f, 0f, 0f))
    val headPosition: StateFlow<Vector3> = _headPosition
    
    private var lastTimestamp = 0L
    private var lastPosition = Vector3(0f, 0f, 0f)
    
    init {
        Matrix.setIdentityM(rotationMatrix, 0)
    }

    /**
     * Запускає відстеження датчиків
     */
    fun start() {
        rotationVectorSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME // ~50Hz, баланс між точністю та battery
            )
        }
    }

    /**
     * Зупиняє відстеження датчиків
     */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // Конвертуємо rotation vector в rotation matrix
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Конвертуємо matrix в quaternion для зручності
        val quaternion = matrixToQuaternion(rotationMatrix)
        _headRotation.value = quaternion

        // Обчислюємо зміну позиції на основі обертання та часу
        updateHeadPosition(event.timestamp)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Ignore accuracy changes
    }

    /**
     * Конвертує rotation matrix в quaternion
     */
    private fun matrixToQuaternion(matrix: FloatArray): Quaternion {
        val trace = matrix[0] + matrix[5] + matrix[10]
        return when {
            trace > 0f -> {
                val s = kotlin.math.sqrt(trace + 1f) * 2f
                Quaternion(
                    w = 0.25f * s,
                    x = (matrix[6] - matrix[9]) / s,
                    y = (matrix[8] - matrix[2]) / s,
                    z = (matrix[1] - matrix[4]) / s
                )
            }
            matrix[0] > matrix[5] && matrix[0] > matrix[10] -> {
                val s = kotlin.math.sqrt(1f + matrix[0] - matrix[5] - matrix[10]) * 2f
                Quaternion(
                    w = (matrix[6] - matrix[9]) / s,
                    x = 0.25f * s,
                    y = (matrix[4] + matrix[1]) / s,
                    z = (matrix[8] + matrix[2]) / s
                )
            }
            matrix[5] > matrix[10] -> {
                val s = kotlin.math.sqrt(1f + matrix[5] - matrix[0] - matrix[10]) * 2f
                Quaternion(
                    w = (matrix[8] - matrix[2]) / s,
                    x = (matrix[4] + matrix[1]) / s,
                    y = 0.25f * s,
                    z = (matrix[9] + matrix[6]) / s
                )
            }
            else -> {
                val s = kotlin.math.sqrt(1f + matrix[10] - matrix[0] - matrix[5]) * 2f
                Quaternion(
                    w = (matrix[1] - matrix[4]) / s,
                    x = (matrix[8] + matrix[2]) / s,
                    y = (matrix[9] + matrix[6]) / s,
                    z = 0.25f * s
                )
            }
        }
    }

    /**
     * Оновлює позицію голови на основі обертання (для resize через forward/backward movement)
     */
    private fun updateHeadPosition(timestamp: Long) {
        if (lastTimestamp == 0L) {
            lastTimestamp = timestamp
            return
        }

        val deltaTime = (timestamp - lastTimestamp) / 1_000_000_000f // секунди
        lastTimestamp = timestamp

        // Приблизна оцінка руху вперед/назад на основі зміни обертання
        // У реальному застосунку можна використовувати допоміжний акселерометр
        val currentPos = _headPosition.value
        _headPosition.value = currentPos // Поки що статична позиція
        lastPosition = currentPos
    }

    /**
     * Отримує поточну rotation matrix
     */
    fun getRotationMatrix(): FloatArray {
        return rotationMatrix.copyOf()
    }

    /**
     * Отримує forward vector (напрямок погляду)
     */
    fun getForwardVector(): Vector3 {
        val matrix = getRotationMatrix()
        // Forward vector - це третя колонка матриці (z-axis)
        return Vector3(
            x = -matrix[8],
            y = -matrix[9],
            z = -matrix[10]
        ).normalize()
    }
}

