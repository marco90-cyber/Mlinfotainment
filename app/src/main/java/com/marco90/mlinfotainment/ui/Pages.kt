package com.marco90.mlinfotainment.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.marco90.mlinfotainment.data.CarSettings
import com.marco90.mlinfotainment.media.MusicRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Le pagine che si aprono dalla barra in basso: a schermo intero, nello stile della schermata di guida. */
@Composable
fun PageHost(page: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        when (page) {
            "nav" -> NavPage()
            "phone" -> PhonePage()
            "music" -> MusicPage()
            "apps" -> AppsPage()
        }
    }
}

@Composable
private fun PageTitle(icon: ImageVector, title: String) {
    val c = LocalAuto.current
    Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = c.onCard, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BigButton(label: String, icon: ImageVector, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    val c = LocalAuto.current
    Row(
        modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) c.accent else c.card)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (primary) c.onAccent else c.onCard, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label, color = if (primary) c.onAccent else c.onCard, fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAuto.current
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.accent else c.card)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(label, color = if (selected) c.onAccent else c.onCard, fontSize = 15.sp, maxLines = 1) }
}

@Composable
private fun fieldColors() = LocalAuto.current.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.onCard, unfocusedTextColor = c.onCard,
        focusedBorderColor = c.accent, unfocusedBorderColor = c.sub,
        cursorColor = c.accent, focusedLabelColor = c.accent, unfocusedLabelColor = c.sub
    )
}

// ---------------------------------------------------------------- navigatore

@Composable
private fun NavPage() {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val c = LocalAuto.current
    val nav = Navigators.byKey(s.navigator)
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    fun go(dest: String?) {
        if (!dest.isNullOrBlank()) s.pushRecent(dest)
        Navigators.open(ctx, nav, dest)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle(Icons.Rounded.Navigation, "Navigazione")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Navigators.all.filter { Navigators.installed(ctx, it) || it.key == s.navigator }.forEach { n ->
                Chip(n.label, n.key == s.navigator) { s.navigator = n.key }
            }
        }
        BigButton("Apri ${nav.label}", Icons.Rounded.Navigation, Modifier.fillMaxWidth(), primary = true) { go(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Casa", Icons.Rounded.Home, Modifier.weight(1f)) {
                if (s.homeAddress.isBlank()) toast(ctx, "Imposta l'indirizzo di casa nelle impostazioni") else go(s.homeAddress)
            }
            BigButton("Lavoro", Icons.Rounded.Work, Modifier.weight(1f)) {
                if (s.workAddress.isBlank()) toast(ctx, "Imposta l'indirizzo di lavoro nelle impostazioni") else go(s.workAddress)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Dove vuoi andare?") }, singleLine = true,
                colors = fieldColors(), modifier = Modifier.weight(1f)
            )
            BigButton("Vai", Icons.Rounded.Search, Modifier, primary = true) {
                if (query.isNotBlank()) { go(query.trim()); query = "" }
            }
        }
        Text("Preferiti", color = c.sub, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
        s.places.forEachIndexed { i, (name, addr) ->
            PlaceRow(name, addr, Icons.Rounded.Star, onClick = { go(addr) }, onDelete = { s.removePlace(i) })
        }
        BigButton("Aggiungi un preferito", Icons.Rounded.Add, Modifier.fillMaxWidth()) { adding = true }
        if (s.recents.isNotEmpty()) {
            Text("Recenti", color = c.sub, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
            s.recents.forEach { addr -> PlaceRow(addr, null, Icons.Rounded.History, onClick = { go(addr) }, onDelete = null) }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (adding) AddPlaceDialog({ adding = false }) { n, a -> s.addPlace(n, a); adding = false }
}

@Composable
private fun PlaceRow(title: String, sub: String?, icon: ImageVector, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    val c = LocalAuto.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.card)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onCard, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = c.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onDelete != null) {
            Icon(
                Icons.Rounded.Delete, "Elimina", tint = c.sub,
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onDelete() }.padding(4.dp)
            )
        }
    }
}

@Composable
private fun AddPlaceDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var addr by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo preferito") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome (es. Palestra)") }, singleLine = true)
                OutlinedTextField(addr, { addr = it }, label = { Text("Via, numero, città") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (addr.isNotBlank()) onSave(name.ifBlank { addr }, addr) }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

// ---------------------------------------------------------------- telefono

private data class Contact(val name: String, val number: String)

private fun hasPerm(ctx: android.content.Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

private fun callNumber(ctx: android.content.Context, number: String) {
    if (number.isBlank()) return
    val uri = Uri.fromParts("tel", number, null)
    val i = if (hasPerm(ctx, Manifest.permission.CALL_PHONE)) Intent(Intent.ACTION_CALL, uri) else Intent(Intent.ACTION_DIAL, uri)
    start(ctx, i, "Nessuna app Telefono")
}

private fun loadFavorites(ctx: android.content.Context): List<Contact> = try {
    val out = mutableListOf<Contact>()
    ctx.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        "starred=1", null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
    )?.use { cur ->
        while (cur.moveToNext()) out += Contact(cur.getString(0) ?: "", cur.getString(1) ?: "")
    }
    out.distinctBy { it.name }
} catch (e: Exception) {
    emptyList()
}

private fun loadRecents(ctx: android.content.Context): List<Contact> = try {
    val out = mutableListOf<Contact>()
    ctx.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER),
        null, null, CallLog.Calls.DATE + " DESC"
    )?.use { cur ->
        while (cur.moveToNext() && out.size < 40) {
            val num = cur.getString(1) ?: continue
            out += Contact(cur.getString(0) ?: num, num)
        }
    }
    out.distinctBy { it.number }.take(20)
} catch (e: Exception) {
    emptyList()
}

@Composable
private fun PhonePage() {
    val ctx = LocalContext.current
    val c = LocalAuto.current
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle(Icons.Rounded.Phone, "Telefono")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Tastierino", tab == 0) { tab = 0 }
            Chip("Preferiti", tab == 1) { tab = 1 }
            Chip("Recenti", tab == 2) { tab = 2 }
        }
        if (tab == 0) {
            DialPad(Modifier.weight(1f).fillMaxWidth())
        } else {
            val perm = if (tab == 1) Manifest.permission.READ_CONTACTS else Manifest.permission.READ_CALL_LOG
            val ok = remember(tick, tab) { hasPerm(ctx, perm) }
            if (!ok) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (tab == 1) "Per vedere i preferiti serve il permesso Contatti." else "Per vedere le chiamate recenti serve il permesso Registro chiamate.",
                        color = c.onCard, fontSize = 16.sp
                    )
                    BigButton("Consenti", Icons.Rounded.Check, Modifier, primary = true) {
                        launcher.launch(arrayOf(perm, Manifest.permission.CALL_PHONE))
                    }
                }
            } else {
                var list by remember(tab, tick) { mutableStateOf<List<Contact>?>(null) }
                LaunchedEffect(tab, tick) {
                    list = withContext(Dispatchers.IO) { if (tab == 1) loadFavorites(ctx) else loadRecents(ctx) }
                }
                val l = list
                if (l != null && l.isEmpty()) {
                    Text(
                        if (tab == 1) "Nessun contatto preferito: segna una stella nei contatti." else "Nessuna chiamata recente.",
                        color = c.sub, fontSize = 16.sp, modifier = Modifier.padding(8.dp)
                    )
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(l ?: emptyList()) { ct ->
                        PlaceRow(ct.name, ct.number, Icons.Rounded.Call, onClick = { callNumber(ctx, ct.number) }, onDelete = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialPad(modifier: Modifier) {
    val ctx = LocalContext.current
    val c = LocalAuto.current
    var number by remember { mutableStateOf("") }
    val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                number.ifEmpty { "Digita un numero" },
                color = if (number.isEmpty()) c.sub else c.onCard,
                fontSize = if (number.length > 14) 24.sp else 34.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            if (number.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Backspace, "Cancella", tint = c.onCard,
                    modifier = Modifier.size(52.dp).clip(CircleShape).clickable { number = number.dropLast(1) }.padding(12.dp)
                )
            }
        }
        keys.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier
                            .weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .background(c.card)
                            .clickable { if (number.length < 20) number += k },
                        contentAlignment = Alignment.Center
                    ) { Text(k, color = c.onCard, fontSize = 30.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth().heightIn(min = 64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1E8E3E))
                .clickable { callNumber(ctx, number) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("Chiama", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------- musica

@Composable
private fun MusicPage() {
    val ctx = LocalContext.current
    val c = LocalAuto.current
    val ms by MusicRepo.state.collectAsState()
    val apps = remember { loadMusicApps(ctx) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle(Icons.Rounded.MusicNote, "Musica")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > maxHeight * 1.2f
            val art = remember(ms.art) { ms.art?.asImageBitmap() }
            val artBox: @Composable (Modifier) -> Unit = { m ->
                Box(m.clip(RoundedCornerShape(28.dp)).background(c.card), contentAlignment = Alignment.Center) {
                    if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, tint = c.sub, modifier = Modifier.size(72.dp))
                }
            }
            val controls: @Composable (Modifier) -> Unit = { m ->
                Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    when {
                        ms.title != null -> {
                            Text(ms.title ?: "", color = c.onCard, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
                            Text(ms.artist ?: "", color = c.sub, fontSize = 16.sp, maxLines = 1, textAlign = TextAlign.Center)
                        }
                        !ms.hasAccess -> {
                            Text("Attiva l'accesso alle notifiche per vedere il brano", color = c.onCard, fontSize = 16.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            BigButton("Attiva", Icons.Rounded.Notifications, Modifier, primary = true) {
                                start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "Impossibile aprire le impostazioni")
                            }
                        }
                        else -> Text("Nessuna musica in riproduzione", color = c.sub, fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        BigRound(Icons.Rounded.SkipPrevious, 64, false) { MusicRepo.previous(ctx) }
                        BigRound(if (ms.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 84, true) { MusicRepo.playPause(ctx) }
                        BigRound(Icons.Rounded.SkipNext, 64, false) { MusicRepo.next(ctx) }
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    artBox(Modifier.fillMaxHeight().aspectRatio(1f))
                    controls(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    artBox(Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true))
                    controls(Modifier.fillMaxWidth())
                }
            }
        }
        if (apps.isNotEmpty()) {
            Text("App musicali", color = c.sub, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
            LazyRowApps(apps)
        }
    }
}

@Composable
private fun LazyRowApps(apps: List<AppEntry>) {
    val ctx = LocalContext.current
    val c = LocalAuto.current
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(apps) { app ->
            Column(
                Modifier
                    .width(84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.card)
                    .clickable { launchPackage(ctx, app.pkg) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val icon = rememberAppIcon(app.pkg, 128)
                if (icon != null) Image(icon, app.label, Modifier.size(44.dp))
                Text(app.label, color = c.onCard, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BigRound(icon: ImageVector, size: Int, filled: Boolean, onClick: () -> Unit) {
    val c = LocalAuto.current
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (filled) c.accent else c.card).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = if (filled) c.onAccent else c.onCard, modifier = Modifier.size((size * 0.55f).dp)) }
}

private fun loadMusicApps(ctx: android.content.Context): List<AppEntry> = try {
    val pm = ctx.packageManager
    pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0)
        .map { it.serviceInfo.packageName }
        .distinct()
        .filter { it != ctx.packageName && pm.getLaunchIntentForPackage(it) != null }
        .map { AppEntry(it, pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()) }
        .sortedBy { it.label.lowercase() }
} catch (e: Exception) {
    emptyList()
}

// ---------------------------------------------------------------- app

@Composable
private fun AppsPage() {
    val ctx = LocalContext.current
    val c = LocalAuto.current
    val apps = remember { loadApps(ctx) }
    var q by remember { mutableStateOf("") }
    val shown = remember(q) { if (q.isBlank()) apps else apps.filter { it.label.contains(q.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle(Icons.Rounded.Apps, "App")
        OutlinedTextField(
            value = q, onValueChange = { q = it }, label = { Text("Cerca un'app") },
            singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth()
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(92.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(shown) { app ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.card)
                        .clickable { launchPackage(ctx, app.pkg) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val icon = rememberAppIcon(app.pkg, 160)
                    if (icon != null) Image(icon, app.label, Modifier.size(56.dp))
                    Text(app.label, color = c.onCard, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
