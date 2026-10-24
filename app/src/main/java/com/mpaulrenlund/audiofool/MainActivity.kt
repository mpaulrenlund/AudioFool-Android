package com.mpaulrenlund.audiofool

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mpaulrenlund.audiofool.storage.Storage
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.AudioFoolTheme
import com.mpaulrenlund.audiofool.ui.AppRoot
import com.mpaulrenlund.audiofool.ui.PermissionScreen

class MainActivity : ComponentActivity() {
    private val app get() = application as AudioFoolApp
    private var hasAccess by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        app.startTimes.activityCreated()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasAccess = Storage.hasAllFilesAccess()

        setContent {
            val dark by app.settings.darkTheme.collectAsStateWithLifecycle()
            val theme = remember(dark) { AppTheme(dark, app.tokens) }
            // The status and navigation bar icons follow the app's theme, not the phone's.
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            val state by app.library.state.collectAsStateWithLifecycle()

            val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
                val path = tree?.let { Storage.pathFromTree(this, it) } ?: return@rememberLauncherForActivityResult
                app.settings.addFolder(path)
                app.library.foldersChanged()
            }

            // The first frame with the library on it ends a cold or warm start.
            val shown = state.library != null
            LaunchedEffect(shown) {
                if (shown) {
                    withFrameNanos { }
                    app.startTimes.libraryShown()
                }
            }

            AudioFoolTheme(theme) {
                if (!hasAccess) {
                    PermissionScreen { startActivity(Storage.allFilesAccessIntent(this)) }
                } else {
                    AppRoot(app, dark) { pickFolder.launch(null) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the system's all-files access page.
        if (!hasAccess && Storage.hasAllFilesAccess()) {
            hasAccess = true
            app.startTimes.noColdStart()
            app.startLibrary()
        }
    }
}
