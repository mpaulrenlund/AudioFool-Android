package com.mpaulrenlund.audiofool.core.format

import org.junit.Test
import kotlin.test.assertEquals

// The desktop's Display (AudioFool/Formatting/Display.cs) has no tests of its
// own; these pin down the examples its comments give.

class DisplayTest {
    @Test fun times_under_an_hour_are_minutes_and_seconds() {
        assertEquals("0:00", Display.time(0))
        assertEquals("0:09", Display.time(9_999))
        assertEquals("4:07", Display.time(247_000))
        assertEquals("42:58", Display.time((42 * 60 + 58) * 1000L))
    }

    @Test fun times_past_an_hour_add_the_hours() {
        assertEquals("1:02:33", Display.time((3600 + 2 * 60 + 33) * 1000L))
        assertEquals("1:00:00", Display.time(3_600_000))
    }

    @Test fun a_negative_time_reads_as_zero() = assertEquals("0:00", Display.time(-5000))

    @Test fun numbers_are_blank_when_untagged() {
        assertEquals("", Display.number(null))
        assertEquals("", Display.number(0))
        assertEquals("7", Display.number(7))
    }

    @Test fun counts_separate_thousands() {
        assertEquals("8,127", Display.count(8127))
        assertEquals("26,795", Display.count(26795))
        assertEquals("3", Display.count(3))
    }

    @Test fun sample_rates_drop_a_trailing_zero() {
        assertEquals("44.1 kHz", Display.sampleRate(44100))
        assertEquals("48 kHz", Display.sampleRate(48000))
        assertEquals("192 kHz", Display.sampleRate(192000))
        assertEquals("2822.4 kHz", Display.sampleRate(2822400))
        assertEquals("", Display.sampleRate(null))
    }

    @Test fun bitrates_and_bit_depths() {
        assertEquals("320 kbps", Display.bitrate(320))
        assertEquals("1,411 kbps", Display.bitrate(1411))
        assertEquals("", Display.bitrate(0))
        assertEquals("24 bit", Display.bitDepth(24))
        assertEquals("1 bit", Display.bitDepth(1))
        assertEquals("—", Display.bitDepth(null))
    }

    @Test fun percentages() {
        assertEquals("0%", Display.percent(0.0))
        assertEquals("100%", Display.percent(1.0))
        assertEquals("62.4%", Display.percent(0.624))
        assertEquals("<0.1%", Display.percent(1.0 / 26000))
    }

    @Test fun sizes_in_binary_units() {
        assertEquals("512 bytes", Display.size(512))
        assertEquals("83 MB", Display.size(83L * 1024 * 1024))
        assertEquals("1.4 TB", Display.size((1.4 * 1024 * 1024 * 1024 * 1024).toLong()))
        assertEquals("692 GB", Display.size(692L * 1024 * 1024 * 1024))
    }

    @Test fun long_durations_keep_the_two_largest_units() {
        assertEquals("74 d 3 h", Display.longDuration(((74 * 24 + 3) * 60 + 20) * 60_000L))
        assertEquals("14 h 20 m", Display.longDuration((14 * 60 + 20) * 60_000L))
        assertEquals("52 m", Display.longDuration(52 * 60_000L + 30_000))
    }
}
