package com.mpaulrenlund.audiofool

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.library.DriveChange
import com.mpaulrenlund.audiofool.lastfm.LastFm
import com.mpaulrenlund.audiofool.library.LibraryRepository
import com.mpaulrenlund.audiofool.settings.AppSettings
import com.mpaulrenlund.audiofool.settings.MusicFolder
import com.mpaulrenlund.audiofool.storage.Storage
import com.mpaulrenlund.audiofool.theme.AppTokens
import com.mpaulrenlund.audiofool.art.Thumbnails
import com.mpaulrenlund.audiofool.core.playback.ResumePoint
import com.mpaulrenlund.audiofool.playback.NowPlaying
import com.mpaulrenlund.audiofool.playback.OutputRoutes
import com.mpaulrenlund.audiofool.playback.PlaybackService
import com.mpaulrenlund.audiofool.playback.Player
import com.mpaulrenlund.audiofool.playback.SystemControls
import com.mpaulrenlund.audiofool.playback.WaveformReader
import com.mpaulrenlund.audiofool.ui.ArtSources
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * The process: settings, tokens and the library, made once. The library cache
 * starts loading here, before the first screen, so a cold start can show it at once.
 */
class AudioFoolApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: AppSettings
        private set
    lateinit var library: LibraryRepository
        private set
    val tokens: AppTokens by lazy { AppTokens.get(assets) }
    val startTimes = StartTimes()
    val nowPlaying = NowPlaying()

    /** The queue and playback (spec 6.1); the output stage starts on the first play. */
    val player: Player by lazy {
        Player(
            context = this,
            state = nowPlaying,
            scope = scope,
            resumePath = File(noBackupFilesDir, "resume.json").path,
            waveforms = WaveformReader(File(noBackupFilesDir, "waveforms"), tokens.mobile.number("nowPlaying.waveformBars").toInt()),
            routes = OutputRoutes(this),
            settings = settings,
        ).also { p ->
            p.watchers.add(lastFm)
            p.onChange = {
                systemControls.update()
                PlaybackService.sync(this, p.state.isPlaying)
            }
        }
    }

    /** Last.fm (spec 7): the connection Settings shows, and the scrobbles, queued in the app's own storage. */
    val lastFm: LastFm by lazy { LastFm(this, noBackupFilesDir) }

    /** The media session (lock screen, notification, headset buttons), made with the first queue. */
    val systemControls: SystemControls by lazy {
        SystemControls(this, player, scope).also { it.onArt = { PlaybackService.running?.update() } }
    }

    /**
     * Cover thumbnails (spec 8), kept in the app's own storage: the lists' size
     * (`albums.thumb`, which Artists and the mini player shrink from) and the
     * album header's (`album.art`), in pixels for this screen.
     */
    val art: ArtSources by lazy {
        val density = resources.displayMetrics.density
        fun px(path: String) = (tokens.mobile.number(path) * density).roundToInt()
        val folder = File(noBackupFilesDir, "art")
        ArtSources(Thumbnails(folder, px("albums.thumb"), scope), Thumbnails(folder, px("album.art"), scope))
    }

    private var libraryStarted = false

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        library = LibraryRepository(
            cachePath = File(noBackupFilesDir, "library.bin").path,
            scope = scope,
            folders = { settings.folders.value },
        )
        scope.launch(Dispatchers.IO) { tokens }
        startLibrary()
        prefillCovers()
        restoreQueue()

        Storage.watchVolumes(this) { arrived ->
            val folders = settings.folders.value.filter { it.included }.map { it.path }
            val unavailable = library.state.value.unavailableFolders
            if (DriveChange.worthRefresh(folders, unavailable, arrived) { File(it).isDirectory }) library.rescan()
        }
    }

    /**
     * After each scan, makes the small cover copies the lists will need, newest
     * albums first (what Artists shows), then the rest; and once a run, drops
     * copies unused for 90 days (the desktop's ArtDiskCache.Prune).
     */
    private fun prefillCovers() {
        scope.launch {
            art.list.prune()
            library.state
                .filter { !it.scanning && it.library != null }
                .map { it.library!! }
                .distinctUntilChanged { a, b -> a === b }
                .collect { lib ->
                    val newest = lib.artists.mapNotNull { it.newestAlbum }
                    val seen = newest.toHashSet()
                    art.list.prefill(newest + lib.artists.flatMap { it.albums }.filter { it !in seen })
                }
        }
    }

    /**
     * Reopens to last time's queue, paused where it was (spec 6.1, Resume), once the
     * first library (the cache, usually) is there to find its songs in.
     */
    private fun restoreQueue() {
        scope.launch {
            val point = withContext(Dispatchers.IO) { player.readResumePoint() } ?: return@launch
            val lib = library.state.filter { it.library?.allTracks?.isNotEmpty() == true }.map { it.library!! }.first()
            val lookup = ResumePoint.lookupIn(lib.allTracks)
            withContext(Dispatchers.Main) { player.restore(point, lookup) }
        }
    }

    /**
     * Starts the library once there is something to read: all-files access, and
     * a folder list (the volumes' Music folders until the user picks others).
     */
    fun startLibrary() {
        if (libraryStarted || !Storage.hasAllFilesAccess()) return
        libraryStarted = true
        if (!settings.foldersChosen) {
            settings.setFolders(Storage.defaultMusicFolders(this).map { MusicFolder(it) })
        }
        scope.launch(Dispatchers.IO) { library.start() }
    }
}

/**
 * Start-up times for the debug screen, on the uptime clock. Cold: the process
 * started and the first frame showed the library. Warm: the process was already
 * running when the screen was created.
 */
class StartTimes {
    // Compose state, so the debug screen shows them as soon as they are known.
    var coldStartMs: Long? by mutableStateOf(null)
        private set
    var warmStartMs: Long? by mutableStateOf(null)
        private set
    var warmStarts: Int by mutableIntStateOf(0)
        private set
    @Volatile private var activityCreatedAt: Long = 0
    @Volatile private var coldPending = true

    fun activityCreated() {
        activityCreatedAt = SystemClock.uptimeMillis()
    }

    /** The user had to grant access first, so this process's start says nothing about speed. */
    fun noColdStart() {
        coldPending = false
    }

    /** The first frame with a library on it has been drawn. */
    fun libraryShown() {
        val now = SystemClock.uptimeMillis()
        if (coldPending) {
            coldPending = false
            coldStartMs = now - android.os.Process.getStartUptimeMillis()
        } else if (activityCreatedAt != 0L) {
            warmStartMs = now - activityCreatedAt
            warmStarts++
        }
        activityCreatedAt = 0
    }
}
