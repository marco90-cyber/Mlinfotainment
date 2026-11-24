package com.marco90.mlinfotainment.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.marco90.mlinfotainment.BuildConfig
import com.marco90.mlinfotainment.core.Triggers
import com.marco90.mlinfotainment.embed.EmbeddedNav
import com.marco90.mlinfotainment.data.CarSettings
import com.marco90.mlinfotainment.data.GridLayout
import com.marco90.mlinfotainment.data.Module
import com.marco90.mlinfotainment.data.SpeedRepo
import com.marco90.mlinfotainment.data.WeatherRepo
import com.marco90.mlinfotainment.media.MusicRepo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashActions(
    val settings: () -> Unit,
    val allApps: () -> Unit,
    val pickFav: () -> Unit,
    val askLocation: () -> Unit,
    val edit: () -> Unit,
    val hasLocation: Boolean
)

@Composable
fun Dashboard(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val c = LocalAuto.current
    val modules = s.activeModules
    var edit by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var showFav by remember { mutableStateOf(false) }
    var permTick by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) {
                SpeedRepo.start(ctx)
                MusicRepo.start(ctx)
                WidgetHost.start(ctx)
                permTick++
            } else if (e == Lifecycle.Event.ON_STOP) {
                SpeedRepo.stop()
                WidgetHost.stop(ctx)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            SpeedRepo.stop()
        }
    }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
        SpeedRepo.start(ctx)
        Triggers.syncAll(ctx)
    }
    val hasLoc = remember(permTick) { SpeedRepo.hasPermission(ctx) }
    val a = DashActions(
        settings = onOpenSettings,
        allApps = { showAll = true },
        pickFav = { showFav = true },
        edit = { edit = true },
        askLocation = {
            locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        hasLocation = hasLoc
    )

    BackHandler(enabled = edit) { edit = false }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Rail(a, vertical = true)
                ModuleGrid(a, true, edit, { edit = it }, Modifier.weight(1f).fillMaxHeight().padding(6.dp))
            }
        } else {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ModuleGrid(a, false, edit, { edit = it }, Modifier.weight(1f).fillMaxWidth().padding(6.dp))
                Rail(a, vertical = false)
            }
        }
    }

    if (showAll) AppPickerDialog(pick = false) { showAll = false }
    if (showFav) AppPickerDialog(pick = true) { showFav = false }
}

@Composable
fun EmptyHint() {
    val c = LocalAuto.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Nessun modulo attivo. Aprilo dalle impostazioni.", color = c.sub, fontSize = 16.sp)
    }
}

@Composable
fun ModuleView(key: String, modifier: Modifier, a: DashActions) {
    if (GridLayout.isWidget(key)) {
        key.substring(2).toIntOrNull()?.let { WidgetView(it, modifier) }
        return
    }
    when (Module.entries.firstOrNull { it.key == key }) {
        Module.NAV -> NavCard(modifier)
        Module.SPEED -> SpeedCard(modifier, a)
        Module.MUSIC -> MusicCard(modifier)
        Module.WEATHER -> WeatherCard(modifier)
        Module.APPS -> AppsCard(modifier, a)
        else -> {}
    }
}

@Composable
fun CardBox(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalAuto.current
    Box(
        modifier = modifier.clip(RoundedCornerShape(28.dp)).background(c.card).padding(16.dp),
        content = content
    )
}

// ---------------------------------------------------------------- barra laterale / inferiore

private class RailItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun Rail(a: DashActions, vertical: Boolean) {
    val c = LocalAuto.current
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val items = listOf(
        RailItem(Icons.Rounded.Navigation, "Navigatore") { Navigators.open(ctx, Navigators.byKey(s.navigator), null) },
        RailItem(Icons.Rounded.Phone, "Telefono") { openDialer(ctx) },
        RailItem(Icons.Rounded.MusicNote, "Musica") { openMusic(ctx) },
        RailItem(Icons.Rounded.Apps, "App") { a.allApps() },
        RailItem(Icons.Rounded.Dashboard, "Layout") { a.edit() },
        RailItem(Icons.Rounded.Settings, "Impostazioni") { a.settings() }
    )
    if (vertical) {
        Column(
            Modifier.width(88.dp).fillMaxHeight().background(c.rail).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) { items.forEach { RailButton(it) } }
    } else {
        Row(
            Modifier.fillMaxWidth().background(c.rail).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) { items.forEach { RailButton(it) } }
    }
}

@Composable
private fun RailButton(item: RailItem) {
    val c = LocalAuto.current
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable { item.onClick() }.padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(item.icon, item.label, tint = c.onCard, modifier = Modifier.size(30.dp))
        Text(item.label, color = c.sub, fontSize = 11.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- navigazione

@Composable
private fun NavCard(modifier: Modifier) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val c = LocalAuto.current
    val nav = Navigators.byKey(s.navigator)
    val installed = Navigators.installed(ctx, nav)
    if (BuildConfig.SYSTEM_BUILD && s.embedNav && installed) {
        EmbeddedNav(modifier, nav.pkg) { }
        return
    }
    val goHome = {
        if (s.homeAddress.isBlank()) toast(ctx, "Imposta l'indirizzo di casa nelle impostazioni")
        else Navigators.open(ctx, nav, s.homeAddress)
    }
    val goWork = {
        if (s.workAddress.isBlank()) toast(ctx, "Imposta l'indirizzo di lavoro nelle impostazioni")
        else Navigators.open(ctx, nav, s.workAddress)
    }
    CardBox(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxHeight < 150.dp) {
                // scheda bassa: tre pulsanti in fila
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1.6f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.accent)
                            .clickable { Navigators.open(ctx, nav, null) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.Navigation, null, tint = c.onAccent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (installed) nav.label else "${nav.label} non installato",
                            color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DestChip("Casa", Icons.Rounded.Home, Modifier.weight(1f).fillMaxHeight()) { goHome() }
                    DestChip("Lavoro", Icons.Rounded.Work, Modifier.weight(1f).fillMaxHeight()) { goWork() }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Navigation, null, tint = c.onAccent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Navigazione", color = c.onCard, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (installed) nav.label else "${nav.label} non installato",
                                color = c.sub, fontSize = 14.sp
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.accent)
                            .clickable { Navigators.open(ctx, nav, null) }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Apri ${nav.label}", color = c.onAccent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DestChip("Casa", Icons.Rounded.Home, Modifier.weight(1f)) { goHome() }
                        DestChip("Lavoro", Icons.Rounded.Work, Modifier.weight(1f)) { goWork() }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestChip(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAuto.current
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.cardHigh)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = c.onCard, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = c.onCard, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

// ---------------------------------------------------------------- velocità e orologio

@Composable
private fun SpeedCard(modifier: Modifier, a: DashActions) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val c = LocalAuto.current
    val sp by SpeedRepo.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val mph = s.unit == "mph"
    val raw = sp.speedKmh
    val shown = if (raw == null) null else (if (mph) raw * 0.621371f else raw).let { if (it < 2f) 0 else Math.round(it) }
    CardBox(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val big = maxHeight > 110.dp
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (!a.hasLocation) {
                    Column(Modifier.weight(1f)) {
                        Text("Velocità GPS", color = c.sub, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(c.cardHigh)
                                .clickable { a.askLocation() }.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) { Text("Consenti la posizione", color = c.onCard, fontSize = 15.sp) }
                    }
                } else {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text(
                            shown?.toString() ?: "--",
                            color = c.onCard,
                            fontSize = if (big) 68.sp else 44.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (mph) "mph" else "km/h",
                            color = c.sub, fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = if (big) 12.dp else 6.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
                        color = c.onCard, fontSize = if (big) 34.sp else 26.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)),
                        color = c.sub, fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- musica

@Composable
private fun MusicCard(modifier: Modifier) {
    val ctx = LocalContext.current
    val c = LocalAuto.current
    val ms by MusicRepo.state.collectAsState()
    CardBox(modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            val art = remember(ms.art) { ms.art?.asImageBitmap() }
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(c.cardHigh),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = c.sub, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (ms.title != null) {
                    Text(ms.title ?: "", color = c.onCard, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ms.artist ?: "", color = c.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (!ms.hasAccess) {
                    Text("Musica", color = c.onCard, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Attiva l'accesso alle notifiche per vedere il brano",
                        color = c.accent, fontSize = 13.sp, maxLines = 2,
                        modifier = Modifier.clickable {
                            start(ctx, Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "Impossibile aprire le impostazioni")
                        }
                    )
                } else {
                    Text("Nessuna musica", color = c.onCard, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tocca per aprire un'app musicale", color = c.sub, fontSize = 13.sp, maxLines = 1,
                        modifier = Modifier.clickable { openMusic(ctx) })
                }
            }
            RoundIcon(Icons.Rounded.SkipPrevious, 44) { MusicRepo.previous(ctx) }
            RoundIcon(if (ms.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 56, filled = true) { MusicRepo.playPause(ctx) }
            RoundIcon(Icons.Rounded.SkipNext, 44) { MusicRepo.next(ctx) }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, size: Int, filled: Boolean = false, onClick: () -> Unit) {
    val c = LocalAuto.current
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(size.dp)
            .clip(CircleShape)
            .background(if (filled) c.accent else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (filled) c.onAccent else c.onCard, modifier = Modifier.size((size * 0.6f).dp))
    }
}

// ---------------------------------------------------------------- meteo

private fun weatherInfo(code: Int?): Pair<String, ImageVector> = when (code) {
    null -> "Meteo" to Icons.Rounded.Cloud
    0 -> "Sereno" to Icons.Rounded.WbSunny
    1, 2 -> "Poco nuvoloso" to Icons.Rounded.FilterDrama
    3 -> "Nuvoloso" to Icons.Rounded.Cloud
    45, 48 -> "Nebbia" to Icons.Rounded.Cloud
    in 51..57 -> "Pioviggine" to Icons.Rounded.Grain
    in 61..67, in 80..82 -> "Pioggia" to Icons.Rounded.Umbrella
    in 71..77, 85, 86 -> "Neve" to Icons.Rounded.AcUnit
    in 95..99 -> "Temporale" to Icons.Rounded.Thunderstorm
    else -> "Variabile" to Icons.Rounded.Cloud
}

@Composable
private fun WeatherCard(modifier: Modifier) {
    val ctx = LocalContext.current
    val c = LocalAuto.current
    val sp by SpeedRepo.state.collectAsState()
    val w by WeatherRepo.state.collectAsState()
    LaunchedEffect(sp.lat != null) {
        while (true) {
            val st = SpeedRepo.state.value
            val lat = st.lat
            val lon = st.lon
            if (lat != null && lon != null) WeatherRepo.refresh(ctx, lat, lon)
            delay(60_000L)
        }
    }
    val (label, icon) = weatherInfo(w.code)
    CardBox(modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (w.temp != null) "${w.temp}°" else "--",
                    color = c.onCard, fontSize = 28.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (w.temp == null) (if (w.failed) "Meteo non disponibile" else "In attesa della posizione")
                    else if (w.place != null) "$label · ${w.place}" else label,
                    color = c.sub, fontSize = 14.sp, maxLines = 1
                )
                if (w.temp != null && w.place == null && !w.placeInfo.isNullOrBlank()) {
                    Text("Località: ${w.placeInfo}", color = c.sub, fontSize = 10.sp, maxLines = 2)
                }
            }
            if (w.min != null && w.max != null) {
                Text("${w.min}° / ${w.max}°", color = c.sub, fontSize = 16.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- app preferite giganti

@Composable
private fun AppsCard(modifier: Modifier, a: DashActions) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val c = LocalAuto.current
    val favs = s.favList.filter { ctx.packageManager.getLaunchIntentForPackage(it) != null }
    CardBox(modifier.then(Modifier)) {
        if (favs.isEmpty()) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).clickable { a.pickFav() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scegli le tue app preferite", color = c.onCard, fontSize = 17.sp)
                }
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                favs.take(4).forEach { pkg ->
                    val label = remember(pkg) {
                        try {
                            val pm = ctx.packageManager
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (e: Exception) { pkg }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.cardHigh)
                            .clickable { launchPackage(ctx, pkg) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val icon = rememberAppIcon(pkg, 160)
                        if (icon != null) Image(icon, label, Modifier.weight(1f, fill = false).sizeIn(maxHeight = 64.dp, maxWidth = 64.dp))
                        Text(label, color = c.onCard, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
