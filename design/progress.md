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

2026-10-30, after 0.5.1: **the docs slimmed** (the user's call). The decisions for 0.2.6 and
older and the session notes moved unchanged to [`progress-archive.md`](progress-archive.md), with
HANDOFF's history (the 0.2.x changes, "where things stood after session 2–5"). HANDOFF keeps the
current state, the build loop and the traps (526 → 421 lines); this file keeps the decisions
from 0.3.0 on, session 1's, and both test lists whole (1,859 → about 950 lines).

2026-10-30: **the test lists sorted** (the user's call). Of the 91 unticked items, the ones that
recorded a failure later fixed and confirmed (0.3.0 to 0.4.7's driver results), the ones a later
build answered (the S24's mixers and 44.1 kHz, the DAC's name in the readout, the M21's FiiO
bypass, first scans, Rescan), and the ones whose feature changed (the mini player preview, the
pre-driver "Rate Matched" readouts, plugging in moving the music to the DAC: it pauses since
0.4.4) are dropped. What is left is grouped by subject: 17 open on the S24 and 11 on the M21,
plus a "probably fine from daily use" group per phone (8 and 3) for the user to tick with one
word. The old per-version lists, with every result, are in `progress-archive.md`.

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

Decisions for 0.2.6 and older (0.2.x, 0.1.x and sessions 2–5) are in
[`progress-archive.md`](progress-archive.md), newest first. Session 1's still stand:

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

Sorted on 2026-10-30, after 0.5.1. Only what is still open is here; the per-version lists with
every result up to 0.5.1 are in [`progress-archive.md`](progress-archive.md). A new build adds
its own section at the top, as before. (From …) says which build first asked.

### Bit-Perfect through the driver

- [ ] **A DSD file with BP on** (from 0.4.0). A DSF or DFF: does the DAC show DSD (or DoP), and
  does it sound right, not hiss? A DSD64 file goes to the DAC as DoP at 176.4 kHz. A
  DSD128 file too, if you have one (352.8 kHz). Pausing it may click as the DAC drops out of
  DSD (a known gap).
- [ ] **A notification or a navigation prompt while the driver plays** (from 0.3.0 and 0.0.5).
  Is it heard, and where: through the DAC, the speaker, or not at all? Does the music pause,
  get quieter, or play on?
- [ ] **The USB-C 3.5 mm adapter's name** in the readout (from 0.2.5). The Shanling's own name
  already shows ("Shanling UA2 Plus · 48 kHz" on 0.4.0).
- [ ] **BP greyed on the speaker and Bluetooth** (from 0.0.5). Unplug the DAC: BP and the
  Settings chip go dim and can't be tapped. With Bluetooth connected: the same, and the readout
  names Bluetooth. Connecting Bluetooth while playing with BP on: it plays on there.

### Everyday playback

- [ ] **The lock screen and the notification** (from 0.0.4). While playing, lock the phone: the
  cover, title, artist, play/pause, previous, next and a seekbar, all working. The same in the
  shade. Paused, the notification can be swiped away; tapping it opens Now Playing.
- [ ] **Bluetooth headset buttons** (from 0.0.4). Play/pause, next and previous from the headset
  (and a watch, if you use one), also after a restart with nothing played yet: it resumes where
  you left off. Turning the headset off pauses.
- [ ] **Interruptions** (from 0.0.4 and 0.1.0). A call or the voice assistant pauses it and it
  carries on after; a navigation prompt lowers it briefly; another music app starting stops it.
- [ ] **Resume** (from 0.0.4). Seek into a song, pause, swipe the app away from recents, open it
  again: the mini player shows the song, paused, at the same place; Play carries on with the
  rest of the album.
- [ ] **Battery and heat** over an hour of playing with the screen off (from 0.0.4), with BP on
  and with it off.
- [ ] **Font size** one or two steps up in Android's Display settings (from 0.1.0): Artists, an
  album, Now Playing and Settings still fit.

### Last.fm (from 0.1.0)

- [ ] **Connect.** Settings > Last.fm > Connect with the desktop's API key and shared secret,
  approve on last.fm, come back: "Last.fm: connected as <you>" with a green dot.
- [ ] **Scrobbles reach your profile.** "Scrobbling now" when a song starts; the scrobble at
  its start time once past half (or four minutes). Seeking ahead doesn't count.
- [ ] **The offline queue.** Airplane mode, two songs past the threshold, airplane mode off,
  play anything: both scrobbles appear with their own times.
- [ ] **Disconnect and connect again**: the key and secret are filled in already.

### The library

- [ ] **Search ignores accents** (from 0.0.2): "bjork" finds Björk.
- [ ] **Folders** (from 0.0.1). Add Folder… picks another folder, shown as "Internal storage ·
  name". Unticking hides its artists at once; ticking brings them back without a rescan.
- [ ] **Statistics against the desktop's** (from 0.1.0): the figures and the Formats line (the
  desktop counts every library, the phone its own folders).

### Probably fine from daily use: say if any isn't

Never reported one by one, but you'd have noticed by now if they were wrong. A "fine" ticks them
all; name any that isn't.

- [ ] The launcher icon (the PlayStation logo on white).
- [ ] Artists with their newest album's cover; covers filling in as you scroll; smooth scrolling.
- [ ] Artist → Albums → Album and the back gesture held halfway (the screen underneath shows);
  the artist still highlighted at the same place.
- [ ] Theme switching on every screen, the status bar readable in both.
- [ ] Covers without a frame; the one-line quality ("FLAC · 1,901 kbps · 24-Bit · 48 kHz").
- [ ] Now Playing's spacing (no big gap over the quality line) and a smooth, gliding seekbar.
- [ ] Every format you have plays (FLAC, MP3, AAC, ALAC, Opus).
- [ ] Gapless through the speaker and Bluetooth, not only the DAC.

## To test on the M21

Sorted on 2026-10-30, as the S24's list above.

### Playback

- [ ] **Nothing changed since 0.4.0** (from 0.4.0). The output stage writes through a new layer
  for the S24's driver; the M21 should play exactly as 0.2.6 did. BP on, an album through the
  headphone out: gapless, the readout as before ("3.5mm · 96 kHz · 24-Bit · Exclusive"), seeks
  without clicks.
- [ ] **DSD** (from 0.0.5). A DSF or DFF plays, as PCM at 88.2 kHz (no DoP to the M21's DAC).
- [ ] **Bluetooth** (from 0.0.5). BP greyed; the readout names Bluetooth.
- [ ] **A USB DAC on the M21**, if you have one to try (from 0.0.5): BP works the M21's way;
  report the readout.

### The SD card and the full library

- [ ] **Pull the SD card while a song on it plays** (from 0.1.0). It stops and shows paused; the
  card's artists stay, greyed. Card back, Play: it carries on from where it stopped. The same
  with the card ejected from Android's Settings, and with the music paused.
- [ ] **The card out at start** (from 0.0.1, 0.0.2). Settings shows "SD card · Music" greyed,
  "Not available"; the card's songs listed, greyed, and can't be tapped; the LIBRARY line says
  "Unavailable: SD card · Music (N tracks kept)". Card back: "Up to date", nothing removed.
- [ ] **Cold start with the full library** (from 0.0.1), three times: Statistics > Debug > Cold
  start and Cache. The library should be on screen at once.
- [ ] **Rescan with the full library** (from 0.0.1): Logo > Rescan Library; Last scan should read
  about 0 and reuse every track.
- [ ] **Statistics** with the full library (from 0.1.0): the figures against the desktop's (499
  artists · 2,376 albums · 26,795 tracks when last seen), and how long the screen takes to open.
- [ ] **Scrolling, the letter strip and Search** with ~26,000 tracks (from 0.0.2, 0.2.0): flings
  smooth; a slide down the strip keeps up and its letters fit the M21's shorter screen; Search
  keeps up with typing.
- [ ] **Last.fm** (from 0.1.0): connected on the M21 too (the same key, a second session for the
  same user), scrobbles arriving.

### Probably fine from daily use: say if any isn't

- [ ] Now Playing's bigger cover, with the readout still fitting under the controls (also at a
  larger font size).
- [ ] Now Playing's controls: Repeat and Shuffle side by side over BP.
- [ ] The folders list: the internal and the SD card's `Music` folders, the card's shown as "SD
  card · Music".

## Session notes

The notes for 0.2.3 and older are in [`progress-archive.md`](progress-archive.md). Later
versions record their notes in the decisions above.
