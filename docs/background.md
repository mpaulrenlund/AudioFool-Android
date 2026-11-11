Background on the S24's own USB driver, Debug, the open Bit-Perfect items and the test lists. Moved out of HANDOFF.md on 2026-11-11, unchanged except for cross-references.

## Background worth knowing (2026-10-30, still true)

- **True bit-perfect on the S24 works** (since 0.4.6): with BP on and a USB DAC in, the app's
  own USB Audio driver (libusb, `usbaudio_driver.cpp`) plays to the DAC; Android's audio stack
  is bypassed. Everything is in `design/usb-bit-perfect.md` and spec §6.2. What it took:
  - **Volume is a hearing-safety matter.** Samsung turns a USB DAC down in its own software and
    says it takes 0 dB off, so the driver's volume comes from the user only (quietest first,
    then where the keys left it). Two loud blasts happened on the way (0.4.2, 0.4.3). Treat any
    volume change here as a hearing-safety change.
  - **Giving the DAC back** needs a USB reset, or it stays silent; Android's media volume goes
    to 1 and the DAC to 0 dB at a give-back. The DAC is muted while BP takes it and at rate
    changes (no pop since 0.4.6).
  - **Android ending the app** (0.4.7, 0.4.8): the service stays foreground while the DAC is
    held; paused and out of sight (`MainActivity.onStop`) the DAC is given back at once (the
    user's call); the next start repairs a DAC left held (`UsbDriver.repairIfLeftHeld`).
  - **The stutter at app switches** (0.4.9–0.5.1): with BP on no `AudioTrack` played, so
    Samsung/Android didn't count the app as playing and **stopped the whole process for about a
    second** at a switch, foreground service or not. 0.5.0's heartbeat thread proved it (the app
    stopped 1005 ms of a 1006 ms gap). 0.5.1's `SilentTrack` (a static `AudioTrack` looping
    zeros, started and paused with `UsbOutput`) fixed it. 128 ms in flight and a 1 s ring
    (0.4.9) stay.
- **Debug is how phone results come back** (logo > Statistics > last row > Debug): logs can't
  be read off the phones. The Playback panel (route, Bit-Perfect, mixers, what each song opened
  as), the Debug panel (start times, cache and scan numbers, formats, covers filled, the token
  sheet), and the USB driver row (gaps since the stream started, "longest gap N ms:
  the app stopped N ms, a send to the DAC N ms, waiting for samples N ms", the event thread's
  priority, "silent track: playing / paused"). Keep every number there reachable: cheap, and
  they name the cause if a stall comes back.
- **Still open on the Bit-Perfect list** (unticked in `design/progress.md`; not asked for, don't
  start unasked): a **DSD file** through the driver; **notification sounds** while the driver
  holds the DAC; the **M21 check** that 0.4.0's new output layer changed nothing there (BP on,
  an album through the headphone out: gapless, readout, seeks without clicks). The user also
  might want BP remembered per output; only if they ask.
- **The test lists** in `design/progress.md` hold only what is still open, newest build first,
  then by subject (sorted on 2026-10-30; the "seen in daily use" items under them the user
  confirmed with "All fine" that day). Each build adds its own section at the top. The
  per-version lists with every result up to 0.5.1 are in `design/progress-archive.md`. Don't
  block on any of them.
