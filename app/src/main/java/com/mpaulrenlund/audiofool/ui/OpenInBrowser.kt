package com.mpaulrenlund.audiofool.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** AudioFool's releases on GitHub, where each build is published: the menus' Version row opens it (spec 4.1, 2026-11-10). */
const val RELEASES_URL = "https://github.com/mpaulrenlund/AudioFool-Android/releases"

/**
 * Opens [url] in the phone's default browser (the user's call, 2026-11-10: "open the
 * default browser"), not in an app that claims the site's links, as GitHub's own does.
 * The default browser is the one a plain web link goes to; where none has been
 * chosen, Android asks which, and with no browser at all the tap does nothing. The
 * manifest's `<queries>` lets the app see the browsers.
 */
fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    defaultBrowser(context.packageManager)?.let { intent.setPackage(it) }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

/** The package of the browser chosen as the default, or null when none is (Android's chooser answers instead). */
internal fun defaultBrowser(packages: PackageManager): String? {
    val anyPage = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
    val browsers = packages.queryIntentActivities(anyPage, 0).map { it.activityInfo.packageName }.toSet()
    return packages.resolveActivity(anyPage, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it in browsers }
}
