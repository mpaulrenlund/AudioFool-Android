package com.mpaulrenlund.audiofool.core.playback

/**
 * What the readout calls the output (0.2.5, the user's call): a USB DAC by its own
 * name ("Shanling UA2 Plus" rather than "USB"), and a headphone jack by its size
 * ("3.5mm", "4.4mm" rather than "Headphones"). New here; the desktop names its
 * output by mode.
 */
object OutputNames {
    /**
     * A USB device by the name Android gives it (`AudioDeviceInfo.productName`),
     * without the "USB-Audio - " some phones put in front. "USB" when it has no name
     * of its own: blank, or the phone's model (what Android calls a device it can't name).
     */
    fun usb(productName: String?, phoneModel: String): String {
        val name = productName?.trim()?.removePrefix(USB_AUDIO_PREFIX)?.trim().orEmpty()
        return if (name.isEmpty() || name.equals(phoneModel.trim(), ignoreCase = true)) OutputRoute.Usb.label else name
    }

    /**
     * A headphone jack. On a FiiO player, by the jack FiiO says is in use
     * ([fiioJack], its `HEADSET_TYPE_CHANGED` `type`); "Headphones" while it hasn't
     * said, or for a value not known. Any other phone's headphone jack is 3.5 mm.
     */
    fun headphones(isFiio: Boolean, fiioJack: Int): String =
        if (!isFiio) JACK_3_5 else FIIO_JACKS[fiioJack] ?: OutputRoute.Headphones.label

    /** The name for [route]: [usb] and [headphones] where they apply, else the route's own label. */
    fun of(route: OutputRoute, usbProductName: String?, phoneModel: String, isFiio: Boolean, fiioJack: Int): String = when (route) {
        OutputRoute.Usb -> usb(usbProductName, phoneModel)
        OutputRoute.Headphones -> headphones(isFiio, fiioJack)
        else -> route.label
    }

    const val JACK_3_5 = "3.5mm"
    const val JACK_4_4 = "4.4mm"

    private const val USB_AUDIO_PREFIX = "USB-Audio - "

    /**
     * FiiO's `HEADSET_TYPE_CHANGED` `type` values, read off the M21 over adb (0.2.5):
     * 1 with headphones in the 3.5 mm jack, 8 in the 4.4 mm, 0 unplugged.
     */
    private val FIIO_JACKS: Map<Int, String> = mapOf(1 to JACK_3_5, 8 to JACK_4_4)
}
