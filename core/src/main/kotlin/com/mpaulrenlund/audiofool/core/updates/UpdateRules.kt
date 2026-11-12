package com.mpaulrenlund.audiofool.core.updates

import kotlin.math.roundToLong

/**
 * When to look for an update and what to offer (spec 9; 2026-11-12, the user's
 * request and calls): a look when the app opens, at most every [CHECK_EVERY_MS]; the
 * newest release, if it is newer than this build and than the version last turned
 * down with Cancel.
 */
object UpdateRules {
    /** Six hours between looks at GitHub ("at most every few hours"). */
    const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    /** Due when GitHub was never looked at, long enough ago, or the clock has gone back past it. */
    fun isDue(lastCheckedAtMs: Long?, nowMs: Long): Boolean =
        lastCheckedAtMs == null || nowMs < lastCheckedAtMs || nowMs - lastCheckedAtMs >= CHECK_EVERY_MS

    /** The release to offer, or null: the newest, if newer than [current] and than [skipped]. */
    fun offer(releases: List<AppRelease>, current: AppVersion, skipped: AppVersion?): AppRelease? {
        val newest = releases.maxByOrNull { it.version } ?: return null
        if (newest.version <= current) return null
        if (skipped != null && newest.version <= skipped) return null
        return newest
    }

    /** "12 of 27 MB": the download so far, in whole megabytes (1,000,000 bytes, as Android counts). */
    fun progress(doneBytes: Long, totalBytes: Long): String = "${mb(doneBytes)} of ${mb(totalBytes)} MB"

    private fun mb(bytes: Long): Long = (bytes / 1_000_000.0).roundToLong()
}
