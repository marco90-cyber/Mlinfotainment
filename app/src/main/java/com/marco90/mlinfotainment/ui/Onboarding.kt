package com.marco90.mlinfotainment.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.marco.mltheme.MLChoiceRow
import com.marco.mltheme.MLDependent
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSettingsScaffold
import com.marco.mltheme.MLSwitchRow
import com.marco90.mlinfotainment.core.Triggers
import com.marco90.mlinfotainment.data.CarSettings
import com.marco90.mlinfotainment.data.Module

/** Prima apertura: benvenuto, permessi, prime scelte. */
@Composable
fun OnboardingHost() {
    var step by remember { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    when (step) {
        0 -> WelcomeStep { step = 1 }
        1 -> PermissionsPage(
            onBack = { step = 0 },
            title = "1 di 2 · Permessi",
            footer = {
                MLGroup("Quando hai finito") {
                    MLNavRow("Avanti", "Quelli che salti li puoi dare dopo dalle impostazioni", icon = Icons.Rounded.ArrowForward) { step = 2 }
                }
            }
        )
        else -> ConfigStep(onBack = { step = 1 })
    }
}

@Composable
private fun WelcomeStep(next: () -> Unit) {
    MLSettingsScaffold("Benvenuto", {}) {
        MLGroup("MLinfotainment") {
            MLNavRow(
                "La tua schermata per l'auto",
                "Tachimetro, musica, meteo, navigatore e widget. Due passaggi veloci: permessi e prime scelte.",
                icon = Icons.Rounded.DirectionsCar
            ) { }
            MLNavRow("Inizia", null, icon = Icons.Rounded.ArrowForward) { next() }
        }
    }
}

@Composable
private fun ConfigStep(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    var showFav by remember { mutableStateOf(false) }
    var showBt by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }

    MLSettingsScaffold("2 di 2 · Prime scelte", onBack) {
        MLGroup("Navigazione") {
            MLChoiceRow(
                "Navigatore",
                Navigators.all.map { it.key to (it.label + if (Navigators.installed(ctx, it)) "" else " (non installato)") },
                s.navigator
            ) { s.navigator = it }
            MLNavRow("Indirizzo di casa", s.homeAddress.ifBlank { "Non impostato" }, icon = Icons.Rounded.Home) { editing = "home" }
            MLNavRow("Indirizzo di lavoro", s.workAddress.ifBlank { "Non impostato" }, icon = Icons.Rounded.Work) { editing = "work" }
            MLSwitchRow(
                "Navigatore a fianco", "Schermo diviso: schermata auto e navigatore affiancati",
                s.sideBySide, icon = Icons.Rounded.Navigation
            ) { s.sideBySide = it }
            MLChoiceRow("Unità di velocità", listOf("kmh" to "km/h", "mph" to "mph"), s.unit) { s.unit = it }
        }
        MLGroup("Apertura automatica") {
            MLSwitchRow("Apri da solo quando guidi", "Con i segnali qui sotto", s.autoStart, icon = Icons.Rounded.DirectionsCar) {
                s.autoStart = it
                Triggers.syncAll(ctx)
            }
            MLDependent(s.autoStart) {
                MLSwitchRow("Bluetooth dell'auto", "Quando il telefono si collega all'auto", s.trigBt, icon = Icons.Rounded.Bluetooth) { s.trigBt = it }
                MLDependent(s.trigBt) {
                    MLNavRow(
                        "Scegli l'auto",
                        if (s.btList.isEmpty()) "Nessuna scelta" else "${s.btList.size} scelti",
                        icon = Icons.Rounded.DirectionsCar
                    ) { showBt = true }
                }
                MLSwitchRow("Riconoscimento guida", "Google capisce che sei in auto", s.trigActivity, icon = Icons.Rounded.Sensors) {
                    s.trigActivity = it
                    Triggers.syncAll(ctx)
                }
                MLSwitchRow("Velocità GPS", "Consuma un po' più di batteria", s.trigGps, icon = Icons.Rounded.Speed) {
                    s.trigGps = it
                    Triggers.syncAll(ctx)
                }
            }
        }
        MLGroup("Cosa mostrare") {
            Module.entries.forEach { m ->
                MLSwitchRow(m.title, null, s.enabled(m)) { s.setEnabled(m, it) }
            }
            MLNavRow("App preferite", "Fino a 4, in grande", icon = Icons.Rounded.Apps) { showFav = true }
        }
        MLGroup("Fatto") {
            MLNavRow(
                "Apri la schermata", "Sposta e ridimensiona tenendo premuto, o dal tasto Layout",
                icon = Icons.Rounded.Check
            ) {
                s.onboardingDone = true
                Triggers.syncAll(ctx)
            }
        }
    }

    if (showFav) AppPickerDialog(pick = true) { showFav = false }
    if (showBt) BtDevicesDialog { showBt = false }
    val which = editing
    if (which != null) {
        AddressDialog(
            title = if (which == "home") "Indirizzo di casa" else "Indirizzo di lavoro",
            initial = if (which == "home") s.homeAddress else s.workAddress,
            onDismiss = { editing = null },
            onSave = { v -> if (which == "home") s.homeAddress = v else s.workAddress = v; editing = null }
        )
    }
}
