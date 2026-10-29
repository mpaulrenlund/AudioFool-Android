package com.mpaulrenlund.audiofool.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.tokenSurface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** The PlayStation logo, unchanged (`PlayStation-Logo-1994.png`, copied into the assets by the build). */
const val LOGO_ASSET = "brand/PlayStation-Logo-1994.png"

/**
 * The logo, which is the menu button (spec 4.1): 36 x 28 in a 48 dp touch area.
 * Its menu holds Settings, Theme (switches at once), Statistics and Rescan Library (0.5.0).
 */
@Composable
fun LogoMenuButton(menuOpen: Boolean, onOpen: () -> Unit, onClose: () -> Unit, actions: AppActions) {
    val t = LocalAppTheme.current
    val context = LocalContext.current
    val logo = remember { context.assets.open(LOGO_ASSET).use { BitmapFactory.decodeStream(it).asImageBitmap() } }
    Box(
        Modifier.size(t.dp("topBar.logoButton")).tappable(CircleShape, onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Image(logo, contentDescription = "Menu", modifier = Modifier.size(t.dp("topBar.logo.width"), t.dp("topBar.logo.height")))
        if (menuOpen) LogoMenu(onClose, actions)
    }
}

/**
 * The menu itself. There is no mockup: it is a panel under the logo (panel
 * colours, border and corners) with settings-sized rows, and `shadow.headerArt`
 * under it so it reads as lifted over the list. Theme keeps the menu open, so the
 * switch can be seen and undone; the others close it.
 */
@Composable
private fun LogoMenu(onClose: () -> Unit, actions: AppActions) {
    val t = LocalAppTheme.current
    val shadows = t.shadow("shadow.headerArt")
    // The popup's window has to leave room for the shadow, or it is cut off.
    val room = Dp(shadows.maxOf { (it.blur + it.spread + max(abs(it.offsetX), abs(it.offsetY))).toFloat() })
    val density = LocalDensity.current
    val offset = with(density) {
        IntOffset((t.dp("menu.offsetX") - room).toPx().roundToInt(), (t.dp("topBar.logoButton") - room).toPx().roundToInt())
    }
    val shape = RoundedCornerShape(t.radius("radius.surface"))
    Popup(offset = offset, onDismissRequest = onClose, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .padding(room)
                .width(t.dp("menu.width"))
                .tokenSurface(shape, t.color("color.panel.bg"), shadows + t.shadow("shadow.panelHighlight"), t.color("color.panel.border"), t.dp("borderWidth"))
                .padding(t.dp("menu.listPadding")),
        ) {
            MenuRow("Settings") {
                onClose()
                actions.open(Route.Settings)
            }
            MenuRow("Theme", value = t.name, onClick = actions.toggleTheme)
            MenuRow("Statistics") {
                onClose()
                actions.open(Route.Statistics)
            }
            MenuRow("Rescan Library") {
                onClose()
                actions.rescan()
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, value: String? = null, onClick: () -> Unit) {
    val t = LocalAppTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(t.dp("menu.rowHeight"))
            .tappable(RoundedCornerShape(t.radius("radius.row")), onClick)
            .padding(horizontal = t.dp("menu.rowPaddingX")),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Line(label, t.text("menu.textSize", "color.text.primary"), Modifier.weight(1f))
        if (value != null) Line(value, t.text("menu.valueSize", "color.text.muted"))
    }
}
