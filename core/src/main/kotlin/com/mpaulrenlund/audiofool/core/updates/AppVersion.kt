package com.mpaulrenlund.audiofool.core.updates

/**
 * A build's version as the app and its release tags write it: "0.18.1" in the app,
 * "v0.18.1" on GitHub. Compared number by number, a missing part counting as 0
 * ("0.19" is "0.19.0"). Nothing to port: the desktop has no update check.
 */
class AppVersion private constructor(private val parts: List<Int>, val text: String) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val c = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0
    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()
    override fun toString(): String = text

    companion object {
        /** Null for anything but one to four whole numbers between dots, after an optional "v". */
        fun parse(s: String): AppVersion? {
            val text = s.trim().removePrefix("v").removePrefix("V")
            val pieces = text.split('.')
            if (pieces.size !in 1..4) return null
            val parts = pieces.map { p -> if (p.isEmpty() || p.length > 9 || !p.all { it in '0'..'9' }) return null else p.toInt() }
            return AppVersion(parts, text)
        }
    }
}
