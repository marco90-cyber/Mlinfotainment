package com.marco90.mlinfotainment.core

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Serve solo a dare il comando "schermo diviso" (Android non lo permette alle app normali).
 * Non legge né il contenuto dello schermo né gli eventi.
 */
class SplitService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        @Volatile var instance: SplitService? = null
    }
}
