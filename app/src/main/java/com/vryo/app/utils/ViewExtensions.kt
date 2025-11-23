package com.vryo.app.utils

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Розділяє два view для стерео відображення (ліве/праве око)
 * Використовує scale та translation для split view
 */
fun splitViewForStereo(leftView: View, rightView: View, container: ViewGroup) {
    container.post {
        val containerWidth = container.width
        val containerHeight = container.height
        
        if (containerWidth > 0 && containerHeight > 0) {
            val halfWidth = containerWidth / 2f
            
            // Налаштовуємо left view - ліва половина
            (leftView.layoutParams as? FrameLayout.LayoutParams)?.apply {
                width = containerWidth
                height = containerHeight
                leftMargin = 0
                topMargin = 0
            } ?: run {
                leftView.layoutParams = FrameLayout.LayoutParams(
                    containerWidth,
                    containerHeight
                )
            }
            
            // Масштабуємо та зміщуємо left view
            leftView.scaleX = 0.5f
            leftView.scaleY = 1f
            leftView.pivotX = 0f
            leftView.translationX = 0f
            
            // Налаштовуємо right view - права половина
            (rightView.layoutParams as? FrameLayout.LayoutParams)?.apply {
                width = containerWidth
                height = containerHeight
                leftMargin = 0
                topMargin = 0
            } ?: run {
                rightView.layoutParams = FrameLayout.LayoutParams(
                    containerWidth,
                    containerHeight
                )
            }
            
            // Масштабуємо та зміщуємо right view
            rightView.scaleX = 0.5f
            rightView.scaleY = 1f
            rightView.pivotX = containerWidth.toFloat()
            rightView.translationX = halfWidth
        }
    }
}

