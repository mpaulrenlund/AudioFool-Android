package com.mpaulrenlund.audiofool

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.library.DriveChange
import com.mpaulrenlund.audiofool.library.LibraryRepository
import com.mpaulrenlund.audiofool.settings.AppSettings
import com.mpaulrenlund.audiofool.settings.MusicFolder
import com.mpaulrenlund.audiofool.storage.Storage
import com.mpaulrenlund.audiofool.theme.AppTokens
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

        Storage.watchVolumes(this) { arrived ->
            val folders = settings.folders.value.filter { it.included }.map { it.path }
            val unavailable = library.state.value.unavailableFolders
            if (DriveChange.worthRefresh(folders, unavailable, arrived) { File(it).isDirectory }) library.rescan()
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
