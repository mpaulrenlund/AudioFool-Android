package com.mpaulrenlund.audiofool.core.playback

import org.junit.Test
import kotlin.test.assertEquals

// What the readout calls the output (0.2.5, the user's call). New: the desktop names its output by mode.

class OutputNamesTest {
    @Test fun a_usb_dac_by_its_own_name() {
        assertEquals("Shanling UA2 Plus", OutputNames.usb("Shanling UA2 Plus", "SM-S928B"))
        assertEquals("Shanling UA2 Plus", OutputNames.usb("  Shanling UA2 Plus ", "SM-S928B"))
    }

    @Test fun without_the_usb_audio_prefix() =
        assertEquals("Shanling UA2 Plus", OutputNames.usb("USB-Audio - Shanling UA2 Plus", "SM-S928B"))

    @Test fun usb_when_it_has_no_name_of_its_own() {
        assertEquals("USB", OutputNames.usb(null, "SM-S928B"))
        assertEquals("USB", OutputNames.usb("  ", "SM-S928B"))
        assertEquals("USB", OutputNames.usb("SM-S928B", "SM-S928B"))
    }

    @Test fun another_phones_headphone_jack_is_3_5_mm() =
        assertEquals("3.5mm", OutputNames.headphones(isFiio = false, fiioJack = -1))

    @Test fun a_fiio_says_headphones_until_it_names_the_jack() {
        assertEquals("Headphones", OutputNames.headphones(isFiio = true, fiioJack = -1))
        assertEquals("Headphones", OutputNames.headphones(isFiio = true, fiioJack = 2))
    }

    // Read off the M21 (0.2.5): type 1 with the 3.5 mm jack, 8 with the 4.4 mm.
    @Test fun the_m21s_jacks_by_fiios_type() {
        assertEquals("3.5mm", OutputNames.headphones(isFiio = true, fiioJack = 1))
        assertEquals("4.4mm", OutputNames.headphones(isFiio = true, fiioJack = 8))
    }

    @Test fun the_route_decides_which_name() {
        assertEquals("Shanling UA2 Plus", OutputNames.of(OutputRoute.Usb, "Shanling UA2 Plus", "SM-S928B", isFiio = false, fiioJack = -1))
        assertEquals("Speaker", OutputNames.of(OutputRoute.Speaker, "SM-S928B", "SM-S928B", isFiio = false, fiioJack = -1))
        assertEquals("Bluetooth", OutputNames.of(OutputRoute.Bluetooth, "WH-1000XM5", "SM-S928B", isFiio = false, fiioJack = -1))
    }
}
