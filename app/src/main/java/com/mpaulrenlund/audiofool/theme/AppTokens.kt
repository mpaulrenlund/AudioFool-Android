package com.mpaulrenlund.audiofool.theme

import android.content.res.AssetManager
import com.mpaulrenlund.audiofool.core.theme.ThemeTokens

/**
 * The three token files, as the build copies them from design/ into the app's
 * assets: the desktop's PS1 Light and PS2 Dark (unchanged) and mobile-tokens.json.
 * Parsed once per process.
 */
class AppTokens(val light: ThemeTokens, val dark: ThemeTokens, val mobile: ThemeTokens) {
    companion object {
        const val LIGHT_FILE = "tokens/theme-tokens.json"
        const val DARK_FILE = "tokens/theme-tokens.dark.json"
        const val MOBILE_FILE = "tokens/mobile-tokens.json"

        @Volatile private var loaded: AppTokens? = null

        fun get(assets: AssetManager): AppTokens = loaded ?: synchronized(this) {
            loaded ?: AppTokens(
                light = ThemeTokens.parse(assets.read(LIGHT_FILE)),
                dark = ThemeTokens.parse(assets.read(DARK_FILE)),
                mobile = ThemeTokens.parse(assets.read(MOBILE_FILE)),
            ).also { loaded = it }
        }

        private fun AssetManager.read(name: String) = open(name).bufferedReader().use { it.readText() }
    }
}
