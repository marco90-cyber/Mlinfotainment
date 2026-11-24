package com.marco90.mlinfotainment.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.marco.mltheme.MLThemeSettings
import com.marco90.mlinfotainment.data.CarSettings

/**
 * Schermata di guida in stile Android Auto (giorno/notte come da MLTheme);
 * le impostazioni usano i componenti di MLTheme.
 */
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val view = LocalView.current
    val s = remember { CarSettings.get(ctx) }
    val ml = remember { MLThemeSettings.get(ctx) }
    val dark = when (ml.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    var settingsOpen by remember { mutableStateOf(false) }
    val onboarding = !s.onboardingDone
    val settings = settingsOpen || onboarding
    val keepOn = s.keepOn
    val immersive = s.immersive

    DisposableEffect(settings, keepOn, immersive, dark) {
        val activity = ctx as? ComponentActivity
        if (activity != null) {
            val w = activity.window
            if (!settings && keepOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val ctl = WindowCompat.getInsetsController(w, view)
            ctl.isAppearanceLightStatusBars = !dark
            ctl.isAppearanceLightNavigationBars = !dark
            if (!settings && immersive) {
                ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctl.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                ctl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    CompositionLocalProvider(LocalAuto provides (if (dark) DarkAuto else LightAuto)) {
        if (onboarding) OnboardingHost()
        else if (settingsOpen) SettingsHost { settingsOpen = false }
        else Dashboard { settingsOpen = true }
    }
}
