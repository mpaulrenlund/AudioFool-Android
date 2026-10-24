package com.mpaulrenlund.audiofool.core.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A token file (`design/theme-tokens.json`, `theme-tokens.dark.json` or
 * `mobile-tokens.json`), every leaf kept under its JSON path: `color.panel.bg`,
 * `radius.surface`, `artists.rowHeight`. Ported from the desktop's
 * `AudioFool.Core/Theme/ThemeTokens.cs`, with its tests.
 *
 * Strings are typed by where they live: under `color` they must be colours,
 * under `shadow` CSS box shadows, anywhere else they are kept as text. An array
 * of objects with an `id` is addressed by id, so a reordered column keeps its
 * name. A bad value throws [ThemeTokenException] naming its path.
 */
class ThemeTokens(
    val colors: Map<String, ThemeColor>,
    val numbers: Map<String, Double>,
    val flags: Map<String, Boolean>,
    val texts: Map<String, String>,
    val lists: Map<String, List<String>>,
    val shadows: Map<String, List<ShadowLayer>>,
    /** The `type.*` groups, one per text role, with their colour resolved to a full path. */
    val textStyles: List<TextStyleToken>,
) {
    /** `font.family`, in fallback order. */
    val fontFamily: List<String> get() = lists["font.family"] ?: emptyList()

    /** Every key, of every kind. */
    val keys: Set<String>
        get() = colors.keys + numbers.keys + flags.keys + texts.keys + lists.keys + shadows.keys

    fun number(path: String): Double = numbers[path] ?: throw ThemeTokenException(path, "is not a number token")
    fun color(path: String): ThemeColor = colors[path] ?: throw ThemeTokenException(path, "is not a colour token")
    fun shadow(path: String): List<ShadowLayer> = shadows[path] ?: throw ThemeTokenException(path, "is not a shadow token")
    fun textStyle(name: String): TextStyleToken =
        textStyles.firstOrNull { it.name == name } ?: throw ThemeTokenException("type.$name", "is not a type style")

    companion object {
        private val json = Json { isLenient = false }

        fun parse(text: String): ThemeTokens {
            val colors = LinkedHashMap<String, ThemeColor>()
            val numbers = LinkedHashMap<String, Double>()
            val flags = LinkedHashMap<String, Boolean>()
            val texts = LinkedHashMap<String, String>()
            val lists = LinkedHashMap<String, List<String>>()
            val shadows = LinkedHashMap<String, List<ShadowLayer>>()

            fun join(path: String, name: String) = if (path.isEmpty()) name else "$path.$name"

            fun walk(e: JsonElement, path: String) {
                when (e) {
                    is JsonObject -> for ((name, value) in e) {
                        // "$description" and the like are notes about the file.
                        if (name.startsWith('$')) continue
                        walk(value, join(path, name))
                    }

                    is JsonArray -> if (e.all { it is JsonPrimitive && it.isString }) {
                        lists[path] = e.map { (it as JsonPrimitive).content }
                    } else {
                        for (item in e) {
                            val id = (item as? JsonObject)?.get("id") as? JsonPrimitive
                            if (id == null || !id.isString)
                                throw ThemeTokenException(path, "an array must hold strings, or objects that each have a string \"id\"")
                            val itemPath = join(path, id.content)
                            for ((name, value) in item.jsonObjectOrThrow(path)) if (name != "id") walk(value, join(itemPath, name))
                        }
                    }

                    is JsonNull -> throw ThemeTokenException(path, "unexpected null")

                    is JsonPrimitive -> when {
                        e.isString -> {
                            val s = e.content
                            when {
                                path.startsWith("color.") -> colors[path] = ThemeColor.parse(s, path)
                                path.startsWith("shadow.") -> shadows[path] = ShadowLayer.parseList(s, path)
                                else -> texts[path] = s
                            }
                        }
                        e.booleanOrNull != null -> flags[path] = e.booleanOrNull!!
                        e.doubleOrNull != null -> numbers[path] = e.doubleOrNull!!
                        else -> throw ThemeTokenException(path, "unexpected value ${e.content}")
                    }
                }
            }

            walk(json.parseToJsonElement(text), "")

            return ThemeTokens(
                colors = colors,
                numbers = numbers,
                flags = flags,
                texts = texts,
                lists = lists,
                shadows = shadows,
                textStyles = readTextStyles(numbers, flags, texts, colors, lists),
            )
        }

        private fun JsonElement.jsonObjectOrThrow(path: String) =
            this as? JsonObject ?: throw ThemeTokenException(path, "expected an object")

        private fun readTextStyles(
            numbers: Map<String, Double>, flags: Map<String, Boolean>, texts: Map<String, String>,
            colors: Map<String, ThemeColor>, lists: Map<String, List<String>>,
        ): List<TextStyleToken> {
            val names = (numbers.keys + texts.keys + flags.keys)
                .filter { it.startsWith("type.") }
                .map { it.split('.')[1] }
                .distinct()
                .sorted()

            val tabular = lists["font.tabularFiguresFor"] ?: emptyList()
            tabular.firstOrNull { it !in names }?.let {
                throw ThemeTokenException("font.tabularFiguresFor", "\"$it\" is not a type.* entry")
            }

            return names.map { name ->
                val p = "type.$name"
                fun num(leaf: String) = numbers["$p.$leaf"]

                val size = num("size") ?: throw ThemeTokenException(p, "has no size")
                val weight = num("weight") ?: 400.0
                if (weight < 1 || weight > 999 || weight != floor(weight))
                    throw ThemeTokenException("$p.weight", "$weight is not an OpenType weight (1-999)")

                // "text.muted" names a colour token; it is stored as the full path.
                val colorRef = texts["$p.color"] ?: throw ThemeTokenException(p, "has no color")
                val colorKey = "color.$colorRef"
                if (colorKey !in colors) throw ThemeTokenException("$p.color", "\"$colorRef\" is not a colour token")

                val maxLines = num("maxLines")
                if (maxLines != null && (maxLines < 1 || maxLines != floor(maxLines)))
                    throw ThemeTokenException("$p.maxLines", "$maxLines is not a whole number of lines")

                TextStyleToken(
                    name = name,
                    size = size,
                    weight = weight.toInt(),
                    colorKey = colorKey,
                    lineHeight = num("lineHeight"),
                    maxLines = maxLines?.toInt(),
                    letterSpacingEm = num("letterSpacingEm"),
                    uppercase = flags["$p.uppercase"] == true,
                    tabularFigures = name in tabular,
                )
            }
        }
    }
}

/** One text role from `type.*`. [lineHeight] is a multiple of the size, as in CSS. */
data class TextStyleToken(
    val name: String,
    val size: Double,
    val weight: Int,
    val colorKey: String,
    val lineHeight: Double?,
    val maxLines: Int?,
    val letterSpacingEm: Double?,
    val uppercase: Boolean,
    val tabularFigures: Boolean,
) {
    val key: String get() = "type.$name"
}

/** A colour as four bytes. Parsed from `#RRGGBB`, `rgb()` or `rgba()`. */
data class ThemeColor(val a: Int, val r: Int, val g: Int, val b: Int) {
    /** Opacity 0-1. */
    val alpha: Double get() = a / 255.0

    /** 0xAARRGGBB, as Android and Compose take colours. */
    val argb: Int get() = (a shl 24) or (r shl 16) or (g shl 8) or b

    override fun toString(): String = "#%02X%02X%02X%02X".format(a, r, g, b)

    companion object {
        fun parse(text: String, path: String = ""): ThemeColor {
            val s = text.trim()
            if (s.startsWith('#')) {
                // Only #RRGGBB. CSS's 8-digit form is RRGGBBAA and Android's is
                // AARRGGBB, so accepting either would make one of them silently wrong.
                val rgb = if (s.length == 7) s.substring(1).toIntOrNull(16) else null
                if (rgb != null) return ThemeColor(255, (rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
                throw ThemeTokenException(path, "\"$text\" is not a #RRGGBB colour (use rgba() for transparency)")
            }

            val open = s.indexOf('(')
            if (open > 0 && s.endsWith(')')) {
                val fn = s.substring(0, open).trim().lowercase()
                val parts = s.substring(open + 1, s.length - 1).split(',').map { it.trim() }
                if ((fn == "rgb" && parts.size == 3) || (fn == "rgba" && parts.size == 4)) {
                    val channels = IntArray(3) { i ->
                        parts[i].takeIf { p -> p.isNotEmpty() && p.all { it in '0'..'9' } }?.toIntOrNull()
                            ?.takeIf { it in 0..255 }
                            ?: throw ThemeTokenException(path, "\"${parts[i]}\" in \"$text\" is not a channel value 0-255")
                    }
                    var alpha = 1.0
                    if (fn == "rgba") {
                        alpha = parts[3].toDoubleOrNull()?.takeIf { it in 0.0..1.0 }
                            ?: throw ThemeTokenException(path, "\"${parts[3]}\" in \"$text\" is not an alpha 0-1")
                    }
                    // .NET's Math.Round rounds halves to even; so does this.
                    return ThemeColor(Math.rint(alpha * 255).roundToInt(), channels[0], channels[1], channels[2])
                }
            }

            throw ThemeTokenException(path, "\"$text\" is not a colour")
        }
    }
}

/**
 * One layer of a CSS `box-shadow`: `[inset] x y [blur [spread]] color`.
 * Lengths are pixels (dp here); a bare `0` needs no unit.
 */
data class ShadowLayer(
    val inset: Boolean,
    val offsetX: Double,
    val offsetY: Double,
    val blur: Double,
    val spread: Double,
    val color: ThemeColor,
) {
    companion object {
        /** A comma-separated list of layers. Commas inside `rgba(...)` are not separators. */
        fun parseList(text: String, path: String = ""): List<ShadowLayer> {
            val layers = mutableListOf<ShadowLayer>()
            var depth = 0
            var start = 0
            for (i in 0..text.length) {
                if (i == text.length || (text[i] == ',' && depth == 0)) {
                    layers.add(parse(text.substring(start, i), path))
                    start = i + 1
                } else if (text[i] == '(') depth++
                else if (text[i] == ')') depth--
            }
            return layers
        }

        private fun parse(layer: String, path: String): ShadowLayer {
            var s = layer.trim()
            var inset = false
            if (s.startsWith("inset ", ignoreCase = true)) {
                inset = true
                s = s.substring(6).trim()
            }

            // The colour is whatever follows the lengths: rgba(...), rgb(...) or #hex.
            val colorAt = s.indexOfFirst { it == '#' || it == 'r' || it == 'R' }
            if (colorAt < 0) throw ThemeTokenException(path, "\"${layer.trim()}\" has no colour")
            val color = ThemeColor.parse(s.substring(colorAt), path)

            val lengths = s.substring(0, colorAt).split(' ').filter { it.isNotEmpty() }.map { length(it, layer, path) }
            if (lengths.size !in 2..4) throw ThemeTokenException(path, "\"${layer.trim()}\" needs 2-4 lengths before the colour")

            return ShadowLayer(inset, lengths[0], lengths[1], lengths.getOrElse(2) { 0.0 }, lengths.getOrElse(3) { 0.0 }, color)
        }

        private fun length(token: String, layer: String, path: String): Double {
            val number = if (token.endsWith("px", ignoreCase = true)) token.dropLast(2) else token
            val value = number.toDoubleOrNull()
            if (value == null || (number == token && value != 0.0))
                throw ThemeTokenException(path, "\"$token\" in \"${layer.trim()}\" is not a px length")
            return value
        }
    }
}

class ThemeTokenException(val tokenPath: String, problem: String) :
    RuntimeException(if (tokenPath.isEmpty()) problem else "$tokenPath: $problem")
