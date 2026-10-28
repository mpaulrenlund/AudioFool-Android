package com.mpaulrenlund.audiofool.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.library.LibraryRelocator
import com.mpaulrenlund.audiofool.core.library.SafeFile
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.playback.HeardPoint
import com.mpaulrenlund.audiofool.core.playback.OutputNames
import com.mpaulrenlund.audiofool.core.playback.OutputPath
import com.mpaulrenlund.audiofool.core.playback.OutputReadout
import com.mpaulrenlund.audiofool.core.playback.OutputRoute
import com.mpaulrenlund.audiofool.core.playback.PlayOrder
import com.mpaulrenlund.audiofool.core.playback.PlugGuard
import com.mpaulrenlund.audiofool.core.playback.RepeatMode
import com.mpaulrenlund.audiofool.core.playback.ResumePoint
import com.mpaulrenlund.audiofool.settings.AppSettings
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
 * pauses or ducks it), and a pause when headphones are unplugged; and
 * Bit-Perfect (spec 6.2): the setting, greyed out where the route can't do it,
 * and the output opened again when either changes during playback.
 *
 * Main thread only; the output stage's news is posted here.
 */
class Player(
    private val context: Context,
    val state: NowPlaying,
    private val scope: CoroutineScope,
    private val resumePath: String,
    private val waveforms: WaveformReader,
    private val routes: OutputRoutes,
    private val settings: AppSettings,
) : OutputStage.Listener {
    private val main = Handler(Looper.getMainLooper())
    private var engineMade = false
    private val engine: OutputStage by lazy {
        BassRuntime.initialise()
        engineMade = true
        OutputStage(this, routes)
    }
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var queue: List<Track> = emptyList()
    private val order = PlayOrder()

    /** The queue index being decoded; the one heard is [heardIndex]. */
    private var decodingIndex = -1
    private var heardIndex = -1

    /** Seeks sent to the output stage, to compare with its [OutputStage.seeksDone]. */
    private var seeksAsked = 0L

    /** True once the queue is in the output stage; false after a restore, until Play. */
    private var engaged = false

    private var waveformJob: Job? = null

    /** Called on any change the system's controls show (the media session). */
    var onChange: () -> Unit = {}

    /** Told what is heard while playing: Last.fm and the listening time. */
    val watchers = ArrayList<PlayWatcher>()

    /** The track the watchers were last told started, by identity. */
    private var watchedTrack: Track? = null

    init {
        state.positionSource = ::positionMs
        routes.bitPerfectOn = settings.bitPerfect.value
        state.bitPerfect = routes.bitPerfectOn
        routes.onChange = ::routeChanged
        routes.onPlug = ::plugged
        routes.onPluggedIn = ::pluggedIn
        routes.start()
        showRoute()
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
        plugGuard.settled()
        queue = tracks
        order.reset(tracks.size, index, state.shuffle)
        startAt(index, 0)
    }

    fun playPause() {
        plugGuard.settled()
        if (state.isPlaying) pause() else resume()
    }

    /** Never started by a plug (0.2.3): see [PlugGuard]. */
    private val plugGuard = PlugGuard()

    /**
     * Play asked for from outside the app: a headset or Bluetooth key, the lock
     * screen, the notification. Ignored just after a USB or wired device came or went.
     */
    fun resumeFromSystem() {
        if (state.isPlaying) return
        if (!plugGuard.mayStart(SystemClock.elapsedRealtime())) {
            android.util.Log.i(TAG, "play from outside ignored: ${plugGuard.sincePlug(SystemClock.elapsedRealtime())} ms after a plug")
            return
        }
        resume()
    }

    /**
     * A USB DAC or headphones were plugged in (main thread): playing music pauses, so nothing
     * plays through them until the user presses Play (0.4.4, the user's call: music had moved
     * from the speaker to a just-plugged DAC by itself, loud).
     */
    private fun pluggedIn() {
        if (!state.isPlaying) return
        android.util.Log.i(TAG, "paused: a USB DAC or headphones were plugged in")
        pause()
    }

    /** A USB or wired device came or went (main thread): a play from outside just before it is undone. */
    private fun plugged() {
        if (plugGuard.plugged(SystemClock.elapsedRealtime()) && state.isPlaying) {
            android.util.Log.i(TAG, "play from outside undone: a device was plugged in or out")
            pause()
        }
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
        plugGuard.settled()
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
        if (engaged) {
            seeksAsked++
            engine.seek(to)
        }
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

    /**
     * A volume is being ejected (spec 8: the SD card can go at any time). Android
     * ends any process still holding a file open on it a moment later, so its
     * songs are let go at once: one playing (or being decoded) stops, paused where
     * it was heard, and one opened ahead for a gapless join is dropped. Its songs
     * stay in the queue, unavailable until the card is back.
     */
    fun letGoOf(root: String) {
        if (!engaged) return
        val on = { t: Track -> LibraryRelocator.isUnder(t.filePath, root) }
        val heard = queue.getOrNull(heardIndex)
        val decoding = queue.getOrNull(decodingIndex)
        if ((heard != null && on(heard)) || (decoding != null && on(decoding))) {
            state.heldPositionMs = positionMs()
            engine.stop()
            engaged = false
            setPlaying(false)
            save()
            onChange()
        } else {
            engine.setUpcoming(upcomingAfter(decodingIndex).filterNot(on))
        }
    }

    /** Where in the heard track the speakers are. */
    fun positionMs(): Long {
        // Paused, the place is held (pause and seek set it), and the screens recompose on it.
        if (!engaged || !engineMade || !state.isPlaying) return state.heldPositionMs
        // A seek the output hasn't carried out yet: where it is going, not where it was (0.4.1, the S24).
        if (engine.seeksDone < seeksAsked) return state.heldPositionMs
        val heard = engine.heard
        val track = state.track
        return if (track != null && heard.track === track) heard.positionMs.coerceIn(0, maxOf(0, track.durationMs)) else state.heldPositionMs
    }

    /** For the debug panel: how tracks joined, and whether the output ran dry while fed. */
    fun outputStats(): String =
        if (!engineMade) "–"
        else "${engine.gaplessJoins} gapless joins · ${engine.formatReopens} reopened for a new format · ${engine.underruns} underruns · ${engine.fadedCuts} cuts faded"

    /** For audio focus: ducked while another app speaks briefly. */
    private fun setVolume(level: Float) {
        if (engineMade) engine.setVolume(level)
    }

    // ---- Bit-Perfect (spec 6.2, 6.4) ----

    /** The BP button and the Settings chip: on or off, saved; nothing where it is greyed out. */
    fun toggleBitPerfect() {
        if (routes.greyed) return
        val on = !routes.bitPerfectOn
        // 0.4.4, the user's call after Bit-Perfect off came back far too loud on the S24: with the
        // driver holding the DAC, BP off pauses; Android then sets the DAC's volume its own way, and
        // the user plays on when ready.
        if (!on && state.isPlaying && routes.driver?.holding == true) pause()
        settings.setBitPerfect(on)
        routes.bitPerfectOn = on
        state.bitPerfect = on
        if (engaged && engineMade) engine.reconfigure() else if (!on) routes.clearMixer()
        showRoute()
        onChange()
    }

    /** A USB DAC, headphones or Bluetooth came or went (main thread). */
    private fun routeChanged() {
        // Android moves a shared output by itself; a bit-perfect one (or one that
        // could now be) is opened again the new route's way.
        if (engaged && engineMade && (routes.bitPerfectOn || engine.openedPath != OutputPath.Shared)) engine.reconfigure()
        showRoute()
    }

    private fun showRoute() {
        state.bitPerfectGreyed = routes.greyed
        state.readout = readoutFor(state.track)
        // The system output's rate settles once the track plays: look again shortly.
        main.removeCallbacks(readoutAgain)
        main.postDelayed(readoutAgain, READOUT_SETTLE_MS)
    }

    private val readoutAgain = Runnable { state.readout = readoutFor(state.track) }

    /** The debug screen's emulator switch ([OutputRoutes.pretend]): off, Headphones, USB, off. */
    fun cyclePretendRoute() {
        routes.setPretend(
            when (routes.pretend) {
                null -> OutputRoute.Headphones
                OutputRoute.Headphones -> OutputRoute.Usb
                else -> null
            },
        )
    }

    val pretendRoute: String get() = routes.pretend?.let { if (it == OutputRoute.Usb) "USB (made-up bit-perfect mixer)" else it.label } ?: "off: the real route"

    /** For the debug screen: the route, the USB mixer and what the system reports, for the user to send back. */
    fun outputDetails(): List<Pair<String, String>> {
        val opened = if (engineMade) engine.openedFormat else null
        val asked = if (engineMade) engine.askedFormat else null
        return listOf(
            "Route" to "${routes.route.label} · ${routes.deviceSummary()}",
            "Phone" to "${Build.MANUFACTURER} · ${Build.BRAND} · ${Build.MODEL}" +
                if (routes.speakerIsHeadphones) " · speaker taken as headphones" else "",
            "Bit-Perfect" to "${if (routes.bitPerfectOn) "on" else "off"} · way here: ${routes.way.name}" +
                if (routes.greyed) " (greyed)" else "",
            "Opened as" to (opened?.let { "${engine.openedPath.name} · ${describe(it)}" + if (it.dop) " · DoP" else "" } ?: "–"),
            "System output" to routes.systemOutputRate().let { if (it > 0) Display.sampleRate(it) else "–" } +
                " (mixer property ${audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "–"} Hz)" +
                if (fiioRate > 0) " · FiiO reports ${Display.sampleRate(fiioRate)}" else "",
            "Direct" to (asked?.let { "${describe(it)}: ${routes.directSupport(it)}" } ?: "–"),
            "USB mixers" to routes.mixerSummary(),
            "Mixer set" to "${routes.preferredMixer()}${routes.lastMixerNote.takeIf { it.isNotEmpty() }?.let { " · last: $it" } ?: ""}",
            "FiiO jack" to (if (fiioJackType < 0) "nothing yet (FiiO says at a plug or unplug)"
                else "type $fiioJackType → ${OutputNames.headphones(isFiio = true, fiioJack = fiioJackType)}"),
            "Plug guard" to (plugGuard.sincePlug(SystemClock.elapsedRealtime())?.let { "last plug ${Display.time(it)} ago" } ?: "no plug yet") +
                " · ${plugGuard.ignored} plays ignored · ${plugGuard.undone} undone",
        )
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

    override fun onOpened(track: Track, asked: PcmFormat, opened: PcmFormat, path: OutputPath) = main.post {
        val how = if (path == OutputPath.Shared) "" else " (${path.name})"
        val line = "${track.displayTitle} (${track.kind}): ${describe(asked)} → output ${describe(opened)}$how"
        android.util.Log.i(TAG, "opened $line · ${routes.route.label}")
        state.openedLog = (listOf(line) + state.openedLog).take(LOG_SIZE)
        // A new output: FiiO's rate is the old one's until it reports again.
        fiioRate = 0
        showRoute()
    }.let { }

    override fun onOutputLost() = main.post {
        android.util.Log.i(TAG, "the USB DAC went away: paused")
        if (state.isPlaying) pause()
    }.let { }

    override fun onFinished() = main.post {
        // The end of the queue: back to its start, paused, so Play plays it again.
        engaged = false
        setPlaying(false)
        watchedTrack = null
        watchers.forEach { it.stopped() }
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
        if (changed) {
            showRoute()
            loadWaveform(track)
        }
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
     * The readout (spec 6.3): the route, then the rate and depth, then a note
     * ([OutputReadout]). A mixer entry's rate and depth are what was set; the
     * shared output and the M21's way read the rate the system's media output
     * runs at, and its depth isn't something Android tells an app, so the shared
     * readout leaves it out (the desktop's does the same when WASAPI doesn't say).
     */
    private fun readoutFor(track: Track?): String {
        val route = routes.route
        val opened = if (engineMade) engine.openedFormat else null
        val asked = if (engineMade) engine.askedFormat else null
        val source = asked?.sampleRate ?: track?.sampleRate ?: 0
        // The DAC's own name, or the jack in use (0.2.5, the user's call).
        val name = OutputNames.of(route, routes.deviceName, Build.MODEL, routes.speakerIsHeadphones, fiioJackType)
        // Nothing heard (the M21 unplugged, 0.2.6): no rate or note after it.
        if (name == OutputNames.NO_OUTPUT) return name
        if (opened == null) return OutputReadout.describe(name, OutputPath.Shared, routes.systemOutputRate(), 0, source, false)
        // A FiiO's own headphone out: the rate FiiO says its DAC runs at, once it has said (0.1.5).
        val fiio = route == OutputRoute.Headphones && routes.speakerIsHeadphones
        val rate = if (fiio) OutputReadout.hardwareRate(routes.systemOutputRate(), fiioRate) else routes.systemOutputRate()
        return when (val path = engine.openedPath) {
            OutputPath.BitPerfect, OutputPath.RateMatched -> OutputReadout.describe(name, path, opened.sampleRate, opened.bits, source, opened.dop)
            OutputPath.SourceRate -> OutputReadout.describe(name, path, rate, opened.bits, source, false)
            OutputPath.Shared -> OutputReadout.describe(name, path, rate, 0, source, false)
        }
    }

    private fun indexOf(track: Track, near: Int): Int {
        if (near in queue.indices && queue[near] === track) return near
        return queue.indexOfFirst { it === track }
    }

    private fun setPlaying(playing: Boolean) {
        if (state.isPlaying == playing) return
        state.isPlaying = playing
        main.removeCallbacks(letGoOfDac)
        if (playing) {
            registerNoisy()
            main.removeCallbacks(periodicSave)
            main.postDelayed(periodicSave, SAVE_EVERY_MS)
            main.removeCallbacks(tick)
            main.post(tick)
        } else {
            unregisterNoisy()
            main.removeCallbacks(periodicSave)
            main.removeCallbacks(tick)
            main.postDelayed(letGoOfDac, LET_GO_OF_DAC_MS)
        }
        onChange()
    }

    /** Paused a while with the driver holding the USB DAC: it goes back to Android (the user's call, 0.4.0). */
    private val letGoOfDac = Runnable {
        if (!state.isPlaying && engaged && engineMade && routes.driver?.holding == true) engine.letGo()
    }

    /** The driver took or gave back the DAC, or it came or went: the readout and BP's state. */
    fun usbChanged() = showRoute()

    /** Android allowed the app the DAC: playing with Bit-Perfect on, it now goes through the driver. */
    fun usbAllowed() = routeChanged()

    /**
     * While playing, the watchers hear where the speakers are, as the desktop's
     * scrobbler does from its 250 ms position timer. The heard track and its
     * place come from the same timeline, so across a gapless join they change
     * together.
     */
    private val tick = object : Runnable {
        override fun run() {
            if (!state.isPlaying) return
            val track = state.track
            if (track != null) {
                if (track !== watchedTrack) {
                    watchedTrack = track
                    watchers.forEach { it.started(track) }
                }
                val position = positionMs()
                watchers.forEach { it.position(track, position) }
            }
            main.postDelayed(this, TICK_MS)
        }
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
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (engineMade && engine.fixedVolume) {
                // A bit-perfect path is never turned down: pause for the prompt instead.
                resumeOnFocusGain = state.isPlaying
                if (state.isPlaying) pause()
            } else {
                setVolume(DUCKED)
            }
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
            // Not when the driver's own give-back drops the DAC for a moment (0.4.0).
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && state.isPlaying && routes.driver?.handingBack() != true) pause()
        }
    }

    /**
     * FiiO's own signals (the M21, 0.1.5): `fiio.intent.action.HEADSET_TYPE_CHANGED`
     * at a plug or unplug and `fiio.intent.action.SAMPLE_RATE_CHANGED` when its
     * output's rate changes, both sent by FiiO's status bar. The rate is the
     * readout's on the M21. (0.2.2 dropped the debug screen's log of them: done with.)
     */
    private val fiio = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras
            android.util.Log.i(TAG, "FiiO ${intent.action?.substringAfterLast('.')}: " +
                (extras?.keySet()?.joinToString { "$it=${anyExtra(extras, it)}" } ?: "no extras"))
            // The DAC's rate (0 when stopped, which keeps the last one): the readout's, from now on.
            val rate = intExtra(intent, "rate")
            if (intent.action == FIIO_SAMPLE_RATE_CHANGED && rate > 0) {
                fiioRate = rate
                state.readout = readoutFor(state.track)
            }
            // The jack in use (0.2.5): 1 is 3.5 mm, 8 is 4.4 mm; 0 is nothing plugged in (0.2.6: "No Output").
            if (intent.action == FIIO_HEADSET_TYPE_CHANGED && extras?.containsKey("type") == true) {
                val jack = intExtra(intent, "type")
                fiioJackType = jack
                settings.fiioJack = jack
                state.readout = readoutFor(state.track)
            }
        }
    }

    private fun intExtra(intent: Intent, key: String): Int =
        intent.extras?.let { anyExtra(it, key) }.let { (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull() } ?: 0

    /** FiiO's last `HEADSET_TYPE_CHANGED` `type` (0.2.5: which jack), saved, or -1 until it has sent one. */
    private var fiioJackType = settings.fiioJack

    /**
     * The rate FiiO last said its DAC runs at for the output open now, or 0 until
     * it says (forgotten whenever an output opens). The M21's readout goes by it.
     */
    private var fiioRate = 0

    /** An extra of a type not known in advance (the typed getters need it known). */
    @Suppress("DEPRECATION")
    private fun anyExtra(bundle: android.os.Bundle, key: String): Any? = bundle.get(key)

    init {
        val filter = IntentFilter().apply {
            addAction(FIIO_HEADSET_TYPE_CHANGED)
            addAction(FIIO_SAMPLE_RATE_CHANGED)
        }
        // Sent by FiiO's status bar, an app of its own: only an exported receiver hears it.
        context.registerReceiver(fiio, filter, Context.RECEIVER_EXPORTED)
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        // Exported: on the M21 "becoming noisy" comes from FiiO's status bar (an app
        // of its own, uid 10089), not from Android's audio service, and a receiver
        // that isn't exported never hears it (0.1.5, found in the M21's broadcast
        // history). Anyone sending it can only pause the music.
        context.registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_EXPORTED)
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

        const val FIIO_HEADSET_TYPE_CHANGED = "fiio.intent.action.HEADSET_TYPE_CHANGED"
        const val FIIO_SAMPLE_RATE_CHANGED = "fiio.intent.action.SAMPLE_RATE_CHANGED"
        const val SAVE_EVERY_MS = 10_000L

        /** Paused this long, the USB DAC the driver holds goes back to Android (about a minute, the user's call). */
        const val LET_GO_OF_DAC_MS = 60_000L
        const val TICK_MS = 250L
        const val DUCKED = 0.2f

        /** How long after an output opens the readout looks at the system's rate again. */
        const val READOUT_SETTLE_MS = 1_000L

        fun describe(f: PcmFormat) = OutputRoutes.describe(f)
    }
}

/** For the debug panel: a heard point as text. */
fun HeardPoint.describe(): String = "${track?.displayTitle ?: "silence"} at ${Display.time(positionMs)}"
