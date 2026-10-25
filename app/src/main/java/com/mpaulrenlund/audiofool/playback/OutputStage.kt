package com.mpaulrenlund.audiofool.playback

import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.playback.DopRule
import com.mpaulrenlund.audiofool.core.playback.HearingTimeline
import com.mpaulrenlund.audiofool.core.playback.OutputMode
import com.mpaulrenlund.audiofool.core.playback.OutputPath
import com.mpaulrenlund.audiofool.core.playback.HeardPoint
import com.mpaulrenlund.audiofool.core.playback.ReopenPoint
import com.mpaulrenlund.audiofool.core.playback.TrackEnd
import com.mpaulrenlund.audiofool.core.playback.TrackEndAction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * AudioFool's own output stage (spec 6.1): BASS decodes, and this writes the
 * samples to an `AudioTrack` opened at the track's own sample rate and bit depth,
 * so the app decides what reaches the output.
 *
 * **Gapless:** the next track is opened while the current one plays. When the
 * current one runs out, a next track in the same format is joined on in the same
 * `AudioTrack`, with not a frame between them; one in another format waits until
 * the last track has been heard out, then the output is reopened for it
 * ([TrackEnd], ported from the desktop).
 *
 * **What is heard:** every write goes into a [HearingTimeline] with the track and
 * place it came from; the `AudioTrack`'s playback head says which frame is
 * coming out, so the screens show the song being heard, not the one being
 * decoded half a second ahead.
 *
 * **Bit-Perfect** (spec 6.2, session 4): before each output is opened, [routes]
 * says how ([OutputPlan]): through a USB mixer entry it has just set (the
 * `AudioTrack` then takes exactly that entry's format), at the track's own rate
 * in whole-number samples (the M21), or shared. Bit-perfect paths are never
 * turned down: [fixedVolume]. A change of route or of the setting reopens the
 * output where the song was heard ([reconfigure]).
 *
 * Everything runs on one thread that owns BASS's streams and the `AudioTrack`;
 * the rest of the app talks to it through commands, and hears back through the
 * [Listener], on that thread.
 */
class OutputStage(private val listener: Listener, private val routes: OutputRoutes) {
    interface Listener {
        /** The decoder has moved onto [track]: started, or joined on after the last. The next one is wanted now. */
        fun onDecoding(track: Track)

        /** What comes out of the speakers is now [track] (null: silence). */
        fun onHeard(track: Track?)

        /** Songs that wouldn't open and were passed over. */
        fun onSkipped(tracks: List<Track>)

        /** The output was opened for [track]: what its decoder asked for, what the output took, and how. */
        fun onOpened(track: Track, asked: PcmFormat, opened: PcmFormat, path: OutputPath)

        /** The last song has been heard out (or nothing in a play would open). */
        fun onFinished()
    }

    private sealed interface Command {
        class Start(val candidates: List<Track>, val startMs: Long, val play: Boolean) : Command
        class Upcoming(val tracks: List<Track>) : Command
        class Seek(val ms: Long) : Command
        class Volume(val level: Float) : Command
        data object Reconfigure : Command
        data object Pause : Command
        data object Resume : Command
        data object Stop : Command
    }

    private val commands = LinkedBlockingQueue<Command>()
    private val timeline = HearingTimeline()

    /** What the speakers are playing, updated as the output plays (any thread may read it). */
    @Volatile var heard: HeardPoint = HeardPoint(null, 0)
        private set

    /** The format the output is open in, or null when it is closed. */
    @Volatile var openedFormat: PcmFormat? = null
        private set

    /** How the open output was opened (shared, bit-perfect, rate matched, at the source rate). */
    @Volatile var openedPath: OutputPath = OutputPath.Shared
        private set

    /** A bit-perfect path: the samples go out as decoded, so nothing turns them down (focus pauses instead of ducking). */
    val fixedVolume: Boolean get() = openedFormat != null && (openedPath == OutputPath.BitPerfect || openedPath == OutputPath.SourceRate)

    /** What the track the output was opened for asked for; a next track asking the same joins. */
    @Volatile var askedFormat: PcmFormat? = null
        private set

    @Volatile var isPlaying: Boolean = false
        private set

    /** Tracks joined on in the same output (gapless), since the app started. For the debug panel. */
    @Volatile var gaplessJoins = 0
        private set

    /** Times the output was reopened between two tracks for another format (a short gap). */
    @Volatile var formatReopens = 0
        private set

    /**
     * Times the output ran dry while it was being fed: what would be heard as a
     * gap or a click. Not counted while the end of the queue plays out, or while paused.
     */
    @Volatile var underruns = 0
        private set
    private var underrunsSeen = 0

    // ---- the thread's own state ----
    private var current: Decoder? = null
    private var next: Decoder? = null
    private var nextTried = false
    private var upcoming: List<Track> = emptyList()
    private var output: AudioTrack? = null
    private var volume = 1f
    private val head = Head()
    private var lastHeardTrack: Track? = null

    /** Converted samples waiting to be written, and where they came from. */
    private val pending: ByteBuffer = ByteBuffer.allocateDirect(Decoder.CHUNK_FRAMES * 8 * 4).order(ByteOrder.nativeOrder())
    private var pendingTrack: Track? = null
    private var pendingStartSeconds = 0.0
    private var pendingFramesLogged = 0L
    private var pendingBytesWritten = 0L

    /** The current track has run out and its end is being heard; then [afterDrain] happens. */
    private var draining = false
    private var afterDrain: TrackEndAction = TrackEndAction.Finish
    private var drainLastHead = -1L
    private var drainStillSince = 0L

    private val thread = Thread(::run, "AudioFool output").apply {
        isDaemon = true
        start()
    }

    // ---- commands, from any thread ----

    /** Plays the first of [candidates] that opens, from [startMs]; the others are passed over. */
    fun start(candidates: List<Track>, startMs: Long, play: Boolean) = commands.put(Command.Start(candidates, startMs, play))

    /** What comes after the track being decoded, in the order to try it (a song that won't open is passed over). */
    fun setUpcoming(tracks: List<Track>) = commands.put(Command.Upcoming(tracks))

    fun seek(ms: Long) = commands.put(Command.Seek(ms))
    fun pause() = commands.put(Command.Pause)
    fun resume() = commands.put(Command.Resume)
    fun stop() = commands.put(Command.Stop)

    /** For audio focus: ducked while another app speaks. Not on a bit-perfect path ([fixedVolume]). */
    fun setVolume(level: Float) = commands.put(Command.Volume(level))

    /**
     * The route or the Bit-Perfect setting changed: the output is opened again
     * the way [routes] now says, carrying on from what was heard (the desktop
     * reopens for "Bit-Perfect switched mid-track" and "a change of sound device").
     */
    fun reconfigure() = commands.put(Command.Reconfigure)

    // ---- the thread ----

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        while (true) {
            val busy = isPlaying && current != null
            val command = if (busy) commands.poll() else commands.poll(250, TimeUnit.MILLISECONDS)
            try {
                if (command != null) handle(command)
                if (isPlaying && current != null) pump() else updateHeard()
            } catch (e: RuntimeException) {
                // A bad file or a lost output must not take the thread down with it.
                android.util.Log.w("AudioFoolOutput", "output stage", e)
                closeAll()
                listener.onFinished()
            }
        }
    }

    private fun handle(command: Command) {
        when (command) {
            is Command.Start -> startPlaying(command.candidates, command.startMs, command.play)
            is Command.Upcoming -> {
                upcoming = command.tracks
                next?.close()
                next = null
                nextTried = false
            }
            is Command.Seek -> seekTo(command.ms)
            is Command.Volume -> {
                volume = command.level
                output?.setVolume(outputVolume())
            }
            Command.Reconfigure -> reopenTheNewWay()
            Command.Pause -> {
                output?.pause()
                isPlaying = false
                updateHeard()
            }
            Command.Resume -> if (current != null && output != null) {
                forgetUnderruns()
                output?.play()
                isPlaying = true
                drainStillSince = 0
            }
            Command.Stop -> closeAll()
        }
    }

    private fun startPlaying(candidates: List<Track>, startMs: Long, play: Boolean) {
        next?.close()
        next = null
        nextTried = false
        current?.close()
        current = null
        draining = false
        pending.clear().limit(0)

        val skipped = ArrayList<Track>()
        var opened: Decoder? = null
        for ((i, track) in candidates.withIndex()) {
            opened = Decoder.open(track, if (i == 0) startMs else 0, routes::mayOpenDop)?.let { openFor(it, reuse = true) }
            if (opened != null) break
            skipped += track
        }
        if (skipped.isNotEmpty()) listener.onSkipped(skipped)

        val decoder = opened ?: run {
            closeAll()
            listener.onFinished()
            return
        }
        current = decoder
        heard = HeardPoint(decoder.track, (decoder.positionSeconds * 1000).toLong())
        lastHeardTrack = decoder.track
        listener.onDecoding(decoder.track)
        listener.onHeard(decoder.track)
        if (play) {
            output?.play()
            isPlaying = true
        } else {
            isPlaying = false
        }
    }

    /**
     * Opens the output for [format], or, with [reuse], empties the one already
     * open in that format (a skip: immediate, not gapless). False when nothing
     * would open.
     *
     * How is [routes]' call ([OutputPlan]): a USB mixer entry it has set, in
     * exactly its format; the track's rate in whole-number samples (the M21);
     * or shared, trying the track's own encoding (24-bit goes as float), then
     * float, then 16-bit. A bit-perfect way that won't open falls back to shared.
     */
    private fun openOutput(format: PcmFormat, track: Track, reuse: Boolean): Boolean {
        val open = output
        if (reuse && open != null && askedFormat == format) {
            open.pause()
            open.flush()
            open.setVolume(outputVolume())
            resetCounting(format.sampleRate)
            listener.onOpened(track, format, openedFormat!!, openedPath)
            return true
        }

        releaseOutput()
        when (val plan = routes.prepare(format)) {
            is OutputPlan.Mixer -> {
                val f = plan.format
                val opened = PcmFormat(f.sampleRate, f.channelCount, f.encoding, format.dop)
                if (use(build(opened, exact = f), opened, format, if (plan.bitPerfect) OutputPath.BitPerfect else OutputPath.RateMatched, track)) return true
                routes.mixerTrackFailed()
            }
            OutputPlan.SourceRate -> {
                // Whole-number samples only: 16-bit as 16-bit, anything wider as 32-bit.
                // Never 24-bit packed: the M21 turns it into static (0.1.1).
                val encodings = if (format.encoding == AudioFormat.ENCODING_PCM_16BIT) {
                    listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_32BIT)
                } else {
                    listOf(AudioFormat.ENCODING_PCM_32BIT)
                }
                for (encoding in encodings) {
                    val f = format.withEncoding(encoding)
                    if (use(build(f), f, format, OutputPath.SourceRate, track)) return true
                }
            }
            OutputPlan.Shared -> Unit
        }
        // Shared: Android mixes in float, so a 24-bit track goes as float, which holds
        // its samples exactly. A 24-bit packed track came out as static on the M21
        // (Android 13, 0.1.1), though the emulator played it.
        val encodings = listOf(format.encoding, AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT)
            .filter { it != AudioFormat.ENCODING_PCM_24BIT_PACKED }.distinct()
        for (encoding in encodings) {
            val f = format.withEncoding(encoding)
            if (use(build(f), f, format, OutputPath.Shared, track)) return true
        }
        return false
    }

    /** Takes [made] as the output, if it was made. */
    private fun use(made: AudioTrack?, opened: PcmFormat, asked: PcmFormat, path: OutputPath, track: Track): Boolean {
        made ?: return false
        output = made
        openedFormat = opened
        askedFormat = asked
        openedPath = path
        made.setVolume(outputVolume())
        resetCounting(opened.sampleRate)
        listener.onOpened(track, asked, opened, path)
        return true
    }

    private fun outputVolume(): Float = if (fixedVolume) 1f else volume

    /**
     * Opens the output for [decoder] and returns the decoder that will play: a
     * DoP decoder whose output didn't come out bit-perfect at its rate is
     * reopened as PCM (the desktop's "DoP refused ... opened as PCM instead"),
     * and the rate isn't tried for DoP again. Null when nothing would open; the
     * decoders tried are closed.
     */
    private fun openFor(decoder: Decoder, reuse: Boolean): Decoder? {
        if (openOutput(decoder.format, decoder.track, reuse)) {
            val mode = if (openedPath == OutputPath.BitPerfect) OutputMode.Exclusive else OutputMode.Shared
            if (!decoder.format.dop || DopRule.mayPlayOn(mode, openedFormat?.sampleRate ?: 0, decoder.format.sampleRate)) return decoder
        }
        if (!decoder.format.dop) {
            decoder.close()
            return null
        }
        android.util.Log.i("AudioFoolPlay", "DoP refused at ${decoder.format.sampleRate} Hz: ${decoder.track.filePath.substringAfterLast('/')} opened as PCM instead")
        routes.refuse(decoder.format.sampleRate)
        val at = (decoder.positionSeconds * 1000).toLong()
        decoder.close()
        releaseOutput()
        val pcm = Decoder.open(decoder.track, at) ?: return null
        if (openOutput(pcm.format, pcm.track, reuse = false)) return pcm
        pcm.close()
        return null
    }

    /** Builds an `AudioTrack` for [format]; [exact] is a mixer entry's own format, used as it is. */
    private fun build(format: PcmFormat, exact: AudioFormat? = null): AudioTrack? {
        // A mixer entry may give its channels as an index mask; the buffer size only needs the count.
        val mask = OutputRoutes.maskOf(format.channels)
        if (mask == AudioFormat.CHANNEL_INVALID) return null
        return try {
            val audioFormat = exact ?: AudioFormat.Builder().setSampleRate(format.sampleRate).setEncoding(format.encoding).setChannelMask(mask).build()
            val min = AudioTrack.getMinBufferSize(format.sampleRate, mask, format.encoding)
            if (min <= 0) return null
            // About half a second: room to open the next track while this one plays.
            val size = maxOf(min * 2, format.bytesPerFrame * format.sampleRate / 2)
            val track = AudioTrack.Builder()
                .setAudioAttributes(OutputRoutes.MEDIA)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(size - size % format.bytesPerFrame)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            if (track.state == AudioTrack.STATE_INITIALIZED) track else {
                track.release()
                null
            }
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: UnsupportedOperationException) {
            null
        }
    }

    private fun countUnderruns(out: AudioTrack) {
        val count = out.underrunCount
        if (count > underrunsSeen) underruns += count - underrunsSeen
        underrunsSeen = count
    }

    /** From here on, only new underruns count (after a flush, a pause or the end playing out). */
    private fun forgetUnderruns() {
        underrunsSeen = output?.underrunCount ?: 0
    }

    private fun resetCounting(sampleRate: Int) {
        timeline.reset(sampleRate)
        head.reset(output)
        forgetUnderruns()
        pending.clear().limit(0)
        pendingBytesWritten = 0
        pendingFramesLogged = 0
    }

    /** Decodes and writes one step. */
    private fun pump() {
        val decoder = current ?: return
        val out = output ?: return
        val format = openedFormat ?: return

        if (draining) {
            drainStep()
            return
        }

        if (!pending.hasRemaining()) {
            val startSeconds = decoder.positionSeconds
            if (!decoder.read(pending, format.encoding)) {
                trackRanOut(decoder)
                return
            }
            pendingTrack = decoder.track
            pendingStartSeconds = startSeconds
            pendingBytesWritten = 0
            pendingFramesLogged = 0
        }

        val written = out.write(pending, pending.remaining(), AudioTrack.WRITE_NON_BLOCKING)
        when {
            written < 0 -> reopenAfterLoss(decoder)
            written == 0 -> {
                // The output is full: the slack in which to open the next track.
                if (!nextTried) openNext()
                updateHeard()
                SystemClock.sleep(10)
            }
            else -> {
                countUnderruns(out)
                pendingBytesWritten += written
                val frames = pendingBytesWritten / format.bytesPerFrame
                val newFrames = (frames - pendingFramesLogged).toInt()
                if (newFrames > 0) {
                    timeline.append(pendingTrack, pendingStartSeconds + pendingFramesLogged.toDouble() / format.sampleRate, newFrames)
                    pendingFramesLogged = frames
                }
                updateHeard()
            }
        }
    }

    /** The current track's audio has all been written: what comes next ([TrackEnd]). */
    private fun trackRanOut(decoder: Decoder) {
        if (!nextTried) openNext()
        // The output follows each track's own format, as the desktop's exclusive
        // output does: only a track asking for the same format joins.
        val action = TrackEnd.decide(
            repeatOne = false,
            hasOutput = true,
            nextOpened = next != null,
            sharedOutput = false,
            nextAtOutputRate = next != null && next?.format == askedFormat,
            nextIsDop = next?.format?.dop == true,
            nextMidSave = false,
            hasNext = upcoming.isNotEmpty(),
        )
        when (action) {
            TrackEndAction.Splice -> {
                gaplessJoins++
                decoder.close()
                current = next
                next = null
                nextTried = false
                current?.let { listener.onDecoding(it.track) }
            }
            else -> {
                // Reopen, or the end: wait for the last of this track to be heard.
                draining = true
                afterDrain = if (action == TrackEndAction.Reopen) TrackEndAction.Reopen else TrackEndAction.Finish
                drainLastHead = -1
                drainStillSince = 0
            }
        }
    }

    /** Waits for the output to play what it holds, then reopens it for the next track or lets it go. */
    private fun drainStep() {
        val heardFrames = head.read(output)
        updateHeard()
        val now = SystemClock.uptimeMillis()
        if (heardFrames != drainLastHead) {
            drainLastHead = heardFrames
            drainStillSince = now
        }
        // Heard out, or the head has stopped short (a buffer the output never started on).
        val done = heardFrames >= timeline.totalFrames || (drainStillSince != 0L && now - drainStillSince > 400)
        if (!done) {
            SystemClock.sleep(10)
            return
        }
        draining = false
        // The next track may have changed while this one was heard out (shuffle or repeat turned).
        if (afterDrain == TrackEndAction.Reopen && !nextTried) openNext()
        val nextDecoder = next
        if (afterDrain == TrackEndAction.Reopen && nextDecoder != null) {
            current?.close()
            next = null
            nextTried = false
            // The mixer is set for the new rate only now, after the last song was heard out.
            val playing = openFor(nextDecoder, reuse = true)
            current = playing
            if (playing != null) {
                formatReopens++
                output?.play()
                listener.onDecoding(playing.track)
                return
            }
            listener.onSkipped(listOf(nextDecoder.track))
        }
        closeAll()
        heard = HeardPoint(null, 0)
        updateHeardTrack(null)
        listener.onFinished()
    }

    /** Opens the first upcoming track that will open, for the join. */
    private fun openNext() {
        nextTried = true
        val skipped = ArrayList<Track>()
        for (track in upcoming) {
            val decoder = Decoder.open(track, mayOpenDop = routes::mayOpenDop)
            if (decoder != null) {
                next = decoder
                break
            }
            skipped += track
        }
        if (skipped.isNotEmpty()) listener.onSkipped(skipped)
    }

    private fun seekTo(ms: Long) {
        val decoder = current ?: return
        val out = output ?: return
        draining = false
        out.pause()
        out.flush()
        decoder.seek(ms)
        resetCounting(openedFormat?.sampleRate ?: 44100)
        heard = HeardPoint(decoder.track, ms)
        if (isPlaying) out.play()
    }

    /** The output went away under us (a route change): reopened, carrying on from what was heard. */
    private fun reopenAfterLoss(decoder: Decoder) {
        val at = ReopenPoint.choose(decoder.track, heard, (decoder.positionSeconds * 1000).toLong())
        decoder.seek(at)
        current = null
        val playing = openFor(decoder, reuse = false)
        if (playing == null) {
            closeAll()
            listener.onFinished()
            return
        }
        current = playing
        heard = HeardPoint(playing.track, at)
        if (isPlaying) output?.play()
    }

    /**
     * Opens the output again the way [routes] now says (the route or the
     * setting changed), from where the song was heard. The song's decoder is
     * opened again too: DSD may go from PCM to DoP or back. Paused stays paused.
     */
    private fun reopenTheNewWay() {
        val decoder = current
        if (decoder == null || output == null) {
            if (output == null) routes.clearMixer()
            return
        }
        val at = ReopenPoint.choose(decoder.track, heard, (decoder.positionSeconds * 1000).toLong())
        next?.close()
        next = null
        nextTried = false
        draining = false
        decoder.close()
        current = null
        releaseOutput()
        val playing = Decoder.open(decoder.track, at, routes::mayOpenDop)?.let { openFor(it, reuse = false) }
        if (playing == null) {
            closeAll()
            listener.onSkipped(listOf(decoder.track))
            listener.onFinished()
            return
        }
        current = playing
        heard = HeardPoint(playing.track, at)
        if (isPlaying) output?.play()
    }

    private fun updateHeard() {
        if (output == null) return
        val point = timeline.lookup(head.read(output)) ?: return
        heard = HeardPoint(point.track, (point.seconds * 1000).toLong())
        updateHeardTrack(point.track)
    }

    private fun updateHeardTrack(track: Track?) {
        if (track !== lastHeardTrack) {
            lastHeardTrack = track
            listener.onHeard(track)
        }
    }

    private fun releaseOutput() {
        output?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            it.release()
        }
        output = null
        openedFormat = null
        askedFormat = null
        openedPath = OutputPath.Shared
    }

    private fun closeAll() {
        releaseOutput()
        routes.clearMixer()
        current?.close()
        current = null
        next?.close()
        next = null
        nextTried = false
        draining = false
        isPlaying = false
        pending.clear().limit(0)
    }

    /**
     * The `AudioTrack`'s playback head as a frame count that doesn't wrap (the
     * platform gives an unsigned 32-bit count) and starts at 0 when [reset].
     */
    private class Head {
        private var last = 0L
        private var total = 0L

        fun reset(track: AudioTrack?) {
            last = raw(track)
            total = 0
        }

        fun read(track: AudioTrack?): Long {
            val r = raw(track)
            total += (r - last) and 0xFFFFFFFFL
            last = r
            return total
        }

        private fun raw(track: AudioTrack?): Long =
            track?.let { runCatching { it.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrNull() } ?: last
    }
}
