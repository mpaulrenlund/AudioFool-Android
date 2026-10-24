package com.mpaulrenlund.audiofool.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mpaulrenlund.audiofool.R
import com.mpaulrenlund.audiofool.core.theme.ShadowLayer
import com.mpaulrenlund.audiofool.core.theme.ThemeColor

/**
 * The current theme, with every token reachable by its JSON path:
 *
 * - [color], [shadow] and [desktopNumber] read the desktop's own files
 *   (`color.panel.bg`, `shadow.controlButton`, `radius.surface`), the Light or
 *   the Dark one as the theme says.
 * - [dp], [sp] and [number] read mobile-tokens.json (`artists.rowHeight`).
 * - [mobileColor] reads mobile-tokens.json's own colours (`color.bpLabel.on`),
 *   picking the `.light` or `.dark` value.
 *
 * Screens read colours through [LocalAppTheme] while they compose, so a theme
 * switch recomposes them with the new values; nothing holds on to a colour.
 */
@Immutable
class AppTheme(val isDark: Boolean, val tokens: AppTokens) {
    private val palette = if (isDark) tokens.dark else tokens.light

    /** "PS2 Dark" or "PS1 Light", as Settings shows it (spec 4.5). */
    val name: String get() = if (isDark) "PS2 Dark" else "PS1 Light"

    fun color(path: String): Color = palette.color(path).toCompose()

    /** A desktop colour as the token file has it, for building a shadow layer from it. */
    fun themeColor(path: String): ThemeColor = palette.color(path)

    fun mobileColor(path: String): Color = tokens.mobile.color("$path.${if (isDark) "dark" else "light"}").toCompose()

    fun shadow(path: String): List<ShadowLayer> = palette.shadow(path)

    /** A number from the desktop files, e.g. `radius.surface`. Sizes are the same in both. */
    fun desktopNumber(path: String): Double = palette.number(path)

    fun radius(path: String): Dp = desktopNumber(path).dp

    /** A mobile size, in dp. */
    fun dp(path: String): Dp = tokens.mobile.number(path).dp

    /** A mobile text size, in sp. */
    fun sp(path: String): TextUnit = tokens.mobile.number(path).sp

    fun number(path: String): Double = tokens.mobile.number(path)

    /**
     * A text style in Selawik. [size] is a mobile token path; [color] a desktop
     * colour path. Numbers use tabular figures (spec 1): Selawik's 400, 600 and
     * 700 weights have them by default, and `tnum` asks for them anyway.
     */
    fun text(
        size: String,
        color: String,
        weight: Int = 400,
        letterSpacingEm: Double? = null,
        lineHeight: Double? = null,
    ): TextStyle = TextStyle(
        fontFamily = Selawik,
        fontWeight = FontWeight(weight),
        fontSize = sp(size),
        color = color(color),
        letterSpacing = letterSpacingEm?.em ?: TextUnit.Unspecified,
        lineHeight = lineHeight?.let { (number(size) * it).sp } ?: TextUnit.Unspecified,
        fontFeatureSettings = "tnum",
    )

    fun withDark(dark: Boolean) = if (dark == isDark) this else AppTheme(dark, tokens)

    companion object {
        val Selawik = FontFamily(
            Font(R.font.selawik_regular, FontWeight.W400),
            Font(R.font.selawik_semibold, FontWeight.W600),
            Font(R.font.selawik_bold, FontWeight.W700),
        )
    }
}

fun ThemeColor.toCompose(): Color = Color(argb)

val LocalAppTheme = staticCompositionLocalOf<AppTheme> { error("No AppTheme: wrap the content in AudioFoolTheme") }

/** Provides [theme] to everything inside. */
@Composable
fun AudioFoolTheme(theme: AppTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppTheme provides theme, content = content)
}
