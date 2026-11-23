package com.marco90.mlinfotainment.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSettingsScaffold
import com.marco90.mlinfotainment.core.Triggers
import com.marco90.mlinfotainment.media.MusicRepo

@Composable
fun PermissionsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        Triggers.syncAll(ctx)
    }
    @Suppress("UNUSED_VARIABLE")
    val refresh = tick // rilegge i permessi quando si torna dalle impostazioni di sistema

    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun openSystem(i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast(ctx, "Impossibile aprire le impostazioni") }
    }

    val loc = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val bgLoc = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    val notif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
    val bt = Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)
    val act = Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACTIVITY_RECOGNITION)
    val overlay = Settings.canDrawOverlays(ctx)
    val listener = MusicRepo.hasAccess(ctx)
    val battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    MLSettingsScaffold("Permessi", onBack) {
        MLGroup("Per aprirsi da solo") {
            PermRow(Icons.Rounded.Layers, "Mostra sopra altre app", "Apre la schermata auto subito, senza toccare niente", overlay) {
                openSystem(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
            PermRow(Icons.Rounded.Bluetooth, "Bluetooth", "Per riconoscere l'auto", bt) {
                if (Build.VERSION.SDK_INT >= 31) launcher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            }
            PermRow(Icons.Rounded.Sensors, "Riconoscimento attività", "Per capire che sei in auto", act) {
                if (Build.VERSION.SDK_INT >= 29) launcher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
            }
            PermRow(Icons.Rounded.LocationOn, "Posizione", "Velocità GPS e meteo", loc) {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            PermRow(Icons.Rounded.MyLocation, "Posizione sempre", "Serve al GPS in sottofondo dopo il riavvio", bgLoc) {
                if (!loc) {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else if (Build.VERSION.SDK_INT >= 29) {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                }
            }
            PermRow(Icons.Rounded.Notifications, "Notifiche", "Per l'avviso in sottofondo", notif) {
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
            PermRow(Icons.Rounded.Bolt, "Batteria senza limiti", "Evita che il sistema fermi i rilevatori", battery) {
                openSystem(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        MLGroup("Per la musica") {
            PermRow(Icons.Rounded.MusicNote, "Accesso alle notifiche", "Mostra il brano e la copertina", listener) {
                openSystem(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, subtitle: String, ok: Boolean, onClick: () -> Unit) {
    MLNavRow(
        title, subtitle,
        icon = icon,
        trailing = {
            Text(
                if (ok) "Attivo" else "Da attivare",
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        },
        onClick = onClick
    )
}
