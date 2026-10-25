package com.mpaulrenlund.audiofool.core.scrobbling

import com.mpaulrenlund.audiofool.core.library.SafeFile
import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import java.io.File
import java.net.URLDecoder
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Ported from the desktop's tests/AudioFool.Core.Tests/ScrobblingTests.cs.
// Times are milliseconds; the desktop's 0.25 s ticks are 250 ms ones.

class ScrobblingTest {
    private val t0 = 1_790_596_800_000L // 2026-09-28 12:00 UTC

    private fun makeTrack(
        title: String = "Tom Sawyer",
        artist: String = "Rush",
        albumArtist: String = "Rush",
        path: String = "/sdcard/Music/Rush/01.flac",
    ) = Track(
        filePath = path,
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        album = "Moving Pictures",
        trackNumber = 1,
        durationMs = 270_000,
    )

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Plays from [fromMs] to [toMs] in 250 ms ticks. */
    private fun play(tracker: PlayTracker, fromMs: Long, toMs: Long, startMs: Long): List<AdvanceResult> {
        val results = ArrayList<AdvanceResult>()
        var s = fromMs
        while (s <= toMs) {
            results.add(tracker.advance(s, startMs + (s - fromMs)))
            s += 250
        }
        return results
    }

    // ------------------------------------------------------------ signing

    @Test fun signature_sorts_names_skips_format_and_appends_secret() {
        val sig = LastFmApi.sign(
            listOf("token" to "tok", "method" to "auth.getSession", "format" to "json", "api_key" to "key"),
            "secret",
        )
        assertEquals(md5("api_keykeymethodauth.getSessiontokentoksecret"), sig)
    }

    @Test fun signature_orders_array_parameters_ordinally_and_hashes_utf8() {
        val sig = LastFmApi.sign(
            listOf("artist[0]" to "Björk", "albumArtist[0]" to "Björk", "album[0]" to "Homogenic"),
            "s",
        )
        // 'A' (0x41) sorts before '[' (0x5B), so albumArtist[0] precedes album[0].
        assertEquals(md5("albumArtist[0]Björkalbum[0]Homogenicartist[0]Björks"), sig)
    }

    @Test fun authorize_url_carries_key_and_token() {
        assertEquals("https://www.last.fm/api/auth/?api_key=abc&token=t%2B1", LastFmApi.authorizeUrl(" abc ", "t+1"))
    }

    // ------------------------------------------------------------ entries

    @Test fun entry_uses_track_artist_and_sends_album_artist_separately() {
        val e = assertNotNull(ScrobbleEntry.from(makeTrack(artist = "Elton John feat. Kiki Dee", albumArtist = "Elton John"), 270_400, t0))

        assertEquals("Elton John feat. Kiki Dee", e.artist)
        assertEquals("Elton John", e.albumArtist)
        assertEquals(270, e.durationSeconds)
        assertEquals(t0 / 1000, e.timestamp)
    }

    @Test fun entry_falls_back_to_album_artist() {
        assertEquals("Rush", ScrobbleEntry.from(makeTrack(artist = " "), 240_000, t0)!!.artist)
    }

    @Test fun entry_needs_a_title_and_an_artist() {
        assertNull(ScrobbleEntry.from(makeTrack("", "Rush", "Rush"), 240_000, t0))
        assertNull(ScrobbleEntry.from(makeTrack("Tom Sawyer", "", ""), 240_000, t0))
    }

    // ------------------------------------------------------------ rules

    @Test fun threshold_is_half_or_four_minutes() {
        assertNull(PlayTracker.threshold(20_000))
        assertNull(PlayTracker.threshold(30_000))
        assertEquals(15_500, PlayTracker.threshold(31_000))
        assertEquals(150_000, PlayTracker.threshold(300_000))
        assertEquals(240_000, PlayTracker.threshold(480_000))
        assertEquals(240_000, PlayTracker.threshold(1_200_000))
    }

    @Test fun a_play_scrobbles_once_at_the_threshold_with_its_start_time() {
        val tracker = PlayTracker()
        assertTrue(tracker.start(makeTrack(), 200_000, t0))

        val results = play(tracker, 0, 199_000, t0)
        val due = results.withIndex().filter { it.value.due != null }

        assertEquals(1, due.size)
        assertEquals(100 * 4, due[0].index) // tick at 100 s
        assertEquals(t0 / 1000, due[0].value.due!!.timestamp)
    }

    @Test fun seeking_forward_is_not_listening() {
        val tracker = PlayTracker()
        tracker.start(makeTrack(), 200_000, t0)

        play(tracker, 0, 10_000, t0)
        val after = play(tracker, 180_000, 199_000, t0 + 11_000)

        assertTrue(after.all { it.due == null })
        assertTrue(tracker.played < 31_000)
    }

    @Test fun pausing_adds_nothing() {
        val tracker = PlayTracker()
        tracker.start(makeTrack(), 200_000, t0)
        play(tracker, 0, 50_000, t0)

        for (i in 0 until 1000) tracker.advance(50_000, t0 + (60 + i) * 1000L)

        assertEquals(50_000, tracker.played)
    }

    @Test fun a_jump_back_to_the_start_is_a_new_play_and_scrobbles_again() {
        val tracker = PlayTracker()
        tracker.start(makeTrack(), 100_000, t0)
        val first = play(tracker, 0, 99_750, t0)

        // Repeat-One: the position simply wraps round.
        val loopAt = t0 + 100_000
        val wrap = tracker.advance(250, loopAt)
        val second = play(tracker, 500, 99_750, loopAt + 250)

        assertEquals(1, first.count { it.due != null })
        assertTrue(wrap.restarted)
        val again = second.single { it.due != null }.due!!
        assertEquals((loopAt - 250) / 1000, again.timestamp)
    }

    @Test fun seeking_back_mid_track_is_not_a_restart() {
        val tracker = PlayTracker()
        tracker.start(makeTrack(), 200_000, t0)
        play(tracker, 0, 60_000, t0)

        assertFalse(tracker.advance(30_000, t0 + 61_000).restarted)
        assertEquals(60_000, tracker.played)
    }

    @Test fun the_same_file_starting_again_before_scrobbling_continues_the_play() {
        val tracker = PlayTracker()
        tracker.start(makeTrack(), 200_000, t0)
        play(tracker, 0, 60_000, t0)

        // Switching Bit-Perfect reopens the current track from the top.
        assertFalse(tracker.start(makeTrack(), 200_000, t0 + 61_000))
        val due = play(tracker, 0, 40_000, t0 + 61_000).single { it.due != null }.due!!

        assertEquals(t0 / 1000, due.timestamp)
    }

    @Test fun another_track_starts_a_new_play() {
        val tracker = PlayTracker()
        tracker.start(makeTrack(), 200_000, t0)
        play(tracker, 0, 60_000, t0)

        assertTrue(tracker.start(makeTrack(path = "/sdcard/Music/Rush/02.flac"), 200_000, t0 + 61_000))
        assertEquals(0, tracker.played)
    }

    @Test fun short_tracks_never_scrobble() {
        val tracker = PlayTracker()
        tracker.start(makeTrack(), 30_000, t0)

        assertTrue(play(tracker, 0, 30_000, t0).all { it.due == null })
    }

    @Test fun after_stop_nothing_scrobbles() {
        val tracker = PlayTracker()
        tracker.start(makeTrack(), 200_000, t0)
        play(tracker, 0, 50_000, t0)
        tracker.stop()

        assertTrue(play(tracker, 50_000, 150_000, t0).all { it.due == null })
    }

    @Test fun retagging_mid_play_scrobbles_the_new_tags_without_restarting() {
        val tracker = PlayTracker()
        tracker.start(makeTrack(), 200_000, t0)
        play(tracker, 0, 60_000, t0)

        assertTrue(tracker.retag(makeTrack(title = "Tom Sawyer (Live)", artist = "Rush.")))
        assertEquals(60_000, tracker.played)

        val due = play(tracker, 60_250, 150_000, t0 + 60_250).single { it.due != null }.due!!
        assertEquals("Tom Sawyer (Live)", due.track)
        assertEquals("Rush.", due.artist)
        assertEquals(t0 / 1000, due.timestamp)
    }

    @Test fun retagging_another_file_changes_nothing() {
        val tracker = PlayTracker()
        tracker.start(makeTrack(), 200_000, t0)

        assertFalse(tracker.retag(makeTrack(title = "Red Barchetta", path = "/sdcard/Music/Rush/02.flac")))
        assertEquals("Tom Sawyer", tracker.current!!.title)

        tracker.stop()
        assertFalse(tracker.retag(makeTrack(title = "Limelight")))
        assertNull(tracker.current)
    }

    // ------------------------------------------------------------ responses

    @Test fun batch_outcomes_read_each_ignored_code() {
        val root = LastFmApi.parse(
            """
            {"scrobbles":{"@attr":{"accepted":1,"ignored":2},"scrobble":[
              {"ignoredMessage":{"code":"0","#text":""}},
              {"ignoredMessage":{"code":"3","#text":"Timestamp too old"}},
              {"ignoredMessage":{"code":"5","#text":"Daily limit"}}]}}
            """,
        )
        assertEquals(
            listOf(ScrobbleOutcome.Accepted, ScrobbleOutcome.Ignored, ScrobbleOutcome.Deferred),
            LastFmApi.parseScrobbleOutcomes(root, 3),
        )
    }

    @Test fun a_batch_of_one_comes_back_as_an_object() {
        val root = LastFmApi.parse(
            """
            {"scrobbles":{"@attr":{"accepted":0,"ignored":1},"scrobble":
              {"ignoredMessage":{"code":"1","#text":"Artist ignored"}}}}
            """,
        )
        assertEquals(listOf(ScrobbleOutcome.Ignored), LastFmApi.parseScrobbleOutcomes(root, 1))
    }

    @Test fun a_numeric_ignored_code_is_read_too() {
        val root = LastFmApi.parse("""{"scrobbles":{"scrobble":[{"ignoredMessage":{"code":5}}]}}""")
        assertEquals(listOf(ScrobbleOutcome.Deferred), LastFmApi.parseScrobbleOutcomes(root, 1))
    }

    // ------------------------------------------------------------ queue

    private fun tempQueuePath(): String =
        File(Files.createTempDirectory("AudioFoolTests").toFile(), "scrobbles.json").path

    private fun entry(track: String, atMs: Long) = ScrobbleEntry(artist = "Rush", track = track, timestamp = atMs / 1000)

    @Test fun queue_survives_a_reload_and_drops_what_is_too_old() {
        val path = tempQueuePath()
        val q = ScrobbleQueue.load(path, t0)
        q.add(entry("old", t0 - 15L * 24 * 3600 * 1000))
        q.add(entry("recent", t0 - 2L * 24 * 3600 * 1000))

        val reloaded = ScrobbleQueue.load(path, t0)

        assertEquals(listOf("recent"), reloaded.peek(50).map { it.track })
        assertEquals(1, ScrobbleQueue.load(path, t0).count)
    }

    @Test fun queue_removes_only_what_was_sent() {
        val q = ScrobbleQueue.load(tempQueuePath(), t0)
        q.add(entry("a", t0))
        val batch = q.peek(50)
        q.add(entry("b", t0))

        q.remove(batch)

        assertEquals(listOf("b"), q.peek(50).map { it.track })
    }

    @Test fun queue_removes_by_identity_not_by_equal_data() {
        val q = ScrobbleQueue.load(tempQueuePath(), t0)
        q.add(entry("a", t0))
        q.add(entry("a", t0))

        q.remove(q.peek(1))

        assertEquals(1, q.count)
    }

    @Test fun an_unreadable_queue_file_is_an_empty_queue() {
        val path = tempQueuePath()
        File(path).writeText("{ not json")

        assertEquals(0, ScrobbleQueue.load(path, t0).count)
    }

    @Test fun a_damaged_queue_file_is_read_from_the_save_before() {
        val path = tempQueuePath()
        val q = ScrobbleQueue.load(path, t0)
        q.add(entry("a", t0))
        q.add(entry("b", t0))
        assertTrue(File(SafeFile.backupPathFor(path)).exists())

        // Cut short, as a crash mid-save could leave it.
        File(path).writeText("[{\"artist\":\"Ru")

        val reloaded = ScrobbleQueue.load(path, t0)
        assertEquals(listOf("a"), reloaded.peek(50).map { it.track })

        // Written again from the backup, so the next start reads the file itself.
        assertEquals(listOf("a"), ScrobbleQueue.load(path, t0).peek(50).map { it.track })
    }

    // ------------------------------------------------------------ scrobbler

    private class ManualClock(@Volatile var now: Long)

    /** Answers Last.fm calls by method name and records every form it was sent. */
    private class FakeLastFm : LastFmTransport {
        val calls = ArrayList<Map<String, String>>()

        @Volatile var answer: (Map<String, String>) -> HttpAnswer = { HttpAnswer(200, "{}") }

        override fun post(url: String, form: String): HttpAnswer {
            val fields = form.split('&').associate {
                val (name, value) = it.split('=', limit = 2)
                URLDecoder.decode(name, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
            }
            synchronized(calls) { calls.add(fields) }
            return answer(fields)
        }

        fun of(method: String): List<Map<String, String>> = synchronized(calls) { calls.filter { it["method"] == method } }

        val count: Int get() = synchronized(calls) { calls.size }
    }

    private fun until(condition: () -> Boolean) {
        var i = 0
        while (i < 200 && !condition()) {
            Thread.sleep(10)
            i++
        }
        assertTrue(condition())
    }

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun connected(): Triple<LastFmScrobbler, FakeLastFm, ManualClock> {
        val fake = FakeLastFm()
        val clock = ManualClock(t0)
        val scrobbler = LastFmScrobbler(ScrobbleQueue.load(tempQueuePath(), t0), scope(), { clock.now }) { key, secret ->
            LastFmApi(key, secret, fake)
        }
        scrobbler.connect("key", "secret", LastFmSession("marcus", "sk1"))
        return Triple(scrobbler, fake, clock)
    }

    private fun playThrough(scrobbler: LastFmScrobbler, clock: ManualClock, toMs: Long, fromMs: Long = 250) {
        var s = fromMs
        while (s <= toMs) {
            clock.now = t0 + s
            scrobbler.advance(s)
            s += 250
        }
    }

    @Test fun a_play_sends_now_playing_then_one_signed_scrobble() {
        val (scrobbler, fake, clock) = connected()

        scrobbler.trackStarted(makeTrack(), 200_000)
        playThrough(scrobbler, clock, 150_000)

        until { fake.of("track.scrobble").size == 1 && scrobbler.pending == 0 }
        val nowPlaying = fake.of("track.updateNowPlaying").single()
        assertEquals("Tom Sawyer", nowPlaying["track"])

        val sent = fake.of("track.scrobble")[0]
        assertEquals("Rush", sent["artist[0]"])
        assertEquals("Rush", sent["albumArtist[0]"])
        assertEquals((t0 / 1000).toString(), sent["timestamp[0]"])
        assertEquals("sk1", sent["sk"])

        val signed = sent.filterKeys { it != "api_sig" && it != "format" }.toList()
        assertEquals(LastFmApi.sign(signed, "secret"), sent["api_sig"])
    }

    @Test fun a_failed_send_keeps_the_scrobble_and_retries_after_the_backoff() {
        val (scrobbler, fake, clock) = connected()
        fake.answer = {
            if (it["method"] == "track.scrobble") HttpAnswer(503, """{"error":16,"message":"Temporarily unavailable"}""")
            else HttpAnswer(200, "{}")
        }

        scrobbler.trackStarted(makeTrack(), 200_000)
        playThrough(scrobbler, clock, 101_000)
        until { scrobbler.lastError != null }

        // Still inside the one-minute backoff: no second attempt.
        playThrough(scrobbler, clock, 130_000, fromMs = 101_250)
        Thread.sleep(50)
        assertEquals(1, fake.of("track.scrobble").size)
        assertEquals(1, scrobbler.pending)

        fake.answer = { HttpAnswer(200, "{}") }
        clock.now = t0 + 3 * 60_000
        scrobbler.advance(130_250)

        until { scrobbler.pending == 0 }
        assertNull(scrobbler.lastError)
    }

    @Test fun a_rejected_session_stops_sending_and_keeps_the_queue() {
        val (scrobbler, fake, clock) = connected()
        fake.answer = {
            if (it["method"] == "track.scrobble") HttpAnswer(403, """{"error":9,"message":"Invalid session key"}""")
            else HttpAnswer(200, "{}")
        }
        val failures = ArrayList<String>()
        scrobbler.onAuthFailed = { synchronized(failures) { failures.add(it) } }

        scrobbler.trackStarted(makeTrack(), 200_000)
        playThrough(scrobbler, clock, 101_000)
        until { scrobbler.needsReconnect }

        clock.now = t0 + 2 * 3600_000
        scrobbler.advance(101_250)
        Thread.sleep(50)

        assertEquals(1, fake.of("track.scrobble").size)
        assertEquals(1, synchronized(failures) { failures.size })
        assertEquals(1, scrobbler.pending)
    }

    @Test fun ticks_from_the_next_track_before_its_change_are_skipped() {
        val (scrobbler, fake, clock) = connected()
        val first = makeTrack()
        val next = makeTrack(title = "Red Barchetta", path = "/sdcard/Music/Rush/02.flac")

        scrobbler.trackStarted(first, 200_000)
        playThrough(scrobbler, clock, 199_750)
        until { fake.of("track.scrobble").size == 1 }

        // Gapless join: the next track is heard, its position near zero, but the
        // player hasn't said so yet.
        scrobbler.advance(250, next)
        scrobbler.advance(500, next)
        Thread.sleep(50)

        assertEquals(1, fake.of("track.updateNowPlaying").size)
    }

    @Test fun retagging_the_playing_track_resends_now_playing_and_scrobbles_the_new_tags() {
        val (scrobbler, fake, clock) = connected()

        scrobbler.trackStarted(makeTrack(), 200_000)
        playThrough(scrobbler, clock, 40_000)
        until { fake.of("track.updateNowPlaying").size == 1 }

        scrobbler.trackRetagged(makeTrack(title = "Tom Sawyer (Live)"))
        until { fake.of("track.updateNowPlaying").size == 2 }
        assertEquals("Tom Sawyer (Live)", fake.of("track.updateNowPlaying")[1]["track"])

        playThrough(scrobbler, clock, 150_000, fromMs = 40_250)
        until { fake.of("track.scrobble").size == 1 && scrobbler.pending == 0 }
        val sent = fake.of("track.scrobble")[0]
        assertEquals("Tom Sawyer (Live)", sent["track[0]"])
        assertEquals((t0 / 1000).toString(), sent["timestamp[0]"])
    }

    @Test fun retagging_with_nothing_last_fm_shows_changed_sends_nothing() {
        val (scrobbler, fake, _) = connected()

        scrobbler.trackStarted(makeTrack(), 200_000)
        until { fake.of("track.updateNowPlaying").size == 1 }

        scrobbler.trackRetagged(makeTrack())
        scrobbler.trackRetagged(makeTrack(title = "Red Barchetta", path = "/sdcard/Music/Rush/02.flac"))
        Thread.sleep(50)

        assertEquals(1, fake.of("track.updateNowPlaying").size)
    }

    @Test fun state_follows_connection_switch_failures_and_rejection() {
        val unconnected = LastFmScrobbler(ScrobbleQueue.load(tempQueuePath(), t0), scope())
        assertEquals(ScrobblerState.Disconnected, unconnected.state)

        val (scrobbler, fake, clock) = connected()
        var changes = 0
        scrobbler.onStatusChanged = { changes++ }
        assertEquals(ScrobblerState.Scrobbling, scrobbler.state)

        scrobbler.enabled = false
        assertEquals(ScrobblerState.Off, scrobbler.state)
        assertEquals(1, changes)
        scrobbler.enabled = true

        fake.answer = {
            if (it["method"] == "track.scrobble") HttpAnswer(503, """{"error":11,"message":"Service offline"}""")
            else HttpAnswer(200, "{}")
        }
        scrobbler.trackStarted(makeTrack(), 200_000)
        playThrough(scrobbler, clock, 101_000)
        until { scrobbler.state == ScrobblerState.Failing }

        // The failed flush may still be unwinding, and a flush in progress makes
        // another a no-op, so keep asking until one runs.
        fake.answer = { HttpAnswer(200, "{}") }
        until {
            scrobbler.flush()
            scrobbler.state == ScrobblerState.Scrobbling
        }

        fake.answer = {
            if (it["method"] == "track.scrobble") HttpAnswer(403, """{"error":9,"message":"Invalid session key"}""")
            else HttpAnswer(200, "{}")
        }
        scrobbler.trackStarted(makeTrack(path = "/sdcard/Music/Rush/02.flac"), 200_000)
        playThrough(scrobbler, clock, 101_000)
        until { scrobbler.state == ScrobblerState.NeedsReconnect }
    }

    @Test fun switched_off_nothing_is_sent_or_queued() {
        val (scrobbler, fake, clock) = connected()
        scrobbler.enabled = false

        scrobbler.trackStarted(makeTrack(), 200_000)
        playThrough(scrobbler, clock, 150_000)
        Thread.sleep(50)

        assertEquals(0, fake.count)
        assertEquals(0, scrobbler.pending)
    }

    @Test fun the_daily_limit_keeps_the_scrobble_queued() {
        val (scrobbler, fake, clock) = connected()
        fake.answer = {
            if (it["method"] == "track.scrobble")
                HttpAnswer(200, """{"scrobbles":{"scrobble":{"ignoredMessage":{"code":"5","#text":"Daily limit"}}}}""")
            else HttpAnswer(200, "{}")
        }

        scrobbler.trackStarted(makeTrack(), 200_000)
        playThrough(scrobbler, clock, 101_000)
        until { scrobbler.lastSentAtMs != null }

        assertEquals(1, scrobbler.pending)
    }

    // ------------------------------------------------------------ the api's errors

    @Test fun an_error_answer_becomes_a_last_fm_exception_with_its_code() {
        val api = LastFmApi("k", "s") { _, _ -> HttpAnswer(400, """{"error":14,"message":"Unauthorized Token"}""") }
        val e = assertFailsWith<LastFmException> { kotlinx.coroutines.runBlocking { api.getSession("tok") } }
        assertTrue(e.isTokenNotYetAuthorised)
    }

    @Test fun an_answer_without_json_is_transient() {
        val api = LastFmApi("k", "s") { _, _ -> HttpAnswer(502, "<html>Bad gateway</html>") }
        val e = assertFailsWith<LastFmException> { kotlinx.coroutines.runBlocking { api.getToken() } }
        assertTrue(e.isTransient)
    }

    @Test fun a_session_is_read_with_its_user_name() {
        val api = LastFmApi("k", "s") { _, _ -> HttpAnswer(200, """{"session":{"name":"marcus","key":"sk9","subscriber":0}}""") }
        assertEquals(LastFmSession("marcus", "sk9"), kotlinx.coroutines.runBlocking { api.getSession("tok") })
    }
}
