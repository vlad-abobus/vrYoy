package com.vryo.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Дані для 3D вікна overlay
 */
@Parcelize
data class OverlayWindowData(
    val id: String,
    var positionX: Float = 0f,
    var positionY: Float = 0f,
    var positionZ: Float = -2.0f, // Відстань від камери в метрах
    var rotationX: Float = 0f,
    var rotationY: Float = 0f,
    var rotationZ: Float = 0f,
    var scale: Float = 1.0f,
    val url: String? = null,
    var isVisible: Boolean = true
) : Parcelable

/**
 * Quaternion для обертання (w, x, y, z)
 */
data class Quaternion(
    val w: Float,
    val x: Float,
    val y: Float,
    val z: Float
) {
    companion object {
        fun identity() = Quaternion(1f, 0f, 0f, 0f)
    }
}

/**
 * 3D Vector
 */
data class Vector3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    fun length(): Float = kotlin.math.sqrt(x * x + y * y + z * z)
    fun normalize(): Vector3 {
        val len = length()
        return if (len > 0.0001f) Vector3(x / len, y / len, z / len) else Vector3(0f, 0f, 1f)
    }
}

