package com.mpaulrenlund.audiofool.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.library.SafeFile
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.playback.HeardPoint
import com.mpaulrenlund.audiofool.core.playback.OutputMode
import com.mpaulrenlund.audiofool.core.playback.OutputReadout
import com.mpaulrenlund.audiofool.core.playback.PlayOrder
import com.mpaulrenlund.audiofool.core.playback.RepeatMode
import com.mpaulrenlund.audiofool.core.playback.ResumePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The queue and what plays from it (spec 6.1): a tap on a song plays it with the
 * rest of its album after it; Previous, Next, shuffle and repeat follow the
 * desktop's `PlayOrder`; the output stage does the playing. Keeps [state] up to
 * date for the screens, and the system's controls through [onChange].
 *
 * Also holds what the phone asks of a player: audio focus (another app's sound
 * pauses or ducks it), and a pause when headphones are unplugged.
 *
 * Main thread only; the output stage's news is posted here.
 */
class Player(
    private val context: Context,
    val state: NowPlaying,
    private val scope: CoroutineScope,
    private val resumePath: String,
    private val waveforms: WaveformReader,
) : OutputStage.Listener {
    private val main = Handler(Looper.getMainLooper())
    private var engineMade = false
    private val engine: OutputStage by lazy {
        BassRuntime.initialise()
        engineMade = true
        OutputStage(this)
    }
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var queue: List<Track> = emptyList()
    private val order = PlayOrder()

    /** The queue index being decoded; the one heard is [heardIndex]. */
    private var decodingIndex = -1
    private var heardIndex = -1

    /** True once the queue is in the output stage; false after a restore, until Play. */
    private var engaged = false

    private var waveformJob: Job? = null

    /** Called on any change the system's controls show (the media session). */
    var onChange: () -> Unit = {}

    init {
        state.positionSource = ::positionMs
    }

    val hasQueue: Boolean get() = queue.isNotEmpty()

    // ---- what the screens and the system ask for ----

    /** A tap on a song (spec 2): it plays, with the rest of its album after it. */
    fun playFromAlbum(track: Track, albumTracks: List<Track>) {
        val index = albumTracks.indexOfFirst { it === track }.takeIf { it >= 0 }
            ?: albumTracks.indexOfFirst { it.filePath == track.filePath }
        if (index < 0) play(listOf(track), 0) else play(albumTracks, index)
    }

    fun play(tracks: List<Track>, index: Int) {
        if (index !in tracks.indices) return
        queue = tracks
        order.reset(tracks.size, index, state.shuffle)
        startAt(index, 0)
    }

    fun playPause() {
        if (state.isPlaying) pause() else resume()
    }

    fun resume() {
        val track = state.track ?: return
        if (!engaged) {
            // After a restore or the end of the queue: open it now, from where it was held.
            val index = heardIndex.takeIf { it in queue.indices } ?: return
            if (queue[index] !== track) return
            startAt(index, state.heldPositionMs)
            return
        }
        if (!requestFocus()) return
        engine.resume()
        setPlaying(true)
    }

    fun pause() {
        if (engaged) engine.pause()
        state.heldPositionMs = positionMs()
        setPlaying(false)
        save()
    }

    /** Next (desktop's rule): the next in the play order, wrapping only with Repeat All. */
    fun next() {
        if (queue.isEmpty()) return
        val target = order.next(heardIndex, wrap = state.repeat == RepeatMode.All)
        if (target >= 0) jumpTo(target, forward = true)
    }

    /**
     * Previous (desktop's rule): restarts the song if more than three seconds in,
     * otherwise steps back; at the start of the order, restarts.
     */
    fun previous() {
        if (queue.isEmpty()) return
        if (positionMs() > 3000) {
            seek(0)
            return
        }
        val target = order.previous(heardIndex, wrap = state.repeat == RepeatMode.All)
        if (target < 0) seek(0) else jumpTo(target, forward = false)
    }

    fun seek(ms: Long) {
        val track = state.track ?: return
        val to = ms.coerceIn(0, maxOf(0, track.durationMs))
        state.heldPositionMs = to
        if (engaged) engine.seek(to)
        save()
        onChange()
    }

    /** Shuffle on or off, keeping the song that plays (desktop's PlayOrder). */
    fun toggleShuffle() {
        state.shuffle = !state.shuffle
        if (queue.isNotEmpty()) {
            order.reset(queue.size, heardIndex.coerceAtLeast(0), state.shuffle)
            if (engaged) engine.setUpcoming(upcomingAfter(decodingIndex))
        }
        save()
        onChange()
    }

    /** Off, All, One, Off (the desktop's cycle). */
    fun cycleRepeat() {
        state.repeat = when (state.repeat) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        if (engaged) engine.setUpcoming(upcomingAfter(decodingIndex))
        save()
        onChange()
    }

    /** Where in the heard track the speakers are. */
    fun positionMs(): Long {
        // Paused, the place is held (pause and seek set it), and the screens recompose on it.
        if (!engaged || !engineMade || !state.isPlaying) return state.heldPositionMs
        val heard = engine.heard
        val track = state.track
        return if (track != null && heard.track === track) heard.positionMs.coerceIn(0, maxOf(0, track.durationMs)) else state.heldPositionMs
    }

    /** For the debug panel: how tracks joined, and whether the output ran dry while fed. */
    fun outputStats(): String =
        if (!engineMade) "–"
        else "${engine.gaplessJoins} gapless joins · ${engine.formatReopens} reopened for a new format · ${engine.underruns} underruns"

    /** For audio focus: ducked while another app speaks briefly. */
    private fun setVolume(level: Float) {
        if (engineMade) engine.setVolume(level)
    }

    // ---- resume (spec 6.1) ----

    /**
     * Puts back the queue saved last time, paused where it was heard, once the
     * library is there to find its songs in. Only once, and only when nothing has
     * been played since the app started.
     */
    fun restore(point: ResumePoint, lookup: (String) -> Track?) {
        if (queue.isNotEmpty()) return
        val restored = point.resolve(lookup) ?: return
        queue = restored.queue
        state.shuffle = restored.shuffle
        state.repeat = restored.repeat
        if (!(restored.shuffle && order.restore(restored.order))) order.reset(queue.size, restored.index, restored.shuffle)
        heardIndex = restored.index
        decodingIndex = restored.index
        engaged = false
        state.heldPositionMs = restored.positionMs
        showHeard(restored.index)
        onChange()
    }

    /** What was saved last time, or null. Reads a file: off the main thread. */
    fun readResumePoint(): ResumePoint? =
        SafeFile.read(resumePath, System.currentTimeMillis()) { bytes -> ResumePoint.decode(bytes) }.first

    /** Saves what to reopen to. Cheap: a few hundred bytes. */
    fun save() {
        val index = heardIndex
        if (index !in queue.indices) return
        val point = ResumePoint(
            paths = queue.map { it.filePath },
            index = index,
            positionMs = positionMs(),
            shuffle = state.shuffle,
            repeat = state.repeat,
            order = if (state.shuffle) order.order else emptyList(),
        )
        val bytes = point.encode()
        scope.launch(Dispatchers.IO) {
            synchronized(this@Player) {
                try {
                    SafeFile.write(resumePath) { it.write(bytes) }
                } catch (e: IOException) {
                    // Next save tries again.
                }
            }
        }
    }

    // ---- the output stage's news (its thread) ----

    override fun onDecoding(track: Track) = main.post {
        val index = indexOf(track, near = decodingIndex)
        if (index < 0) return@post
        decodingIndex = index
        engine.setUpcoming(upcomingAfter(index))
    }.let { }

    override fun onHeard(track: Track?) = main.post {
        if (track == null) return@post
        val index = indexOf(track, near = heardIndex)
        if (index < 0) return@post
        android.util.Log.i(TAG, "heard ${track.displayTitle} · ${outputStats()}")
        heardIndex = index
        showHeard(index)
        save()
        onChange()
    }.let { }

    override fun onSkipped(tracks: List<Track>) = main.post {
        state.skippedLog = (tracks.map { it.filePath.substringAfterLast('/') + ": " + Decoder.lastError } + state.skippedLog).take(LOG_SIZE)
    }.let { }

    override fun onOpened(track: Track, asked: PcmFormat, opened: PcmFormat) = main.post {
        val line = "${track.displayTitle} (${track.kind}): ${describe(asked)} → output ${describe(opened)}"
        android.util.Log.i(TAG, "opened $line")
        state.openedLog = (listOf(line) + state.openedLog).take(LOG_SIZE)
        state.readout = readoutFor(track)
    }.let { }

    override fun onFinished() = main.post {
        // The end of the queue: back to its start, paused, so Play plays it again.
        engaged = false
        setPlaying(false)
        val first = order.order.firstOrNull() ?: return@post
        heardIndex = first
        decodingIndex = first
        state.heldPositionMs = 0
        showHeard(first)
        save()
        onChange()
    }.let { }

    // ---- inside ----

    private fun startAt(index: Int, positionMs: Long) {
        if (!requestFocus()) return
        val wrap = state.repeat == RepeatMode.All
        val candidates = order.candidates(index, forward = true, wrap = wrap).map { queue[it] }
        engaged = true
        heardIndex = index
        decodingIndex = index
        state.heldPositionMs = positionMs
        showHeard(index)
        engine.start(candidates, positionMs, play = true)
        setPlaying(true)
    }

    private fun jumpTo(index: Int, forward: Boolean) {
        val candidates = order.candidates(index, forward, wrap = state.repeat == RepeatMode.All).map { queue[it] }
        heardIndex = index
        decodingIndex = index
        state.heldPositionMs = 0
        showHeard(index)
        if (engaged) {
            engine.start(candidates, 0, play = state.isPlaying)
        }
        save()
        onChange()
    }

    /** What the output stage tries after [index]: the same song for Repeat One, else the play order's next ones. */
    private fun upcomingAfter(index: Int): List<Track> {
        if (index !in queue.indices) return emptyList()
        if (state.repeat == RepeatMode.One) return listOf(queue[index])
        val wrap = state.repeat == RepeatMode.All
        val next = order.next(index, wrap)
        return if (next < 0) emptyList() else order.candidates(next, forward = true, wrap = wrap).map { queue[it] }
    }

    private fun showHeard(index: Int) {
        val track = queue[index]
        val changed = state.track !== track
        state.track = track
        state.queueIndex = order.positionOf(index).takeIf { state.shuffle && it >= 0 } ?: index
        state.queueSize = queue.size
        state.readout = readoutFor(track)
        if (changed) loadWaveform(track)
    }

    private fun loadWaveform(track: Track) {
        waveformJob?.cancel()
        state.waveform = null
        waveformJob = scope.launch {
            val levels = withContext(Dispatchers.IO) { runCatching { waveforms.levels(track) }.getOrNull() }
            withContext(Dispatchers.Main) { if (state.track === track) state.waveform = levels }
        }
    }

    /**
     * The readout (spec 6.3) for the shared output, which is all session 3 opens:
     * the system mixer's rate, and "(resampled)" when the track's rate differs.
     * The mixer's bit depth isn't something Android tells an app, so it is left
     * out (the desktop's readout does the same when WASAPI doesn't say).
     */
    private fun readoutFor(track: Track): String {
        val mixer = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        val source = (if (engineMade) engine.openedFormat?.sampleRate else null) ?: track.sampleRate ?: 0
        return OutputReadout.describe(OutputMode.Shared, mixer, 0, source, false)
    }

    private fun indexOf(track: Track, near: Int): Int {
        if (near in queue.indices && queue[near] === track) return near
        return queue.indexOfFirst { it === track }
    }

    private fun setPlaying(playing: Boolean) {
        if (state.isPlaying == playing) return
        state.isPlaying = playing
        if (playing) {
            registerNoisy()
            main.removeCallbacks(periodicSave)
            main.postDelayed(periodicSave, SAVE_EVERY_MS)
        } else {
            unregisterNoisy()
            main.removeCallbacks(periodicSave)
        }
        onChange()
    }

    /** While playing, the place is saved now and then, so a phone that kills the app loses little. */
    private val periodicSave = object : Runnable {
        override fun run() {
            if (!state.isPlaying) return
            save()
            main.postDelayed(this, SAVE_EVERY_MS)
        }
    }

    // ---- audio focus and unplugging (spec 6.1) ----

    private var resumeOnFocusGain = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener({ change -> onFocusChange(change) }, main)
        .setWillPauseWhenDucked(false)
        .build()

    private fun requestFocus(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                if (state.isPlaying) pause()
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // A call or a voice assistant: carry on when it is over.
                resumeOnFocusGain = state.isPlaying
                if (state.isPlaying) pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> setVolume(DUCKED)
            AudioManager.AUDIOFOCUS_GAIN -> {
                setVolume(1f)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resume()
                }
            }
        }
    }

    private var noisyRegistered = false

    /** Headphones unplugged (or a Bluetooth headset gone): pause, as every player does. */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && state.isPlaying) pause()
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED)
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        context.unregisterReceiver(noisy)
        noisyRegistered = false
    }

    private companion object {
        /** `adb logcat -s AudioFoolPlay` on the emulator. */
        const val TAG = "AudioFoolPlay"
        const val LOG_SIZE = 30
        const val SAVE_EVERY_MS = 10_000L
        const val DUCKED = 0.2f

        fun describe(f: PcmFormat) = "${Display.sampleRate(f.sampleRate)} / ${f.depthName}, ${f.channels} ch"
    }
}

/** For the debug panel: a heard point as text. */
fun HeardPoint.describe(): String = "${track?.displayTitle ?: "silence"} at ${Display.time(positionMs)}"
