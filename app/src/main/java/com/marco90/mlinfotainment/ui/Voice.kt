package com.marco90.mlinfotainment.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.Intent
import com.marco90.mlinfotainment.data.CarSettings
import com.marco90.mlinfotainment.data.SpeedRepo
import com.marco90.mlinfotainment.media.MusicRepo
import java.util.Locale

/** Comandi vocali: "vai a ...", "chiama ...", "metti ...", "apri ...", "pausa", "avanti"... */
object Voice {
    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Dimmi cosa fare")

    private fun after(t: String, vararg prefixes: String): String? =
        prefixes.firstOrNull { t.startsWith("$it ") }?.let { t.removePrefix(it).trim() }

    fun handle(ctx: Context, said: String) {
        val s = CarSettings.get(ctx)
        val t = said.trim().lowercase(Locale.ITALIAN)

        when (t) {
            "pausa", "stop", "ferma", "metti in pausa", "metti pausa" -> { MusicRepo.pause(ctx); return }
            "play", "riprendi", "continua", "riproduci", "metti play" -> { MusicRepo.resume(ctx); return }
            "avanti", "prossimo", "prossima", "successivo", "salta", "brano successivo" -> { MusicRepo.next(ctx); return }
            "indietro", "precedente", "brano precedente" -> { MusicRepo.previous(ctx); return }
        }

        after(
            t, "vai a", "vai al", "vai alla", "vai in", "vai da", "portami a", "portami al", "portami alla",
            "portami in", "naviga verso", "naviga a", "navigami a", "navigazione verso", "andiamo a"
        )?.let { dest ->
            val addr = when (dest) {
                "casa", "a casa" -> s.homeAddress.also { if (it.isBlank()) { toast(ctx, "Imposta l'indirizzo di casa"); return } }
                "lavoro", "al lavoro", "ufficio" -> s.workAddress.also { if (it.isBlank()) { toast(ctx, "Imposta l'indirizzo di lavoro"); return } }
                else -> dest
            }
            s.pushRecent(addr)
            toast(ctx, "Navigo verso $addr")
            Navigators.open(ctx, Navigators.byKey(s.navigator), addr)
            return
        }

        after(t, "chiama", "telefona a", "telefona", "chiamami")?.let { who ->
            val digits = who.replace(" ", "")
            if (digits.isNotEmpty() && digits.all { it.isDigit() || it == '+' }) {
                voiceCall(ctx, digits)
            } else {
                val number = findNumber(ctx, who)
                if (number != null) voiceCall(ctx, number)
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
                    toast(ctx, "Per chiamare per nome serve il permesso Contatti (Permessi)")
                else toast(ctx, "Non trovo \"$who\" nei contatti")
            }
            return
        }

        after(t, "metti", "riproduci", "suona", "fai partire", "ascolta", "metti su")?.let { q ->
            if (q.isNotBlank()) {
                toast(ctx, "Cerco: $q")
                MusicRepo.playFromSearch(ctx, q)
                return
            }
        }

        after(t, "apri", "avvia", "lancia")?.let { name ->
            val app = loadApps(ctx).firstOrNull { it.label.lowercase(Locale.ITALIAN) == name }
                ?: loadApps(ctx).firstOrNull { it.label.lowercase(Locale.ITALIAN).contains(name) }
            if (app != null) launchPackage(ctx, app.pkg) else toast(ctx, "Non trovo l'app \"$name\"")
            return
        }

        toast(ctx, "Non ho capito: \"$said\"")
    }

    private fun voiceCall(ctx: Context, number: String) {
        val uri = Uri.fromParts("tel", number, null)
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        start(ctx, Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri), "Nessuna app Telefono")
    }

    private fun findNumber(ctx: Context, name: String): String? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** Ritorna una funzione che apre il riconoscimento vocale e esegue il comando detto. */
@Composable
fun rememberVoice(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) Voice.handle(ctx, text)
    }
    return {
        try { launcher.launch(Voice.intent()) } catch (e: Exception) { toast(ctx, "Riconoscimento vocale non disponibile") }
    }
}

/** Blocco della tastiera in movimento. */
object MotionLock {
    var until by mutableLongStateOf(0L)
}

@Composable
fun rememberMotionLocked(): Boolean {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val sp by SpeedRepo.state.collectAsState()
    val v = sp.speedKmh ?: 0f
    return s.lockMoving && v >= 8f && System.currentTimeMillis() > MotionLock.until
}

@Composable
fun LockedBanner(text: String, modifier: Modifier = Modifier) {
    val c = LocalAuto.current
    val voice = rememberVoice()
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = c.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = c.onCard, fontSize = 16.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(c.accent).clickable { voice() }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Mic, null, tint = c.onAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Parla", color = c.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(c.cardHigh)
                    .clickable { MotionLock.until = System.currentTimeMillis() + 15 * 60_000L }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) { Text("Sono un passeggero", color = c.onCard, fontSize = 15.sp) }
        }
    }
}
