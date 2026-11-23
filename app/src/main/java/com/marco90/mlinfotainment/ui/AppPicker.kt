package com.marco90.mlinfotainment.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.marco90.mlinfotainment.data.CarSettings

data class AppEntry(val pkg: String, val label: String)

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun rememberAppIcon(pkg: String, px: Int = 128): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(pkg) {
        try {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

/** [pick] = true: scegli le app preferite; false: elenco di tutte le app per aprirne una. */
@Composable
fun AppPickerDialog(pick: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val apps = remember { loadApps(ctx) }
    var selected by remember { mutableStateOf(s.favList) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pick) "App preferite (fino a 4)" else "Tutte le app") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(apps) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (pick) {
                                    selected = if (app.pkg in selected) selected - app.pkg
                                    else if (selected.size < 4) selected + app.pkg else selected
                                } else {
                                    launchPackage(ctx, app.pkg)
                                    onDismiss()
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = rememberAppIcon(app.pkg, 96)
                        if (icon != null) Image(icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, Modifier.weight(1f))
                        if (pick) Checkbox(checked = app.pkg in selected, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = {
            if (pick) TextButton(onClick = { s.favList = selected; onDismiss() }) { Text("Fine") }
            else TextButton(onClick = onDismiss) { Text("Chiudi") }
        },
        dismissButton = {
            if (pick) TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}
