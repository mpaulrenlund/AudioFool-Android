package com.mpaulrenlund.audiofool.core.genius

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * Reads the answers of genius.com's own web API (the one its pages use), each
 * asked for with `text_format=plain`, so every text arrives as
 * `{"plain": "..."}`. Each function takes the `response` object. Anything
 * missing is skipped rather than failing the page: Genius leaves fields out
 * freely.
 *
 * Ported from the desktop's `AudioFool.Core/Genius/GeniusJson.cs` (session 75).
 */
object GeniusJson {
    /** The songs of a `search/song` answer, in Genius's order. */
    fun searchHits(response: JsonElement): List<GeniusHit> {
        val hits = ArrayList<GeniusHit>()
        val sections = response.obj()?.get("sections") as? JsonArray ?: return hits

        for (section in sections) {
            val list = section.obj()?.get("hits") as? JsonArray ?: continue
            for (hit in list) {
                val h = hit.obj() ?: continue
                val result = h["result"] ?: continue
                val song = hit(result) ?: continue
                if ((text(h, "type") ?: text(result, "_type")) == "song") hits.add(song)
            }
        }
        return hits
    }

    /** The `song` of a `songs/{id}` answer. */
    fun song(response: JsonElement): GeniusSong {
        val song = response.obj()?.get("song")?.obj() ?: throw GeniusException("Genius sent a page without its song.")
        val hit = hit(song) ?: throw GeniusException("Genius sent a song without its name.")

        val about = plain(song, "description")?.takeUnless { it == "?" }

        val album = song["album"]?.obj()?.let { text(it, "name") }
        val released = text(song, "release_date_for_display")

        val credits = ArrayList<GeniusCredit>()
        addCredit(credits, "Written by", names(song, "writer_artists"))
        addCredit(credits, "Produced by", names(song, "producer_artists"))
        addCredit(credits, "Released", listOfNotNull(released))
        addCredit(credits, "Recorded at", listOfNotNull(text(song, "recording_location")))
        (song["custom_performances"] as? JsonArray)?.forEach { p ->
            text(p, "label")?.let { label -> addCredit(credits, creditLabel(label), names(p, "artists")) }
        }

        val connections = ArrayList<GeniusConnection>()
        (song["song_relationships"] as? JsonArray)?.forEach { r ->
            val type = text(r, "relationship_type") ?: text(r, "type")
            if (type == null || type == "translation_of" || type == "translations") return@forEach

            val songs = (r.obj()?.get("songs") as? JsonArray)?.mapNotNull(::hit) ?: emptyList()
            if (songs.isNotEmpty()) connections.add(GeniusConnection(connectionLabel(type), songs))
        }

        return GeniusSong(hit.id, hit.title, hit.artist, hit.url, about, album, released, credits, connections)
    }

    /**
     * The explanations of a `referents?song_id=` answer, in the order of the
     * lyrics. The write-up's own entry is left out (it is the About text), and
     * so are explanations Genius hasn't accepted yet.
     */
    fun annotations(response: JsonElement): List<GeniusAnnotation> {
        val notes = ArrayList<GeniusAnnotation>()
        val referents = response.obj()?.get("referents") as? JsonArray ?: return notes

        for (r in referents) {
            if (bool(r, "is_description") || text(r, "classification") == "unreviewed") continue
            val fragment = text(r, "fragment") ?: continue
            val list = r.obj()?.get("annotations") as? JsonArray ?: continue

            val first = list.firstOrNull()?.obj() ?: continue
            val body = plain(first, "body") ?: continue
            notes.add(GeniusAnnotation(fragment, body, bool(first, "verified")))
        }
        return notes
    }

    /** The answered questions of a `questions?song_id=` answer, pinned ones first as Genius sends them. */
    fun answers(response: JsonElement): List<GeniusAnswer> {
        val answers = ArrayList<GeniusAnswer>()
        val questions = response.obj()?.get("questions") as? JsonArray ?: return answers

        for (q in questions) {
            val question = plain(q, "body") ?: continue
            val answer = q.obj()?.get("answer")?.obj()?.let { plain(it, "body") } ?: continue
            answers.add(GeniusAnswer(question, answer))
        }
        return answers
    }

    /** One page of a `songs/{id}/comments` answer. */
    fun comments(response: JsonElement): GeniusCommentPage {
        val comments = ArrayList<GeniusComment>()
        (response.obj()?.get("comments") as? JsonArray)?.forEach { c ->
            val body = plain(c, "body") ?: return@forEach
            val o = c.obj() ?: return@forEach

            val author = o["author"]?.obj()?.let { text(it, "login") }
                ?: o["anonymous_author"]?.obj()?.let { text(it, "name") }
            val posted = number(o, "created_at")?.longOrNull?.let(Instant::ofEpochSecond)

            comments.add(GeniusComment(author ?: "Anonymous", posted, int(o, "votes_total") ?: 0, body))
        }

        val total = int(response, "total_count") ?: comments.size
        return GeniusCommentPage(comments, total, int(response, "next_page"))
    }

    /** "sampled_in" as the screen says it: "Sampled in". */
    fun connectionLabel(type: String): String = when (type) {
        "samples" -> "Samples"
        "sampled_in" -> "Sampled in"
        "interpolates" -> "Interpolates"
        "interpolated_by" -> "Interpolated by"
        "cover_of" -> "Cover of"
        "covered_by" -> "Covered by"
        "remix_of" -> "Remix of"
        "remixed_by" -> "Remixed by"
        "live_version_of" -> "Live version of"
        "performed_live_as" -> "Performed live as"
        else -> if (type.isEmpty()) type else type[0].uppercaseChar() + type.substring(1).replace('_', ' ')
    }

    private val smallWords = setOf("At", "By", "In", "On", "Of", "For", "And", "With", "To", "From")

    /**
     * A credit label Genius's contributors typed ("Mastered At") with its small
     * words in lower case ("Mastered at"), to sit beside "Recorded at". The rest
     * is left as typed, so "A&R" stays.
     */
    fun creditLabel(label: String): String {
        val words = label.split(' ').filter { it.isNotEmpty() }.toMutableList()
        for (i in 1 until words.size) {
            if (words[i] in smallWords) words[i] = words[i].lowercase()
        }
        return words.joinToString(" ")
    }

    private fun hit(song: JsonElement): GeniusHit? {
        val o = song.obj() ?: return null
        val id = number(o, "id")?.longOrNull ?: return null
        val title = text(o, "title") ?: return null

        val artist = o["primary_artist"]?.obj()?.let { text(it, "name") } ?: text(o, "artist_names") ?: ""
        return GeniusHit(id, title, artist, text(o, "url") ?: "")
    }

    private fun addCredit(credits: MutableList<GeniusCredit>, label: String, names: List<String>) {
        if (names.isNotEmpty()) credits.add(GeniusCredit(label, names))
    }

    private fun names(e: JsonElement, property: String): List<String> =
        (e.obj()?.get(property) as? JsonArray)?.mapNotNull { a -> a.obj()?.let { text(it, "name") } } ?: emptyList()

    /**
     * A `{"plain": "..."}` text, trimmed; null when absent or empty. A bare
     * string is taken as it is: a question's own text comes that way, while its
     * answer comes wrapped (seen on the live site, 2026-11-04).
     */
    private fun plain(e: JsonElement, property: String): String? {
        val v = e.obj()?.get(property) ?: return null
        (v as? JsonPrimitive)?.takeIf { it.isString }?.let { return it.content.trim().ifEmpty { null } }
        return v.obj()?.let { text(it, "plain") }
    }

    private fun text(e: JsonElement, property: String): String? =
        (e.obj()?.get(property) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }

    private fun bool(e: JsonElement, property: String): Boolean =
        (e.obj()?.get(property) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true

    /** A number Genius sent as a number (not as a string, and not null). */
    private fun number(e: JsonElement, property: String): JsonPrimitive? =
        (e.obj()?.get(property) as? JsonPrimitive)?.takeIf { !it.isString && it.booleanOrNull == null && it.content != "null" }

    private fun int(e: JsonElement, property: String): Int? = number(e, property)?.intOrNull

    private fun JsonElement.obj(): JsonObject? = this as? JsonObject
}
