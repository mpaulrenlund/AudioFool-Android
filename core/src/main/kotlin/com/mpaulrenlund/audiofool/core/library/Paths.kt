package com.mpaulrenlund.audiofool.core.library

import java.io.File
import java.util.Locale

/**
 * The few path helpers the ported code needs; the desktop's System.IO.Path
 * calls map onto these. On the phone every path uses '/'. Both '/' and '\' are
 * read as separators so the JVM tests also run on a Windows PC.
 *
 * Paths are compared ignoring case, as on the desktop: Android's shared storage
 * and SD cards (FUSE over casefolded ext4, FAT and exFAT) are case-insensitive.
 */
object Paths {
    /** The separator new paths are joined with. */
    val SEPARATOR: Char = File.separatorChar

    fun isSeparator(c: Char): Boolean = c == '/' || c == '\\'

    private fun lastSeparator(path: String): Int = path.indexOfLast(::isSeparator)

    fun fileName(path: String): String = path.substring(lastSeparator(path) + 1)

    fun directoryName(path: String): String? {
        val slash = lastSeparator(path)
        return when {
            slash > 0 -> path.substring(0, slash).let { if (it.endsWith(':')) it + path[slash] else it }
            slash == 0 && path.length > 1 -> path.substring(0, 1)
            else -> null
        }
    }

    /** ".flac", or "" when there is none. */
    fun extension(path: String): String {
        val name = fileName(path)
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "" else name.substring(dot)
    }

    fun fileNameWithoutExtension(path: String): String {
        val name = fileName(path)
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name else name.substring(0, dot)
    }

    /** Joins with the separator [folder] already uses, so '/' paths stay '/' paths on any PC. */
    fun combine(folder: String, name: String): String {
        if (folder.isNotEmpty() && isSeparator(folder.last())) return folder + name
        val separator = when {
            '/' in folder -> '/'
            '\\' in folder -> '\\'
            else -> SEPARATOR
        }
        return "$folder$separator$name"
    }

    fun trimEndSeparators(path: String): String = path.trimEnd { isSeparator(it) }

    /** A case-insensitive key for maps of paths (the desktop's OrdinalIgnoreCase). */
    fun key(path: String): String = path.lowercase(Locale.ROOT)

    fun same(a: String, b: String): Boolean = a.equals(b, ignoreCase = true)
}
