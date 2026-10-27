# True Bit-Perfect on the S24: what it takes

Written 2026-10-27, before anything is built (HANDOFF: "write up what it takes before building
anything"). Nothing here is decided until the user says so.

## Why the S24 can't do it today

Through Android, the S24 offers the DAC only the mixer's **default** entries (48 to 384 kHz,
16/24/32-bit) with **no bit-perfect entry and no 44.1 kHz** (the user's Debug report,
2026-10-25). So BP on is "Rate Matched" at 48 kHz and up: the right rate, but still Android's
mixer. A 44.1 kHz song, most of the library, is resampled to 48 kHz either way. No setting an
app can reach changes that.

## The way round it: the app drives the DAC itself

This is what USB Audio Player Pro, HiBy Music and Neutron do on phones without root.

1. **Permission.** Android's `UsbManager` asks the user once to let AudioFool use the DAC,
   then hands the app a file descriptor for it.
2. **Take the DAC from Android.** Claiming the DAC's audio streaming interface with force
   detaches Android's own USB audio driver from it. Android then sees the USB audio device
   gone: **while AudioFool holds the DAC, every other sound (notifications, other apps, a
   call) comes out of the phone's speaker**, as it does with those apps.
3. **Speak USB Audio Class to it.** Read the DAC's descriptors; pick the alternate setting
   whose format holds the song (16-bit in 2-byte slots, 24-bit in 3 or 4, 32-bit); set the
   DAC's clock to the song's rate; send the samples as **isochronous transfers**, one packet
   every USB microframe (8,000 a second), sized by the DAC's **feedback** (an asynchronous DAC
   says how fast its own clock is taking samples, and the packet sizes follow it, or it
   clicks or drifts).
4. **Give it back.** Release the interface and reattach Android's driver, so the DAC is an
   ordinary Android output again (BP off, the app stopping, or after a pause).

Android's Java USB API can do the permission, the descriptors and control requests, but **not
isochronous transfers**. Those need native code: either **libusb** (the usual library, which
works on Android from a file descriptor, `libusb_wrap_sys_device`; current 1.0.30,
LGPL-2.1) or the same Linux `usbfs` ioctls written by hand.

What is gained: **the song's own rate and samples reach the DAC unchanged, 44.1 kHz
included**, at any rate the DAC itself offers (the 192 kHz `AudioFormat` cap no longer
applies, so DSD128 over DoP at 352.8 kHz becomes possible).

## What it touches

| Part | What |
|---|---|
| `third_party/libusb/` (new) | libusb's source, vendored unchanged as TagLib is, built by CMake as its own `.so` (LGPL: dynamic linking, licence text kept) |
| `app/src/main/cpp/usbaudio_jni.cpp` (new) | Open from the fd, claim and detach, set the alternate setting and rate, keep a few transfers in flight on an event thread, read the feedback, reattach on release |
| `core/.../usbaudio/` (new, JVM tests) | The pure logic: UAC1/UAC2 descriptor parsing, choosing the alternate setting for a song, packet sizes from the feedback value, packing samples into the DAC's slots, the volume control's dB range |
| `OutputStage` | A third kind of output beside the `AudioTrack`: same decoders, same gapless joins and fades, same position counting (frames the DAC has taken) |
| `OutputRoutes` / `Player` | The DAC held by the app is a route of its own. While held, Android's "device removed" and "becoming noisy" for that DAC must **not** pause the music or reopen the output; a real unplug (`USB_DEVICE_DETACHED`) must. `PlugGuard` likewise |
| Volume | Android's volume no longer reaches the DAC. Volume keys go to the app through the media session (remote volume), even with the screen off |
| Manifest | `android.hardware.usb.host`, a `USB_DEVICE_ATTACHED` filter for audio-class devices (so the permission prompt has an "always" box and doesn't come back at every plug) |
| Readout, Debug | "Shanling UA2 Plus · 44.1 kHz · 16-Bit · Exclusive" for real; a USB panel in Debug (descriptors, rates the DAC's clock offers, feedback, transfers, errors) |
| Spec §6.2, §6.3 | The S24 + USB DAC row; "Rate Matched" stays as the fallback when the app can't take the DAC |

## How it can be tested

- **Nothing here can reach the DAC**: the emulator has no USB passthrough on Windows, and
  each phone's one port holds the DAC while testing. Every step on the real DAC is a test
  build on the S24 and a report back.
- **The pure logic is tested on the JVM**, against the user's DAC's real descriptors. So the
  first build should only **read** the DAC (no change to playback) and let the user save the
  descriptors to a file and send it.
- **Expect several rounds** on the S24 for the transfers: feedback mistakes sound like clicks
  every few seconds or a slow drift, and only the phone shows them.

## Suggested order

1. **0.3.0 — look only.** Ask for the DAC, read its descriptors and its clock's rates, show
   them on Debug, save them as a text file. Answers the open question "does the DAC itself
   offer 44.1 kHz?". Playback unchanged.
2. **Core logic with tests**, built against those descriptors.
3. **The native driver** behind BP on the S24, with the fallback to today's mixer path when
   claiming fails. Test builds until it plays clean, gapless, through seeks and rate changes.
4. **The rest**: volume keys, release after a pause, unplug while playing, DoP and DSD128,
   then the spec and the device lists.

## Decided (the user, 2026-10-27)

- **libusb**, vendored unchanged (LGPL-2.1, its own `.so`), not hand-written `usbfs` code.
- **Volume:** the DAC's own hardware volume when it has one. Without one, the app turns the
  samples down, and the readout says "Exclusive" only at 100%.
- **Hand the DAC back to Android about a minute after a pause**, and at once when BP is turned
  off or the player stops.
- **A look-only build first** (0.3.0): read the DAC and show what it offers, playback unchanged;
  the driver is built afterwards against the user's real DAC.

## The look-only build

- Asks for the DAC from Statistics > Debug (a tap, not at every plug: the "always" filter in the
  manifest comes with the driver), reads its descriptors and shows them parsed.
- **A second tap tries the hard part**: take the DAC from Android, ask its clock which rates it
  offers and its volume control its range (class requests to an interface need the interface
  claimed, so this can't be done while Android's driver holds it), give it back, and check
  Android sees the DAC again. That proves on the S24 that Samsung lets an app take and return
  the DAC before any of the driver is written.
- Saves all of it as a text file the user can send.

## What the S24 showed (0.3.0, the user, 2026-10-27)

The DAC is a **Shanling UA2 Plus, `20B1:3028`** (an XMOS USB chip), **USB Audio Class 2.0**:

- **Clock 5**, internal and programmable. **It offers 44100, 48000, 88200, 96000, 176400,
  192000, 352800, 384000, 705600 and 768000 Hz** (it sat at 48000). So the DAC itself takes
  44.1 kHz: the S24's missing 44.1 kHz is Android's mixer, not the DAC, and the driver is worth
  building.
- **Feature unit 3**: master volume **−63.5 to 0.0 dB in 0.5 dB steps** (at 0.0), mute, and
  channel volumes. So the hardware volume (the user's first choice) is there.
- **Interface 1**, all async with feedback endpoint `0x81`, 776 bytes a microframe (768 kHz ×
  8 bytes ÷ 8,000, plus one frame): alt 1 **16-bit in 2 bytes**, alt 2 **24-bit in 3 bytes**,
  alt 3 **32-bit in 4 bytes**, alt 4 **native DSD** (raw, 32-bit slots). All stereo.
- **Samsung lets the app take it**: interfaces 0 and 1 were claimed with force, the clock and
  volume requests were answered (they need the interface claimed), and giving back worked
  (interface 0 was already back, interface 1 reattached "ok").
- **But Android kept listing the DAC as an output the whole time** (`AudioManager` still had it
  after 3 s). Android's list follows the USB device being attached, not its sound driver, so it
  can't tell us when the DAC is ours, and the "every other sound comes from the speaker"
  expectation above is unproven: other sounds may instead go to a USB output with no driver
  behind it (silent). To check on the S24 with the driver. On the good side, holding the DAC
  then triggers no route change or "becoming noisy" to guard against; a real unplug still has
  to be caught by `USB_DEVICE_DETACHED`.

### Giving it back silences the DAC (the user, 2026-10-27)

After "Take it and give it back", AudioFool's song showed as playing but **nothing came out of
the DAC**. Reattaching Android's driver to the interfaces (`USBDEVFS_CONNECT`) brings the
kernel's sound driver back as a new sound card, but Android's audio service is never told: the
USB device itself never left (the same reason Android kept listing it while it was held), so
Android keeps writing to the old card, which is gone, and its mixer carries on in silence.

**A real unplug and replug brings the sound back** (the user, 2026-10-27), which fits.

So **giving back has to make Android see the DAC unplugged and plugged in again.** The likely
way: after releasing the interfaces, **reset the USB device** (`USBDEVFS_RESET`, or libusb's
`libusb_reset_device`), which makes it re-enumerate as if replugged; Android then rebuilds its
USB audio device from scratch. That needs checking on the S24 before the driver is built round
it: whether the sound comes back, whether permission survives (a replug keeps it), what the
DAC's volume does, and that the app's own `USB_DEVICE_DETACHED`/"becoming noisy" handling
doesn't take the replug for a real unplug.

### What 0.3.1's two tests showed (the user's report, 2026-10-27 15:08)

- **With a USB reset: Android dropped the DAC 102 ms after the reset and set it up again
  1,172 ms later**, as at a replug. So a reset is seen as a replug after all (Samsung's kernel
  re-enumerates rather than just rebinding the driver).
- **After a 5 s wait** (no reset): Android never dropped the DAC, as in 0.3.0.
- Android's USB output was `card=1;device=0` before and after both, so the card number did
  not change. The kernel's list (`/proc/asound/cards`) isn't there for an app to read.
- From the user: **after the wait the DAC was silent**, as in 0.3.0, so waiting is ruled out.
  **After the reset the DAC's volume was unchanged, but Android asked for access again**: the
  re-enumerated DAC is a new device to Android, and the permission went with the old one. So
  the driver needs the manifest's `USB_DEVICE_ATTACHED` filter (the "always open AudioFool"
  choice), which grants access at each attach without asking, before it can hand back with a
  reset.
