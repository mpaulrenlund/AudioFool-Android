What the S24 and the M21 turned out to do, and how to reach them (and the emulator) over adb. Moved out of HANDOFF.md on 2026-11-11, unchanged except for cross-references.

## What the phones turned out to do

- **FiiO M21** (Android 13; `Build.MANUFACTURER` "QUALCOMM", `BRAND` "qti", `MODEL` "FiiO M21"):
  - Android lists no headphone device: the 3.5 mm and 4.4 mm jacks are the built-in
    "speaker" (48 kHz, 16-bit, per Android). `SpeakerlessPlayers` spots a FiiO by "FiiO" in
    any of those names (or the speaker's name) and takes the speaker route as Headphones.
  - Its "becoming noisy" at an unplug comes from FiiO's status bar (`com.android.systemui`,
    uid 10089, an app), so the receiver must be `RECEIVER_EXPORTED` or it never hears it. No
    `ACTION_HEADSET_PLUG` is ever sent, and the kernel's jack files can't be read.
  - FiiO's own broadcasts reach an exported receiver: `fiio.intent.action.SAMPLE_RATE_CHANGED`
    (`rate`: the DAC's rate, 0 when stopped; `format`) and `fiio.intent.action.HEADSET_TYPE_CHANGED`
    (`type` **1 for the 3.5 mm jack, 8 for the 4.4 mm, 0 unplugged**; read off the M21 over adb
    in 0.2.5). Neither is sticky, and nothing an app can read says which jack is in, so the
    last type is saved (`settings` → `fiioJack`). The readout's rate goes by `rate`
    (`OutputReadout.hardwareRate`).
  - **It has no speaker:** unplugged, nothing is heard (the user), hence "No Output".
  - The DAC runs at the song's rate even with BP off ("Headphones · 96 kHz"); BP adds
    the full-volume, whole-number-sample path. 24-bit songs go as 32-bit with BP on, as
    float with it off: **24-bit packed `AudioTrack`s come out as static there.**
  - It holds the full library: 27,115 files from the SD card. Its first full scan took
    8 min 49 s (about 51 files a second); later starts read the cache.
- **S24 Ultra:** **no SD card slot**, and a smaller library (192 artists · 821 albums · 8,127
  tracks on 2026-10-24; first scan under 30 s). With the user's USB dongle DAC, Android's mixer
  list is default entries only, at 48, 88.2, 96, 176.4, 192, 352.8 and 384 kHz (16, 24, 32-bit),
  **no bit-perfect entry and no 44.1 kHz**: that is why the app has its own USB driver. Before
  the driver, BP on was "Rate Matched" at 48 kHz and up; on Android's path a 44.1 kHz song is
  resampled to 48 kHz.

## Reaching the phones

- **Both phones reach adb over USB when the user plugs them in** (one at a time, usually);
  0.8.1 was installed this way (the user prefers GitHub releases, since 0.9.0). Check
  `adb devices -l` first: when the emulator is up too, name the device (`-s` or `-t`).
- **The M21** (plugged in 2026-10-25, 2026-10-26 and 2026-11-05). If adb doesn't list it and
  Windows shows no FiiO device (VID 2972), ask for: USB debugging on, the USB notification set
  to "File transfer", "Allow" on the prompt, a data cable (on 2026-11-05 it showed up once
  "Allow" was tapped). Its screen:
  the logo (menu) is at (56, 104) in its 750 × 1334 px. adb shows it as
  `model:FiiO_M21` with the serial "?", so pick it with `adb -t <transport_id>` from
  `adb devices -l` (the emulator is the other device). adb isn't on the PATH: it is
  `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`. `adb install -r` of a release build
  updates in place (same key, data kept). Useful: `dumpsys activity broadcasts history` (each
  broadcast, its sender and who received it; extras show as STRIPPED),
  `dumpsys activity broadcasts` (registered receivers), and `uiautomator dump` to read the
  app's screen without the user typing it out. A plain `adb logcat` capture dropped when
  USB reconnected and caught little.
- **The S24 can't use a cable while testing the DAC** (one USB-C port). Wireless debugging
  was tried and the user couldn't get the pairing step to work, so S24 results come as typed
  readings from Statistics > Debug (Playback). Never type a pairing code for the user. **Over
  USB the S24 works** (2026-11-05, the user turned debugging on and allowed this PC): adb serial
  `R5CWC28ETAW`, `model:SM_S928U`; 0.8.1 went on with `adb -s R5CWC28ETAW install -r`. Pick it
  with `-s` when the emulator is up too. 0.8.1 reached it this way; since 0.9.0 builds come
  from the private repo's releases, and USB is for when the user asks.
- **The emulator** holds the sample music: `/sdcard/Music` (with `Long/`: 200 artists × 2
  albums of silent MP3s; `Gapless Live/`: three songs of exactly 12.000 s; `Waveform Test/`: a
  60 s pink-noise FLAC with swells and a near-silent dip at 24–27 s, for the seekbar's shape)
  and, on the SD card `/storage/0000-0000/Music`, part of the sample set and `SD Card Band/`:
  two 3-minute FLACs for pulling the card mid-song. **Those two are damaged again** (ffmpeg
  fails at the start of both, found 2026-10-26): rebuild them before relying on them. When last
  noted (0.2.6), the app there had Bit-Perfect saved on, Repeat All and Shuffle on, and Last.fm
  not connected. The AVD sometimes exits on its own (exit code 4): start it again. The app's
  uid there is 10236 (for `dumpsys audio`).
- `dist/` (ignored by git) holds every build's APK, its notes (`notes-<v>.md`, since 0.8.1) and
  the M21's captured reports (`m21-*.txt`). An APK there carries AudioFool's Last.fm key: it
  goes only to this PC, the phones and the private repo's releases, never anywhere public.
- **Last.fm signs in with the user's own username and password** (after 0.8.0), which only
  the user types, on the phones: never sign in for them, on the emulator either. The emulator
  shows the form, the not-connected Listening History screen, and nothing past that. Without
  AudioFool's key in `gradle.properties` (see *Where everything lives* in `HANDOFF.md`), a throwaway release for the emulator can take
  a dummy one on the command line:
  `-Paudiofool.lastfm.apiKey=<32 zeros> -Paudiofool.lastfm.secret=<32 zeros>`; never copy
  that build to `dist/` or give it to the user.
