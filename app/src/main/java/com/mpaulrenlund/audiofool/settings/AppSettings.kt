package com.mpaulrenlund.audiofool.settings

import android.content.Context
import android.content.SharedPreferences
import com.mpaulrenlund.audiofool.core.library.MusicFolderList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One entry in the music folder list (spec 4.5): a plain path, included or left out. */
@Serializable
data class MusicFolder(val path: String, val included: Boolean = true)

/**
 * The app's settings, in SharedPreferences: small, read once at start (the theme
 * must be known before the first frame) and written when they change.
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _darkTheme = MutableStateFlow(prefs.getString(KEY_THEME, null) != THEME_LIGHT)

    /** Dark when nothing is saved (spec 2). */
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    fun setDarkTheme(dark: Boolean) {
        _darkTheme.value = dark
        prefs.edit().putString(KEY_THEME, if (dark) THEME_DARK else THEME_LIGHT).apply()
    }

    private val _bitPerfect = MutableStateFlow(prefs.getBoolean(KEY_BIT_PERFECT, false))

    /** Bit-Perfect (spec 6.2): off when nothing is saved, which means the normal shared output. */
    val bitPerfect: StateFlow<Boolean> = _bitPerfect.asStateFlow()

    fun setBitPerfect(on: Boolean) {
        _bitPerfect.value = on
        prefs.edit().putBoolean(KEY_BIT_PERFECT, on).apply()
    }

    /**
     * The last headphone jack a FiiO player named (its `HEADSET_TYPE_CHANGED` `type`),
     * or -1 when none has: FiiO says only at a plug, so a fresh start shows this one
     * until it says again (0.2.5, the user's call).
     */
    var fiioJack: Int
        get() = prefs.getInt(KEY_FIIO_JACK, -1)
        set(value) = prefs.edit().putInt(KEY_FIIO_JACK, value).apply()

    private val _folders = MutableStateFlow(readFolders())

    /** The music folders, in the order they were added. */
    val folders: StateFlow<List<MusicFolder>> = _folders.asStateFlow()

    /** Whether a folder list has ever been saved; until then the app offers the usual places. */
    val foldersChosen: Boolean get() = prefs.contains(KEY_FOLDERS)

    fun setFolders(folders: List<MusicFolder>) {
        // One entry per folder, whichever way it was written (MusicFolderList, from the desktop).
        val distinct = MusicFolderList.distinct(folders.map { it.path })
            .map { path -> folders.first { MusicFolderList.sameFolder(it.path, path) }.copy(path = path) }
        _folders.value = distinct
        prefs.edit().putString(KEY_FOLDERS, json.encodeToString(distinct)).apply()
    }

    fun addFolder(path: String) = setFolders(folders.value + MusicFolder(path))

    fun setIncluded(path: String, included: Boolean) =
        setFolders(folders.value.map { if (MusicFolderList.sameFolder(it.path, path)) it.copy(included = included) else it })

    fun removeFolder(path: String) = setFolders(folders.value.filterNot { MusicFolderList.sameFolder(it.path, path) })

    private fun readFolders(): List<MusicFolder> =
        prefs.getString(KEY_FOLDERS, null)?.let { runCatching { json.decodeFromString<List<MusicFolder>>(it) }.getOrNull() }
            ?: emptyList()

    companion object {
        const val FILE = "settings"
        private const val KEY_THEME = "theme"
        private const val KEY_FOLDERS = "musicFolders"
        private const val KEY_BIT_PERFECT = "bitPerfect"
        private const val KEY_FIIO_JACK = "fiioJack"
        private const val THEME_DARK = "dark"
        private const val THEME_LIGHT = "light"
    }
}
