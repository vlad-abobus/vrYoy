package com.vryo.app.renderer

import android.opengl.Matrix
import android.view.View
import com.vryo.app.model.OverlayWindowData
import com.vryo.app.model.Quaternion
import com.vryo.app.model.Vector3

/**
 * Клас для 3D проєктування overlay вікон
 * Виконує проєктування 3D позицій у 2D екранні координати з урахуванням стерео offset
 */
class Overlay3D {
    companion object {
        const val INTEROCULAR_DISTANCE = 0.063f // 63mm між очима (стандарт)
        const val DEFAULT_DEPTH = -2.0f // Відстань від камери в метрах
    }

    // Проєкційні матриці для лівого та правого ока
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    /**
     * Обчислює проєкційну матрицю для ока
     * @param eyeOffset зміщення ока (±INTEROCULAR_DISTANCE/2)
     * @param aspectRatio співвідношення сторін екрану
     * @param nearPlane ближня площина проєкції
     * @param farPlane дальня площина проєкції
     * @param fov field of view (градуси)
     */
    fun calculateProjectionMatrix(
        eyeOffset: Float,
        aspectRatio: Float,
        nearPlane: Float = 0.1f,
        farPlane: Float = 100f,
        fov: Float = 90f
    ): FloatArray {
        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val top = nearPlane * kotlin.math.tan(fovRad / 2f)
        val bottom = -top
        val left = bottom * aspectRatio
        val right = top * aspectRatio

        Matrix.frustumM(
            projectionMatrix, 0,
            left, right, bottom, top,
            nearPlane, farPlane
        )

        // Застосовуємо зміщення ока
        Matrix.translateM(projectionMatrix, 0, -eyeOffset, 0f, 0f)

        return projectionMatrix
    }

    /**
     * Проєктує 3D точку в 2D екранні координати
     * @param worldPos 3D позиція у світових координатах
     * @param headRotation обертання голови (quaternion)
     * @param eyeOffset зміщення ока
     * @param screenWidth ширина екрану
     * @param screenHeight висота екрану
     * @return екранні координати (x, y) та глибина (z)
     */
    fun projectToScreen(
        worldPos: Vector3,
        headRotation: Quaternion,
        eyeOffset: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Vector3 {
        // Створюємо view matrix з обертання голови
        val viewMatrix = quaternionToMatrix(headRotation)
        
        // Застосовуємо зміщення ока
        Matrix.translateM(viewMatrix, 0, -eyeOffset, 0f, 0f)

        // Model matrix (позиція вікна)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, worldPos.x, worldPos.y, worldPos.z)

        // MVP matrix
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0)

        // Проєктуємо точку
        val point = floatArrayOf(0f, 0f, 0f, 1f) // центр вікна
        Matrix.multiplyMV(point, 0, mvpMatrix, 0, point, 0)

        // Нормалізуємо однорідні координати
        if (point[3] != 0f) {
            point[0] /= point[3]
            point[1] /= point[3]
            point[2] /= point[3]
        }

        // Конвертуємо в екранні координати
        val screenX = (point[0] + 1f) * 0.5f * screenWidth
        val screenY = (1f - point[1]) * 0.5f * screenHeight

        return Vector3(screenX, screenY, point[2])
    }

    /**
     * Конвертує quaternion в rotation matrix
     */
    private fun quaternionToMatrix(quaternion: Quaternion): FloatArray {
        val matrix = FloatArray(16)
        Matrix.setRotateM(
            matrix, 0,
            quaternionToEulerAngle(quaternion) * 180f / kotlin.math.PI.toFloat(),
            0f, 1f, 0f // Y-axis rotation (heading)
        )
        return matrix
    }

    /**
     * Конвертує quaternion в Euler angle (спрощена версія)
     */
    private fun quaternionToEulerAngle(q: Quaternion): Float {
        // Yaw angle (обертання навколо Y-осі)
        val sinYaw = 2f * (q.w * q.y - q.x * q.z)
        val cosYaw = 1f - 2f * (q.y * q.y + q.z * q.z)
        return kotlin.math.atan2(sinYaw, cosYaw)
    }

    /**
     * Обчислює перетин ray (променя погляду) з площиною на заданій глибині
     * Використовується для drag операцій
     */
    fun projectRayToDepth(
        cameraOrigin: Vector3,
        forwardVector: Vector3,
        depth: Float
    ): Vector3 {
        // Перетин ray з площиною z = depth
        val t = (depth - cameraOrigin.z) / forwardVector.z
        return Vector3(
            cameraOrigin.x + forwardVector.x * t,
            cameraOrigin.y + forwardVector.y * t,
            depth
        )
    }

    /**
     * Лінійна інтерполяція (lerp) для плавного переміщення
     */
    fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t.coerceIn(0f, 1f)
    }

    /**
     * Лінійна інтерполяція для Vector3
     */
    fun lerpVector(start: Vector3, end: Vector3, t: Float): Vector3 {
        return Vector3(
            lerp(start.x, end.x, t),
            lerp(start.y, end.y, t),
            lerp(start.z, end.z, t)
        )
    }
}

