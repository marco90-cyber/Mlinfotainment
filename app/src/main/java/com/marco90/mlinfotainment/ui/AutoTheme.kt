package com.marco90.mlinfotainment.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colori della schermata di guida, nello stile di Android Auto (giorno e notte). */
data class AutoColors(
    val bg: Color,
    val card: Color,
    val cardHigh: Color,
    val rail: Color,
    val onCard: Color,
    val sub: Color,
    val accent: Color,
    val onAccent: Color
)

val DarkAuto = AutoColors(
    bg = Color(0xFF0E1013),
    card = Color(0xFF1C2026),
    cardHigh = Color(0xFF2A3038),
    rail = Color(0xFF14171B),
    onCard = Color(0xFFE8EAED),
    sub = Color(0xFF9AA0A6),
    accent = Color(0xFF8AB4F8),
    onAccent = Color(0xFF062E6F)
)

val LightAuto = AutoColors(
    bg = Color(0xFFEBEFF4),
    card = Color(0xFFFFFFFF),
    cardHigh = Color(0xFFE2E7EE),
    rail = Color(0xFFF8FAFC),
    onCard = Color(0xFF1F2328),
    sub = Color(0xFF5F6368),
    accent = Color(0xFF1A73E8),
    onAccent = Color(0xFFFFFFFF)
)

val LocalAuto = staticCompositionLocalOf { DarkAuto }
