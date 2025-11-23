package com.vryo.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.vryo.app.model.OverlayWindowData

object Persistence {
    private const val PREFS_NAME = "vrYo_prefs"
    private const val KEY_WINDOW_COUNT = "window_count"
    private const val KEY_WINDOW_PREFIX = "window_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Зберігає позиції всіх вікон
     * Використовує простий формат: кожне вікно зберігається окремо через SharedPreferences
     */
    fun saveWindows(context: Context, windows: List<OverlayWindowData>) {
        val prefs = getPrefs(context).edit()
        prefs.putInt(KEY_WINDOW_COUNT, windows.size)
        
        windows.forEachIndexed { index, window ->
            val prefix = "$KEY_WINDOW_PREFIX$index"
            prefs.putString("${prefix}_id", window.id)
            prefs.putFloat("${prefix}_x", window.positionX)
            prefs.putFloat("${prefix}_y", window.positionY)
            prefs.putFloat("${prefix}_z", window.positionZ)
            prefs.putFloat("${prefix}_rx", window.rotationX)
            prefs.putFloat("${prefix}_ry", window.rotationY)
            prefs.putFloat("${prefix}_rz", window.rotationZ)
            prefs.putFloat("${prefix}_scale", window.scale)
            prefs.putString("${prefix}_url", window.url)
            prefs.putBoolean("${prefix}_visible", window.isVisible)
        }
        
        prefs.apply()
    }

    /**
     * Завантажує збережені позиції вікон
     */
    fun loadWindows(context: Context): List<OverlayWindowData> {
        val prefs = getPrefs(context)
        val count = prefs.getInt(KEY_WINDOW_COUNT, 0)
        val windows = mutableListOf<OverlayWindowData>()
        
        for (i in 0 until count) {
            val prefix = "$KEY_WINDOW_PREFIX$i"
            val id = prefs.getString("${prefix}_id", null) ?: continue
            
            windows.add(
                OverlayWindowData(
                    id = id,
                    positionX = prefs.getFloat("${prefix}_x", 0f),
                    positionY = prefs.getFloat("${prefix}_y", 0f),
                    positionZ = prefs.getFloat("${prefix}_z", -2.0f),
                    rotationX = prefs.getFloat("${prefix}_rx", 0f),
                    rotationY = prefs.getFloat("${prefix}_ry", 0f),
                    rotationZ = prefs.getFloat("${prefix}_rz", 0f),
                    scale = prefs.getFloat("${prefix}_scale", 1.0f),
                    url = prefs.getString("${prefix}_url", null),
                    isVisible = prefs.getBoolean("${prefix}_visible", true)
                )
            )
        }
        
        return windows
    }

    /**
     * Зберігає окреме вікно
     */
    fun saveWindow(context: Context, window: OverlayWindowData) {
        val windows = loadWindows(context).toMutableList()
        val index = windows.indexOfFirst { it.id == window.id }
        if (index >= 0) {
            windows[index] = window
        } else {
            windows.add(window)
        }
        saveWindows(context, windows)
    }

    /**
     * Видаляє збережене вікно
     */
    fun deleteWindow(context: Context, windowId: String) {
        val windows = loadWindows(context).toMutableList()
        windows.removeAll { it.id == windowId }
        saveWindows(context, windows)
    }
}

