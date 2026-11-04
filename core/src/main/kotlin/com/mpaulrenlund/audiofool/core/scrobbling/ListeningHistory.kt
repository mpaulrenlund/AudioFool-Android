package com.mpaulrenlund.audiofool.core.scrobbling

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Listening History (spec 4.7): ported from the desktop's
// `AudioFool.Core/Scrobbling/ListeningHistory.cs` (session 74).

/** The spans Last.fm keeps top lists for. */
enum class ListeningPeriod(
    /** The `period` parameter Last.fm takes. */
    val apiName: String,
    /** The desktop's word for it. */
    val label: String,
    /**
     * The phone's word for it (the user's call): the desktop's six words don't fit in
     * one row on either phone, so the switch shows these.
     */
    val shortLabel: String,
) {
    Week("7day", "7 Days", "7D"),
    Month("1month", "1 Month", "1M"),
    ThreeMonths("3month", "3 Months", "3M"),
    SixMonths("6month", "6 Months", "6M"),
    Year("12month", "12 Months", "12M"),
    AllTime("overall", "All Time", "All"),
}

/**
 * The user's totals from user.getInfo. The three counts are newer than the rest of
 * the API and null when an answer leaves them out.
 */
data class ListenerProfile(
    val userName: String,
    val scrobbles: Long,
    val registered: Instant?,
    val artists: Long?,
    val albums: Long?,
    val tracks: Long?,
)

/** One line of a top list. [artist] is null for the artists list itself. */
data class TopEntry(val name: String, val artist: String?, val plays: Long)

/** One recent play. A song still playing has no time. */
data class RecentPlay(val track: String, val artist: String, val album: String, val playedAt: Instant?, val nowPlaying: Boolean)

/**
 * Reads Last.fm's JSON, which is XML turned into JSON and carries its quirks:
 * numbers come as strings, text nodes as `{"#text": ...}`, attributes under
 * `@attr`, and a list of one item as the item itself.
 */
object ListeningJson {
    fun profile(root: JsonElement): ListenerProfile {
        val user = (root as? JsonObject)?.get("user") as? JsonObject
            ?: throw LastFmException(0, "Last.fm returned no profile.")

        val reg = user["registered"]
        val registered = (number(reg, "unixtime") ?: number(reg, "#text"))
            ?.takeIf { it > 0 }
            ?.let(Instant::ofEpochSecond)

        return ListenerProfile(
            text(user, "name"),
            number(user, "playcount") ?: 0,
            registered,
            number(user, "artist_count"),
            number(user, "album_count"),
            number(user, "track_count"),
        )
    }

    /**
     * @param listName "topartists", "topalbums" or "toptracks".
     * @param itemName "artist", "album" or "track".
     */
    fun top(root: JsonElement, listName: String, itemName: String, limit: Int): List<TopEntry> {
        val list = (root as? JsonObject)?.get(listName) ?: return emptyList()

        val isArtists = itemName == "artist"
        return items(list, itemName)
            .map { e -> TopEntry(text(e, "name"), if (isArtists) null else text(e, "artist"), number(e, "playcount") ?: 0) }
            .filter { it.name.isNotEmpty() }
            .take(limit)
    }

    /** At most [limit] plays: Last.fm sends one more when a song is playing. */
    fun recent(root: JsonElement, limit: Int): List<RecentPlay> {
        val list = (root as? JsonObject)?.get("recenttracks") ?: return emptyList()

        return items(list, "track")
            .map { e ->
                val attr = e["@attr"]
                val nowPlaying = attr != null && text(attr, "nowplaying") == "true"
                val date = e["date"]
                val at = if (!nowPlaying && date != null) number(date, "uts")?.let(Instant::ofEpochSecond) else null
                RecentPlay(text(e, "name"), text(e, "artist"), text(e, "album"), at, nowPlaying)
            }
            .filter { it.track.isNotEmpty() }
            .take(limit)
    }

    private fun items(list: JsonElement, name: String): List<JsonObject> {
        val items = (list as? JsonObject)?.get(name) ?: return emptyList()
        return when (items) {
            is JsonArray -> items.filterIsInstance<JsonObject>()
            is JsonObject -> listOf(items)
            else -> emptyList()
        }
    }

    /** A string, a number, or an object's `#text` or `name`; "" when none. */
    private fun text(parent: JsonElement?, name: String): String {
        val value = (parent as? JsonObject)?.get(name) ?: return ""
        return when (value) {
            is JsonObject -> value["#text"]?.let { primitiveText(it) } ?: text(value, "name")
            else -> primitiveText(value)
        }
    }

    /** The desktop's String (trimmed) and Number (as written) cases; anything else is "". */
    private fun primitiveText(value: JsonElement): String {
        val p = value as? JsonPrimitive ?: return ""
        return when {
            p is JsonNull -> ""
            p.isString -> p.content.trim()
            p.content.toDoubleOrNull() != null -> p.content
            else -> ""
        }
    }

    private fun number(parent: JsonElement?, name: String): Long? {
        val value = (parent as? JsonObject)?.get(name) as? JsonPrimitive ?: return null
        if (value is JsonNull) return null
        return if (value.isString) value.content.trim().toLongOrNull() else value.content.toLongOrNull()
    }
}

/**
 * How Listening History words a time: "12 min ago", "Yesterday", "Oct 30". The
 * desktop's `ListeningTime` (Scrobbling), renamed here because
 * [com.mpaulrenlund.audiofool.core.playback.ListeningTime] is the phone's own
 * listening total.
 */
object HistoryTime {
    private val dayInYear = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val dayWithYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val monthWithYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

    fun ago(play: RecentPlay, now: Instant, zone: ZoneId): String {
        val at = play.playedAt
        if (play.nowPlaying || at == null) return "Now playing"

        val gap = Duration.between(at, now)
        if (gap < Duration.ofMinutes(1)) return "Just now"
        if (gap < Duration.ofHours(1)) return "${gap.toMinutes()} min ago"

        val day = at.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        if (day == today) {
            val hours = gap.toHours()
            return if (hours == 1L) "1 hour ago" else "$hours hours ago"
        }
        if (day == today.minusDays(1)) return "Yesterday"

        return if (day.year == today.year) day.format(dayInYear) else day.format(dayWithYear)
    }

    /** "Mar 2014": the month someone joined. */
    fun month(time: Instant, zone: ZoneId): String = time.atZone(zone).format(monthWithYear)
}
