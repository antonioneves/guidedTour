package com.guidedtour.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.guidedtour.app.data.PlayerState
import com.guidedtour.app.data.TextReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps reading with the screen off and exposes the queue through a media
 * session, so the lock screen, notification and Bluetooth headphones can pause, skip and stop.
 */
class ReaderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var reader: TextReader
    private lateinit var session: MediaSessionCompat
    private var observing: Job? = null

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
        }
        startForeground(NOTIFICATION_ID, buildNotification(reader.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (observing == null) {
            observing = scope.launch {
                reader.state.collect { state ->
                    if (!state.active) {
                        stopSelf()
                    } else {
                        updateSession(state)
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
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

    private fun updateSession(state: PlayerState) {
        val item = state.current ?: return
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Guided Tour")
                .build()
        )
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
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
            .addAction(android.R.drawable.ic_media_previous, "Previous", action(ACTION_PREVIOUS, 1))
            .addAction(
                if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.playing) "Pause" else "Play", action(ACTION_TOGGLE, 2),
            )
            .addAction(android.R.drawable.ic_media_next, "Next", action(ACTION_NEXT, 3))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", action(ACTION_STOP, 4))
            .setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "reading"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "com.guidedtour.app.TOGGLE"
        private const val ACTION_NEXT = "com.guidedtour.app.NEXT"
        private const val ACTION_PREVIOUS = "com.guidedtour.app.PREVIOUS"
        private const val ACTION_STOP = "com.guidedtour.app.STOP"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, ReaderService::class.java))
    }
}
