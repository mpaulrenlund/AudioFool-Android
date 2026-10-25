package com.mpaulrenlund.audiofool.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import com.mpaulrenlund.audiofool.MainActivity
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.library.CoverArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The media session (spec 6.1): what the lock screen, the notification shade,
 * headset and Bluetooth buttons and watches talk to. Made once the player has a
 * queue (a restored one included, so a headset button resumes it), and kept up
 * to date from the [Player]; [PlaybackService] builds its notification on it.
 *
 * Android's own `MediaSession`, so no library comes in for it.
 */
class SystemControls(private val context: Context, private val player: Player, private val scope: CoroutineScope) {
    val session = MediaSession(context, "AudioFool").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = player.resume()
            override fun onPause() = player.pause()
            override fun onSkipToNext() = player.next()
            override fun onSkipToPrevious() = player.previous()
            override fun onSeekTo(pos: Long) = player.seek(pos)
            override fun onStop() = player.pause()
        })
        setSessionActivity(openAppIntent(context))
        isActive = true
    }

    /** The cover for the lock screen and the notification, once read. */
    var art: Bitmap? = null
        private set
    private var artFor: Track? = null
    private var artJob: Job? = null

    /** Called when the cover arrives, so the notification can show it. */
    var onArt: () -> Unit = {}

    fun update() {
        val state = player.state
        val track = state.track ?: return
        val playing = state.isPlaying
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP,
                )
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, player.positionMs(), if (playing) 1f else 0f, SystemClock.elapsedRealtime())
                .build(),
        )

        if (artFor !== track) loadArt(track)
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.displayTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist.ifBlank { track.groupingArtist })
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, (state.queueIndex + 1).toLong())
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, state.queueSize.toLong())
                .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
    }

    /** The full cover, decoded at a size the lock screen and notification use. */
    private fun loadArt(track: Track) {
        artFor = track
        art = null
        artJob?.cancel()
        artJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) { CoverArt.read(track)?.bytes?.let { decode(it, ART_PX) } }
            withContext(Dispatchers.Main) {
                if (artFor === track) {
                    art = bitmap
                    update()
                    onArt()
                }
            }
        }
    }

    companion object {
        private const val ART_PX = 512

        fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_NOW_PLAYING, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** A cover decoded no smaller than [px] on its short side, in a power-of-two step. */
        fun decode(bytes: ByteArray, px: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = Integer.highestOneBit(maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / px))
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}
