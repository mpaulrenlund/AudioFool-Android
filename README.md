# AudioFool for Android

A personal music player for Android: the mobile version of the desktop AudioFool, for a
Galaxy S24 Ultra (Android 17) and a FiiO M21 (Android 13). Kotlin and Jetpack Compose,
with the desktop's two themes, PS2 Dark and PS1 Light, and its library rules ported
from `AudioFool.Core`.

Status: session 2 of 5 (the library screens, navigation and search). Playback,
Bit-Perfect and Last.fm follow; see `design/session-prompts.md` and `design/progress.md`.

## How it works

- **Screens.** Artists → Albums → Album, plus Search, Settings and Statistics (which for
  now holds the debug numbers). The PlayStation logo is the menu button (Settings,
  Theme, Statistics). There are no on-screen back buttons: system back and predictive
  back go up one level, and each screen keeps its scroll position and selection. A
  mini player sits under every library screen once something has played.
- **Storage.** Sideloaded, so the app asks for all-files access and keeps plain folder
  paths (`/storage/emulated/0/Music`, `/storage/1A2B-3C4D/Music`). Until the user picks
  folders, it uses each volume's `Music` folder.
- **The library.** A scan walks the included folders (skipping hidden ones), reads tags
  with TagLib, and builds Artist → Album → Song with the desktop's rules: artists A-Z
  ignoring a leading "The", albums oldest first by full release date, tracks by disc
  then number, album artist over track artist, the most common spelling of a name.
- **The cache.** The scan's result is saved (`library.bin`, a compact binary form of the
  desktop's `library.json`, kept with a backup of the save before). A start shows the
  library from the cache at once, then rescans in the background; a file whose size and
  date haven't changed keeps its cached tags.
- **A missing folder is not a deleted library.** A folder that can't be reached (the SD
  card out, or pulled mid-scan) keeps its tracks, shown as unavailable; they come back
  with the card. The app rescans when storage is mounted or removed.
- **Covers.** The best embedded picture (whole before cut off, front cover, most pixels,
  JPEG over PNG, most bytes: the desktop's rules), else `cover.jpg`, `folder.jpg` and the
  other names the desktop looks for, in its order, ignoring case. An album's cover is
  looked for in its first five tracks, then its folder. Small copies at the size the lists
  show are kept in the app's storage (the desktop's `ArtDiskCache`) and made in the
  background after each scan, so the lists fill from there rather than the SD card.
- **The theme.** The desktop's token files are the app's theme, read by JSON path
  (`color.panel.bg`, `shadow.controlButton`); sizes come from `design/mobile-tokens.json`.
  Dark when nothing is saved; a switch applies at once.

## Formats

Everything in the desktop's `AudioFormats`: MP3, FLAC, Ogg Vorbis, Opus, AAC/M4A, ALAC,
WMA, Musepack, AC-3, DTS, WAV, AIFF, APE, WavPack, TAK, DSD (DSF, DFF) and the tracker
formats. Tags come from TagLib 2.3.2, which reads all of them except TAK, AC-3, DTS, MTM,
UMX and MO3; those show with what their file name gives, as on the desktop.

## Building

Android Studio's SDK (platform 37, build tools 36), its JDK (`jbr`), NDK 30 and CMake 4.1
(Gradle downloads the last two). From the repo:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:verifyRoborazziDebug :app:assembleRelease
```

The release build is signed with the release keystore named in
`~/.gradle/gradle.properties` (`audiofool.signing`); it is not in the repo. HANDOFF.md has
this PC's setup and the whole build, test and release loop.

## Third-party code and licences

- **TagLib 2.3.2** (`third_party/taglib`, unchanged): dual-licensed LGPL 2.1 / MPL 1.1.
  AudioFool uses it under the MPL, linked statically into `libaudiofooltags.so`.
- **Selawik 1.01** (`app/src/main/res/font`): Microsoft's open-source stand-in for Segoe
  UI, SIL Open Font License 1.1 (`third_party/selawik/LICENSE.txt`).
- The PlayStation logo (`PlayStation-Logo-1994.png`) is used unchanged, and the launcher
  icon is the desktop's icon (the same logo) on white.
- BASS for Android (session 3) will be listed here with its licence terms.
