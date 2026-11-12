package com.mpaulrenlund.audiofool.core.updates

import kotlin.math.roundToLong

/**
 * What to offer when the menus' Version row asks (spec 9; 2026-11-12, the user's
 * request and calls): the newest release, if it is newer than this build. Until
 * 0.20.0 the app looked by itself when it opened, at most every six hours, and a
 * version turned down with Cancel wasn't offered again; the user swapped that for the
 * tap ("instead of a notification").
 */
object UpdateRules {
    /** The release to offer, or null: the newest, if newer than [current]. */
    fun offer(releases: List<AppRelease>, current: AppVersion): AppRelease? {
        val newest = releases.maxByOrNull { it.version } ?: return null
        return newest.takeIf { it.version > current }
    }

    /** "12 of 27 MB": the download so far, in whole megabytes (1,000,000 bytes, as Android counts). */
    fun progress(doneBytes: Long, totalBytes: Long): String = "${mb(doneBytes)} of ${mb(totalBytes)} MB"

    private fun mb(bytes: Long): Long = (bytes / 1_000_000.0).roundToLong()
}
