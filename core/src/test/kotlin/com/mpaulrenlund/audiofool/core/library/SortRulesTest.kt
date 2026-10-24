package com.mpaulrenlund.audiofool.core.library

import com.mpaulrenlund.audiofool.core.model.Album
import com.mpaulrenlund.audiofool.core.model.ArtistGroup
import com.mpaulrenlund.audiofool.core.model.Track
import org.junit.Test
import kotlin.test.assertEquals

// Ported from the desktop's tests/AudioFool.Core.Tests/SortRulesTests.cs.

class ArtistSortKeyTest {
    @Test fun strips_a_leading_article() {
        mapOf(
            "The Beatles" to "Beatles",
            "the beatles" to "beatles",
            "THE WHO" to "WHO",
            "The  Doors" to "Doors",      // extra space after the article
            "  The Clash  " to "Clash",   // surrounding whitespace
        ).forEach { (input, expected) -> assertEquals(expected, SortRules.artistSortKey(input), input) }
    }

    @Test fun leaves_everything_else_alone() {
        // "The" is not a whole word in the first three; the fourth is a band literally named "The".
        for (input in listOf("Thelonious Monk", "Theory of a Deadman", "Therapy?", "The", "Radiohead"))
            assertEquals(input, SortRules.artistSortKey(input))
    }

    @Test fun the_the_sorts_under_T_not_as_an_empty_string() =
        assertEquals("The", SortRules.artistSortKey("The The"))

    @Test fun an_article_with_nothing_after_it_keeps_the_original() =
        assertEquals("The", SortRules.artistSortKey("The "))

    @Test fun missing_names_produce_an_empty_key() {
        for (input in listOf(null, "", "   ")) assertEquals("", SortRules.artistSortKey(input))
    }

    @Test fun extra_articles_can_be_opted_into() {
        val articles = listOf("The", "A", "An")
        assertEquals("Perfect Circle", SortRules.artistSortKey("A Perfect Circle", articles))
        assertEquals("Tribe Called Quest", SortRules.artistSortKey("A Tribe Called Quest", articles))

        // Still word-boundary aware with the wider list.
        assertEquals("Anthrax", SortRules.artistSortKey("Anthrax", articles))
        assertEquals("Air", SortRules.artistSortKey("Air", articles))
    }
}

/** The Artists list's A-Z order, through [SortRules.sortArtists] as the app sorts it. */
class ArtistOrderingTest {
    private fun sorted(vararg names: String) =
        SortRules.sortArtists(names.map { ArtistGroup(it, SortRules.artistSortKey(it)) }).map { it.name }

    @Test fun the_bands_file_under_their_second_word() = assertEquals(
        listOf("ABBA", "The Beatles", "Beck", "Cake", "The Cure", "Zebra"),
        sorted("Zebra", "The Beatles", "ABBA", "Beck", "The Cure", "Cake"),
    )

    @Test fun thelonious_sorts_after_the_beatles_because_only_one_is_an_article() = assertEquals(
        listOf("The Beatles", "Thelonious Monk", "The Zombies"),
        sorted("Thelonious Monk", "The Beatles", "The Zombies"),
    )

    @Test fun ordering_is_stable_for_names_sharing_a_sort_key() =
        // Same key, so the tie-break on the display name decides: "Beatles" < "The Beatles".
        assertEquals(listOf("Beatles", "The Beatles"), sorted("The Beatles", "Beatles"))
}

class RecentArtistOrderingTest {
    private val day = 1_788_220_800_000L // 2026-09-01T00:00Z
    private val dayMs = 86_400_000L

    /** An artist whose tracks were added the given number of days after [day]. */
    private fun artist(name: String, vararg albumDays: IntArray) = ArtistGroup(
        name = name,
        sortKey = SortRules.artistSortKey(name),
        albums = albumDays.mapIndexed { i, days ->
            Album(
                title = "$name $i",
                artistName = name,
                tracks = days.mapIndexed { j, d ->
                    Track(
                        filePath = "/music/$name/$i/$j.flac",
                        addedUtc = day + d * dayMs,
                        // Written long after, and in the opposite order: must not matter.
                        modifiedUtc = day + (100 - d) * dayMs,
                    )
                },
            )
        },
    )

    private fun order(vararg artists: ArtistGroup) = SortRules.sortArtistsByRecent(artists.toList()).map { it.name }

    @Test fun most_recently_added_artist_comes_first() = assertEquals(
        listOf("Cake", "ABBA", "Zebra"),
        order(artist("Zebra", intArrayOf(1)), artist("ABBA", intArrayOf(5)), artist("Cake", intArrayOf(9))),
    )

    @Test fun one_new_file_anywhere_brings_the_artist_up() = assertEquals(
        // Beck's old album gained one track on day 20; Air's all arrived on day 10.
        listOf("Beck", "Air"),
        order(artist("Air", intArrayOf(10, 10)), artist("Beck", intArrayOf(1, 1, 20), intArrayOf(2))),
    )

    @Test fun ties_fall_back_to_the_alphabetical_order() = assertEquals(
        listOf("The Beatles", "Cake", "Zebra"),
        order(artist("Zebra", intArrayOf(3)), artist("Cake", intArrayOf(3)), artist("The Beatles", intArrayOf(3))),
    )

    @Test fun an_artist_with_no_dated_tracks_sorts_last() = assertEquals(
        listOf("Cake", "Empty"),
        order(artist("Empty"), artist("Cake", intArrayOf(0))),
    )
}

class AlbumOrderingTest {
    private fun album(title: String, year: Int?) = Album(title = title, artistName = "Test", year = year)

    @Test fun oldest_first_newest_last() = assertEquals(
        listOf("Please Please Me", "Revolver", "Abbey Road"),
        SortRules.sortAlbums(listOf(album("Abbey Road", 1969), album("Please Please Me", 1963), album("Revolver", 1966))).map { it.title },
    )

    @Test fun albums_without_a_year_go_to_the_bottom() = assertEquals(
        listOf("Revolver", "Abbey Road", "Another Bootleg", "Unknown Bootleg"),
        SortRules.sortAlbums(
            listOf(album("Unknown Bootleg", null), album("Revolver", 1966), album("Another Bootleg", null), album("Abbey Road", 1969)),
        ).map { it.title },
    )

    @Test fun albums_from_the_same_year_fall_back_to_title() = assertEquals(
        listOf("Tonight's the Night", "Zuma"),
        SortRules.sortAlbums(listOf(album("Zuma", 1975), album("Tonight's the Night", 1975))).map { it.title },
    )
}

class TrackOrderingTest {
    private fun track(title: String, track: Int?, disc: Int? = null) =
        Track(filePath = "/music/$title.flac", title = title, trackNumber = track, discNumber = disc)

    private fun titles(vararg tracks: Track) = SortRules.sortTracks(tracks.toList()).map { it.title }

    @Test fun ordered_by_track_number() =
        assertEquals(listOf("First", "Second", "Third"), titles(track("Third", 3), track("First", 1), track("Second", 2)))

    @Test fun track_numbers_sort_numerically_not_as_text() =
        assertEquals(listOf("One", "Two", "Ten"), titles(track("Ten", 10), track("Two", 2), track("One", 1)))

    @Test fun disc_number_takes_priority_over_track_number() = assertEquals(
        listOf("D1T1", "D1T2", "D2T1", "D2T2"),
        titles(track("D2T1", 1, 2), track("D1T2", 2, 1), track("D1T1", 1, 1), track("D2T2", 2, 2)),
    )

    @Test fun untagged_disc_is_treated_as_disc_one() =
        assertEquals(listOf("NoDiscTag", "OnDiscTwo"), titles(track("OnDiscTwo", 1, 2), track("NoDiscTag", 1, null)))

    @Test fun untagged_track_numbers_sink_to_the_end() =
        assertEquals(listOf("One", "Two", "Mystery"), titles(track("Mystery", null), track("Two", 2), track("One", 1)))

    // ------------------------------------------------------- ARTISTS · SIZE

    /** An artist whose albums hold tracks of the given sizes in MB. */
    private fun sized(name: String, vararg albumMegabytes: IntArray) = ArtistGroup(
        name = name,
        sortKey = SortRules.artistSortKey(name),
        albums = albumMegabytes.mapIndexed { i, sizes ->
            Album(
                title = "$name $i",
                artistName = name,
                tracks = sizes.mapIndexed { j, mb -> Track(filePath = "/music/$name/$i/$j.flac", fileSize = mb * 1024L * 1024) },
            )
        },
    )

    private fun bySize(vararg artists: ArtistGroup) = SortRules.sortArtistsBySize(artists.toList()).map { it.name }

    @Test fun largest_artist_comes_first() = assertEquals(
        listOf("Cake", "Zebra", "ABBA"),
        bySize(sized("ABBA", intArrayOf(10)), sized("Cake", intArrayOf(30, 30)), sized("Zebra", intArrayOf(40))),
    )

    @Test fun size_counts_every_album() = assertEquals(
        // Air has the biggest single album; Beck's three add up to more.
        listOf("Beck", "Air"),
        bySize(sized("Air", intArrayOf(500)), sized("Beck", intArrayOf(200), intArrayOf(200), intArrayOf(200))),
    )

    @Test fun equal_sizes_fall_back_to_A_to_Z_ignoring_the_article() = assertEquals(
        listOf("The Beatles", "Cream", "Zappa"),
        bySize(sized("Zappa", intArrayOf(100)), sized("Cream", intArrayOf(100)), sized("The Beatles", intArrayOf(100))),
    )

    @Test fun header_clicks_cycle_A_to_Z_recent_size_and_back() {
        assertEquals(ArtistOrder.Recent, SortRules.next(ArtistOrder.Name))
        assertEquals(ArtistOrder.Size, SortRules.next(ArtistOrder.Recent))
        assertEquals(ArtistOrder.Name, SortRules.next(ArtistOrder.Size))
    }

    @Test fun A_to_Z_leaves_the_library_order_alone() {
        val artists = listOf(sized("Zappa", intArrayOf(1)), sized("ABBA", intArrayOf(900)))
        assertEquals(artists, SortRules.sortArtists(artists, ArtistOrder.Name))
        assertEquals(listOf("ABBA", "Zappa"), SortRules.sortArtists(artists, ArtistOrder.Size).map { it.name })
    }
}

/** Spec 4.1: the Artists row shows the newest album's art, by the desktop's release-date rules. */
class NewestAlbumTest {
    private fun track(album: String, year: Int?, date: String? = null) =
        Track(filePath = "/music/$album/${album.hashCode()}-$year.flac", album = album, albumArtist = "A", year = year, releaseDate = date)

    @Test fun the_newest_album_is_the_last_dated_one() {
        val artist = LibraryScanner.build(
            listOf(track("Old", 1990), track("Undated", null), track("Late", 2026, "2026-10-02"), track("Early", 2026)),
        ).artists.single()

        assertEquals(listOf("Old", "Early", "Late", "Undated"), artist.albums.map { it.title })
        assertEquals("Late", artist.newestAlbum?.title)
    }

    @Test fun an_artist_with_only_undated_albums_shows_its_last() {
        val artist = LibraryScanner.build(listOf(track("B", null), track("A", null))).artists.single()
        assertEquals("B", artist.newestAlbum?.title)
    }
}
