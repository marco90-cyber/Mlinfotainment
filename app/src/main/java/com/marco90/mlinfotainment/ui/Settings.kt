package com.marco90.mlinfotainment.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.marco.mltheme.MLChoiceRow
import com.marco.mltheme.MLDependent
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSettingsScaffold
import com.marco.mltheme.MLSliderRow
import com.marco.mltheme.MLSwitchRow
import com.marco.mltheme.MLThemeSettingsGroup
import com.marco90.mlinfotainment.BuildConfig
import com.marco90.mlinfotainment.core.Triggers
import com.marco90.mlinfotainment.embed.EmbedProbePage
import com.marco90.mlinfotainment.data.CarSettings
import com.marco90.mlinfotainment.data.Module

/** Tutte le impostazioni usano i componenti di MLTheme. */
@Composable
fun SettingsHost(onClose: () -> Unit) {
    var page by remember { mutableStateOf("root") }
    BackHandler { if (page == "root") onClose() else page = "root" }
    when (page) {
        "modules" -> ModulesPage { page = "root" }
        "signals" -> SignalsPage { page = "root" }
        "perms" -> PermissionsPage { page = "root" }
        "probe" -> EmbedProbePage { page = "root" }
        else -> RootPage(onClose) { page = it }
    }
}

private fun moduleIcon(m: Module): ImageVector = when (m) {
    Module.NAV -> Icons.Rounded.Navigation
    Module.SPEED -> Icons.Rounded.Speed
    Module.MUSIC -> Icons.Rounded.MusicNote
    Module.WEATHER -> Icons.Rounded.Cloud
    Module.APPS -> Icons.Rounded.Apps
}

private fun signalsSummary(s: CarSettings): String {
    val l = mutableListOf<String>()
    if (s.trigBt) l.add("Bluetooth")
    if (s.trigActivity) l.add("Guida")
    if (s.trigGps) l.add("GPS")
    return if (l.isEmpty()) "Nessuno" else l.joinToString(" · ")
}

@Composable
private fun RootPage(onClose: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    var showFav by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }

    MLSettingsScaffold("Impostazioni", onClose) {
        MLGroup("Avvio automatico") {
            MLSwitchRow(
                "Apri da solo quando guidi", "Con i segnali che scegli",
                s.autoStart, icon = Icons.Rounded.DirectionsCar
            ) { s.autoStart = it; Triggers.syncAll(ctx) }
            MLDependent(s.autoStart) {
                MLNavRow("Segnali di avvio", signalsSummary(s), icon = Icons.Rounded.Sensors) { go("signals") }
            }
            MLNavRow("Permessi", "Posizione, Bluetooth, sopra altre app", icon = Icons.Rounded.Security) { go("perms") }
        }
        MLGroup("Schermata") {
            MLNavRow("Moduli", "Cosa mostrare e in che ordine", icon = Icons.Rounded.Widgets) { go("modules") }
            MLNavRow("App preferite", "Fino a 4, in grande", icon = Icons.Rounded.Apps) { showFav = true }
            MLChoiceRow(
                "Navigatore",
                Navigators.all.map { it.key to (it.label + if (Navigators.installed(ctx, it)) "" else " (non installato)") },
                s.navigator
            ) { s.navigator = it }
            MLNavRow(
                "Indirizzo di casa",
                s.homeAddress.ifBlank { "Non impostato" },
                icon = Icons.Rounded.Home
            ) { editing = "home" }
            MLNavRow(
                "Indirizzo di lavoro",
                s.workAddress.ifBlank { "Non impostato" },
                icon = Icons.Rounded.Work
            ) { editing = "work" }
            MLChoiceRow("Unità di velocità", listOf("kmh" to "km/h", "mph" to "mph"), s.unit) { s.unit = it }
            MLSwitchRow("Schermo sempre acceso", "Mentre guidi", s.keepOn) { s.keepOn = it }
            MLSwitchRow("Schermo intero", "Nasconde le barre di sistema", s.immersive) { s.immersive = it }
        }
        if (BuildConfig.SYSTEM_BUILD) {
            MLGroup("Navigatore incorporato (sperimentale)") {
                MLSwitchRow(
                    "Navigatore dentro la schermata", "Lo mostra nella scheda, invece del pulsante",
                    s.embedNav, icon = Icons.Rounded.Navigation
                ) { s.embedNav = it }
                MLNavRow("Prova incorporamento", "Registro e prova, per capire cosa non va", icon = Icons.Rounded.Sensors) { go("probe") }
            }
        }
        MLThemeSettingsGroup()
    }

    if (showFav) AppPickerDialog(pick = true) { showFav = false }
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

@Composable
private fun AddressDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Via, numero, città") },
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun ModulesPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    MLSettingsScaffold("Moduli", onBack) {
        MLGroup("Mostra") {
            s.orderedModules.forEach { m ->
                MLSwitchRow(m.title, null, s.enabled(m), icon = moduleIcon(m)) { s.setEnabled(m, it) }
            }
        }
        MLGroup("Ordine") {
            s.orderedModules.forEach { m ->
                MLNavRow(
                    m.title, null,
                    icon = moduleIcon(m),
                    trailing = {
                        Row {
                            IconButton(onClick = { s.move(m, -1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Su") }
                            IconButton(onClick = { s.move(m, 1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Giù") }
                        }
                    }
                ) { }
            }
        }
    }
}

@Composable
private fun SignalsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    var showBt by remember { mutableStateOf(false) }
    MLSettingsScaffold("Segnali di avvio", onBack) {
        MLGroup("Quando aprirsi") {
            MLSwitchRow("Bluetooth dell'auto", "Quando il telefono si collega all'auto", s.trigBt, icon = Icons.Rounded.Bluetooth) { s.trigBt = it }
            MLDependent(s.trigBt) {
                MLNavRow(
                    "Dispositivi Bluetooth",
                    if (s.btList.isEmpty()) "Nessuno scelto: scegli l'auto" else "${s.btList.size} scelti",
                    icon = Icons.Rounded.DirectionsCar
                ) { showBt = true }
            }
            MLSwitchRow(
                "Riconoscimento guida", "Google capisce che sei in auto",
                s.trigActivity, icon = Icons.Rounded.Sensors
            ) { s.trigActivity = it; Triggers.syncAll(ctx) }
            MLSwitchRow(
                "Velocità GPS", "Controlla la velocità in sottofondo (consuma batteria)",
                s.trigGps, icon = Icons.Rounded.Speed
            ) { s.trigGps = it; Triggers.syncAll(ctx) }
            MLDependent(s.trigGps) {
                MLSliderRow(
                    "Soglia di velocità", "${s.gpsThreshold} km/h",
                    s.gpsThreshold.toFloat(), 10f..60f, 9
                ) { s.gpsThreshold = Math.round(it / 5f) * 5 }
            }
        }
        MLGroup("Pausa") {
            MLSliderRow(
                "Pausa tra due aperture", "${s.cooldownMin} min",
                s.cooldownMin.toFloat(), 5f..60f, 10
            ) { s.cooldownMin = Math.round(it / 5f) * 5 }
        }
    }
    if (showBt) BtDevicesDialog { showBt = false }
}

@Composable
private fun BtDevicesDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val allowed = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val devices: List<Pair<String, String>> = remember {
        if (!allowed) emptyList() else try {
            val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
            adapter?.bondedDevices?.map { it.address to (it.name ?: it.address) }
                ?.sortedBy { it.second.lowercase() } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }
    var sel by remember { mutableStateOf(s.btList) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth dell'auto") },
        text = {
            if (devices.isEmpty()) {
                Text(
                    if (allowed) "Nessun dispositivo associato. Associa prima l'auto dalle impostazioni Bluetooth del telefono."
                    else "Serve il permesso Bluetooth: concedilo da Impostazioni > Permessi."
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(devices) { dev ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { sel = if (dev.first in sel) sel - dev.first else sel + dev.first }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = dev.first in sel, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(dev.second)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { s.btList = sel; onDismiss() }) { Text("Fine") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
