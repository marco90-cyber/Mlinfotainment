package com.marco90.mlinfotainment.ui

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.marco90.mlinfotainment.core.SplitService
import com.marco90.mlinfotainment.data.CarSettings
import com.marco90.mlinfotainment.media.MusicRepo

data class Navigator(val key: String, val label: String, val pkg: String)

/** I navigatori supportati: devono essere installati nel telefono. */
object Navigators {
    val all = listOf(
        Navigator("maps", "Google Maps", "com.google.android.apps.maps"),
        Navigator("waze", "Waze", "com.waze"),
        Navigator("here", "HERE WeGo", "com.here.app.maps")
    )

    fun byKey(key: String): Navigator = all.firstOrNull { it.key == key } ?: all[0]

    fun installed(ctx: Context, n: Navigator): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(n.pkg) != null

    fun intentFor(ctx: Context, n: Navigator, dest: String?): Intent? =
        if (dest.isNullOrBlank()) {
            ctx.packageManager.getLaunchIntentForPackage(n.pkg)
        } else {
            val q = Uri.encode(dest)
            when (n.key) {
                "maps" -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage(n.pkg)
                "waze" -> Intent(Intent.ACTION_VIEW, Uri.parse("waze://?q=$q&navigate=yes")).setPackage(n.pkg)
                else -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")).setPackage(n.pkg)
            }
        }

    /** [dest] = indirizzo da raggiungere; null = apre solo il navigatore. Rispetta "Navigatore a fianco". */
    fun open(ctx: Context, n: Navigator, dest: String?) {
        val intent = intentFor(ctx, n, dest)
        if (intent == null) {
            toast(ctx, "${n.label} non è installato")
            return
        }
        if (CarSettings.get(ctx).sideBySide) {
            openSideBySide(ctx, intent)
        } else {
            start(ctx, intent, "Non riesco ad aprire ${n.label}")
        }
    }

    /** Divide lo schermo (con il servizio di accessibilità) e apre il navigatore nell'altra metà. */
    private fun openSideBySide(ctx: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        val go = { start(ctx, intent, "Non riesco ad aprire il navigatore") }
        val act = ctx as? Activity
        if (act != null && act.isInMultiWindowMode) {
            go()
            return
        }
        val svc = SplitService.instance
        if (svc != null) {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            Handler(Looper.getMainLooper()).postDelayed({ go() }, 700L)
        } else {
            toast(ctx, "Per l'affiancato attiva il servizio nei Permessi")
            go()
        }
    }
}

fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

fun start(ctx: Context, intent: Intent, error: String) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast(ctx, error)
    }
}

fun launchPackage(ctx: Context, pkg: String) {
    val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (i == null) toast(ctx, "App non trovata") else start(ctx, i, "Non riesco ad aprire l'app")
}

fun openDialer(ctx: Context) = start(ctx, Intent(Intent.ACTION_DIAL), "Nessuna app Telefono")

fun openMusic(ctx: Context) {
    val pkg = MusicRepo.state.value.pkg
    if (pkg != null) {
        launchPackage(ctx, pkg)
    } else {
        start(
            ctx,
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
            "Nessuna app musicale trovata"
        )
    }
}
