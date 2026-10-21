package com.guidedtour.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.media.app.NotificationCompat.MediaStyle
import com.guidedtour.app.data.PhotoSpot
import com.guidedtour.app.data.PlacePhotos
import com.guidedtour.app.data.PlayerState
import com.guidedtour.app.data.Settings
import com.guidedtour.app.data.TextReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps reading with the screen off and exposes the queue through a media
 * session, so the lock screen, notification and Bluetooth headphones can pause, skip, jump 10
 * seconds back or forward, and stop.
 */
class ReaderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var reader: TextReader
    private lateinit var session: MediaSessionCompat
    private var observing: Job? = null
    /** The current place's photo, for the lock screen and the notification, and the spot it's of. */
    private var art: Bitmap? = null
    private var artSpot: PhotoSpot? = null
    private var artJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        reader = TextReader.get(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reading aloud", NotificationManager.IMPORTANCE_LOW)
        )
        session = MediaSessionCompat(this, "GuidedTour").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = reader.resume()
                override fun onPause() = reader.pause()
                override fun onSkipToNext() = reader.next()
                override fun onSkipToPrevious() = reader.previous()
                override fun onStop() = reader.stop()
                // Headphone and car rewind / fast-forward buttons, and the lock-screen jump buttons.
                override fun onRewind() = reader.seekBy(-SEEK_SECONDS)
                override fun onFastForward() = reader.seekBy(SEEK_SECONDS)
                override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                    when (action) {
                        ACTION_BACK -> reader.seekBy(-SEEK_SECONDS)
                        ACTION_FORWARD -> reader.seekBy(SEEK_SECONDS)
                    }
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> reader.toggle()
            ACTION_NEXT -> reader.next()
            ACTION_PREVIOUS -> reader.previous()
            ACTION_STOP -> reader.stop()
            ACTION_BACK -> reader.seekBy(-SEEK_SECONDS)
            ACTION_FORWARD -> reader.seekBy(SEEK_SECONDS)
        }
        // ServiceCompat passes the service type only on Android 10+, where it exists.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(reader.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (observing == null) {
            observing = scope.launch {
                reader.state.collect { state ->
                    if (!state.active) {
                        stopSelf()
                    } else {
                        loadArt(state.current?.spot)
                        show(state)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        session.isActive = false
        session.release()
    }

    /** Loads the photo of [spot] (unless photos are off), then shows it with the current state. */
    private fun loadArt(spot: PhotoSpot?) {
        if (spot == artSpot) return
        artSpot = spot
        art = null
        artJob?.cancel()
        if (spot == null || !Settings.get(this).placePhotos.value) return
        artJob = scope.launch {
            val photo = PlacePhotos.get(this@ReaderService).photo(spot) ?: return@launch
            if (artSpot != spot) return@launch
            art = photo.bitmap.scaledTo(ART_SIZE)
            reader.state.value.takeIf { it.active }?.let(::show)
        }
    }

    /** What the session and notification show; the state also changes with every word spoken, which they don't. */
    private var shown: List<Any?>? = null

    /** Updates the session and notification, only when what they show has changed: the artwork is sent each time. */
    private fun show(state: PlayerState) {
        val key = listOf(state.current?.key, state.current?.title, state.index, state.queue.size, state.playing, art)
        if (key == shown) return
        shown = key
        updateSession(state)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
    }

    /** Scaled down so its longest side is [size]: artwork goes to the system in one call, limited to 1 MB. */
    private fun Bitmap.scaledTo(size: Int): Bitmap {
        val scale = size.toFloat() / maxOf(width, height)
        return if (scale >= 1f) this else scale((width * scale).toInt(), (height * scale).toInt())
    }

    private fun updateSession(state: PlayerState) {
        val item = state.current ?: return
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Guided Tour")
                // The lock screen, the media controls and Bluetooth or car screens show it as the artwork.
                .apply { art?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_FAST_FORWARD
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                // Android 13+ builds the lock-screen controls from the session; these add the jumps.
                .addCustomAction(ACTION_BACK, "Back $SEEK_SECONDS seconds", R.drawable.ic_replay_10)
                .addCustomAction(ACTION_FORWARD, "Forward $SEEK_SECONDS seconds", R.drawable.ic_forward_10)
                .setState(
                    if (state.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (state.playing) 1f else 0f,
                )
                .build()
        )
    }

    private fun action(code: String, requestCode: Int) = PendingIntent.getService(
        this, requestCode, Intent(this, ReaderService::class.java).setAction(code),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(state: PlayerState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val item = state.current
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item?.title ?: "Reading scanned text")
            .setContentText(if (state.queue.size > 1) "${state.index + 1} of ${state.queue.size}" else "Reading aloud")
            .setContentIntent(open)
            .setLargeIcon(art)
            .addAction(android.R.drawable.ic_media_previous, "Previous", action(ACTION_PREVIOUS, 1))
            .addAction(R.drawable.ic_replay_10, "Back $SEEK_SECONDS seconds", action(ACTION_BACK, 5))
            .addAction(
                if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.playing) "Pause" else "Play", action(ACTION_TOGGLE, 2),
            )
            .addAction(R.drawable.ic_forward_10, "Forward $SEEK_SECONDS seconds", action(ACTION_FORWARD, 6))
            .addAction(android.R.drawable.ic_media_next, "Next", action(ACTION_NEXT, 3))
            // Collapsed (Android 12 and older): back 10, play/pause, forward 10.
            .setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(1, 2, 3))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Media notifications show five buttons at most, so Stop gives way to the jumps: while paused
            // the notification can be swiped away instead, which stops reading.
            .setOngoing(state.playing)
            .setDeleteIntent(action(ACTION_STOP, 4))
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "reading"
        /** Longest side of the lock-screen artwork, in pixels (about 450 KB). */
        private const val ART_SIZE = 400
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "com.guidedtour.app.TOGGLE"
        private const val ACTION_NEXT = "com.guidedtour.app.NEXT"
        private const val ACTION_PREVIOUS = "com.guidedtour.app.PREVIOUS"
        private const val ACTION_STOP = "com.guidedtour.app.STOP"
        private const val ACTION_BACK = "com.guidedtour.app.BACK"
        private const val ACTION_FORWARD = "com.guidedtour.app.FORWARD"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, ReaderService::class.java))
    }
}
