package com.mpaulrenlund.audiofool.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import com.mpaulrenlund.audiofool.AudioFoolApp
import com.mpaulrenlund.audiofool.R
import com.mpaulrenlund.audiofool.core.model.Track

/**
 * The foreground media service (spec 6.1): the notification with the cover and
 * Previous, Play/Pause and Next, on the media session ([SystemControls]), which
 * keeps the app playing while it is out of sight.
 *
 * Foreground while playing; when paused the notification stays (and can be
 * swiped away, which ends the service).
 */
class PlaybackService : Service() {
    private val app get() = application as AudioFoolApp
    private val player get() = app.player
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.playback_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> player.playPause()
            ACTION_NEXT -> player.next()
            ACTION_PREVIOUS -> player.previous()
            ACTION_DISMISSED -> if (!player.state.isPlaying) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // Started with startForegroundService: it must go to the foreground at once,
        // even if the play has already stopped (then it drops back straight away).
        update(mustStartForeground = intent?.action == null)
        return START_NOT_STICKY
    }

    /** Brings the notification up to date with the player. */
    fun update(mustStartForeground: Boolean = false) {
        val track = player.state.track
        val manager = getSystemService(NotificationManager::class.java)
        if (track == null) {
            if (mustStartForeground) startForeground(NOTIFICATION_ID, Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            stopSelf()
            return
        }

        val playing = player.state.isPlaying
        val notification = buildNotification(track, playing)
        if (playing || mustStartForeground) {
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                foreground = true
            } catch (e: IllegalStateException) {
                // Not allowed from the background (Android 12+): it plays on, as an ordinary notification.
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
        if (!playing) {
            if (foreground) {
                stopForeground(STOP_FOREGROUND_DETACH)
                foreground = false
            }
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(track: Track, playing: Boolean): Notification {
        fun action(iconRes: Int, title: String, action: String) =
            Notification.Action.Builder(
                Icon.createWithResource(this, iconRes),
                title,
                PendingIntent.getService(this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE),
            ).build()

        val controls = app.systemControls
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(track.displayTitle)
            .setContentText(track.artist.ifBlank { track.groupingArtist })
            .setSubText(track.album)
            .setLargeIcon(controls.art)
            .setContentIntent(SystemControls.openAppIntent(this))
            .setDeleteIntent(PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_DISMISSED), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(playing)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(action(R.drawable.ic_notification_previous, getString(R.string.previous), ACTION_PREVIOUS))
            .addAction(
                if (playing) action(R.drawable.ic_notification_pause, getString(R.string.pause), ACTION_PLAY_PAUSE)
                else action(R.drawable.ic_notification_play, getString(R.string.play), ACTION_PLAY_PAUSE),
            )
            .addAction(action(R.drawable.ic_notification_next, getString(R.string.next), ACTION_NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(controls.session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away from recents while paused: nothing to keep going for.
        if (!player.state.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        player.save()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        if (running === this) running = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PLAY_PAUSE = "com.mpaulrenlund.audiofool.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.mpaulrenlund.audiofool.NEXT"
        private const val ACTION_PREVIOUS = "com.mpaulrenlund.audiofool.PREVIOUS"
        private const val ACTION_DISMISSED = "com.mpaulrenlund.audiofool.DISMISSED"

        /** The service while it runs (one process, one service). */
        var running: PlaybackService? = null
            private set

        /** Starts the service when playing starts, or brings it up to date. */
        fun sync(context: Context, playing: Boolean) {
            val service = running
            when {
                service != null -> service.update()
                playing -> context.startForegroundService(Intent(context, PlaybackService::class.java))
            }
        }
    }
}
