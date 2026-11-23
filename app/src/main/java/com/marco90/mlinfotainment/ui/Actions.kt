package com.marco90.mlinfotainment.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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

    /** [dest] = indirizzo da raggiungere; null = apre solo il navigatore. */
    fun open(ctx: Context, n: Navigator, dest: String?) {
        val intent: Intent? = if (dest.isNullOrBlank()) {
            ctx.packageManager.getLaunchIntentForPackage(n.pkg)
        } else {
            val q = Uri.encode(dest)
            when (n.key) {
                "maps" -> Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage(n.pkg)
                "waze" -> Intent(Intent.ACTION_VIEW, Uri.parse("waze://?q=$q&navigate=yes")).setPackage(n.pkg)
                else -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")).setPackage(n.pkg)
            }
        }
        if (intent == null) {
            toast(ctx, "${n.label} non è installato")
            return
        }
        start(ctx, intent, "Non riesco ad aprire ${n.label}")
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
