package com.mpaulrenlund.audiofool.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Storage the way a sideloaded player uses it (spec 1): all-files access, and
 * plain folder paths on internal storage and SD cards.
 */
object Storage {
    /** Whether the user has given the app all-files access. */
    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    /** The system page where the user turns all-files access on for this app. */
    fun allFilesAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    /** Each mounted volume's root: internal storage first, then SD cards. */
    fun volumeRoots(context: Context): List<File> =
        context.getSystemService(StorageManager::class.java).storageVolumes
            .filter { it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY }
            .sortedBy { if (it.isPrimary) 0 else 1 }
            .mapNotNull { it.directory }

    /**
     * The folders to start with when none has been chosen: each volume's Music
     * folder, where there is one.
     */
    fun defaultMusicFolders(context: Context): List<String> =
        volumeRoots(context).map { File(it, Environment.DIRECTORY_MUSIC) }.filter { it.isDirectory }.map { it.path }

    /**
     * A folder picked with the system's folder picker, as a plain path:
     * "primary:Music" is internal storage, "1A2B-3C4D:Music" the SD card of that
     * id. Null for a folder not on local storage (a cloud provider).
     */
    fun pathFromTree(context: Context, tree: Uri): String? {
        if (tree.authority != "com.android.externalstorage.documents") return null
        val id = DocumentsContract.getTreeDocumentId(tree)
        val volume = id.substringBefore(':')
        val relative = id.substringAfter(':', "")
        val root = context.getSystemService(StorageManager::class.java).storageVolumes.firstOrNull {
            if (volume == "primary") it.isPrimary else it.uuid.equals(volume, ignoreCase = true)
        }?.directory ?: File("/storage/$volume")
        return if (relative.isEmpty()) root.path else File(root, relative).path
    }

    /** /storage/1A2B-3C4D/...: where Android mounts a card, by its volume id. */
    private val removableCardPath = Regex("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}(/|$)")

    /**
     * "SD card · Music" or "Internal storage · Music" (spec 4.5): where the folder
     * is and its name.
     */
    fun describe(context: Context, path: String): String {
        val volume = context.getSystemService(StorageManager::class.java).storageVolumes
            .firstOrNull { v -> v.directory?.let { path == it.path || path.startsWith(it.path + "/") } == true }
        val where = when {
            // A removed card isn't listed, but its id is still in the path.
            volume == null && removableCardPath.containsMatchIn(path) -> "SD card"
            volume == null -> "Storage"
            volume.isPrimary -> "Internal storage"
            else -> "SD card"
        }
        val name = File(path).name.ifEmpty { path }
        return if (volume?.directory?.path == path) where else "$where · $name"
    }

    /**
     * Calls [onChange] (with true when storage arrived) whenever a volume is
     * mounted, removed or ejected, so the library can tell a removed SD card
     * from a deleted folder (DriveChange).
     */
    fun watchVolumes(
        context: Context,
        onEjecting: (root: String) -> Unit = {},
        onChange: (arrived: Boolean) -> Unit,
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Eject comes first, about 250 ms before vold ends every process still
                // holding a file open on the volume (seen on the emulator, session 5).
                if (intent.action == Intent.ACTION_MEDIA_EJECT) intent.data?.path?.let(onEjecting)
                onChange(intent.action == Intent.ACTION_MEDIA_MOUNTED)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        return receiver
    }
}
