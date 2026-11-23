package com.marco90.mlinfotainment.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.marco90.mlinfotainment.data.CarSettings

/** I widget di sistema (Maps, Spotify, ...) ospitati nella schermata. */
object WidgetHost {
    private const val HOST_ID = 1024
    private var host: AppWidgetHost? = null

    fun host(ctx: Context): AppWidgetHost =
        host ?: AppWidgetHost(ctx.applicationContext, HOST_ID).also { host = it }

    fun mgr(ctx: Context): AppWidgetManager = AppWidgetManager.getInstance(ctx.applicationContext)

    fun start(ctx: Context) {
        try { host(ctx).startListening() } catch (e: Exception) { }
    }

    fun stop(ctx: Context) {
        try { host(ctx).stopListening() } catch (e: Exception) { }
    }

    fun label(ctx: Context, id: Int): String =
        try { mgr(ctx).getAppWidgetInfo(id)?.loadLabel(ctx.packageManager) ?: "Widget $id" } catch (e: Exception) { "Widget $id" }
}

/** Avvia la scelta di un widget: elenco, permesso di sistema, eventuale configurazione. */
@Composable
fun rememberWidgetAdder(): () -> Unit {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    var showPicker by remember { mutableStateOf(false) }
    var pendingId by remember { mutableIntStateOf(-1) }

    fun discard() {
        if (pendingId >= 0) {
            try { WidgetHost.host(ctx).deleteAppWidgetId(pendingId) } catch (e: Exception) { }
        }
        pendingId = -1
    }

    val configLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && pendingId >= 0) {
            s.addWidget(pendingId)
            pendingId = -1
        } else discard()
    }

    fun finish(id: Int) {
        val info = WidgetHost.mgr(ctx).getAppWidgetInfo(id)
        val cfg = info?.configure
        if (cfg != null) {
            pendingId = id
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(cfg)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            try {
                configLauncher.launch(i)
            } catch (e: Exception) {
                s.addWidget(id)
                pendingId = -1
            }
        } else {
            s.addWidget(id)
            pendingId = -1
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingId
        if (r.resultCode == Activity.RESULT_OK && id >= 0) finish(id) else discard()
    }

    if (showPicker) {
        WidgetPickerDialog(
            onDismiss = { showPicker = false },
            onPick = { info ->
                showPicker = false
                val id = WidgetHost.host(ctx).allocateAppWidgetId()
                pendingId = id
                if (WidgetHost.mgr(ctx).bindAppWidgetIdIfAllowed(id, info.provider)) {
                    finish(id)
                } else {
                    val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    try { bindLauncher.launch(i) } catch (e: Exception) { discard() }
                }
            }
        )
    }
    return { showPicker = true }
}

@Composable
private fun WidgetPickerDialog(onDismiss: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val ctx = LocalContext.current
    val items = remember {
        val pm = ctx.packageManager
        WidgetHost.mgr(ctx).installedProviders
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .map { info ->
                val app = try { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() } catch (e: Exception) { "" }
                Triple(info, app, info.loadLabel(pm))
            }
            .sortedWith(compareBy({ it.second.lowercase() }, { it.third.lowercase() }))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scegli un widget") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(items) { (info, app, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(info) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = remember(info) {
                            try { info.loadIcon(ctx, 0)?.toBitmap(96, 96)?.asImageBitmap() } catch (e: Exception) { null }
                        }
                        if (icon != null) Image(icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(label)
                            Text(app, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

/** Scheda "Widget" della schermata principale. */
@Composable
fun WidgetsCard(modifier: Modifier) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val c = LocalAuto.current
    val ids = s.widgetList
    val add = rememberWidgetAdder()
    if (ids.isEmpty()) {
        CardBox(modifier) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).clickable { add() },
                contentAlignment = Alignment.Center
            ) { Text("Aggiungi un widget", color = c.onCard, fontSize = 17.sp) }
        }
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ids.forEach { id -> WidgetView(id, Modifier.weight(1f).fillMaxHeight()) }
    }
}

@Composable
fun WidgetView(id: Int, modifier: Modifier) {
    val ctx = LocalContext.current
    val c = LocalAuto.current
    val info = remember(id) { try { WidgetHost.mgr(ctx).getAppWidgetInfo(id) } catch (e: Exception) { null } }
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(c.card)) {
        if (info == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Widget non disponibile", color = c.sub, fontSize = 14.sp)
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth.value.toInt()
                val h = maxHeight.value.toInt()
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WidgetHost.host(context).createView(context, id, info).also {
                            it.updateAppWidgetSize(Bundle(), w, h, w, h)
                        }
                    },
                    update = { it.updateAppWidgetSize(Bundle(), w, h, w, h) }
                )
            }
        }
    }
}
