package de.spardirekt.agents.pro.diagnostics

import android.util.Log

object DebugLog {
    @Volatile var enabled: Boolean = false
    private const val TAG = "VeoPromptPro"

    fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun e(message: String, t: Throwable? = null) {
        if (enabled) Log.e(TAG, message, t)
    }
}
