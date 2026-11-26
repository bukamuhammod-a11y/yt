package com.example.service

import android.content.Context
import android.content.SharedPreferences

class AssistivePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("assistive_touch_prefs", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean("key_is_enabled", false)
        set(value) = prefs.edit().putBoolean("key_is_enabled", value).apply()

    var opacity: Float
        get() = prefs.getFloat("key_opacity", 0.65f)
        set(value) = prefs.edit().putFloat("key_opacity", value).apply()

    var sizeDp: Int
        get() = prefs.getInt("key_size_dp", 54)
        set(value) = prefs.edit().putInt("key_size_dp", value).apply()

    var overlayBrightness: Float
        get() = prefs.getFloat("key_overlay_brightness", 0.8f)
        set(value) = prefs.edit().putFloat("key_overlay_brightness", value).apply()
}
