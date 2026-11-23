package com.marco90.mlinfotainment.core

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.marco90.mlinfotainment.data.CarSettings

/** Il telefono si collega al Bluetooth dell'auto scelto. */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val s = CarSettings.get(context)
        if (!s.autoStart || !s.trigBt) return
        val dev = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        if (dev.address in s.btList) AutoLauncher.launch(context, "bluetooth")
    }
}

/** Il riconoscimento attività di Google dice che sei salito in auto. */
class DrivingTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val entered = result.transitionEvents.any {
            it.activityType == DetectedActivity.IN_VEHICLE &&
                it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
        }
        val s = CarSettings.get(context)
        if (entered && s.autoStart && s.trigActivity) AutoLauncher.launch(context, "guida")
    }
}

/** Riaccende i rilevatori dopo l'avvio del telefono o un aggiornamento dell'app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Triggers.syncAll(context, fromBackground = true)
    }
}
