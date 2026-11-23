package com.marco90.mlinfotainment.embed

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSettingsScaffold
import com.marco90.mlinfotainment.data.CarSettings
import com.marco90.mlinfotainment.ui.LocalAuto
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro visibile in "Prova incorporamento": serve a capire cosa funziona sul tablet. */
object EmbedLog {
    val lines = mutableStateListOf<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    fun add(text: String) {
        Log.i("MLEmbed", text)
        Handler(Looper.getMainLooper()).post { lines.add("${fmt.format(Date())}  $text") }
    }
}

object EmbedSupport {
    private var done = false

    fun init(ctx: Context) {
        if (done) return
        done = true
        EmbedLog.add("uid=${Process.myUid()} (1000 = sistema) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        var ok = reflectionOk()
        EmbedLog.add("API nascoste senza sblocco: ${if (ok) "sì" else "no"}")
        if (!ok) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("L")
                ok = reflectionOk()
                EmbedLog.add("API nascoste con sblocco: ${if (ok) "sì" else "no"}")
            } catch (e: Throwable) {
                EmbedLog.add("Sblocco API nascoste fallito: $e")
            }
        }
    }

    private fun reflectionOk(): Boolean = try {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
        true
    } catch (e: Throwable) {
        false
    }
}

/** Il display virtuale su cui gira il navigatore. */
object NavDisplay {
    private var vd: VirtualDisplay? = null
    private var displayId = -1
    private var reader: ImageReader? = null

    private const val OWN_CONTENT_ONLY = 1 shl 3
    private const val SUPPORTS_TOUCH = 1 shl 6
    private const val ROTATES_WITH_CONTENT = 1 shl 7
    private const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
    private const val TRUSTED = 1 shl 10
    private const val OWN_FOCUS = 1 shl 14
    private const val PUBLIC = 1

    private fun create(ctx: Context, w: Int, h: Int, dpi: Int, surface: Surface): VirtualDisplay? {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val base = OWN_CONTENT_ONLY or SUPPORTS_TOUCH or ROTATES_WITH_CONTENT or DESTROY_CONTENT_ON_REMOVAL
        val attempts = listOf(
            "pubblico+fidato+fuoco proprio" to (base or PUBLIC or TRUSTED or OWN_FOCUS),
            "pubblico+fidato" to (base or PUBLIC or TRUSTED),
            "pubblico" to (base or PUBLIC),
            "privato" to base
        )
        for ((name, flags) in attempts) {
            try {
                val d = dm.createVirtualDisplay("MLNav", w, h, dpi, surface, flags)
                if (d != null) {
                    displayId = d.display.displayId
                    EmbedLog.add("Display virtuale creato ($name): id=$displayId, ${w}x$h, ${dpi}dpi, flags=$flags")
                    return d
                }
                EmbedLog.add("Display virtuale ($name): risultato nullo")
            } catch (e: Throwable) {
                EmbedLog.add("Display virtuale ($name) non creato: $e")
            }
        }
        return null
    }

    fun attach(ctx: Context, surface: Surface, w: Int, h: Int, pkg: String) {
        val app = ctx.applicationContext
        val dpi = app.resources.displayMetrics.densityDpi
        val existing = vd
        if (existing != null) {
            try {
                existing.resize(w, h, dpi)
                existing.surface = surface
                EmbedLog.add("Superficie riagganciata (${w}x$h)")
            } catch (e: Throwable) {
                EmbedLog.add("Riaggancio fallito: $e")
            }
            return
        }
        val d = create(app, w, h, dpi, surface) ?: return
        vd = d
        launch(app, pkg)
    }

    fun detach() {
        try { vd?.surface = null } catch (e: Throwable) { }
    }

    fun release() {
        try { vd?.release() } catch (e: Throwable) { }
        vd = null
        displayId = -1
        reader?.close()
        reader = null
        EmbedLog.add("Display virtuale chiuso")
    }

    fun launch(ctx: Context, pkg: String) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            EmbedLog.add("Navigatore $pkg non installato")
            return
        }
        try {
            val opts = ActivityOptions.makeBasic()
            opts.setLaunchDisplayId(displayId)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ctx.startActivity(intent, opts.toBundle())
            EmbedLog.add("Avvio di $pkg sul display $displayId inviato senza errori")
        } catch (e: Throwable) {
            EmbedLog.add("Avvio di $pkg sul display $displayId fallito: $e")
        }
    }

    /** Prova senza schermata: display fuori vista + avvio del navigatore, poi chiusura dopo 10 secondi. */
    fun probe(ctx: Context, pkg: String) {
        val app = ctx.applicationContext
        EmbedLog.add("— prova —")
        release()
        val r = ImageReader.newInstance(1280, 720, PixelFormat.RGBA_8888, 2)
        reader = r
        val d = create(app, 1280, 720, app.resources.displayMetrics.densityDpi, r.surface)
        if (d == null) {
            EmbedLog.add("Prova fermata: display non creato")
            return
        }
        vd = d
        launch(app, pkg)
        EmbedLog.add("Se il navigatore si è aperto a tutto schermo sul tablet, l'incorporamento NON funziona. Se non si vede niente, è finito sul display nascosto")
        Handler(Looper.getMainLooper()).postDelayed({ release() }, 10_000L)
    }

    private val injectMethod by lazy {
        android.hardware.input.InputManager::class.java.getMethod(
            "injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType
        )
    }
    private val setDisplayMethod by lazy {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }
    private var injectErrorLogged = false

    private fun inject(ctx: Context, ev: InputEvent) {
        if (displayId < 0) return
        try {
            setDisplayMethod.invoke(ev, displayId)
            injectMethod.invoke(ctx.getSystemService(Context.INPUT_SERVICE), ev, 0)
        } catch (e: Throwable) {
            if (!injectErrorLogged) {
                injectErrorLogged = true
                EmbedLog.add("Invio del tocco fallito: $e")
            }
        }
    }

    fun touch(ctx: Context, e: MotionEvent) {
        val copy = MotionEvent.obtain(e)
        inject(ctx, copy)
        copy.recycle()
    }

    fun key(ctx: Context, code: Int) {
        val now = SystemClock.uptimeMillis()
        inject(ctx, KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        inject(ctx, KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }
}

/** Scheda del navigatore incorporato nella schermata principale. */
@Composable
fun EmbeddedNav(modifier: Modifier, pkg: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalAuto.current
    LaunchedEffect(Unit) { EmbedSupport.init(ctx) }
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(c.card)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {}
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            NavDisplay.attach(context, holder.surface, width, height, pkg)
                        }
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            NavDisplay.detach()
                        }
                    })
                    setOnTouchListener { _, ev ->
                        NavDisplay.touch(context, ev)
                        true
                    }
                }
            }
        )
        Row(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniButton(Icons.Rounded.ArrowBack) { NavDisplay.key(ctx, KeyEvent.KEYCODE_BACK) }
            MiniButton(Icons.Rounded.Close) {
                NavDisplay.release()
                CarSettings.get(ctx).embedNav = false
                onClose()
            }
        }
    }
}

@Composable
private fun MiniButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalAuto.current
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.card).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = c.onCard) }
}

/** Pagina con il registro e la prova: copia il testo e mandalo per capire cosa non va. */
@Composable
fun EmbedProbePage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    LaunchedEffect(Unit) { EmbedSupport.init(ctx) }
    MLSettingsScaffold("Prova incorporamento", onBack) {
        MLGroup("Azioni") {
            MLNavRow(
                "Esegui la prova", "Apre il navigatore su un display nascosto per 10 secondi",
                icon = Icons.Rounded.PlayArrow
            ) { NavDisplay.probe(ctx, com.marco90.mlinfotainment.ui.Navigators.byKey(s.navigator).pkg) }
            MLNavRow("Copia il registro", "Da incollare nella chat", icon = Icons.Rounded.ContentCopy) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("MLinfotainment", EmbedLog.lines.joinToString("\n")))
            }
            MLNavRow("Cancella il registro", null, icon = Icons.Rounded.DeleteSweep) { EmbedLog.lines.clear() }
        }
        MLGroup("Registro") {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (EmbedLog.lines.isEmpty()) {
                    Text("Vuoto. Tocca \"Esegui la prova\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EmbedLog.lines.forEach {
                    Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
