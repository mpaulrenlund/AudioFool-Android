package com.mpaulrenlund.audiofool.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.updates.ApkDownload
import com.mpaulrenlund.audiofool.core.updates.AppRelease
import com.mpaulrenlund.audiofool.core.updates.AppVersion
import com.mpaulrenlund.audiofool.core.updates.GitHubReleases
import com.mpaulrenlund.audiofool.core.updates.UpdateException
import com.mpaulrenlund.audiofool.core.updates.UpdateRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The update check (spec 9; 2026-11-12, the user's requests). A tap on the menus' Version
 * row reads the repo's releases on GitHub ([checkNow]); a build newer than this one is
 * offered: "AudioFool 0.20.1 is available. You have 0.20.0." with Update and Cancel.
 * Otherwise a short [note] says there is none, or that GitHub couldn't be read. (Until
 * 0.20.0 the app looked by itself when it came into sight, at most every six hours, and
 * the Version row opened the releases page; the user swapped both for the tap.) Update
 * downloads the APK into the app's cache, checks it against GitHub's size and SHA-256, and
 * hands it to Android's installer, which asks the user to confirm (and, the first time, to
 * let AudioFool install apps). Cancel stops and closes. Nothing to port: the desktop has
 * no update check.
 *
 * Main thread only.
 */
@Stable
class Updates(
    private val context: Context,
    private val releases: GitHubReleases,
    private val download: ApkDownload,
    /** This build's version name. */
    private val current: String,
) {
    private val scope: CoroutineScope = MainScope()

    /** What the pop-up shows; null when it isn't up. */
    sealed interface Prompt {
        val release: AppRelease

        data class Offer(override val release: AppRelease, val current: String) : Prompt
        data class Downloading(override val release: AppRelease, val done: Long) : Prompt
        data class NeedsPermission(override val release: AppRelease) : Prompt
        data class Installing(override val release: AppRelease) : Prompt
        data class Failed(override val release: AppRelease, val reason: String) : Prompt
    }

    var prompt: Prompt? by mutableStateOf(null)
        private set

    /**
     * The short message a check shows instead of the pop-up (spec 9): while GitHub is
     * read, then that there's nothing newer or that it couldn't be read. The screen takes
     * the last two away after `update.noteMs` ([noteGone]).
     */
    sealed interface Note {
        data object Checking : Note
        data object UpToDate : Note
        data class CheckFailed(val reason: String) : Note
    }

    var note: Note? by mutableStateOf(null)
        private set

    private var checking = false
    private var job: Job? = null

    /** Android's confirmation, waiting until the app is in sight to be shown. */
    private var confirmation: Intent? = null
    private var inSight = false

    private val apkFile get() = File(context.cacheDir, "updates/AudioFool-update.apk")

    init {
        // Whatever an earlier run left: a download, or a session Android never finished.
        scope.launch(Dispatchers.IO) {
            apkFile.parentFile?.deleteRecursively()
            UpdateInstaller.abandonOwnSessions(context)
        }
    }

    // ---- the app's lifecycle (MainActivity) ----

    /** In sight: shows Android's confirmation if one is waiting, and goes on once installing apps is allowed. */
    fun inSight() {
        inSight = true
        confirmation?.let { showConfirmation(it) }
        (prompt as? Prompt.NeedsPermission)?.let { p ->
            if (context.packageManager.canRequestPackageInstalls() && apkFile.isFile) scope.launch { install(p.release) }
        }
    }

    fun outOfSight() {
        inSight = false
    }

    // ---- the menus' Version row ----

    /** Reads GitHub now: a newer build is offered, else a note says there's none or why it couldn't look. */
    fun checkNow() {
        if (checking || prompt != null) return
        val currentVersion = AppVersion.parse(current)
        if (currentVersion == null) {
            failed("this build's version \"$current\" isn't a release number")
            return
        }
        checking = true
        note = Note.Checking
        scope.launch {
            try {
                val offer = UpdateRules.offer(releases.list(), currentVersion)
                if (offer != null) {
                    note = null
                    prompt = Prompt.Offer(offer, current)
                } else {
                    note = Note.UpToDate
                }
            } catch (e: UpdateException) {
                failed(e.message ?: "unknown")
            } finally {
                checking = false
            }
        }
    }

    /** The screen's `update.noteMs` is up: the note goes, unless another has taken its place. */
    fun noteGone(gone: Note) {
        if (note == gone) note = null
    }

    private fun failed(reason: String) {
        // Not on the note, which is up for two seconds: in logcat, for a phone over USB.
        Log.w(TAG, "update check failed: $reason")
        note = Note.CheckFailed(reason)
    }

    // ---- the pop-up's buttons ----

    /** Update, or Try Again: the download, then Android's installer. */
    fun update() {
        val release = prompt?.release ?: return
        job?.cancel()
        prompt = Prompt.Downloading(release, 0)
        job = scope.launch {
            try {
                var shownMb = -1L
                download.fetch(release, apkFile) { done, _ ->
                    // On the IO thread, after every block: the line changes once a megabyte.
                    val mb = done / 1_000_000
                    if (mb != shownMb) {
                        shownMb = mb
                        scope.launch { if ((prompt as? Prompt.Downloading)?.release == release) prompt = Prompt.Downloading(release, done) }
                    }
                }
                install(release)
            } catch (e: UpdateException) {
                prompt = Prompt.Failed(release, e.message ?: "Couldn't download the update.")
            }
        }
    }

    /** Cancel (or system back): stops and closes. The next tap on Version offers the same build again. */
    fun cancel() {
        val shown = prompt ?: return
        job?.cancel()
        job = null
        confirmation = null
        prompt = null
        scope.launch(Dispatchers.IO) {
            apkFile.delete()
            if (shown is Prompt.Installing) UpdateInstaller.abandonOwnSessions(context)
        }
    }

    /** Open Settings: Android's "Install unknown apps" page for AudioFool; coming back with it on goes on. */
    fun openPermission() {
        val page = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun install(release: AppRelease) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            prompt = Prompt.NeedsPermission(release)
            return
        }
        prompt = Prompt.Installing(release)
        try {
            withContext(Dispatchers.IO) {
                UpdateInstaller.commit(context, apkFile)
                apkFile.delete()
            }
        } catch (e: Exception) {
            prompt = Prompt.Failed(release, "Android couldn't start the update: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ---- Android's installer (UpdateInstallReceiver) ----

    /** Android wants the user to confirm: shown now if the app is in sight, else when it comes back. */
    fun confirmationNeeded(intent: Intent) {
        if (prompt !is Prompt.Installing) return
        if (inSight) showConfirmation(intent) else confirmation = intent
    }

    private fun showConfirmation(intent: Intent) {
        confirmation = null
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** How the install ended (on success the process usually ends first). Turned down on Android's own screen: closes. */
    fun installEnded(status: Int, message: String?) {
        val shown = prompt as? Prompt.Installing ?: return
        prompt = when (status) {
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> null
            else -> Prompt.Failed(shown.release, "Android didn't install the update" + (message?.let { ": ${it.trimEnd('.')}." } ?: "."))
        }
    }

    companion object {
        const val TITLE = "Update Available"
        private const val TAG = "AudioFoolUpdate"

        /** The note's one line (the user's words, 2026-11-12: "no new version available"). */
        fun noteText(note: Note): String = when (note) {
            Note.Checking -> "Checking for updates…"
            Note.UpToDate -> "No new version available"
            is Note.CheckFailed -> "Couldn't check for updates"
        }

        /** The pop-up's text for each step. */
        fun message(prompt: Prompt): String = when (prompt) {
            is Prompt.Offer -> "AudioFool ${prompt.release.version} is available. You have ${prompt.current}."
            is Prompt.Downloading -> "Downloading AudioFool ${prompt.release.version}… ${UpdateRules.progress(prompt.done, prompt.release.apkSize)}"
            is Prompt.NeedsPermission -> "Android needs your OK before AudioFool can install updates. Open Settings, allow it, then come back here."
            is Prompt.Installing -> "Android asks you to confirm the update. AudioFool closes while it installs."
            is Prompt.Failed -> prompt.reason
        }
    }
}
