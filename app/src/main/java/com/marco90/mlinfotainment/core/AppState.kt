package com.marco90.mlinfotainment.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Dice se la schermata è già aperta (per non riaprirla sopra se stessa). */
object AppState {
    @Volatile var visible: Boolean = false
}

/** Richieste dalla schermata delle impostazioni alla schermata principale. */
object UiRequests {
    var edit by mutableStateOf(false)
}
