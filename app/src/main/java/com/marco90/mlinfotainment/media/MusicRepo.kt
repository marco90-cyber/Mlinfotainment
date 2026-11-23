package com.marco90.mlinfotainment.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow

data class MusicState(
    val hasAccess: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val pkg: String? = null
)

/** Brano in riproduzione di qualsiasi app musicale (serve l'accesso alle notifiche). */
object MusicRepo {
    val state = MutableStateFlow(MusicState())
    private var controller: MediaController? = null
    private var listening = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { publish(true) }
        override fun onPlaybackStateChanged(state: PlaybackState?) { publish(true) }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        if (!hasAccess(app)) {
            state.value = MusicState(hasAccess = false)
            return
        }
        try {
            val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(app, MediaNotificationListener::class.java)
            if (!listening) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, cn)
                listening = true
            }
            pick(msm.getActiveSessions(cn))
        } catch (e: SecurityException) {
            state.value = MusicState(hasAccess = false)
        }
    }

    private fun pick(list: List<MediaController>?) {
        val c = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (c?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = c
            c?.registerCallback(callback, Handler(Looper.getMainLooper()))
        }
        publish(true)
    }

    private fun publish(access: Boolean) {
        val c = controller
        if (c == null) {
            state.value = MusicState(hasAccess = access)
            return
        }
        val md = c.metadata
        state.value = MusicState(
            hasAccess = access,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            pkg = c.packageName
        )
    }

    fun playPause(ctx: Context) {
        val c = controller
        if (c != null) {
            if (state.value.playing) c.transportControls.pause() else c.transportControls.play()
        } else key(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun next(ctx: Context) {
        val c = controller
        if (c != null) c.transportControls.skipToNext() else key(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(ctx: Context) {
        val c = controller
        if (c != null) c.transportControls.skipToPrevious() else key(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun key(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
