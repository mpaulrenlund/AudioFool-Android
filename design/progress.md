# AudioFool Android — Progress

Update this at the end of every session: tick off what's finished, note anything left open, and record every decision that changed or filled in the spec.

## Sessions

- [x] **1. Project, theme and library** (spec §1, §2, §8) — 2026-10-24
- [x] **2. Library screens, navigation and search** (§2, §3, §4) — 2026-10-24
- [x] **3. Playback** (§3, §5, §6.1, §6.3) — 2026-10-24
- [x] **4. Bit-Perfect, DSD over DoP, and an M21 test build** (§6) — 2026-10-24
- [x] **5. Last.fm, Statistics, polish and release** (§4.5, §4.6, §7) — 2026-10-24, released as v0.1.0
- [x] **0.1.1 fixes** from the first device reports — 2026-10-25
- [x] **0.1.2**: the M21's headphone out — 2026-10-25 (didn't take: 0.1.3)
- [x] **0.1.3**: the M21 recognised by its speaker's name; unplug from the jack — 2026-10-25
- [x] **0.1.4**: no click at a restart or a tap (the desktop's seek fade) — 2026-10-25
- [x] **0.1.5**: unplug pauses on the M21; its readout goes by FiiO's reported rate (both checked on the M21 over adb) — 2026-10-25
- [x] **0.5.1**: a silent `AudioTrack` plays while the BP driver plays, so Android doesn't stop the app at an app switch — 2026-10-30, released as a pre-release
- [x] **0.5.0**: Rescan Library in the logo menu; Debug says where the 1.3 s stalls at an app switch go (the app stopped, a send to the DAC, or waiting for samples) — 2026-10-28, released as a pre-release
- [x] **0.4.9**: less stutter at an app switch with BP on (128 ms sent ahead to the DAC, a 1 s ring); Debug counts the gaps by cause — 2026-10-28, released as a pre-release
- [x] **0.4.8**: paused and out of sight, the DAC goes back to Android at once (Samsung ends the app at a swipe from recents) — 2026-10-28, released as a pre-release
- [x] **0.4.7**: no silent DAC after Android ends the app while BP holds it (foreground while the DAC is held; a DAC left held repaired at the next start) — 2026-10-28, released as a pre-release
- [x] **0.4.6**: the DAC muted from the moment BP takes it until its new stream has settled: no pop — 2026-10-28, released as a pre-release
- [x] **0.4.5**: given back, the DAC returns to 0 dB with Android's media volume lowered to 1; the report counts failed packets and reads the volume back — 2026-10-28, released as a pre-release
- [x] **0.4.4**: the DAC's volume from the user only (quietest first, then where the keys left it; every volume control set); BP off and plugging in pause — 2026-10-28, released as a pre-release
- [x] **0.4.3**: never louder when the DAC changes hands (BP off came back far too loud in 0.4.2) — 2026-10-27, released as a pre-release
- [x] **0.4.2**: the DAC's volume kept across hand-backs (and Android's media volume kept with it); 64 ms in flight to the DAC so the background keeps up; refill timing in the report — 2026-10-27, released as a pre-release
- [x] **0.4.1**: the seekbar no longer flashes back after a seek; the driver's Debug row keeps its last eight notes — 2026-10-27, released as a pre-release
- [x] **0.4.0**: true Bit-Perfect on the S24: the app's own USB Audio driver (libusb), the DAC's hardware volume on the volume keys, given back with a reset — 2026-10-27, released as a pre-release
- [x] **0.3.1**: true Bit-Perfect on the S24, a test build: two ways of giving the DAC back (a USB reset; waiting for Android to close its output first) — 2026-10-27, released as a pre-release
- [x] **0.3.0**: true Bit-Perfect on the S24, step 1: the USB DAC read directly (look only, Debug) — 2026-10-27, released as a pre-release
- [x] **0.2.6**: the M21 unplugged says "No Output"; the S24's buttons higher again — 2026-10-26, released as a pre-release
- [x] **0.2.5**: the readout names the output (the DAC's name, the M21's 3.5mm / 4.4mm jack), "Exclusive" and dots; the S24's buttons higher — 2026-10-26, released as a pre-release
- [x] **0.2.4**: a smooth waveform seekbar (the desktop's 600 columns); the S24's Now Playing buttons higher — 2026-10-26, released as a pre-release
- [x] **0.2.3**: Now Playing's spacing on the S24; plugging in a USB DAC or adapter never starts the music — 2026-10-26, released as a pre-release
- [x] **0.2.2**: Statistics' Playback and Debug panels moved to a Debug screen; no border round covers; one quality line; the readout without "Shared", capitalised; Now Playing's new controls layout and a bigger cover on the M21 — 2026-10-25, released as a pre-release
- [x] **0.2.1**: the letter strip always shown; swipe-to-skip no longer leaves the art off the screen — 2026-10-25, released as a pre-release (the stuck art still to confirm on the S24)
- [x] **0.2.0**: the Artists letter strip; Now Playing swipes sideways to skip (no swipe down); the seekbar glides — 2026-10-25, released as a pre-release

## Decisions made in the design

The user made these while the mockups were designed (October 2026). The spec already reflects them.

- **Faithful layout**, not the bolder variant: the desktop's panels, rows and molded round buttons, scaled for touch.
- **No on-screen back buttons** anywhere, including Now Playing's close arrow. System back and swipe-down do it.
- **The logo is the menu button** (Settings, Theme, Statistics). No ⋮ menu; the search field takes the space.
- **Artists show their newest album's art.**
- **No library sizes in GB** on Artists, Albums or Search. Statistics shows the total size.
- **Album table is # · Song · Time.** No Kind or Disc columns.
- **Search songs show no format column.**
- **Now Playing:** no Like button and no "Hi-Res Lossless" label. The track count reads "3 / 10", right of the title. Quality is two right-aligned lines: format · sample rate, then bit depth · bitrate. The output readout sits centred under the controls.
- **Controls for a right thumb:** Repeat, Shuffle and BP stacked on the left (34 dp, smaller than the transport); Previous, Play/Pause and Next on the right, centred on Shuffle and sized to the screen height.
- **BP** replaces the Bit-Perfect chip at the top: same size as Repeat and Shuffle, labelled "BP", green when on and red when off (Light's red is the mobile token `#B3261E`, since the desktop's Light "off" is yellow and unreadable on the button face). Greyed out and not tappable on Bluetooth and the speaker.
- **Mini player** has Previous, Play/Pause and Next.
- **Theme** defaults to Dark and switches by hand.

## Decisions made for the build

0.5.1 (2026-10-30), released as pre-release [`v0.5.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.5.1) ("AudioFool 0.5.1", versionCode 33): **a silent track keeps Android from stopping the app with
BP on.** 0.5.0's Debug on the S24 (the user): still a stutter at app switches; the longest gap
1006 ms, of which **the app stopped 1005 ms** (the heartbeat thread, which only sleeps, didn't
run either), a send to the DAC 7 ms, samples 1 ms. So the whole process is stopped for about a
second at the switch, though the media service is in the foreground. With BP off it never
stuttered, though an `AudioTrack` holds only about half a second: the difference is that with
BP on no `AudioTrack` plays, so to Android (and Samsung's power management, which spares apps
playing audio) the app isn't playing anything. Now `SilentTrack` plays alongside the driver: a
static `AudioTrack` looping 0.1 s of zeros (48 kHz, 16-bit stereo, media attributes), started
and paused with `UsbOutput` and paused when it's released. Only zeros, so nothing can be heard
wherever Android routes it, and static, so it costs the app no work. Debug's driver row says
"silent track: playing / paused" (or why it couldn't open). Emulator: a check build that started
it at launch showed in `dumpsys audio` as an `AudioTrack`, `state:started`, `mutedState:none`;
the shared path still runs one `AudioTrack` only, and none when idle. The 0.5.0 diagnostics stay
until the S24 confirms. If it still stutters with the silent track playing, the next look is
Samsung's battery setting for the app (Unrestricted).

0.5.0 (2026-10-28), released as pre-release [`v0.5.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.5.0) ("AudioFool 0.5.0", versionCode 32): **Rescan Library in the logo menu** (the user's request), a
fourth row under Statistics. It runs the same rescan as the refresh button in Settings' Music
folders and closes the menu; the scan runs in the background as before. Spec 4.1 updated; the
menu's four screenshots re-recorded (S24 and M21, Light and Dark: the row fits the menu's
width). Emulator: a FLAC copied in and then removed, each followed by Rescan Library from the
menu: Statistics went 832 → 833 → 832 tracks. A new feature, so 0.5.0 rather than 0.4.10;
0.4.10 was built but never published, and its diagnostics ride along:

**Finding where the stalls at an app switch come from.**
0.4.9 on the S24 (the user): 5 gaps from USB refills late, 0 from samples late; the longest
gap **1288 ms**, priority -19. No pop at pause, seek or skip; gapless works. So the samples
were always there and the thread was never demoted: the libusb event thread simply didn't run
for over a second, and no amount sent ahead covers that without slowing every pause and seek.
Three things can stop it that long: **the whole app stopped** (a freezer, Samsung's power
management), **a resubmit held up in the kernel** (usbfs takes the device's lock for a submit,
and another request to the DAC holds it; Android's control requests here time out at 1000
ms), or **waiting for the ring's lock**. Diagnostics only, no behaviour change: a heartbeat
thread at the same priority that only sleeps 5 ms, and the longest resubmit and lock wait.
Debug's driver row gets a second line, "longest gap N ms: the app stopped N ms, a send to the
DAC N ms, waiting for samples N ms", and the stop note carries the same. The fix follows from
which of the three matches the gap. Emulator: starts and plays as before (no DAC there, so the
new line can't show).

0.4.9 (2026-10-28), released as pre-release [`v0.4.9`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.9) ("AudioFool 0.4.9", versionCode 31): **less stutter at an app switch with BP on.** The user,
on 0.4.8: the music still stutters a little when going home or to any other app, only with BP
on ("not as bad as before", i.e. than 0.4.1). The Debug numbers weren't read, so both ways the
driver can run short got more room: **128 ms sent ahead to the DAC** (16 transfers of 8 ms; 64
since 0.4.2), the cause 0.4.2 found; and **a second of samples in the ring** (half a second
before), in case it's the output thread. A pause or seek waits for what was sent ahead before
its fade is heard, so it now takes about 150 ms instead of 85. Debug's driver row starts with
a plain line, "gaps since this stream started: N from USB refills late, N from samples late",
to say which one if it still stutters. Emulator: the shared output unchanged (the driver can't
run there).

0.4.8 (2026-10-28), released as pre-release [`v0.4.8`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.8) ("AudioFool 0.4.8", versionCode 30): **paused and out of sight, the DAC goes back at once.**
0.4.7 on the S24: a swipe from recents while paused still left the DAC silent (YouTube silent
after waiting), and opening AudioFool brought it back: so the app was ended at the swipe, still
holding the DAC, and the start-up repair fixed it. Samsung ends the app the moment it is swiped,
before `onTaskRemoved`'s give-back can run. The user's call (of: this; leave it, with the repair
at the next start; a second watcher process): **when AudioFool leaves the screen (home,
recents, screen off) while paused with the DAC held, it is given back at once**
(`MainActivity.onStop` → `Player.letGoOfDacNow`), so a swipe finds it Android's already. Play
after coming back takes it again (muted, no pop), and Android's media volume goes to 1 as at
every give-back. Paused from the notification or a headset while in another app keeps the
minute's wait. Emulator: play, pause, home, back and play again unchanged with no DAC.
**On the S24 (the user, 2026-10-28): all four checks pass**: after a swipe from recents YouTube
plays through the DAC; Play after home or screen off takes the DAC again in under a second; a
pause from the notification keeps the minute. With 0.4.7's results, the silent DAC after
Android ends the app is fixed.

0.4.7 (2026-10-28), released as pre-release [`v0.4.7`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.7) ("AudioFool 0.4.7", versionCode 29): **the DAC no longer goes silent when Android ends the app
while the driver holds it.** The user saw it after leaving AudioFool paused in the background
with BP on. The cause: at a pause the service dropped out of the foreground straight away, so
Android stopped or froze the app before the minute's give-back ran, and later ended it while it
still held the DAC. Ending the app releases the interfaces but leaves them with no driver (Linux
doesn't bind Android's again), and Android goes on writing to its old sound card: silence until
a replug. Two changes (the user approved the first):
- **The service stays in the foreground while the driver holds the DAC**, paused up to the
  minute before it is given back; the notification can't be swiped away for that minute, and
  can again once the DAC is Android's. **Swiped from recents while paused**, the DAC is given
  back at once, then the service ends (`Player.holdingDac`, `letGoOfDacNow`;
  `PlaybackService.stopWhenDacFree`).
- **At the app's start, a DAC left held is given back** (`UsbDriver.repairIfLeftHeld`, Android
  14+ only, so never on the M21): a plugged DAC the app has permission for whose audio interfaces
  have no kernel driver (`libusb_kernel_driver_active`, new `UsbStream.kernelDriverActive`) is
  claimed, put back to 0 dB and unmuted with Android's media volume lowered to 1, and reset, as
  every give-back is. Covers whatever else ends the app holding it (a crash, Android's memory
  killer, a force stop, installing a build over it). Nothing is stored for it. The Debug
  driver row says "found left held …" and then "given back (left held when Android ended the
  app)".
- Checked on the emulator only (no USB there): all tests and screenshots pass; with no DAC the
  start check does nothing and pause/notification behave as before (foreground while playing,
  not when paused).

2026-10-28, after 0.4.6 on the S24: **the driver plays clean** (no pop, sound after a pause,
volume from the user only, BP off and plugging in pausing). Spec §6.2's "S24 + USB DAC" row now
describes the driver (the plan said the spec would change once it did). Still open: a DSD file
through it, notifications while it holds the DAC, the M21 check.

0.4.6 (2026-10-28), released as pre-release [`v0.4.6`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.6) ("AudioFool 0.4.6", versionCode 28): **no pop when BP takes the DAC.**
0.4.5 gives the DAC back at 0 dB, so each take found it there and set the user's level only a few
tens of ms later, after reading the clock and the volume ranges; the DAC popped loudly as it
changed from Android's stream to the driver's. Now the DAC is muted straight after it is claimed
(before anything is read), stays muted through the rate and setting change, and is unmuted
150 ms after its stream starts (which starts paused, so the DAC locks to silence first). The
same at every rate change between tracks. A DAC without a mute control is turned to the bottom
of its range instead. The give-back is unchanged (no pop reported there; a mute could stay on
across the reset and leave Android silent).

0.4.5 (2026-10-28), released as pre-release [`v0.4.5`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.5) ("AudioFool 0.4.5", versionCode 27): **from 0.4.4's report.** Samsung
never touches the DAC's own volume controls (master −51 dB, channels 0 dB, with Android at 11 of
15): it turns a USB DAC down in its own software, which Android's `getStreamVolumeDb` doesn't
show. So:
- **Given back, the DAC goes back to 0 dB** (`UsbVolume.atZeroDb`), as Android expects it, and
  **Android's media volume is first lowered to 1 if it is higher** (and again once Android has
  the DAC back, in case it restores its own). Through Android the volume can then go all the
  way up (0.4.4 left the DAC at the BP level: too quiet at full Android volume), and what plays
  next starts quiet (0.4.2's blast was the DAC at 0 dB with Android at 9 of 15). BP off still
  pauses. The level in BP is unchanged: the user's, from the keys.
- **The silence after a minute's pause** isn't explained (0 underruns, 0 transfer errors). The
  report now counts failed packets inside transfers (the host's per-packet status, which 0.4.x
  didn't look at), gives the DAC's clock and feedback count in each "stopped" line, and reads
  the volume and mute back from the DAC right after setting them.

0.4.4 (2026-10-28), released as pre-release [`v0.4.4`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.4) ("AudioFool 0.4.4", versionCode 26): **the volume from the user only,
and pauses where the volume could jump.** After 0.4.3 came in at full volume twice on the S24.
The report showed why: Samsung's media volume (9 of 15, 3 of 15) "takes 0 dB off" a USB DAC; it
turns the DAC down inside the DAC, in controls the driver didn't set. The user's calls
(2026-10-28), all three the recommended ones:
- **Where the volume starts**: the first time the driver takes the DAC after the app starts, the
  quietest step that is heard (step 1, 59 dB below the DAC's loudest); after that, where the keys
  last left it (kept while the app runs, not saved). Nothing read from Android or the DAC
  decides it (`UsbVolume.startStep`, tested). The driver sets **every** volume control: the
  level on the master, the channels' own controls opened to 0 dB, so the master alone says how
  loud. The "taken" note lists each control as found.
- **BP off while playing pauses** (with the driver holding the DAC); the user plays on when
  ready. A give-back now waits up to 8 s for Android to set the DAC up again and says whether
  it did.
- **Plugging a USB DAC or headphones in while music plays pauses it** (spec 6.1 updated). The
  driver's own give-back isn't a plug (its window is now 12 s).
- 0.4.3's `startingValue`/`stepAtOrBelow` and the Android-curve reading are gone.

0.4.3 (2026-10-27), released as pre-release [`v0.4.3`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.3) ("AudioFool 0.4.3", versionCode 25): **never louder when the DAC
changes hands.** In 0.4.2, Bit-Perfect off while playing came back far too loud on the S24: the
give-back put the DAC's own volume back to 0 dB and relied on Android's media volume (which
0.4.2 moved with the keys) to bring it down, and it didn't. Why exactly isn't known; the new rule
doesn't depend on it:
- **Giving back**: the DAC's volume stays where the keys left it (only unmuted). Android's media
  volume can only take more off, so Bit-Perfect off is never louder than on; it may be quieter,
  and then Android's volume goes up.
- **Taking**: the DAC goes down by what Android's media volume was taking off, from Android's
  own curve (`AudioManager.getStreamVolumeDb`), to the nearest step not louder
  (`UsbVolume.startingValue`, `stepAtOrBelow`, tested). 0.4.0 to 0.4.2 used a 1 dB-per-step guess
  of the curve, which was louder than Android's: the user turned −28 dB down to −55.
- **Android's media volume is no longer moved by the keys** (0.4.2's mirroring is gone).
- **The notes give both volumes in dB** at each take and give-back, and once Android has the DAC
  again, to show whether Android changes its own volume then.
- The DAC keeps its lowered volume after a give-back, also if it is later plugged in elsewhere.

0.4.2 (2026-10-27), released as pre-release [`v0.4.2`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.2) ("AudioFool 0.4.2", versionCode 24): **two problems from 0.4.1 on the
S24.**
- **Louder after a hand-back**: each take started from Android's media volume, which the volume
  keys hadn't moved (they move the DAC). Now the keys' step is kept and used again, unless
  Android's volume was changed since; and Android's media volume follows the keys while the
  driver holds the DAC (nothing hears it then), so once the DAC is Android's again it plays
  about as loud. "About": Android's curve isn't the driver's 1 dB steps.
- **A few seconds' silence on leaving the app**: with an `AudioTrack`, Android's own audio
  service did the timely part; with the driver the app refills the DAC's transfers itself, and
  only 16 ms were in flight. Now 8 transfers of 8 ms (64 ms). A cut (seek, skip) waits for what
  is in flight before its fade is heard, so it now waits about 85 ms instead of 40.
- **The report says why if it happens again**: the longest gap between refills, how many came
  after the DAC had played all in flight, and the event thread's actual priority; each stream's
  summary as it stops. The Driver row keeps 12 notes.

0.4.1 (2026-10-27), released as pre-release [`v0.4.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.1) ("AudioFool 0.4.1", versionCode 23): **two fixes from the S24's 0.4.0
results.** The driver played well (44.1 kHz, hi-res, gapless, volume keys, unplug, BP off).
- **The seek flash**: until the output stage has carried out a seek, the player gives the place
  being sought (`OutputStage.seeksDone` against `Player.seeksAsked`), not the old heard place.
  It was the gap between asking and the 5 ms fade and flush, on any output.
- **The Driver row keeps its last eight notes with times**: the report showed only the last
  give-back, so whether a minute's pause had handed the DAC back couldn't be told.

0.4.0 (2026-10-27), released as pre-release [`v0.4.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.0) ("AudioFool 0.4.0", versionCode 22): **true Bit-Perfect on the S24, the
driver.** The user said to start it after 0.3.1 proved the give-back. All in
`design/usb-bit-perfect.md` > "The driver (0.4.0)". In short:

- **Where**: Bit-Perfect on, a USB DAC, Android 14+, no bit-perfect mixer entry (the S24). The
  M21 and the emulator's pretend DAC keep their old ways. A track the driver can't play goes
  the mixer's way ("Rate Matched"), the DAC handed back first.
- **libusb 1.0.30** vendored unchanged in `third_party/libusb` (signature checked), built as its
  own `libusb-1.0.so` (LGPL, dynamic); credited in README. The CMake project now builds C too.
- **The output stage writes to a `PcmOutput`**: `AudioTrackOutput` (as before) or `UsbOutput`
  (the driver). Gapless joins, the 5 ms seek fade, the heard position and underruns work the
  same through both. A DAC lost mid-song pauses (`onOutputLost`); a seek while handed back is
  kept for Resume.
- **Given back** at BP off, the player stopping, a minute paused (`Player.LET_GO_OF_DAC_MS`),
  or a track it can't play: volume restored, released, reattached, reset, then it waits for
  Android to set the DAC up again; the drop and return don't pause or count as a plug.
- **Volume**: the DAC's hardware volume in 60 steps of 1 dB (0 mutes), started from Android's
  media volume; the volume keys move it through the media session (remote volume).
- **"Always open AudioFool"**: a `USB_DEVICE_ATTACHED` filter on `UsbAttachActivity`, which shows
  nothing; it lets the driver use the DAC at every plug without asking.
- **Debug**: the USB DAC panel's 0.3.x give-back tests are gone (they'd take the DAC from the
  driver); a **Driver** row shows what it does (each second: the clock's feedback, underruns,
  errors, volume), and the report carries it.
- **Not checked anywhere yet**: the emulator has no USB. Checked there: playback, skips, seeks
  (faded), pause/resume and gapless joins through `AudioTrackOutput`, unchanged; all tests
  pass (19 new core tests on the driver's rules), screenshots unchanged.

0.3.1 (2026-10-27), released as pre-release [`v0.3.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.3.1) ("AudioFool 0.3.1", versionCode 21): **a test of giving the DAC back.**
0.3.0's "Take it and give it back" left the DAC silent until a replug (the user, on the S24).
The user chose a test build before the driver. Debug's USB DAC panel now has two rows, each
one test: **Give it back with a USB reset** (reattach Android's driver, then `USBDEVFS_RESET`)
and **Give it back after a wait** (pause, wait 5 s for Android to close its output, then take
and give back as 0.3.0 did). Each shows the sound card Android plays to (the USB output's
address, "card=N;device=0") and the kernel's list of sound cards, if the app may read it,
before, while held and after. The reset may well not be seen as a replug (Linux usually just
rebinds the driver on a reset); the card numbers will say why either works or not. Also fixed:
a second permission answer no longer overwrites what was read with "not allowed".

0.3.0 (2026-10-27), released as pre-release [`v0.3.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.3.0) ("AudioFool 0.3.0", versionCode 20): **true Bit-Perfect on the S24, step 1 (look only)**.

- **What it takes** is written up in `design/usb-bit-perfect.md`: the app drives the DAC itself
  over USB (as USB Audio Player Pro does), since the S24 offers no bit-perfect mixer entry and
  no 44.1 kHz. The user chose (2026-10-27): **libusb**, vendored unchanged (LGPL-2.1, its own
  `.so`); **volume** by the DAC's own hardware volume, else the app's software volume with
  "Exclusive" only at 100%; **hand the DAC back** to Android about a minute after a pause and
  at once on BP off or stop; and **a look-only build first**, so the driver is built against
  the user's real DAC. libusb is not in yet (it comes with the driver).
- **Statistics > Debug > USB DAC** (new panel): the DAC's name and numbers; **Read the DAC**
  (asks Android once from a tap, not at every plug: the manifest's "always" filter comes with
  the driver) shows its descriptors parsed; **Take it and give it back** claims the audio
  interfaces with force (Android's own driver lets go), asks the UAC2 clock for its rates and
  the feature unit for its volume range (class requests to an interface need it claimed), then
  releases them and binds Android's driver again with `USBDEVFS_CONNECT` (`usbdac_jni.cpp`, new
  small library `audiofoolusb`), timing both changes as Android sees them. It pauses the music
  first. **Send the report** saves `Download/AudioFool-USB-DAC.txt` and opens the share sheet
  (the descriptors in words, the test's results, the raw bytes in hex).
- **Core:** `core/.../usbaudio/UsbAudioDescriptors.kt` (new): UAC1 and UAC2 descriptors
  (clocks, feature units, terminals, each playback setting's format, rates and endpoints),
  UAC2 RANGE replies (`UacRanges`), the report's lines (`UsbAudioReport`). New, with tests
  built from the specs (`UsbAudioDescriptorsTest`, 11); the user's own DAC's bytes join them.
- **Manifest:** `android.hardware.usb.host`, not required (the M21 doesn't need it).
- **Checked:** `:core:test`, `:app:testDebugUnitTest`, `:app:verifyRoborazziDebug` (no picture
  changed: ThemeLab draws Debug without the panel), `:app:assembleRelease`. On the emulator
  (no USB): the library loads, the panel says "none plugged in", a tap says "no USB DAC
  plugged in". Everything with a DAC is on the S24 list.

0.2.6 (2026-10-26), released as pre-release [`v0.2.6`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.6) ("AudioFool 0.2.6", versionCode 19):

- **The M21 unplugged says "No Output"** (the user: with nothing plugged in it still said 3.5 or
  4.4mm, "Shouldn't it say Speaker?"). Asked: the M21 is silent unplugged (it has no speaker),
  and the user chose "No Output". FiiO's `type` 0 (sent at an unplug) is now saved and named
  like the jacks, so a fresh start after unplugging says it too; the readout is just "No
  Output", with no rate after it. BP is unchanged there.
- **The S24's buttons higher again** (the user: "could still go a little higher"):
  `controlsLift` 0.33 / 40 dp → 0.46 / 56 dp. 56 dp on the S24 (37 at font size 1.3), 0 on the M21.
- **Checked:** `:core:test` (a new `OutputNames` test for type 0), `:app:testDebugUnitTest`,
  ThemeLab re-recorded: only the 12 S24 Now Playing pictures changed; font size 1.3 still fits.
  Not on the M21: it was unplugged from this PC by then (the type 0 broadcast itself was seen
  over adb in 0.2.5).

0.2.5 (2026-10-26), released as pre-release [`v0.2.5`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.5) ("AudioFool 0.2.5", versionCode 18):

- **The S24's buttons higher still** (the user: "Could we move the buttons slightly higher?"):
  `controlsLift` 0.2 / 24 dp → 0.33 / 40 dp of the spare height. 40 dp on the S24 (26 at font
  size 1.3), 0 on the M21. The readout stays put.
- **The readout reworded** (the user's list): the USB device's own name in place of "USB"
  ("Shanling UA2 Plus": `AudioDeviceInfo.productName`, without a "USB-Audio - " in front; "USB"
  when blank or the phone's model); "3.5mm" or "4.4mm" in place of "Headphones";
  "Exclusive" in place of "(Bit-Perfect)"; "Rate Matched" without brackets; a dot before the
  note and in place of the "/" between rate and depth. "(Resampled)" became "· Resampled" too
  (asked: the user chose it). `OutputReadout.describe` takes a name; `OutputNames` (core, new,
  with tests) picks it. The desktop's readout by mode is unchanged.
- **Which M21 jack** (found with the M21 on this PC over adb, the user plugging in): FiiO's
  `HEADSET_TYPE_CHANGED` sends `type` **1 for the 3.5 mm jack, 8 for the 4.4 mm**, 0 at an
  unplug. It isn't sticky and nothing an app can read (properties, settings, `dumpsys audio`)
  says which jack is in, so the last type FiiO named is **saved** (`settings` → `fiioJack`, the
  user's call) and shown after a fresh start until FiiO says again. Any other phone's
  headphone jack is "3.5mm". Statistics > Debug gains a **FiiO jack** row ("type 8 → 4.4mm"),
  and every FiiO broadcast is logged under `AudioFoolPlay`.
- **Checked on the M21** (release build over adb): plugged into 4.4 mm, "type 8 → 4.4mm"; after
  a force-stop and fresh start with no plug, Output "4.4mm · 44.1 kHz"; moved to 3.5 mm,
  "3.5mm · 44.1 kHz"; playing with BP on, "3.5mm · 44.1 kHz · 16-Bit · Exclusive". ThemeLab:
  every Now Playing and Settings picture re-recorded (the readout's text); the M21's are
  pixel-identical above the readout; the S24's buttons 16 dp higher. The S24 DAC's real name
  can only be seen on the S24 (ThemeLab shows "Shanling UA2 Plus" as a sample).

0.2.4 (2026-10-26), released as pre-release [`v0.2.4`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.4) ("AudioFool 0.2.4", versionCode 17):

- **The seekbar drawn as one smooth shape** (the user sent a reference picture: "ignoring
  colors ... could we make our seekbar style look more like this?"). It showed a peak outline
  with RMS inside, left and right stacked, and a playhead line, at a high resolution. The
  user's choices: **RMS only, smooth** (not the peak + RMS layers I recommended), **both
  channels combined**, **no playhead line**, and **delete the old waveform files**. So the
  phone now does what the desktop does: 600 RMS columns (`WaveformLevels.COLUMNS`, the
  desktop's `Waveform.Columns`) drawn as one outline through the middle of each column,
  mirrored, clipped at the split (`WaveShape`, ported from `WaveformView.Build`; built once
  per size). `nowPlaying.waveformBars` (96) is gone from the tokens.
- **Stored data:** waveforms now live in `no_backup/waveforms-600/`; the old
  `no_backup/waveforms/` (96 levels each) is deleted at start, and each song's waveform is
  decoded again the first time it plays (the user's call).
- **ThemeLab's made-up waveform** is 600 columns with the mockup's outline and an uneven
  texture (a single sine lined up into a comb at that resolution).
- **Checked:** `:core:test`, `:app:testDebugUnitTest`, ThemeLab re-recorded (every Now
  Playing picture, both themes and sizes); on the emulator with a release build, a new
  60-second pink-noise test song (`/sdcard/Music/Waveform Test`, made with ffmpeg: two swells,
  a near-silent dip at 24–27 s) draws as expected. The SD card's `SD Card Band` FLACs are
  damaged again (ffmpeg: "invalid residual" at the start), so their waveform is a few
  columns stretched across: the files, not the app.

- **The S24's six buttons higher** (the user: "too close to the bottom of the screen, which
  makes pressing them a little awkward. The output row can stay where it is"; S24 only). A
  gap of `controlsLift` between the controls row and the readout: 0.2 of the spare height (as
  0.2.3's), up to 24 dp. 24 dp on the S24 (16 at font size 1.3), 0 on the M21. The readout
  stays put; the room comes out of the two flexible gaps above, so the quality line, seekbar
  and times sit about 12 dp higher too (mine: the simplest way, and the gaps stay even).
- **Checked:** `:core:test`, `:app:testDebugUnitTest`, ThemeLab re-recorded: only the 12 S24 Now
  Playing pictures changed (both themes, font size 1.3 included); the readout is at the same
  place, the buttons 24 dp higher.

0.2.3 (2026-10-26), released as pre-release [`v0.2.3`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.3) ("AudioFool 0.2.3", versionCode 16):

- **The S24's empty space on Now Playing** (the user: "a little too much empty space on the
  s24u"; the M21 "looks great"). The S24's cover is limited by its width, so the height it
  can't use, about 120 dp, all went to the flexible spaces, three quarters of it over the
  quality line. Of the options offered (spread it evenly; grow the waveform; a "Next:" line),
  the user chose the first two and "leave the M21 as it is". Both go by the **spare height**:
  screen height − `artHeightReserve` − the font growth − the cover's side, which is exactly
  0 wherever the height limits the cover (the M21). The waveform grows by 0.2 of it, up to
  24 dp (`waveformGrowth`): 64 dp on the S24. The space below the times grows from weight 1
  toward the space above's 3, level with it at 96 dp of spare (`spareEvenAt`): on the S24
  the gaps over the quality line and over the controls are now about 57 dp each (from about
  97 and 42). Spec 5 updated. Tokens added to `mobile-tokens.json` → `nowPlaying`.
- **Plugging in a USB DAC or adapter never starts the music** (the user asked). The app never
  played at a plug itself (a route change keeps paused paused); the play comes through the
  media session, most likely the adapter "pressing" its button as it connects, or the system
  passing one on. Not reproduced (the S24 isn't here). `PlugGuard` (core, new, 8 tests): a play
  from outside the app (`SystemControls.onPlay`: keys, lock screen, notification) within 3 s
  after a USB or wired device came or went is ignored; one up to 3 s before it is undone with a
  pause (the key can beat Android's notice of the device). The app's own buttons skip it.
  Devices already there when the app starts aren't a plug (registering the callback reports
  them all). Bluetooth isn't counted (the user asked about USB). A play from the lock screen in
  the 3 s after plugging is held back too (mine: rare, and safer than telling them apart). The
  Debug screen's **Plug guard** row shows the last plug and what was ignored or undone; the
  Pretend route row counts as a plug, so it can be tried on the emulator.
- **Checked on the emulator** (release build): a play key with no plug plays; a play key, a
  play/pause key and a headset hook 0.5–1 s after a plug are ignored; a play key 4.5 s after
  plays; a play key 0.4 s before a plug is paused again ("undone" in the log); the app's own
  Play 0.35 s after a plug plays; starting the app shows "no plug yet". Lint: no new issues
  (its one error, `NotificationPermission` in `PlaybackService`, was there before).
- **Checked (spacing):** `:core:test`, `:app:testDebugUnitTest`, ThemeLab re-recorded: only the 12 S24
  Now Playing pictures changed (both themes; font size 1.3 keeps the readout on screen); every
  M21 picture is pixel-identical.

0.2.2 (2026-10-25), released as pre-release [`v0.2.2`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.2) ("AudioFool 0.2.2", versionCode 15):

- **Statistics cleaned up** (the user asked; my recommendation, their call): Statistics shows
  Library, Formats and Listening, then a DEBUG panel whose one row opens a new **Debug**
  screen holding the Playback and Debug panels unchanged. They stay because open device
  items are read off them (the S24 DAC's Route row, DoP, a rate change with BP on, BP greyed
  on Bluetooth, the M21's full-library start and scan). Every "Statistics > Playback" in
  the lists below is now **Statistics > Debug**.
- **Dropped:** the **FiiO signals** row (0.1.5; the M21's readout uses the rate, the log was
  only for finding it) and the **Outputs** row (0.1.1; only for naming the M21's jack), with
  the code that fed them. The FiiO receiver stays for the rate.
- **Checked:** ThemeLab `debug` (new) and `statistics-empty` (the new row), both themes and
  sizes; on the emulator: Statistics ends with the row, Debug shows Playback (Route, Phone,
  Bit-Perfect, …, no Outputs or FiiO rows) and Debug, Back returns to Statistics.
- **No border round covers** (the user saw "a white border" everywhere): it was the 1 dp
  `art.border` / `art.thumbBorder` frame, a mid grey in Dark (#33373F) that reads as a light
  outline beside a dark cover, with the picture inset inside it. Covers are now drawn edge
  to edge in `AlbumArt` (lists, album header, mini player) and Now Playing's `BigArt`. The
  placeholder keeps its frame (mine: without it the square vanishes into the panel). The
  token files are unchanged; the token sheet still shows the frame.
- **The quality line** is one line, right-aligned over the seekbar, in the user's order:
  "FLAC · 1,901 kbps · 24-Bit · 48 kHz" (`QualityLines`, tests updated). The user wrote the
  separator as "-"; I kept the app's " · ". "24-Bit" capitalised and hyphenated to match the
  readout. `nowPlaying.qualityLineGap` removed.
- **The readout** drops "Shared" and capitalises its words: "Speaker · 48 kHz (Resampled)",
  "USB · 96 kHz / 24-Bit (Bit-Perfect)", "(Rate Matched)". Only the phone's
  `OutputReadout.describe(route, …)` changed; the desktop's ported `describe(mode, …)` and its
  tests are as they were (the phone no longer calls it). "DSD over PCM" keeps its lower-case
  "over". The Settings row still says "Shared" before anything has played (left as it was).
- **Checked:** `:core:test` (readout and quality tests updated), ThemeLab Now Playing and
  Settings pictures re-recorded (the readout and the line; ThemeLab has no real covers, so the
  border shows only on the emulator); on the emulator: list and Now Playing covers drawn with no
  frame, "FLAC · 99 kbps · 16-Bit · 44.1 kHz", "Speaker · 48 kHz (Resampled)".
- **Now Playing's controls** (the user's call): Repeat and Shuffle side by side, tops level
  with Play/Pause's; BP centred under them, bottom level with Play/Pause's. The left group is
  now as tall as Play/Pause (78 dp on the M21) instead of the old 118 dp column. On the S24 the
  pair (76 dp) and the full-size transport (279 dp) don't fit in 344 dp, so the transport's
  gaps shrink to fit with at least `modeToTransportGap` (12 dp, new) before Previous: about
  10 dp there instead of 22 (mine: keeps the buttons' size for the thumb). The M21 keeps 17 dp.
- **A bigger cover on the M21, the title lower** (the user asked, "keeping the M21 in mind"):
  `artHeightReserve` 426 → 366 dp, the height the controls (about 40 dp) and the one-line
  quality (about 20 dp) gave up, so the M21's cover is 301 dp (from 241) with the same space
  round the text. The S24's cover is limited by the screen's width and stays 344 dp; the
  room it gained goes to the space above the quality line.
- **Checked:** ThemeLab Now Playing pictures, both themes and sizes, font size 1.3 on the M21
  (the readout still on screen), Repeat One's badge, the S24 with BP greyed; the tops and
  bottoms measured level on the M21's picture; on the emulator Repeat, Shuffle and Play
  respond at their new places.

0.2.1 (2026-10-25), released as pre-release [`v0.2.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.1) ("AudioFool 0.2.1", versionCode 14), from the user's report on 0.2.0 (S24):

- **The letter strip is always shown** (the user's call: "that would work better"), so the
  rows end short of it: `artists.index.width` + 2 × `marginEnd` (32 dp) from the list's edge
  instead of `panel.listPadding`, which shortens long names a little. The fade, the hide
  timer and their tokens are gone. Not shown, and no room kept, with fewer than two letters.
  Spec 2 and 4.1 updated. Every Artists picture changed (the strip; the rows' right end).
- **Swipe to skip left the art stuck part-way off the screen** after every skip on the S24
  (the user: every swipe that changed the song; it stayed until leaving Now Playing). Not
  reproduced: not on the emulator (its injected swipes are slow), not in seven new Robolectric
  tests with a phone's timing, a swipe inside one frame and a cancelled one
  (`SwipeToSkipTest`). 0.2.0 ran the spring back after the skip, and set the finger's place
  through one coroutine per move, which could land after the spring started and stop it.
  Now a skip puts the art and title back in place at once with the new song (no animation
  to be stopped), the finger's place is set directly, a new swipe cancels a running spring
  back, and any song change puts them back in place. A short swipe still springs back.
  Spec 5 updated. **To confirm on the S24.**
- **ThemeLab's Statistics cover check** no longer reads covers without `LocalArt` (as Now
  Playing's art): TagLib can't load in a JVM test, and the failed read went uncaught and
  failed whichever compose test ran next (it hit the new swipe test). The app is unchanged.

0.2.0 (2026-10-25), released as pre-release [`v0.2.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.0) ("AudioFool 0.2.0", versionCode 13):

- **A letter strip on Artists** (the user asked; no desktop rule, no mockup). The user's
  calls: hidden until the list scrolls (so spec 2's "no visible scrollbars" holds at rest),
  a bubble with the letter while a finger is on it, and only the letters the library has.
  Mine: it files by the sort key, so "The Beatles" is under B where the list puts it;
  accents dropped (the root collation sorts "Å" with "A"), "#" for digits and symbols, "•"
  for other scripts; letters listed in the order they first appear, so a jump always goes
  down the list. `ArtistIndex` in core, with tests. A haptic tick at each new letter. The
  strip floats over the rows' right end and reserves no width; hidden, it takes no
  touches. Shown 1.5 s after scrolling stops (`artists.index.hideDelayMs`). Spec 2 and 4.1
  updated; tokens in `mobile-tokens.json` → `artists.index`.
- **Checked:** ThemeLab `artists-index`, `artists-index-pressed` and
  `edge-artists-index-full` (all 28 entries fit the M21's list at about 14 dp each), both
  themes and sizes; every older picture unchanged. On the emulator (200-artist sample):
  hidden at rest, shown while scrolling, gone after; a slide from A to S shows the bubble
  and jumps; a tap on A from the end goes back to the top; a tap on a row's right end
  with the strip hidden opens the artist.
- **Now Playing: no swipe down; swipe sideways to skip** (the user asked). Swipe down to
  close (session 3) is gone, with its token and the `close` action: system back closes it.
  The user's calls: left is Next, right is Previous, following the Previous button's
  3-second rule; only the art and the title block take the swipe (clear of the seekbar's
  own drag); they follow the finger and fade, then spring back. Mine: the song changes
  past 96 dp, or on a flick faster than 1,200 dp/s away from the middle; they fade to 0.35
  over the art's width (`nowPlaying.swipe*`). Spec 5 updated.
- **Checked on the emulator** (Gapless Live, repeat off): a swipe down does nothing; a short
  slow swipe springs back; right restarted a song 7 s in, then went back to 2 / 3; left went
  to 3 / 3; a quick 150 px flick skipped; a drag along the seekbar seeks (0:00 → 0:07)
  without skipping; mid-swipe the art and title slide and fade. ThemeLab pictures
  unchanged (nothing differs at rest).
- **The seekbar glides** (the user: "pretty slow to update"). It already redrew every frame
  from a position about 10 ms fresh, but coloured whole bars only, so the played part moved
  one bar in 96 at a time (about every 3 s in a 5-minute song). Now the split falls at the
  exact place, through the bar being played, as the desktop's `WaveformView` clips its shape
  at the position. Spec 5 updated. **Checked on the emulator** (a 3-minute FLAC): the split
  moved 4–5 px between screenshots, steadily, with a bar cut partway; it used to hold still
  for 1.9 s and jump 10 px. The Now Playing pictures changed by a few pixels (the 2:18 bar).

0.1.5 (2026-10-25), found with the M21 on this PC over adb (the user connected it):

- **The M21 does send "becoming noisy"** at an unplug, but from FiiO's status bar
  (`com.android.systemui`, uid 10089, an app), not Android's audio service. A receiver
  registered `RECEIVER_NOT_EXPORTED` only hears the system and itself, so AudioFool never
  heard it; Poweramp did. 0.1.3's "the M21 never says becoming noisy" was wrong. The noisy
  receiver is now exported: anyone sending it can only pause the music. **Checked on the
  M21:** the broadcast history shows it delivered to AudioFool at the unplug, and it paused.
- **Removed** 0.1.3's `ACTION_HEADSET_PLUG` receiver and 0.1.4's jack-file probe: the M21
  sends neither and apps can't read the files.
- **FiiO's own signals reach apps** (exported receiver): `fiio.intent.action.HEADSET_TYPE_CHANGED`
  (`type`: 8 with headphones in, 0 unplugged) and `fiio.intent.action.SAMPLE_RATE_CHANGED`
  (`format`, `rate`: 44100 while a 44.1 kHz song played with BP on, 0 when stopped). That
  rate is the M21's output as FiiO sees it. Statistics > Playback > **FiiO signals** lists
  them.
- **The M21's readout goes by FiiO's reported rate** (the user's call): on a FiiO's headphone
  route, `OutputReadout.hardwareRate` takes FiiO's last nonzero `rate` for the output open
  now (forgotten at each open, so a new song shows the system's 48 kHz for about a second
  until FiiO reports), else the system output's. Spec §6.2 updated. **Checked on the M21:**
  a 44.1 kHz / 16-bit FLAC with BP on reads "Headphones · 44.1 kHz / 16-bit (bit-perfect)",
  a 96 kHz / 24-bit one "Headphones · 96 kHz / 32-bit (bit-perfect)" (24-bit goes as 32-bit
  on that path since 0.1.1). Statistics' System output row adds "FiiO reports …".
- **Why 0.1.2's FiiO check missed:** the M21 reports `Build.MANUFACTURER` "QUALCOMM",
  `Build.BRAND` "qti", `Build.MODEL` "FiiO M21" (Statistics' Phone row).

0.1.4 (2026-10-25):

- **The click at a restart or a tap (M21, BP on or off)** was the playing song cut off
  mid-wave (pause and flush). Ported the desktop's rule (`OutputChain`: 5 ms raised-cosine
  fade out before a seek's jump, 5 ms in after; never DoP) as `SeekFade` in core, with
  tests. The phone can't change samples already handed to the `AudioTrack`, so the fade
  out is a 5 ms `VolumeShaper` given 40 ms to reach the speakers before the pause (a skip
  or tap is that much slower to answer); the fade in is applied to the decoded samples
  after a seek, a reopen and a start part-way in. A song started from its beginning isn't
  faded in, as on the desktop. Debug: Joins counts "cuts faded".
- **Unplug on the M21:** 0.1.3's `ACTION_HEADSET_PLUG` never arrives ("Jack: nothing yet"),
  yet FiiO's own player pauses on an unplug, so FiiO signals it some other way. A **Jack
  files** row reads the kernel's jack switches (`/sys/class/switch`, `/sys/class/extcon`)
  in case an app may; on the emulator neither can be read. If the M21's can't either, the
  options are FiiO's own broadcast (its name unknown) or leaving unplug-pause out there.
- **M21 Bit-Perfect works** (0.1.3, the user's report).

0.1.3 (2026-10-25):

- **0.1.2's FiiO check didn't catch the M21**: still "Speaker", BP greyed. It wanted
  `Build.MANUFACTURER` or `Build.BRAND` to be exactly "FiiO"; the only name known to say
  FiiO was the speaker's own ("FiiO M21", probably `Build.MODEL`). Now "FiiO" anywhere in
  the maker, brand, model or the speaker's name counts. Statistics > Playback gains a
  **Phone** row (maker · brand · model, and "speaker taken as headphones" when it applies),
  so the next report shows which matched.
- **Unplugging on the M21 didn't pause**: Android never sees headphones there, so it never
  says "becoming noisy". The player now also listens for the jack's own `ACTION_HEADSET_PLUG`
  and pauses on an unplug while playing (on any phone; a second pause does nothing). A
  **Jack** row says what the jack last reported, so if the M21 sends nothing, that shows.
  Not testable on the emulator (no jack, and the shell may not send that broadcast).
- **FiiO's own bypass setting:** the user doesn't think the M21 has one.

0.1.2 (2026-10-25):

- **A FiiO player's speaker is its headphone out.** The user's 0.1.1 report from the M21
  (4.4 mm jack): Route "speaker \"FiiO M21\" · rates: 48 kHz · formats: 16-bit"; Outputs
  "type 1 (earpiece), speaker, type 18 (telephony)", all "FiiO M21". No wired device is ever
  listed, so 0.1.1's "headphones over a speaker" rule had nothing to find. Now, where
  `Build.MANUFACTURER` or `Build.BRAND` is FiiO, the speaker route is Headphones
  (`SpeakerlessPlayers`, core, with tests), so BP works the M21's way (`SourceRate`) and
  the readout says Headphones. The Route row says so on such a player. Spec §6.2 updated.
- **What BP does there is still open.** Android says the "speaker" runs at 48 kHz only, so
  unless FiiO's bypass changes the system output's rate, the readout will say
  "Headphones · 48 kHz (resampled)" with BP on (the spec's rule: never "bit-perfect" unless
  seen). The next report says.

0.1.1 (2026-10-25), from the user's first device reports:

- **The glitch and gap at the end of every song** (S24 through the USB dongle DAC, and the
  M21's 4.4 mm out): when a song ran out, `Decoder.read` cleared its buffer before saying
  so, which left it looking full; at a gapless join the output stage then wrote those
  131,072 bytes of old samples (0.74 s at 44.1 kHz / 16-bit) before the next song. It now
  returns an empty buffer. Every gapless join was hit; a reopen for a new format and the
  end of the queue were not. The session 3 check counted joins and underruns and took the
  12.7 s between 12 s songs for a slow emulator clock; it was this.
- **A tap on a song opens Now Playing** (the user's call): from an album and from Search.
  Back returns to the list it came from. Spec §2 and §5 updated.
- **No 24-bit packed `AudioTrack`s.** On the M21 (Android 13, 4.4 mm out, BP off) every
  24-bit song came out as loud static with the music barely audible, while 16-bit and MP3
  (float) played fine. The shared output now sends 24-bit tracks as float (Android mixes in
  float, and float holds 24-bit samples exactly, so nothing is lost); the M21's way
  (`SourceRate`) sends 16-bit as 16-bit and anything wider as 32-bit. USB mixer entries keep
  their own format. Spec §6.2 updated. Not yet heard on the M21.
- **The headphone route:** the M21's BP couldn't be turned on, so the route wasn't seen as
  Headphones. Now, if Android names the speaker (or nothing) for media while wired
  headphones are listed, the headphones are the route; and the route is looked at again a
  second after a device comes or goes. Statistics > Playback gains an **Outputs** row (every
  output Android lists) so the M21's report shows what its 4.4 mm out is called.
- **The S24's readout said "resampled" with BP on.** No readout bug found on the emulator
  (the readout follows each reopen); "resampled" with BP on means the USB mixer wasn't set
  (no entry at the track's rate, refused, or the track not accepted). Mixer set now says
  "track not accepted" for the last case. Waiting on the S24's Statistics > Playback.

Session 5 (2026-10-24):

- **v0.0.5 skipped** (the user's call): built, never released. Its device items carry over
  to 0.1.0.
- **Last.fm** ported from `AudioFool.Core/Scrobbling` with its 38 tests, unchanged in
  behaviour; times are milliseconds. HTTP is plain `java.net` (no new dependency); the
  scrobbler's sends run in a coroutine scope on the main thread. The queue is
  `no_backup/scrobbles.json` (SafeFile, `.bak`).
- **The play position comes from `PlayWatcher`**: the player tells its watchers the heard
  track and place every 250 ms while playing (the desktop's position timer), and which
  track started (by identity). Last.fm and the listening time both use it.
- **Connect** is the desktop's flow (token, browser, poll auth.getSession every 3 s for up to
  5 minutes), inside the Last.fm panel rather than a window: fields for the API key and
  shared secret, then "Approve AudioFool on last.fm in the browser…" with Open again and
  Cancel. No mockup; sizes in `mobile-tokens.json` → `lastFm.*`. The key and secret are kept
  once a token comes back, so a reconnect is pre-filled; Disconnect forgets only the session.
- **Encrypted storage** without a library: AES-GCM with a 256-bit key in the Android Keystore
  (alias `audiofool.lastfm`), the IV and ciphertext in SharedPreferences `lastfm`. A value
  that won't decrypt reads as missing (connect again).
- **The panel's dot** is `status.lastFmDot` while scrobbling, `status.lastFmOff` otherwise
  (not connected, switched off, failing, or rejected: "Last.fm: connect again"). A failing
  send shows the desktop's queue line under the status.
- **Statistics** (no mockup; the user approved the screenshots): the desktop's Statistics
  window fitted to a phone. LIBRARY: Tracks, Albums, Artists in one row, Play time and On
  disk in the next (figures in `type.statValue`), then the scan line. FORMATS: the desktop's
  bar rows, "count · share · size", bars `slider.volumeFill` on `slider.track`. LISTENING:
  the time listened and since when. Then Playback and Debug as before; the cover check moved
  into Debug. `LibraryStatistics` ported with the totals and file types only (the desktop's
  quality tiers, decades and tag checks aren't in the phone's spec).
- **Listening time** (new, no desktop rule): small forward steps of the heard position of
  the same track, as `PlayTracker` counts a play; saved to `no_backup/listening.json` every
  10 s of listening and when the queue ends; counted from the first run of 0.1.0.
- **An ejected SD card while a song on it is open:** vold ends every process holding a file
  open on the volume about 250 ms after `ACTION_MEDIA_EJECT` (it killed the app on the
  emulator). The player now lets go at the eject: the song playing (or decoding) stops,
  paused where it was heard; one opened ahead for a gapless join is dropped. Play while the
  card is out does nothing; with it back, Play carries on from the place.
- **Font size 1.3:** Now Playing's art reserve grows with the text
  (`nowPlaying.artReserveText`, 140 dp of text at 1.0, times fontScale − 1), so the M21
  keeps the readout on screen. Every other screen fits as it was.
- **Search with no matches** shows the empty panel, as with an empty query (unchanged; not in
  the spec).

Session 4 (2026-10-24):

- **Research first** (shown to the user before building): Android 14+'s
  `setPreferredMixerAttributes` with `MIXER_BEHAVIOR_BIT_PERFECT` is optional for phone
  makers (AOSP: "optional", AIDL HAL only); recent Samsung reports (Z Fold 7 on Android 16,
  S23, A53) say Samsung lists no bit-perfect entries, so the S24 may not offer one. Only
  the plain mixer entries are required for USB. FiiO documents "no SRC for third-party
  apps" only for the old X5 III; reviews say the M21 bypasses Android's resampling for
  every app. An app can't read the hardware's rate or `dumpsys` on either device.
- **USB on Android 14+ (the S24):** with BP on, the bit-perfect mixer entry at the song's
  rate and channels, in the narrowest whole-number format that holds its samples (16-bit
  as 16, else 24, else 32; never narrower than the song); **else an ordinary entry at the
  song's rate, "rate matched"** (the user's call: no resampling, but still the mixer, so
  never called bit-perfect); else shared. `MixerChoice` in core, with tests. The
  `AudioTrack` uses the chosen entry's own `AudioFormat`, as Google's sample does, so an
  index channel mask (seen on a Fold 7) still matches.
- **The M21's way, and USB on Android 13** (the M21 with a DAC): with BP on, the song's own
  rate in whole-number samples (float decoders go out as 32-bit, then 24-bit) at full
  volume. The readout calls it bit-perfect only when `getNativeOutputSampleRate` shows the
  system output at the song's rate; "resampled" otherwise. On the emulator it says
  "Headphones · 48 kHz (resampled)", which is the honest answer there.
- **Greyed out:** Bluetooth, the speaker, anything else (HDMI, the earpiece), and a USB
  device that lists no mixer entries at all. The setting keeps its value; the BP label
  and the Settings dot show it, dimmed.
- **DoP** (desktop `DopRule`, ported with its tests): only on the bit-perfect mixer, at a
  rate it offers in 24- or 32-bit stereo. BASSDSD makes the DoP samples
  (`BASS_DSD_DOP`, float, which holds them exactly). A DoP song that doesn't get a
  bit-perfect output is reopened as PCM and the rate isn't tried again on that device, as
  the desktop does. DoP never joins PCM. Rate-matched is not bit-perfect, so DSD goes as PCM there.
- **Android caps `AudioFormat` at 192 kHz on the emulator** (a hidden limit set per device):
  DSD128 over DoP needs 352.8 kHz, so it may not be possible on the S24 either; it falls
  back to PCM if not. On the S24 list.
- **Route changes and the setting reopen the output** at the place heard (`ReopenPoint`),
  the decoder too (DSD may switch between DoP and PCM). A shared output isn't reopened
  for a route change: Android moves it by itself.
- **No ducking on a bit-perfect path:** a navigation prompt pauses the music and it
  carries on afterwards (bit-perfect means nothing changes the samples).
- **The readout** puts the route first: "USB · 96 kHz / 24-bit (bit-perfect)",
  "Speaker · Shared · 48 kHz (resampled)". The shared rate is now
  `AudioTrack.getNativeOutputSampleRate` (the output media actually goes to, Bluetooth's
  too), not the primary mixer's property. No Bluetooth codec: the codec API is system-only.
- **The Settings row's chip toggles Bit-Perfect** (the row too), greyed in the same cases as
  BP, with `status.bitPerfectOn` / `Off` for the dot. Saved in SharedPreferences
  (`bitPerfect`, off when unset).
- **`MODIFY_AUDIO_SETTINGS`** added to the manifest (needed for the mixer calls; a normal
  permission, no prompt).
- **A "Pretend route" switch on Statistics > Playback** (not saved; the emulator has no USB
  DAC or headphone jack): Headphones, or USB with a made-up list of bit-perfect entries that
  is never sent to Android. It's how the bit-perfect paths, DoP and the reopen were played
  through on the emulator. It ships in the test builds; it changes nothing until tapped.
- **Debug numbers** (Statistics > Playback) for the user to report: the route and the
  device's rates and formats, Bit-Perfect and its way here, how the output was opened, the
  system output's rate, whether Android says it would play the song's format direct, the
  USB device's mixer entries grouped by behaviour, and the mixer set (read back from
  Android) with the last attempt.

Session 3 (2026-10-24):

- **BASS 2.4 for Android, vendored unchanged** in `third_party/bass` (as the desktop keeps
  its DLLs in git): the core and the add-ons for every format the desktop plays, for
  `arm64-v8a` (the phones) and `x86_64` (the emulator), with the Java bindings. `bassmix`
  is left out: the desktop needs it for gapless, here the output stage joins tracks
  itself. **WMA can't play** on the phone: un4seen publishes `basswma` for Windows only.
  TAK and DTS can't play on either. Licence terms in README.
- **No media library.** The media session, the notification and audio focus use
  Android's own `MediaSession`, `Notification.MediaStyle` and `AudioFocusRequest`, so
  nothing new comes in (no Media3).
- **The output's format follows the file:** an `AudioTrack` at the track's own rate;
  16-bit sources as 16-bit, 24- and 32-bit sources as 24-bit packed (BASS decodes to
  float, which holds 24 bits exactly), lossy files as float. If a format won't open, it
  falls back to float, then 16-bit. Songs asking for the same format join in the same
  `AudioTrack` (gapless); one in another format reopens it after the last song has been
  heard out ([TrackEnd], ported, with the output treated like the desktop's exclusive mode).
- **DSD is converted to PCM at 88.2 kHz** (`BASS_CONFIG_DSD_FREQ`, the desktop's fallback
  rate), as float. DoP is session 4 (above).
- **What's shown is what's heard:** the desktop's `HearingTimeline` is ported; the
  `AudioTrack`'s playback head says which song and place are coming out, so the screens
  change song at the join, not half a second early.
- **The media session lives with the queue** (`SystemControls`), not with the service, so a
  headset or Bluetooth button resumes a restored queue before anything has played; the
  service holds the notification and the foreground.
- **Resume** (spec 6.1) is new for the phone (the desktop reopens on the last song at
  0:00): `no_backup/resume.json` holds the queue's paths, the song, the place heard,
  shuffle, repeat and the shuffled order, saved with `SafeFile` on every song change,
  pause, seek and mode change, every 10 s while playing, and when the app goes out of
  sight. Restored paused once the library is loaded; songs no longer in the library drop
  out. `PlayOrder.restore` is added for the shuffled order.
- **The end of the queue** goes back to its first song, paused, so Play plays it again.
  The desktop clears its now-playing bar; the mini player stays here.
- **Now Playing's "3 / 10"** counts in play order while shuffled (the 3rd song heard of 10),
  in album order otherwise.
- **The readout** (spec 6.3) is the shared output's: the system mixer's rate, and
  "(resampled)" when the song's rate differs, e.g. "Shared · 48 kHz (resampled)". The
  mockup's "/ 32-bit" is left out: Android doesn't tell an app the mixer's bit depth, and
  the desktop's readout leaves the depth out when the device doesn't say. Session 4 adds
  the route.
- **Seeking:** a tap seeks at once; a drag moves the bar and seeks on letting go.
  Previous restarts the song after 3 s, as on the desktop. Repeat cycles Off, All, One.
- **Swipe down** closes Now Playing after `nowPlaying.swipeCloseDistance` (96 dp, new);
  the screen follows the finger. The big art's placeholder is the desktop's
  `albumHeader.placeholderIconSize`.
- **Songs that won't open** (a removed SD card, WMA) are passed over to the next in the
  play order and listed in Statistics > Playback > Skipped.
- **Debug numbers** (Statistics > Playback): BASS's version and add-ons, the readout, gapless
  joins, reopens and **underruns** (the output running dry while fed: what a gap would be),
  and for each song the format its decoder asked for and the output opened. The mini
  player preview switch is gone.
- **Previews in ThemeLab** use the mockup's song (24/96 FLAC, 1,140 kbps, 3 / 10, 2:18 of
  5:32) and its made-up waveform, so the pictures line up with `design/screens/`.

Session 2 (2026-10-24):

- **Debug numbers live in Statistics** (the user's call): logo > Statistics shows the
  library line and a Debug panel. Session 5 builds the statistics above them; the Debug
  panel stays at the bottom.
- **Launcher icon: the desktop's icon** (the user's call): the PlayStation logo from
  `icon_preview.png`, unchanged, on white, scaled into the adaptive icon's safe zone.
- **No navigation library.** A small back stack of its own (`ui/Navigation.kt`) with
  `PredictiveBackHandler`, so no new dependency. Screens are named (artist, album title),
  not held, so a rescan shows the new library on an open screen. The gesture shrinks the
  top screen to `predictiveBack.scale` and moves it `shift` dp; committed, it slides away.
- **The row you opened stays selected** when you come back (`state.selectionBg`), which is
  what the mockups show on Artists and Albums.
- **Press feedback** is the desktop's hover colour, `state.hoverOverlay`, while a finger is down.
- **The logo menu** has no mockup: a panel under the logo (`menu.*` tokens) with Settings,
  Theme (with the theme's name; it switches at once and the menu stays open) and
  Statistics, with `shadow.headerArt` under it so it reads as lifted over the list.
- **Unavailable music** (spec 1 says "shown as unavailable"; the desktop only says so in
  its status bar): songs on an unreachable folder are drawn at `disabledOpacity` and can't
  be tapped; an album or artist is greyed when all of its songs are; the folder in Settings
  is greyed and its path line says "Not available". Everything stays listed and browsable.
- **The empty Artists list** says what the desktop's `EmptyStateText` says, pointing to
  Settings (from the logo menu) instead of the desktop's Libraries menu.
- **Search** groups hold the library's own artists and albums, so "3 albums" and "11
  tracks" count all of them, not just the matches. Results are worked out off the main
  thread on an index folded once per library. With an empty query the panel is empty.
- **Covers:** the desktop's `AlbumArtService` rules (first five tracks, then the folder;
  whole pictures first; "no cover" saved only when every file was read, so a missing SD
  card isn't remembered as coverless). Copies at the list size (`albums.thumb`, 64 dp) and
  the album header's (`album.art`), JPEG 90 (PNG with transparency), in `no_backup/art`,
  pruned after 90 days unused; decoded copies in memory up to an eighth of the heap. After
  each scan a background fill makes the list copies, newest albums first.
- **Mini player preview:** until playback exists, a debug switch makes a tapped song show
  as playing (silently), so the mini player and the now-playing rows can be checked.
- **The PlayStation logo** is copied into the assets by the build (`copyAppAssets`, which
  also copies the token files), so the repo's PNG stays the only copy.
- **CSS shadow order:** the first layer of a token is drawn on top, as CSS does
  (`tokenSurface` drew them the other way round before).
- **Settings rows:** the mockups' min-height is the content's, inside the padding.
- New tokens in `mobile-tokens.json`: `menu.*`, `predictiveBack.*`,
  `topBar.searchBarPaddingX`, `panel.headerActionPaddingEnd`, `settings.rowGap`,
  `settings.lineGap`, `settings.statusTextSize` and `album.triangle.strokeOnGrid`.

Session 1:

- New repo `C:\AudioFool-Android` (GitHub `mpaulrenlund/AudioFool-Android`); the desktop repo is untouched.
- BASS for decoding, AudioFool's own `AudioTrack` output stage, and a media session for the system controls.
- All-files access with plain folder paths. Music on internal storage and the SD card; about 26,000 tracks.
- Desktop colour token files copied unchanged; mobile sizes and mobile-only colours in `mobile-tokens.json`.
- Last.fm reuses the desktop's API key, entered in the app, never committed.
- Version 1 includes lock screen and notification controls, headset buttons, gapless, DSD, Last.fm, resume, and a library cache. Playlists are not in version 1.
- **No USB connection to either device** (the user's call, 2026-10-24). Both the S24 and the M21 are tested through GitHub pre-release APKs that the user installs and reports back on. The emulator covers what it can before each hand-over; what only a real device can show goes on the lists below.
- **One signing key from the start** (2026-10-24): every APK the user gets, test builds included, is signed with the same release keystore, kept outside the repo, so each build installs over the last without wiping the cache or settings.
- Ask before big choices only (new dependencies, spec changes, stored data, releases).
- **The repo is public** (the user's call, 2026-10-24). This PC pushes and releases as `marcusrenlund`, a collaborator with write access. Session 3 checks BASS's terms for shipping its libraries in a public repo.

## To test on the S24

### 0.5.1 (a silent track so Android doesn't stop the app with BP on)

Released 2026-10-30 as pre-release [`v0.5.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.5.1) (versionCode 33). Installs over 0.5.0 (same key).

- [ ] **BP on, playing: go home and to other apps, several times**: any stutter left? Debug's
  driver row: "silent track: playing", and "the app stopped … ms" small next to the gaps?
- [ ] **Pause with BP on**: "silent track: paused"? Notifications and other apps' sounds as
  before?

### 0.5.0 (Rescan Library in the logo menu; where the stalls at an app switch go)

Released 2026-10-28 as pre-release [`v0.5.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.5.0) (versionCode 32). Installs over 0.4.9 (same key).

- [x] **Logo menu > Rescan Library**: the menu closes and the scan runs (Statistics' Scan line;
  a song added to the SD card shows up afterwards)? **Yes** (the user, 2026-10-30).

- [x] **BP on, playing: switch apps a few times until it stutters**, then Statistics > Debug,
  the driver row's line "longest gap … ms: the app stopped … ms, a send to the DAC … ms,
  waiting for samples … ms": all four numbers. **Still stutters: 3 late refills, longest gap
  1006 ms; the app stopped 1005 ms, a send 7 ms, samples 1 ms** (the user, 2026-10-30). 0.5.1.

### 0.4.9 (less stutter at an app switch with BP on)

Released 2026-10-28 as pre-release [`v0.4.9`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.9) (versionCode 31). Installs over 0.4.8 (same key).

- [x] **BP on, playing: go home, open other apps, come back**, several times: any stutter? If so,
  Statistics > Debug, the driver row's first line: "gaps since this stream started: … from USB
  refills late, … from samples late". **Still stutters: 5 from USB refills late, 0 from samples
  late; longest gap 1288 ms, priority -19** (the user, 2026-10-28). 0.5.0 looks for the cause.
- [x] **Pause, seek and skip with BP on**: still prompt (about 150 ms now), no click? **No pop.**
- [x] **Gapless and a rate change between tracks** with BP on: as before? **Gapless works.**

### 0.4.8 (the DAC given back when paused AudioFool leaves the screen)

Released 2026-10-28 as pre-release [`v0.4.8`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.8) (versionCode 30). Installs over 0.4.7 (same key). Volume low for the first try.

- [x] **Pause with BP on, swipe AudioFool from recents**: does YouTube play through the DAC
  straight after (quiet: Android's volume at 1)? **Yes** (the user, 2026-10-28).
- [x] **Pause with BP on, go home, come back and press Play**: plays through the driver again,
  no pop, at the step the keys left it? How long before the music starts? **Yes, in less than
  a second.**
- [x] **Screen off while paused, then on, then Play**: the same? **Yes, less than a second.**
- [x] **Pause from the notification while in another app**: the DAC still held for the minute
  (Debug: no "given back" until then)? **Given back after a minute.**

### 0.4.7 (no silent DAC after Android ends the app)

Released 2026-10-28 as pre-release [`v0.4.7`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.7) (versionCode 29). Installs over 0.4.6 (same key). Volume low for the first try.

- [x] **Pause with BP on, leave the app** (home, or the screen off) for two minutes or more, then
  play from another app or plug headphones into the DAC's path: sound? The notification can't be
  swiped away for the first minute after the pause, and can afterwards. Debug's driver row:
  "given back (paused a while)". **YouTube played through the DAC after 2 minutes** (the user,
  2026-10-28).
- [ ] **Pause with BP on, swipe AudioFool from recents**: the DAC plays another app's sound
  straight after? Debug (open AudioFool again): "given back (paused a while)". **No: YouTube
  wasn't heard, also after waiting; opening AudioFool brought it back** (the user, 2026-10-28):
  Samsung ends the app at the swipe. 0.4.8 gives the DAC back as the app leaves the screen.
- [x] **Force stop while playing with BP on** (Settings > Apps > AudioFool > Force stop): the DAC
  is silent, as before. Then open AudioFool: within a few seconds, does another app's sound come
  through the DAC, quiet (Android's volume at 1)? Debug: "found left held …", then "given back
  (left held when Android ended the app)" and "Android has the DAC again after … ms". **Works
  well.**
- [x] **BP on again after that**: plays through the driver, no pop, at the quietest step. **Yes.**

### 0.4.6 (no pop when BP takes the DAC)

Released 2026-10-28 as pre-release [`v0.4.6`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.6) (versionCode 28). Installs over 0.4.5 (same key). Volume low for the first try.

- [x] **BP on while playing (after BP off)**: no pop before the music? **The pop is gone** (the user, 2026-10-28).
- [x] **A track at another rate** (44.1 → 96 kHz) with BP on: no pop at the change? **No pop.**
- [x] **Pause over a minute, then Play**: no pop, sound as before? **No pop; the music plays.**

### 0.4.5 (BP off loud enough again; why the silence)

Released 2026-10-28 as pre-release [`v0.4.5`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.5) (versionCode 27). The user's results, 2026-10-28:

- [x] **BP off while playing**: pauses; turned up from 1, loud enough.
- [ ] **BP on again**: at the level from before, **but a loud pop just before the music**. The
  report: each take found the DAC at 0 dB (where the give-back left it) and set −51 dB a moment
  later; the DAC popped as it changed over in between. Fixed in 0.4.6 (muted through the change).
- [x] **Paused over a minute, then Play**: sound this time. The report: 0 failed packets, the
  DAC's clock 5.5126 of 5.5125 frames a microframe, 62,466 and 120,211 feedback values.

### 0.4.4 (the volume from the user only; BP off and plugging in pause)

Released 2026-10-28 as pre-release [`v0.4.4`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.4) (versionCode 26). The user's results, 2026-10-28:

- [x] **BP on, the first time**: very quiet, turned up with the keys.
- [x] **BP off while playing**: pauses; Play comes out of the DAC. **But with BP off the volume
  can't get loud enough**: at Android's full volume it was too quiet to listen to. The give-back
  leaves the DAC's master at the BP level, and Samsung turns the DAC down on top of that.
- [x] **BP on again**: at the level set before.
- [ ] **Paused over a minute, then Play: playing, but no sound.** The report (12:27:20 to 12:28:20):
  the stream ran a minute with 0 underruns and 0 errors (transfer errors only; packet errors
  weren't counted), longest gap 34 ms. Cause not known yet. After a replug the next take played.
- [x] **Plugging the DAC in while music plays on the speaker**: pauses.

### 0.4.3 (never louder at Bit-Perfect off)

Released 2026-10-27 as pre-release [`v0.4.3`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.3) (versionCode 25). The user's results, 2026-10-28, **two more near-misses**:

- [ ] **BP off while playing**: the music went to the **speaker**, at volume 0 (the report:
  Android hadn't set the DAC up again 4 s after the reset). Unplugging and plugging the DAC back
  in then moved the playing song to it, **loud** (a replug resets the DAC's own volume).
- [ ] **BP on again after a minute: full volume.** The report shows why: Android's media volume
  at 9 of 15 and 3 of 15, yet Android said it took **0 dB** off. Samsung turns a USB DAC down
  inside the DAC (controls the driver didn't set), so "the DAC's volume less Android's cut" was
  0 dB both times. Fixed in 0.4.4: the level comes from the user only.
- [x] The report: 0 underruns, 0 late refills, the longest gap 12 ms.

### 0.4.2 (the volume after a hand-back; playing on in the background)

Released 2026-10-27 as pre-release [`v0.4.2`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.2) (versionCode 24). The user's results, 2026-10-27:

- [x] **Volume after a pause**: the same as before the pause.
- [ ] **BP off: far too loud** ("almost blew out my ear drums"). The give-back put the DAC's own
  volume back to 0 dB and left the rest to Android's media volume, which didn't bring it down
  enough. Fixed in 0.4.3 by never raising the DAC's volume at a give-back.
- [x] **Leaving the app**: plays on without a stutter or a gap. The report: 0 underruns, 0 late
  refills, the longest gap between refills 20 ms of 64, priority −19.
- [x] **Seek**: quick.

### 0.4.1 (the seek flash; the driver's last few notes)

Released 2026-10-27 as pre-release [`v0.4.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.1) (versionCode 23). The user's results, 2026-10-27:

- [x] **Seek**: "seems to be fixed".
- [x] **Paused over a minute**: handed back both times ("given back (paused a while)" at 18:43:44
  and 18:46:47 in the report); Play carried on through the driver ("Exclusive"). **But the
  volume came back much louder**: the driver started from Android's media volume again
  (−28 dB) and forgot the volume keys' setting. Fixed in 0.4.2. No notification sound heard.
- [ ] **A DSD file**: not tried yet.
- [ ] **New: leaving the app (swiping away or switching apps) stops the music for a few
  seconds** with Bit-Perfect on. Likely the refills coming too late in the background: 0.4.2
  queues 64 ms instead of 16 and reports the gaps.

### 0.4.0 (true Bit-Perfect: the driver)

Released 2026-10-27 as pre-release [`v0.4.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.4.0) (versionCode 22). The user's results, 2026-10-27:

- [x] **Plugging in**, Android's question answered.
- [x] **A 16-bit 44.1 kHz FLAC**: "working well"; the Driver row says the setting it should.
- [x] **A few minutes of listening**: working well (no clicks or drift reported).
- [x] **Hi-res and rate changes**: working well; gapless works.
- [x] **Seek and skip**: work, but the seekbar flashed back to where the song was for a moment
  before jumping to the new place. Fixed in 0.4.1.
- [ ] **A DSD file**: couldn't test.
- [x] **Volume keys**: working well.
- [ ] **Paused over a minute**: the Driver row didn't say "given back"; the readout said
  "Shanling UA2 Plus · 48 kHz" (Android's output, so probably handed back). The report was taken
  later and showed only the last note; 0.4.1 keeps the last eight with times. Still to check:
  a notification through the DAC then, and Play carrying on through the driver.
- [x] **BP off while playing**: plays on after about 2 seconds.
- [x] **Unplug while playing**: worked.
- [ ] **A notification while held**: not reported.
- [x] **The report**: "given back (Bit-Perfect off): ok, LIBUSB_ERROR_BUSY, ok" (interface 1 was
  already Android's again with interface 0, as in 0.3.x); "not held · allowed".

### 0.3.1 (true Bit-Perfect, a test: giving the DAC back)

Released 2026-10-27 as pre-release [`v0.3.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.3.1). Installs over 0.3.0 (same key). Playback is unchanged. From logo > Statistics > Debug > **USB
DAC**, with the DAC plugged in and a song paused or playing. Try one row, then play a song;
**if it's silent, unplug the DAC and plug it in again** before trying the other.

- [x] **Give it back with a USB reset**: is the song heard afterwards without a replug? Did
  Android ask for access again? Is the DAC's volume as before? **The song was heard without a
  replug; the volume stayed the same; an access prompt came up right after the reset** (the
  user, 2026-10-27). AudioFool asks only from a tap, so that prompt was Android's own at the
  re-attach; its wording is still to find out.
- [x] **Give it back after a wait** (takes 5 s longer): **silent**, the song playing but nothing
  heard (the user, 2026-10-27). Waiting doesn't help.
- [x] **Send the report**: received 2026-10-27 15:08. The reset made Android drop the DAC and set it
  up again (102 ms, then 1,172 ms), like a replug; the wait didn't. Card 1 before and after both.

### 0.3.0 (true Bit-Perfect, look only: the USB DAC panel)

Released 2026-10-27 as pre-release [`v0.3.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.3.0) ("AudioFool 0.3.0", versionCode 20). Installs over 0.2.6 (same key). Playback is unchanged. All from logo > Statistics > Debug >
**USB DAC**, with the DAC plugged in.

- [x] **DAC** names the DAC and its numbers: "Shanling UA2 Plus · 20B1:3028" (the user, 2026-10-27).
- [x] **Read the DAC**: UAC 2.0, clock 5 (programmable), feature unit 3, alts 1–3 PCM 16/24/32-bit
  and alt 4 native DSD, async with feedback 0x81. Android does not ask again after a replug.
- [ ] **Take it and give it back**: claimed 0 and 1; **44.1 kHz is offered** (44.1 to 768 kHz);
  volume −63.5 to 0 dB in 0.5 dB steps; gave back (0 already bound, 1 ok). Android kept listing
  the DAC throughout (see `design/usb-bit-perfect.md`). **Afterwards the song shows as playing
  but nothing is heard** (the user, 2026-10-27): reattaching Android's driver is not enough to
  give the DAC back (see the write-up). **Unplugging and replugging the DAC brings the sound
  back** (the user, 2026-10-27). Still to answer: where did a notification sound go while it
  was held?
- [x] **Send the report**: received 2026-10-27 (SM-S928U, Android 17). Its 361 raw bytes are
  now `UsbAudioDescriptorsTest`'s `ua2Plus`, and parse to exactly what the S24 showed. A small
  flaw in 0.3.0: the report's "Descriptors" part said "not allowed" (left from a tap before
  Allow) although the bytes and the panel were read fine; fix it with the driver's Debug panel.

### 0.2.6 (the buttons higher again)

Released 2026-10-26 as pre-release [`v0.2.6`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.6)
("AudioFool 0.2.6", versionCode 19). Installs over 0.2.5 (same key).

- [x] **Now Playing's six buttons** another 16 dp higher (56 dp above the output row): right now?
  (The user, 2026-10-27: "s24u and m21 both look good".)

### 0.2.5 (the readout reworded, the buttons higher)

Released 2026-10-26 as pre-release [`v0.2.5`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.5)
("AudioFool 0.2.5", versionCode 18). Installs over 0.2.4 (same key).

- [x] **Now Playing's six buttons** another 16 dp higher: comfortable now? (The user, 2026-10-26:
  "could still go a little higher": 0.2.6.)
- [ ] **The output row through the DAC**: the DAC's own name, e.g. "Shanling UA2 Plus · 96 kHz ·
  24-Bit · Exclusive" or "· Rate Matched". If it says "USB" or an odd name, send the Route row
  (Statistics > Debug): the name in quotes is what Android calls it.
- [ ] **Through the USB-C 3.5 mm adapter**: the adapter's own name.

### 0.2.4 (Now Playing's buttons higher, the smooth seekbar)

Released 2026-10-26 as pre-release [`v0.2.4`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.4)
("AudioFool 0.2.4", versionCode 17). Installs over 0.2.3 (same key).

- [x] **Now Playing's six buttons** sit higher, the output row where it was: are they
  comfortable to press now? (The user, 2026-10-26: "slightly higher" still: 0.2.5.)
- [ ] **The seekbar** is one smooth waveform instead of bars. The first play of each song
  works it out again (a flat bar for a moment, as for a new song); later plays show it at once.
  Seeking by tap and drag as before.

### 0.2.3 (Now Playing's spacing, plugging in)

Released 2026-10-26 as pre-release [`v0.2.3`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.3)
("AudioFool 0.2.3", versionCode 16). Installs over 0.2.2 (same key).

- [ ] **Now Playing's spacing:** no large gap over the quality line; the space over the
  seekbar and over the controls about even; a taller waveform, still easy to drag.
- [x] **Plugging in never starts the music** (the user, 2026-10-26: "unplugging/plugging back in
  is fixed. music isn't automatically playing anymore"): paused, plug in the USB DAC, then the USB-C
  adapter, a few times each: it stays paused. Statistics > Debug > **Plug guard**: the last
  plug's time, and whether a play was ignored or undone (if both stay 0 and it never started,
  the adapter wasn't sending one that time). Unplugged then plugged back while playing: it
  pauses at the unplug and stays paused. Play still works from the lock screen or a headset
  button a few seconds after plugging in.

### 0.2.2 (covers, the quality line, the readout, the controls, Debug)

Released 2026-10-25 as pre-release [`v0.2.2`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.2)
("AudioFool 0.2.2", versionCode 15). Installs over 0.2.1 (same key).

- [ ] **Covers without a frame.** Artists, Albums, an album's header, the mini player and Now
  Playing: no light outline round any cover.
- [ ] **Now Playing's quality line**, one line over the seekbar: e.g. "FLAC · 1,901 kbps ·
  24-Bit · 48 kHz".
- [ ] **The output row**: no "Shared"; e.g. "USB · 96 kHz / 24-Bit (Bit-Perfect)" through the DAC
  with BP on, "(Rate Matched)" at 48 kHz and up, "(Resampled)" on the speaker.
- [ ] **Statistics > Debug** opens the playback and debug numbers; back returns.
- [ ] **Now Playing's controls:** Repeat and Shuffle side by side at the top of Play/Pause, BP
  under them at its bottom. On the S24, are Previous/Play/Next still easy to hit with their
  tighter spacing?

### 0.2.1 (the strip always shown, the stuck art)

Released 2026-10-25 as pre-release [`v0.2.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.1)
("AudioFool 0.2.1", versionCode 14). Installs over 0.2.0 (same key).

- [x] **Swipe to skip, again** (the user, 2026-10-25, on 0.2.1: "Swiping seems much better"). Swipe the cover left and right several times: each skip, the
  new song's cover is in the middle at once, never stuck to the side. A short swipe slides
  back.
- [x] **Letter strip, always there** (the user, 2026-10-25, on 0.2.1: "working well"). It shows without scrolling; tap or slide on it. Do the
  rows' right ends look right next to it?

### 0.2.0 (the letter strip, swipe to skip, the gliding seekbar)

Released 2026-10-25 as pre-release [`v0.2.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.0)
("AudioFool 0.2.0", versionCode 13). Installs over 0.1.5 (same key).

- [x] **Letter strip** (superseded by 0.2.1's always-shown strip, confirmed above). Flick Artists: the strip fades in at the right, and out about 1.5 s after
  the list stops. Slide a thumb down it: the bubble follows, the list jumps letter by letter,
  with a light tick each time. "The …" artists sit under their second word's letter. Is it
  easy to hit with a thumb, and does the tick feel right?
- [x] **Swipe to skip on Now Playing** (0.2.0 left the cover stuck after each skip; fixed in 0.2.1, confirmed above). Swipe the cover left: next song; right: back a song
  (or the start of this one if more than 3 s in). A swipe down does nothing; back closes it.
  Does the distance feel right, and does a quick flick work?
- [ ] **Seekbar glides.** While a long song plays, the blue part of the waveform creeps
  forward smoothly instead of a bar every few seconds.

### 0.1.1 (fixes from the first reports)

Released 2026-10-25 as pre-release
[`v0.1.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.1.1)
("AudioFool 0.1.1", versionCode 7). Installs over 0.1.0 (same key).

User's report on 0.1.0 (2026-10-25): playback through the USB DAC works with BP on and off,
but the readout kept saying "resampled" with BP on.

- [x] **A 24/48 song through the DAC** (the user, 2026-10-25, on 0.1.4): BP on "USB · 48 kHz /
  24-bit (rate matched)", BP off "USB · Shared · 48 kHz". The S24 offers the DAC an ordinary
  mixer entry at 48 kHz but no bit-perfect one there (as expected of Samsung). 0.1.0's
  "resampled" with BP on was most likely a 44.1 kHz song with no entry at that rate (shared,
  48 kHz).
- [x] **USB mixers** with the DAC (the user, 2026-10-25): "default: 48, 88.2, 96, 176.4, 192,
  352.8, 384 kHz · 16, 24, 32-bit · 2 ch". **No bit-perfect entries at all, and no 44.1 kHz.**
  So BP on the S24 is "rate matched" at 48 kHz and up, and a 44.1 kHz song falls back to
  shared and is resampled to 48 kHz (the readout says so). True bit-perfect on the S24 would
  need the app's own USB driver (libusb, as USB Audio Player Pro): a new dependency and a
  spec change, the user's call. Not decided.
- [x] **A 24/96 song through the DAC** (2026-10-25): BP on "USB · 96 kHz / 24-bit (rate
  matched)", BP off "USB · 48 kHz (resampled)". BP keeps hi-res at its own rate on the S24.
- [ ] **Route** row with the DAC: does the DAC itself list 44.1 kHz ("rates: …")? If it does,
  Samsung leaves 44.1 out of the mixer; if not, the DAC doesn't offer it over USB.
- [x] **The readout with BP on.** Through the DAC, play a 16/44 and a 24/96 FLAC with BP on, and
  send a photo of logo > Statistics > Playback for each (Output, Route, Bit-Perfect, Opened
  as, USB mixers, Mixer set). That says whether the S24 offers the DAC a mixer at those
  rates and what happened when it was asked.
- [x] **Tapping a song opens Now Playing**, from an album and from Search; back returns to the list.
- [x] **No glitch or gap between songs** through the DAC: a live or continuous album (BP off, then on).
  Reported on 0.1.0: a loud glitch at the end of every song, and no gapless.

### 0.1.0 (session 5: Last.fm, Statistics, edge cases)

Released 2026-10-24 as
[`v0.1.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.1.0)
("AudioFool for Android 0.1.0", a full release, versionCode 6). Installs over v0.0.4 (same key). Also holds v0.0.5's Bit-Perfect items below, which were
never released.

- [ ] **Connect Last.fm.** Settings > Last.fm > Connect: paste the desktop's API key and shared
  secret, Connect. The browser opens last.fm's approval page; approve, come back: within a
  few seconds the panel says "Last.fm: connected as <you>" with a green dot.
- [ ] **Scrobbles reach your profile.** Play a song past half its length (or four minutes):
  last.fm shows "Scrobbling now" when it starts, and the scrobble at its start time.
  Seeking ahead doesn't count as listening.
- [ ] **Offline queue.** Airplane mode, play two songs past the threshold, airplane mode off,
  play anything: both scrobbles appear, with their own times. (Statistics shows nothing for
  this; the Last.fm panel says "Last send failed…" while it waits.)
- [ ] **Disconnect and reconnect.** Disconnect: "not connected". Connect again: the key and
  secret are filled in already.
- [ ] **Statistics.** Logo > Statistics: the five figures and the formats against the desktop's
  Statistics window (the desktop counts every library; the phone only its own folders).
  Listening goes up while you play.
- [ ] **Font size.** Android Settings > Display > Font size one or two steps up: Artists, an
  album, Now Playing and Settings still fit (Now Playing's art shrinks to keep the readout).
- [ ] **A call while playing.** It pauses for the call and carries on after (checked on the
  emulator with a simulated call).

### v0.0.5 (session 4: Bit-Perfect)

**Not released:** the user skipped v0.0.5 at the start of session 5 (2026-10-24). These
items are tested on 0.1.0, which installs over v0.0.4 (same key). Report from logo > **Statistics** > **Playback**: Output, Route,
Bit-Perfect, Opened as, System output, Direct, USB mixers, Mixer set. A photo of the panel
is fine.

- [x] **What the S24 offers your DAC.** Plug the DAC in, open Statistics > Playback: report
  **USB mixers** (two lines, "bit-perfect: …" and "default: …", or "none") and **Route**.
  This answers whether Samsung gives the S24 a bit-perfect mixer at all.
- [x] **24/96 FLAC, Bit-Perfect on and off.** Play it with BP off, then tap BP (Now Playing):
  report Output and Mixer set for each. Expected with BP on: "USB · 96 kHz / 24-bit
  (bit-perfect)" or "(rate matched)"; with BP off: "USB · Shared · 48 kHz (resampled)" or
  similar. Does the DAC's own display (if it has one) show 96 kHz with BP on?
- [ ] **16/44 FLAC, Bit-Perfect on and off.** The same: "USB · 44.1 kHz / 16-bit (…)".
- [ ] **A rate change without cutting the end.** An album or queue going from a 44.1 kHz song
  to a 96 kHz one with BP on: the end of the first song is heard in full, then a short gap
  (the output reopens at the new rate), and the readout changes to 96 kHz.
- [ ] **DSD64 over DoP.** With BP on, play a DSF/DFF: does the DAC show DSD (or DoP), and does
  it sound right (not hiss)? Output should say "USB · 176.4 kHz · DSD over PCM
  (bit-perfect)". If it says "/ 32-bit float … (rate matched)" or 88.2 kHz, it went as PCM:
  report Mixer set. A DSD128 file too, if you have one: it needs 352.8 kHz, which Android may
  not allow (then PCM).
- [ ] **Volume.** With BP on through the DAC, the phone's volume keys: do they change the
  DAC's volume, or nothing? (Bit-perfect leaves the volume to the DAC.) Do notification
  sounds still play, and where?
- [ ] **BP greyed on Bluetooth and the speaker.** Unplug the DAC: BP and the Settings chip go
  dim and can't be tapped; Output says "Speaker · Shared · …". Same with Bluetooth
  connected: "Bluetooth · Shared · … ".
- [ ] **Switching routes mid-song.** While playing with BP on: plug the DAC in (it should move
  to the DAC and reopen bit-perfect at the place it was), pull it out (it pauses, as an
  unplug does), connect Bluetooth (BP goes grey, it plays on Bluetooth).
- [ ] **A navigation prompt with BP on** (through the DAC): the music pauses for it and
  carries on after, rather than getting quieter.

### v0.0.4 (session 3: playback)

Released 2026-10-24 as pre-release
[`v0.0.4`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.0.4)
("Playback (session 3 test build)"). Installs over v0.0.3 (same key). Report from logo > **Statistics** > **Playback**.

- [ ] **Formats.** Play a song of each kind you have: FLAC 16/44 and 24/96 (or 24/192),
  MP3, AAC, ALAC, Opus, DSD. Each plays; Playback > Opened shows, per song, what it asked
  for and what the output took, e.g. "96 kHz / 24-bit, 2 ch → output 96 kHz / 24-bit,
  2 ch". Report any where the two halves differ (the output fell back), and any song
  under Skipped.
- [ ] **Gapless.** A live album or a continuous one (Dark Side of the Moon, a DJ mix):
  no gap, click or dip between songs. Playback > Joins should count one gapless join per
  song change and **0 underruns**. A song in another format than the last (16-bit after
  24-bit) reopens the output, with a short gap: that one is expected.
- [ ] **Now Playing.** Tap the mini player: the cover, title, artist, album, "3 / 10", the
  quality lines, the waveform (flat for a moment the first time, then the song's shape),
  the times moving. Tap and drag the waveform to seek. Repeat cycles off / all / one (the
  "1" badge); Shuffle; Previous restarts after 3 s, else goes back. Swipe down or the
  back gesture closes it. Both themes.
- [ ] **Lock screen and notification.** While playing, lock the phone: the cover, title,
  artist, play/pause, previous, next and a seekbar, all working. Same in the notification
  shade. Pause, then swipe the notification away: it goes. Tapping it opens Now Playing.
- [ ] **Bluetooth headset buttons.** Play/pause, next and previous from the headset (and
  from a watch, if you use one). Also after a restart with nothing played yet: the button
  should resume where you left off.
- [ ] **Unplug pausing.** Playing through wired headphones (or a USB-C adapter), pull them
  out: it pauses. Turning a Bluetooth headset off does the same.
- [ ] **Interruptions.** A call, or a voice assistant, pauses it and it carries on after;
  a navigation prompt lowers it briefly; another music app starting stops it.
- [ ] **Resume.** Play an album, seek into a song, pause, swipe the app away from recents,
  open it again: the mini player shows the song, paused; Now Playing shows the same place;
  Play carries on from there, with the rest of the album after it.
- [ ] **The rates.** Playback > Output with the phone speaker, and with Bluetooth, while a
  44.1 kHz and a 96 kHz song play (e.g. "Shared · 48 kHz (resampled)").
- [ ] **Battery and heat** over an hour of playing with the screen off: anything unusual?

### v0.0.3 (the first scan shows the library as it is found)

Released 2026-10-24 as pre-release
[`v0.0.3`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.0.3).

- [ ] **First scan.** Statistics > Forget the cache, swipe the app away and open it: the ARTISTS header shows "Reading tags: N of 8,127" and artists appear within a few seconds, filling in as the scan goes. At the end the count goes and Statistics > Last scan has the time. Then open it again: the library is there at once, with no count.

### Session 2 build (`v0.0.2`)

Released 2026-10-24 as pre-release
[`v0.0.2`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.0.2).
Installs over v0.0.1 (same key): the library, cache and settings stay. The debug numbers
are now under the logo > **Statistics**.

- [ ] **Icon.** The launcher shows the PlayStation logo on white, not Android's default.
- [ ] **Artists.** Opens on Artists with your 192 artists, each with its newest album's cover. Covers appear as you scroll; Statistics > Covers counts up to 821 as the background fill works through them.
- [ ] **Scrolling.** Fling from the top of Artists to the bottom and back: smooth, no stutter, covers filling in. Same for a long album list. (The emulator can't judge this: its software renderer takes about 30 ms a frame for anything.)
- [ ] **Drill-down and back.** Artist → Albums → Album, then the back gesture: hold it halfway, and the screen underneath shows while the one you're leaving shrinks; let go and it slides away. The artist you opened is still highlighted, at the same scroll position.
- [ ] **Search.** Tap the search field: the keyboard comes up; results update as you type, in ARTISTS / ALBUMS / SONGS. Back closes the keyboard, back again returns. Accents are ignored: "bjork" finds Björk.
- [ ] **Theme on every screen.** Logo > Theme switches at once (the menu stays open to show it); Settings > Theme too. Check Artists, Albums, Album, Search, Settings and Statistics in both themes; the status bar icons are readable in both.
- [ ] **Mini player.** Statistics > Mini player preview on, then tap a song: the mini player shows it, the song's row turns blue with the ▸ triangle, Play/Pause toggles. No sound yet (session 3). Off hides it again.
- [ ] **Start times.** Statistics > Cold start and Warm start (back out, open again) with the new screens. Emulator: 1.3 s cold, 1.1 s warm with 826 tracks.
- [ ] **Session 1 items still open below** (folders, rescan, theme, covers, token sheet), now reached from Settings and Statistics.

### Session 1 build (`v0.0.1`)

Session 1 build (`v0.0.1`). Report from the **Debug** panel at the bottom of the screen.

The S24 has **no SD card slot** and holds a smaller library than the desktop's: **192 artists · 821 albums · 8,127 tracks** (the user, 2026-10-24). The full ~26,000-track library and SD card removal are tested on the M21.

- [x] **Install and access.** v0.0.1 installed, was given access and read the library (2026-10-24).
- [ ] **Folders.** The list starts with the phone's `Music` folder. "Add Folder…" picks another on internal storage, shown as "Internal storage · name" with its path. Unticking hides that folder's artists at once; ticking brings them back without a rescan.
- [x] **Counts.** 192 artists · 821 albums · 8,127 tracks (2026-10-24). Still to report: the Formats line.
- [x] **First scan.** Under 30 seconds for 8,127 tracks (2026-10-24). Exact figure, if wanted: Debug > Last scan.
- [x] **Cold start.** "Very fast" (2026-10-24). Exact figures, if wanted: Debug > Cold start and Cache.
- [ ] **Warm start.** Back out with the back gesture and open it again: Debug > Warm start.
- [ ] **Rescan.** The refresh button in MUSIC FOLDERS: Last scan should read about 0 and reuse every track.
- [ ] **Theme.** The moon chip switches to PS1 Light at once and back; the choice survives a restart; the status bar icons are readable in both.
- [ ] **Covers.** The "Newest album of …" row shows a cover and says whether it was Embedded or Folder.
- [ ] **Token sheet.** Debug > Token sheet: tap through the four pages in both themes; back returns.

## To test on the M21

### 0.4.0 (nothing new, a check)

The output stage now writes through a new layer (for the S24's driver); the M21 should play
exactly as 0.2.6 did.

- [ ] **BP on, an album through the headphone out**: gapless, the readout as before, seeks
  without clicks.

### 0.2.6 (No Output)

Released 2026-10-26 as pre-release [`v0.2.6`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.6) (versionCode 19).

- [x] **Unplug the headphones** (the user, 2026-10-27: "Seems to be fixed"): the output row says "No Output"; close and reopen the app with
  nothing plugged in: still "No Output"; plug into either jack: "3.5mm" or "4.4mm" again.

### 0.2.5 (the jack names)

Released as [`v0.2.5`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.5); installed on the M21 over adb (the update kept the saved jack: "3.5mm").
Checked over adb already (above); on the phone:

- [x] **The output row** says "3.5mm" or "4.4mm" for the jack in use, also after the app is
  closed and started again (the user, 2026-10-26: "Output updates correctly"; unplugged it kept
  the last jack: 0.2.6).

### 0.2.4

- [x] **The smooth seekbar** (the user, 2026-10-26: "Seekbar is working/looking great").

0.2.2, released as pre-release [`v0.2.2`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.2) (versionCode 15):

- [ ] **Now Playing's bigger cover**: the title, artist, album and "3 / 12" sit lower, the cover
  larger, and the readout still fits under the controls (also at a larger font size).
- [ ] **Now Playing's controls**: Repeat and Shuffle side by side at the top of Play/Pause, BP
  under them at its bottom.

0.2.0, released as pre-release [`v0.2.0`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.2.0) (versionCode 13):

- [ ] **Letter strip on the full library.** With ~26,000 tracks: does a slide down the strip
  keep up (no stutter as the list jumps), and do the letters fit the M21's shorter screen?

0.1.5 (unplug, and the readout by FiiO's rate), checked on the M21 itself over adb (2026-10-25);
released as pre-release [`v0.1.5`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.1.5)
("AudioFool 0.1.5", versionCode 12):

- [x] **Unplugging pauses** (4.4 mm, BP on): "it paused when i unplugged!", and the
  broadcast history shows "becoming noisy" delivered to AudioFool.
- [x] **The readout follows FiiO's rate**: 44.1 kHz / 16-bit and 96 kHz / 32-bit, both "(bit-perfect)".
- [x] **The 3.5 mm jack**: unplugging pauses too (the user, 2026-10-25).
- [x] **BP off** (a 96 kHz song): "Headphones · Shared · 96 kHz" (the user, 2026-10-25). FiiO
  runs its DAC at the song's rate even through Android's mixer, so nothing is resampled; what
  BP adds on the M21 is the full-volume, whole-number-sample path, not the rate.

0.1.4 (the click), released 2026-10-25 as pre-release
[`v0.1.4`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.1.4)
("AudioFool 0.1.4", versionCode 10). The user's report on 0.1.3 (2026-10-25): BP works; unplugging doesn't
pause and Jack says "nothing yet"; FiiO's own player does pause on an unplug; a small click
on the M21 only, mostly at a restart and at a tapped song, BP on or off.

- [x] **No click** at a restart, a tapped song, Next or a seekbar drag: "Click is gone"
  (the user, 2026-10-25).
- [x] **Jack files**: "can't be read" both plugged in and unplugged (2026-10-25). With no
  noisy broadcast, no `ACTION_HEADSET_PLUG` and no readable switch, an app has no standard
  way to see the M21's unplug; FiiO's player uses something of FiiO's own. **Unplug-pause on
  the M21 is open**, waiting on the user's choice: leave it, or capture a logcat from the
  M21 at an unplug to find FiiO's signal.

0.1.3 (the M21 by its speaker's name, and the jack), released 2026-10-25 as pre-release
[`v0.1.3`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.1.3)
("AudioFool 0.1.3", versionCode 9). The user's report on 0.1.2
(2026-10-25): Output still "Speaker" on the 3.5 mm and 4.4 mm jacks, BP greyed, unplugging
while playing doesn't pause, and no FiiO bypass setting that they know of.

- [x] **BP can be turned on**, Output starts "Headphones". If not, report the **Phone** row.
- [x] **Unplugging pauses** (3.5 mm and 4.4 mm). Either way, report the **Jack** row after
  plugging in and pulling out: "nothing yet" means the M21 doesn't send the jack's signal.
- [x] **With BP on**, a 44.1 kHz and a 96 kHz FLAC: Output and System output.

0.1.2 (the M21's headphone out), released 2026-10-25 as pre-release
[`v0.1.2`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.1.2)
("AudioFool 0.1.2", versionCode 8). The user's report on 0.1.1 (2026-10-25): 24-bit plays
cleanly now and the join glitch is gone, but BP still couldn't be turned on through the
4.4 mm jack; Output "Speaker · Shared · 48 kHz", and Android lists no headphone output.

- [x] **BP can be turned on** through the 4.4 mm (and 3.5 mm) jack, and Output starts with
  "Headphones". Statistics > Playback > Route ends "FiiO M21: its speaker is the headphone out".
- [x] **With BP on**, a 44.1 kHz and a 96 kHz FLAC: report Output and System output. If System
  output stays 48 kHz, Output says "(resampled)": FiiO's bypass isn't changing Android's rate.
- [x] **FiiO's own setting:** is there an SRC bypass / "All to All" / bit-perfect switch in the
  M21's settings or pull-down? Does it change System output?
- [x] **Unplugging the headphones** pauses (the M21 may not tell apps, since Android sees no
  headphones).
- [ ] **Bluetooth** still greys BP ("Bluetooth · Shared · …").

0.1.1 (fixes from the first reports), released as pre-release
[`v0.1.1`](https://github.com/mpaulrenlund/AudioFool-Android/releases/tag/v0.1.1). User's report on 0.1.0 (2026-10-25): through the
4.4 mm out, 24-bit songs were loud static with the music barely audible (16-bit and MP3
fine); BP couldn't be turned on.

- [x] **24-bit songs play cleanly** through the 4.4 mm out with BP off (a 24/96 and a 24/44.1
  FLAC), and through the 3.5 mm out.
- [x] **BP can be turned on** with headphones in. If it still can't, send a photo of Statistics >
  Playback: **Route** and **Outputs** say what the M21 calls its headphone outs.
- [x] **With BP on**, the same 24-bit songs: clean? Report Output, Opened as and System output.
- [x] **Tapping a song opens Now Playing.**
- [x] **No glitch or gap between songs** through the 4.4 mm out: a live or continuous album.
  Reported on 0.1.0: a loud glitch at the end of every song, and no gapless.

0.1.0 (session 5), with the full library on the SD card:

- [ ] **Pull the SD card while a song on it plays.** It stops and shows paused (it used to be
  killed by Android on the emulator); the card's artists stay, greyed. Put the card back,
  Play: it carries on from where it stopped. Same with the card ejected from Android's
  Settings, and with the music paused rather than playing.
- [ ] **Cold start with the full library.** Swipe the app away and open it, three times:
  Statistics > Debug > Cold start. The library should be on screen at once.
- [ ] **Statistics** with the full library: the figures against the desktop's (499 artists ·
  2,376 albums · 26,795 tracks when last seen), and how long the screen takes to open.
- [ ] **Last.fm** on the M21 as on the S24 (the same key; a second session for the same user).

Session 4 (`v0.0.5`, not released: tested on 0.1.0 instead), the M21's first playback build. Report from logo > **Statistics** >
**Playback** (a photo is fine).

- [ ] **Install over the last build** and play something from the SD card through the 3.5 mm or
  4.4 mm out: it plays, Now Playing shows the waveform, the notification works. Report
  Route (it should say "Headphones" and the device type).
- [ ] **Bit-Perfect on the headphone out.** BP should be tappable. Play a 44.1 kHz FLAC, then a
  96 kHz one, with BP on: report Output, System output and Direct for each. **The key
  question is System output:** if it follows the song (44.1 kHz, then 96 kHz), FiiO's
  bypass is in the path and Output says "(bit-perfect)"; if it stays at 48 kHz, it says
  "(resampled)". Then the same with BP off.
- [ ] **FiiO's own setting.** Is there a setting on the M21 for the SRC bypass (in Settings or
  the pull-down)? Does turning it on or off change System output?
- [ ] **DSD on the M21.** A DSF/DFF plays (as PCM: Output "… 88.2 kHz …"); no DoP to the
  internal DAC.
- [ ] **Gapless** across a live album, BP on and off: Playback > Joins, 0 underruns.
- [ ] **Bluetooth on the M21:** BP greyed, "Bluetooth · Shared · …".
- [ ] **Unplugging headphones** pauses.
- [ ] (If you have a USB DAC for the M21) **USB on Android 13:** BP works the M21's way;
  report Output and System output.

Session 2 (`v0.0.2`), with the full library on the SD card:

- **First scan (2026-10-24, in progress):** 27,115 files found; 12,000 read after a little
  over 3 minutes (about 60 a second, so about 8 minutes in all). Working, but the Artists
  screen shows nothing until it ends: it says "Music appears here as it's found", which
  this build doesn't do. Fixed in v0.0.3. Still to report: Statistics > Last scan when it
  finishes.
- [ ] **v0.0.3 first scan.** Install v0.0.3, Statistics > Forget the cache, swipe the app away and
  open it: within a few seconds artists appear and keep filling in, with "Reading tags: N of
  27,115" in the ARTISTS header. Does scrolling and opening an artist work while it reads?
  Report Statistics > Last scan when it ends, to compare with v0.0.2's.
  **Reported 2026-10-24: Last scan 528,563 ms (8 min 49 s)**, about 51 files a second.
  v0.0.2's run was only estimated (about 60 a second at the 3-minute mark), so whether the
  as-found rebuilds slowed it is not settled. The first scan as it now behaves: the user
  says "it's OK".

- [ ] **Scrolling the full library.** Fling through all ~499 artists and a long album list on the Snapdragon 680: smooth? Covers fill in as the background fill works (Statistics > Covers, up to ~2,376).
- [ ] **SD card out.** With the card out, Settings shows "SD card · Music" greyed with "Not available"; the card's artists, albums and songs stay listed, greyed, and its songs can't be tapped. Card back: everything as before, "Up to date".
- [ ] **Search** across the full library: results keep up with typing.

Started early (session 1, `v0.0.1`): the M21 has the SD card slot, and the full ~26,000-track library is meant to be on it. Session 4 adds the playback and Bit-Perfect items. Report from the **Debug** panel.

- [ ] **Install and access.** v0.0.1 installs on Android 13; "Allow…" opens the all-files access page; after allowing, the app comes back and starts reading.
- [ ] **Folders.** The list starts with the internal and the SD card's `Music` folders (where there are any), the card's shown as "SD card · Music". "Add Folder…" picks a folder on the card.
- [ ] **Counts.** The LIBRARY line against the desktop's status bar (499 artists · 2,376 albums · 26,795 tracks when last seen there), and the Formats line.
- [ ] **First scan.** How long the first full scan of the whole library took: Debug > Last scan, with its read, reused and removed counts.
- [ ] **Cold start with the full library.** Swipe the app away and open it again, three times: Debug > Cold start each time, and Cache ("read and built in"). The library should be on screen at once on the Snapdragon 680.
- [ ] **Rescan.** The refresh button in MUSIC FOLDERS: Last scan should read about 0 and reuse every track.
- [ ] **SD card out.** With the app open, eject or remove the card: the counts stay the same, and the LIBRARY line says "Unavailable: SD card · Music (N tracks kept)". Open the app with the card still out: the same. Put it back: "Up to date", nothing removed.

## Session notes

### 0.2.3 — Now Playing's spacing on the S24; plugging in never plays (2026-10-26)

**Changed:** `NowPlayingScreen.kt` (the spare height, `Seekbar`'s height), `mobile-tokens.json`
(`waveformGrowth`, `spareEvenAt`), spec 5, the S24 Now Playing pictures. `core/.../PlugGuard.kt`
(+ test), `OutputRoutes.onPlug` (and the pretend route), `Player.resumeFromSystem` and the
Plug guard row, `SystemControls.onPlay`, spec 6.1.

**Verified:** `:core:test`, `:app:testDebugUnitTest`, `verifyRoborazziDebug` (M21 unchanged),
the plug guard on the emulator with a release build (above); the signed 0.2.3 APK installed over
it on the emulator and played. Released as `v0.2.3`.

### 0.2.2 — a Debug screen; covers, the quality line, the readout (2026-10-25)

**Changed:** `StatisticsScreen.kt` (`DebugScreen`, the row, `DebugActions.openDebug`),
`Route.Debug`, `AppRoot` (one `DebugNumbers` for both screens), `Player.outputDetails` and
`OutputRoutes` (the two rows gone), ThemeLab `debug`, spec 4.6.

**Verified:** `:core:test`, `:app:testDebugUnitTest`, recorded pictures, the release build
on the emulator (above).

### 0.2.1 — the strip always shown, the stuck art (2026-10-25)

**Changed:** `ArtistIndexStrip.kt` (no fade or timer; `showsStrip`, `stripRoom`), the Artists
list's end padding, `SwipeToSkip` (direct offset, no animation on a skip, reset on a song
change), `StatisticsScreen`'s `ArtCheck` guard, `SwipeToSkipTest` (7 tests), spec 2, 4.1, 5.

**Verified:** `:core:test`, `:app:testDebugUnitTest` (every suite, no failures), recorded
pictures (Artists changed, Statistics unchanged), the release build on the emulator: the
strip at rest and after 3 s, a tap on S jumps; a swipe left skips with the new song centred
0.15 s later; a swipe right restarts; a short swipe springs back. **The stuck art itself was
never seen here**, so the S24 has to confirm it.

### 0.2.0 — the Artists letter strip, swipe to skip, the gliding seekbar (2026-10-25)

**Changed:** `core/.../library/ArtistIndex.kt` (+ tests), `ui/ArtistIndexStrip.kt`, Artists wired
to it in `LibraryScreens.kt`, `artists.index` tokens, three ThemeLab pictures, spec 2 and 4.1.
Then Now Playing's `SwipeToSkip` (art and title block) in place of swipe-down, the
`nowPlaying.swipe*` tokens, spec 5. Driven on the emulator with `input swipe` (a 150 px
flick needs about 20 ms to pass the fling speed) and `input motionevent` for mid-swipe
screenshots. Then the seekbar's split at the exact place, measured on the emulator by
reading the played colour along one pixel row of rapid screenshots.

**Emulator data fixed:** both `SD Card Band` FLACs on the emulator's card were damaged
(ffmpeg: "invalid residual"; the second's header unreadable), likely from a card pulled
mid-write in session 5. The app opened them, got no audio, and ended the queue at 0:00.
Rebuilt as 3-minute tones with the same tags (ffmpeg `sine`, level slowly changing).

**Verified:** `:core:test`, `:app:testDebugUnitTest`, `recordRoborazziDebug` (only the new
pictures changed), the release build on the emulator (versionCode 12 still; bump before
release). Not on a phone yet.

### 0.1.5 — the M21's unplug, found over adb (2026-10-25)

**How:** the user connected the M21 to this PC (adb sees it as `model:FiiO_M21`, serial "?",
so pick it by `-t <transport_id>` from `adb devices -l`; the emulator is the other device).
`adb shell dumpsys activity broadcasts history` lists each broadcast with its sender and every
receiver it was delivered to; `dumpsys activity broadcasts` lists registered receivers. The
broadcast extras show as STRIPPED there, so AudioFool's Statistics showed them (read with
`uiautomator dump`). A plain `adb logcat` capture dropped when USB reconnected and caught
little; the broadcast history held everything needed.

**Changed:** the readout on a FiiO's headphone out goes by FiiO's rate (`OutputReadout.hardwareRate`, tested); the noisy receiver exported; the jack receiver and probe removed; a receiver for
FiiO's two signals, shown in Statistics.

**Verified:** `:core:test`, `:app:testDebugUnitTest`, `verifyRoborazziDebug`, the release
build; installed on the M21 with `adb install -r` (versionCode 11, same key, data kept); the
user unplugged while playing: paused, and the history shows the delivery. Then
versionCode 12 with the readout: read off the M21's Now Playing with `uiautomator dump` for
a 44.1 kHz and a 96 kHz song (above).

### 0.1.4 — no click at a restart or a tap (2026-10-25)

**Reported** (0.1.3): M21 BP works. Unplugging doesn't pause ("Jack: nothing yet"), though
FiiO's own player pauses. A small click on the M21, at a restart and a tapped song.

**Changed:** `SeekFade` (core, ported from the desktop's `OutputChain`, with
`SeekFadeTest`); `OutputStage.cut` fades a playing song out with a `VolumeShaper` before
any pause-and-flush (seek, skip, tap, reopen); `Decoder.fadeInNext` fades in after a jump;
Statistics' Jack files row and "cuts faded".

**Verified:** `:core:test` (`SeekFadeTest`), `:app:testDebugUnitTest`,
`verifyRoborazziDebug`, the release build. Emulator: a restart with Previous and a tap on
another song: "2 cuts faded" (the shaper made and applied), the next gapless join 12.01 s
later with 0 underruns; Jack files "can't be read" for both folders. **Not checked here:**
whether the M21's click is gone (only listening can say), and its jack files.

### 0.1.3 — the M21 by its speaker's name, and the jack (2026-10-25)

**Reported** (0.1.2, M21): still "Speaker" and BP greyed on both jacks; unplugging doesn't
pause; no FiiO bypass setting known.

**Changed:** `SpeakerlessPlayers.speakerIsHeadphones` takes the maker, brand, model and the
speaker's name, any containing "FiiO"; `OutputRoutes` passes them; `Player` listens for
`ACTION_HEADSET_PLUG` and pauses on an unplug; Statistics' Phone and Jack rows.

**Verified:** `:core:test` (the M21's names and a Samsung's in the test),
`:app:testDebugUnitTest`, `verifyRoborazziDebug`, the release build. Emulator: starts,
Phone "Google · google · sdk_gphone64_x86_64", Jack "nothing yet", BP greyed on its
speaker. **Not checked here:** the M21's names and its jack signal.

### 0.1.2 — the M21's headphone out (2026-10-25)

**Reported** (0.1.1): S24: the join glitch is gone, Now Playing opens on a tap, 24-bit and
BP work through the DAC. M21: 24-bit plays cleanly, the glitch is gone, but BP still can't
be turned on (Route and Outputs above: only a "speaker").

**Changed:** `SpeakerlessPlayers` (core) takes a FiiO player's speaker route as Headphones;
`OutputRoutes` uses it, and its Route row says so.

**Verified:** `:core:test` (three new tests), `:app:testDebugUnitTest`,
`verifyRoborazziDebug` and the release build. Emulator: not a FiiO, so its speaker stays
Speaker with BP greyed. **Not checked here:** anything on the M21 (its list above).

### 0.1.1 — fixes from the first device reports (2026-10-25)

**Reported** (0.1.0): S24 + USB DAC plays with BP on and off, but the readout stayed
"resampled" with BP on; the user wants a song tap to open Now Playing; the M21's 4.4 mm out
turned 24-bit songs into static, with BP greyed. Then, on both phones: a loud glitch at the
end of every song, and no gapless.

**Changed:** `Decoder.read` returns an empty buffer at a song's end (the join glitch); `AppRoot` opens Now Playing after a song tap; `OutputStage` never opens a 24-bit
packed output (shared: float; the M21's way: 32-bit); `OutputRoutes` takes wired headphones
over a speaker Android still names, looks again a second after a device change, lists every
output (Statistics' new Outputs row) and notes a mixer entry whose track wasn't accepted.

**Verified:** `:core:test`, `:app:testDebugUnitTest`, `verifyRoborazziDebug` (no picture
changed) and the release build. Emulator: a song tapped in Search and in an album opens Now
Playing (keyboard closed; back returns to the list); a 24/96 FLAC opens "96 kHz / 24-bit →
output 96 kHz / 32-bit float" on the speaker, and with the pretend Headphones route and BP on
"→ 96 kHz / 32-bit (SourceRate)"; the emulator's outputs are its speaker and a telephony
out, so its route stays Speaker. Gapless Live (12.000 s songs, by ffprobe): the next song
was heard 12.72 s after the last started before the fix, 11.96 and 11.99 s after. **Not checked here:** whether float and 32-bit play cleanly
on the M21, its route, and the S24's mixer (the device lists above).

### Session 5 — Last.fm, Statistics, polish and release (2026-10-24)

**Device results:** none reported yet for v0.0.4 or v0.0.5 (v0.0.5 was skipped), so step 1
had nothing to fix; those lists carry over to 0.1.0.

**Built:** Last.fm (`core/.../scrobbling`: `LastFmApi`, `PlayTracker`, `ScrobbleEntry`,
`ScrobbleQueue`, `LastFmScrobbler`, ported with `ScrobblingTests`; the app's `LastFm`,
`LastFmStore` and Settings' panel), `PlayWatcher`, Statistics (`LibraryStatistics` ported
with its totals and file-type tests; `ListeningTime`/`ListeningCounter` with tests; the app's
`Listening` and the screen), the SD card eject fix (`Player.letGoOf`) and Now Playing's
reserve at larger font sizes.

**Verified:**
- `:core:test`, `:app:testDebugUnitTest` and `verifyRoborazziDebug`: 340 tests, 0 failures.
  Every earlier picture unchanged; new: Settings' Last.fm panel (connected, connecting),
  Statistics (full-size figures, empty), the edge cases (very long names, a 120-song album,
  an empty library, search with no library) and the main screens at font size 1.3, both
  themes and both sizes.
- Emulator, release build: Statistics with the sample library (829 tracks, 10 formats); the
  listening time counting while playing and updating on the open screen; Gapless Live still
  2 joins, 0 underruns; an SD card unmounted while a song on it played (the app survived,
  paused, the place kept; Play with the card out did nothing; with it back Play carried on
  at 0:18), and while paused; the app started with the card out ("Unavailable: SD card ·
  Music (9 tracks kept)"); a simulated call (paused while ringing and in the call, playing
  after); cold start 1.6 s with 829 tracks.
- **Found on the emulator and fixed:** the eject kill (above); the M21's readout cut off at
  font size 1.3.
- **Not checked here** (on the device lists): a real Last.fm connection and scrobbles (needs
  your key), the full library's cold start and Statistics, a physical card pull, a real
  call, and the font size on the phones.

**Differences from the spec** (spec updated, §4.5, §4.6, §7): the connect form sits in the
Last.fm panel (no mockup), with a queue line under the status when sends fail; Statistics'
layout and the Debug panel below it; the listening time starts at 0.1.0.

### Session 4 — Bit-Perfect, DSD over DoP, and an M21 test build (2026-10-24)

**Researched first** (shown to the user, who chose the rate-matched fallback): see
Decisions. Sources: Android's "Improve audio playback" page, AOSP's "Preferred mixer
attributes", the SDK's own `AudioManager` docs (on this PC), jastreamer PRs #53/#56 (Fold 7),
LastWave #118 (S23), aurora #7 (A53), Roon's community (S24 Ultra, FiiO M23), FiiO's X5 III
FAQ, and M21 reviews (SoundStage! Simplifi, Headfonia).

**Built:** `OutputRoutes` (the route from `getAudioDevicesForAttributes`, followed by an
`AudioDeviceCallback`; a USB device's mixer entries; setting and clearing the mixer; the
debug numbers), the output stage opening each output by `OutputPlan` (mixer entry,
source rate, shared) with `fixedVolume` and a reopen on a route or setting change, DSD
as DoP through BASSDSD, the player's Bit-Perfect setting (saved), focus pausing instead of
ducking on a bit-perfect path, the readout with the route, the BP button wired (on, off,
greyed), the Settings row's chip, and Statistics > Playback's new rows with the pretend
route switch. Ported with their tests: `DopRule` (and the DoP cases of
`PlaybackDecisionTests`). New, with tests: `BitPerfectRule`, `MixerChoice`, the routed
`OutputReadout.describe`.

**Verified:**
- `:core:test` (21 new: DoP 3, Bit-Perfect 18), `:app:testDebugUnitTest`, 289 in all, 0
  failures. `verifyRoborazziDebug`: every earlier picture unchanged; 16 new: Now Playing with
  BP on (USB bit-perfect readout) and greyed (speaker), and Settings with BP on (rate
  matched) and greyed (Bluetooth), both themes, both sizes.
- Emulator, release build (versionCode 5) installed over v0.0.4:
  - The speaker: BP and the Settings chip greyed, a tap does nothing; "Speaker · Shared · 48
    kHz (resampled)"; Statistics shows the route ("speaker \"sdk_gphone64_x86_64\""), "way
    here: None (greyed)".
  - `Gapless Live`: 2 gapless joins, 0 underruns, shared and on the (pretend) bit-perfect mixer.
  - Pretend Headphones, BP tapped on mid-song: reopened at the place as SourceRate, 96 kHz /
    24-bit; readout "Headphones · 48 kHz (resampled)" (the emulator's output stays at 48 kHz).
  - Pretend USB: 24/96 FLAC opened "(BitPerfect)" at 96 kHz / 24-bit; the DFF opened as **DoP,
    176.4 kHz / 24-bit**, "USB · 176.4 kHz · DSD over PCM (bit-perfect)"; BP off mid-song
    reopened it as 88.2 kHz float PCM, shared; on again, DoP again.
  - The setting survives a restart (on); the pretend switch doesn't (off).
  - The media session still shows the song.
- Found and fixed on the emulator: `AudioFormat` refuses rates above 192 kHz there (the
  pretend list crashed building 352.8 kHz).
- **Not checkable on the emulator** (on the S24 and M21 lists): any real USB DAC (what Samsung
  lists, whether the mixer is set, DoP into a DAC), the M21's bypass (System output), the
  volume keys on a bit-perfect output, Bluetooth, unplugging, and a ducking prompt.

**Differences from the spec** (the spec is updated, §6.2 and §6.3): the rate-matched
fallback (the user's call); the M21's way checks the system output's rate before calling
itself bit-perfect; a USB device with no mixer entries greys BP out; no Bluetooth codec in
the readout (system-only API); the shared readout has the route first ("Speaker · Shared ·
48 kHz"), so the mockup's "Shared · 48 kHz / 32-bit (resampled)" isn't what the app shows
(ThemeLab's mockup picture keeps the mockup's text).

### Session 3 — Playback (2026-10-24)

**Built:** BASS for Android vendored (core, FLAC, DSD, Opus, AAC, ALAC, APE, WavPack,
Musepack, AC-3), the output stage (BASS decode streams into an `AudioTrack` at the song's
own rate and depth, gapless joins in one output, a reopen for a new format, what's
heard from the playback head), the player (queue, play order, repeat, shuffle, Previous's
3-second rule, audio focus, unplug pausing, resume), the media session and the foreground
service with the notification, Now Playing (spec 5), the waveform worked out in the
background and cached per file, the readout, and the mini player and every song tap
wired to the queue. Ported with their tests: `PlayOrder`, `ReopenPoint`, `HearingTimeline`,
`OutputReadout`, `TrackEnd`, `WaveformLevels` (`Waveform`'s decoding half is in the app).
New, with tests: `QualityLines` (spec 5's two lines), `ResumePoint` (the saved queue),
`PlayOrder.restore`. The sample script gained `Gapless Live` (a sweep cut into three
songs at sample counts mid-frame).

**Verified:**
- `:core:test` 250 (72 new), `:app:testDebugUnitTest` 17, `verifyRoborazziDebug`: Now Playing
  as the mockup and with Repeat One / Shuffle / paused / no waveform yet, both themes, both
  sizes (8 new pictures), compared side by side with `design/screens/`; Statistics changed
  (the preview switch gone); every other picture unchanged.
- Emulator, release build installed over v0.0.3 (same key):
  - Every sample format plays, opened as asked: FLAC 16/44 (16-bit), FLAC 24/96 (24-bit),
    ALAC 24/48 (24-bit), WAV (16-bit), AIFF (24-bit), WavPack (16-bit); MP3, AAC, Opus and
    Vorbis as float; DSF and DFF as 88.2 kHz float PCM.
  - `Gapless Live`: two gapless joins, **0 underruns**, three times (once with the app out of
    sight); MP3 → MP3 joins too; WAV 16 → AIFF 24 reopens the output, as it should.
  - The end of the queue goes back to its first song, paused.
  - The media session: the shade's player with the embedded cover, title, artist and
    seekbar; `MEDIA_PAUSE`, `HEADSETHOOK`, `MEDIA_NEXT` and `MEDIA_PREVIOUS` work.
  - Seeking by tapping the waveform (75% → 9 s); swipe down closes Now Playing; the
    theme switches on Now Playing.
  - Resume: killed paused at 0:10 of Live Part 2, reopened with it in the mini player and
    Now Playing at 0:10; a play key from the home screen carried on from 10.26 s.
- Not checked on the emulator (on the S24 list): the lock screen (the AVD has none set),
  Bluetooth and wired headsets, unplug pausing, audio focus against another app, and the
  sound itself.

**Differences from the mockups (design/screens):**
- **The readout** reads "Shared · 48 kHz (resampled)", without the mockup's "/ 32-bit"
  (Android doesn't say the mixer's depth; see Decisions).
- **Art placeholder:** the desktop's music note, as everywhere else (session 2).
- **Font:** Selawik sits the album line 1–2 dp off the mockup's Segoe UI (known).
- BP shows its off state and does nothing yet (session 4), as the prompt asks.

**Differences from the spec, and what it didn't say** (all in Decisions above): WMA doesn't
play on Android; what the end of the queue does; "3 / 10" while shuffled; the swipe-down
distance; songs that won't open are passed over.

### Session 2 follow-up — the first scan shows the library as it is found (v0.0.3, 2026-10-24)

The M21's first scan of v0.0.2 (27,115 files from the SD card, about 8 minutes) showed
an empty Artists panel saying "Music appears here as it's found" until it ended.

**Built:** the scanner hands each track to the app as its tags are read
(`LibraryScanner.scan(onRead = ...)`); on a first scan (no cache) the repository rebuilds
the library from what has been read every 1.5 s, or 4 × the last rebuild's time when that
is longer, and the Artists list fills in. The partial rebuilds stop before the result is
shown. The ARTISTS header shows "Reading tags: N of M" whenever a scan reads tags (also a
rescan that finds new files); nothing while it only checks for changes.

**Verified:** `:core:test` 178 (two new: tracks handed over as read, none for files reused
from the cache), `:app:testDebugUnitTest` 17, ThemeLab: two new pictures per size and theme
(`artists-first-scan`, `artists-first-scan-starting`), the others unchanged. Emulator,
cache forgotten: "Scanning the library" at 2.4 s, "Reading tags: 88 of 826" at 3.0 s, the
first artists at 3.6 s with covers filling in, done in 4.8 s with the same 209 · 413 · 826.
A normal start shows the cached library at once with no count.

**Decisions:** the partial library is only shown, never saved: a first scan cut short
starts again next time. The count sits in the panel header, right-aligned, 13 sp
`text.muted` (`artists.countSize`), as no mockup shows a scan.

### Session 2 — Library screens, navigation and search (2026-10-24)

**Built:** the shared parts (screen frame with the title glow, panel, panel header, rows
in their three looks, chips, round buttons, the search field, album art with the
desktop's placeholder), navigation with predictive back, Artists with the logo menu,
Albums, Album, Search, Settings, Statistics (holding the debug numbers), the mini player
(hidden until something plays, with a debug preview), cover thumbnails cached on disk and
in memory with a background fill, and the launcher icon. Session 1's temporary screen is
gone. Ported with tests: the desktop's `ArtDiskCache`, `CutOffImage.WholeFirst` and
`Display` (times, counts, rates, sizes). New, with tests: `SearchResults` (Search's three
groups) and the back stack (`Navigator`).

**Verified:**
- `:core:test` 176 tests, `:app:testDebugUnitTest` 17, `verifyRoborazziDebug`: every
  screen in both themes at both sizes, plus nothing-played, SD-card-out and the logo menu
  (56 pictures in `app/screenshots/`, the token sheet's 16 included), compared side by side with `design/screens/`.
- Emulator, release build installed over v0.0.1 (the update path works): 209 artists ·
  413 albums · 826 tracks with folder covers. Drill-down and back; the back gesture held
  halfway (the screen underneath shows, the top one shrinks) and committed (scroll
  position and selection kept); Search with the keyboard (the panel shrinks above it; back
  closes the keyboard first); the logo menu; the theme from the menu and from Settings;
  the mini player preview (now-playing row, triangle, Play/Pause); the SD card unmounted
  (the card's songs greyed but kept, the folder "Not available") and mounted again ("Up to
  date", nothing removed). Cold start 1.3 s, warm 1.1 s, cache 78 ms, a rescan reads 0.
- Not judged on the emulator: scrolling smoothness (its software renderer takes ~30 ms a
  frame for everything; the UI thread was slow on 31 of 955 frames). It's on both device lists.

**Differences from the mockups (design/screens):**
- **Font:** Selawik is a little lighter than the mockups' Segoe UI, so text looks thinner
  and lines sit 1–2 dp differently. Known since session 1.
- **Art placeholders:** the desktop's music note on the panel colour (spec 2), not the
  mockups' stripes, which stand for album art.
- **Search finds more than the mockup shows:** the desktop's rule matches every song whose
  title, artist, album artist or album holds every term, so "polygon" also lists all of The
  Polygon Set's songs and its other albums. The mockup's three songs were sample data.
- **Album #** is the tag's track number (the desktop's table), so a second disc starts at
  1 again; the mockup numbers its ten songs straight through.
- **Bit-Perfect** reads "Shared" under the label: the readout (spec 6.3) needs the output
  stage (session 3). The BP chip and Last.fm's Connect do nothing yet.
- **Last.fm's line** stays on one line where it fits; the mockup wraps it.
- **Play/Pause** uses `shadow.playButton` (spec 2); the mini player mockup used
  `shadow.controlButton`.
- The search field's focus ring is `state.searchFocusRing` (spec 4.4); the mockup used
  `accent.playback` (the same colour in Dark, different in Light).

### Session 1 — Project, theme and library (2026-10-24)

**Built:** the Gradle project (`:core` plain Kotlin, `:app` Android), the release keystore,
the theme from the three token files, ThemeLab (Roborazzi) with a token sheet, settings
(theme, music folders), all-files access, the TagLib tag reader through the NDK, the
scan and its cache, the ported Core rules with their tests, a temporary screen with the
library counts, folders, theme and debug numbers, `tools/make-sample-music.sh`,
HANDOFF.md and README.md.

**Verified:**
- `:core:test`, 152 tests: the desktop's SortRules, ReleaseDate (sort and parse),
  LibrarySearch (accent-insensitive), LibraryCache, FileStamp, ScanSummary, path
  containment and rebasing, unavailable folders, drive lost mid-scan, removed counts,
  date added, MusicFolderList, DriveChange, MovedCopy, ThemeTokens (with the parity test:
  both colour files have the same keys and the same non-colour values), ImageInfo,
  CutOffImage, BestCover, DsfHeaderReader; plus tests of the mobile tokens and folder art.
- `:app:testDebugUnitTest`: Dark by default, a switch saved and applied at once; the folder
  list saved once per folder with its tick. `verifyRoborazziDebug`: the token sheet's four
  pages, both themes, S24 (384 × 832) and M21 (375 × 667): 16 pictures in `app/screenshots/`.
- Emulator (Android 17): every sample format read with the right tags, rates, depths and
  lengths: FLAC 16/44 and 24/96, MP3 ID3v2.3 and v2.4, AAC, ALAC 24/48 (told from AAC),
  Opus, Vorbis, WAV, AIFF 24-bit, WavPack, DSF and DFF (DSD64, 1 bit), an untagged MP3 by
  its file name; a hidden folder skipped; `Folder.JPG` found ignoring case. Release build,
  26 tracks on internal storage and the emulator's SD card: cold start 740–960 ms, warm
  680 ms, cache read and built in 5–30 ms, a rescan reads 0 and reuses 26. **SD card
  unmounted while running:** 26 tracks kept, 7 marked unavailable, 0 removed; mounted
  again: all back, 0 removed. Theme switch at once and after a restart.
- Not checked on the emulator: the system folder picker ("Add Folder…"); on the S24 list.

**Differences from the spec, and what it didn't say:**
- The screen is temporary; the spec §4 screens come in session 2.
- **No launcher icon yet**: Android's default shows. The spec doesn't say; ask in session 2.
- `mobile-tokens.json` gained `borderWidth: 1` (the spec's "1 dp" borders), so no screen
  hard-codes it.

**Decisions (session 1):**
- **Package** `com.mpaulrenlund.audiofool`; `minSdk 33`, target and compile SDK 37; portrait,
  edge to edge, predictive back on. Test builds are versioned `0.0.x`.
- **Font: Selawik, bundled** (the user's call). Regular, Semibold and Bold only: the spec uses
  400/600/700, and these three weights have tabular digits by default (checked in the font's
  metrics), so numbers line up without a font feature (`tnum` is asked for anyway). Glyphs
  Selawik lacks fall back to the system font.
- **Tag reader: TagLib 2.3.2 through the NDK** (the user's call), the C++ library TagLibSharp
  is ported from; it reads DSF and DFF. Vendored unchanged in `third_party/taglib`, linked
  statically under the MPL. Strings cross JNI as UTF-8 bytes. Where TagLib's APE tags give no
  year for a full date, the year comes from the date, as TagLibSharp gives it.
- **Screenshot tests: Roborazzi** (on Robolectric), not Paparazzi: Robolectric also runs the
  app's Android unit tests, its SDK is pinned apart from the app's (Paparazzi's layoutlib
  trails new compile SDKs), and its native graphics draw the real font, shadows and
  gradients. The pictures are kept in `app/screenshots/` and verified on every run.
- **Theme access by JSON path**, as the desktop's resources are: `color(...)`, `shadow(...)`
  and `desktopNumber(...)` read the Light or Dark desktop file; `dp(...)` and `sp(...)` read
  mobile-tokens.json; `mobileColor(...)` takes its `.light` or `.dark` value. The build copies
  the files from `design/` into the assets, so `design/` stays the only copy.
- **CSS shadows drawn as the tokens say**: outer layers behind, inset layers clipped inside,
  CSS blur converted to Android's. The Dark title glow is drawn from its tokens.
- **System bar icons follow the app's theme**, not the phone's.
- **The cache is binary**, not JSON: the desktop's `library.json` content with strings stored
  once (an album's folder, an artist's name), so a phone loads 26,000 tracks at once. Saved
  with a backup and read from it when damaged or missing (the desktop's SafeJsonFile).
- **Name order** uses a root-locale collator that ignores case but not accents (the desktop's
  InvariantCultureIgnoreCase); **search** folds case and accents (IgnoreNonSpace).
- **Paths compare ignoring case**, as on the desktop; Android's shared storage and SD cards
  are case-insensitive.
- **The folder list starts as each volume's `Music` folder** until the user changes it.
- **A left-out folder's tracks stay in the cache, hidden**; a folder taken off the list loses
  them (MusicFolderList).
- **Not ported:** the desktop's search for a folder on another drive letter (Android mounts a
  card under its own id, which doesn't change); tag writing and its leftovers.
- The scan reads 4 files at once (the desktop uses every core; an SD card prefers fewer).
