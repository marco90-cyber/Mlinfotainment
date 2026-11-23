package com.marco90.mlinfotainment.core

/** Dice se la schermata è già aperta (per non riaprirla sopra se stessa). */
object AppState {
    @Volatile var visible: Boolean = false
}
