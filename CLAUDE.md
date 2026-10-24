# AudioFool for Android

A personal Android music player, the mobile version of the desktop AudioFool. **Kotlin / Jetpack Compose**, with **BASS for Android** for decoding and AudioFool's own output stage, for a Galaxy S24 Ultra (Android 17) and a FiiO M21 (Android 13, so `minSdk 33`).

## Read these first

- **`HANDOFF.md`**: current state, the build–install–verify loop, and traps worth not rediscovering. Start here. (Session 1 creates it.)
- **`design/android-spec.md`**: the source of truth for every screen, size and behaviour. Read it before any UI work.
- **`design/progress.md`**: every decision and what each session verified. Read the Decisions section before UI work, and add to it when you change something.
- `design/session-prompts.md`: the build sessions, in order.

## Where things are

| What | Where |
|---|---|
| This repo | `C:\AudioFool-Android`, GitHub `mpaulrenlund/AudioFool-Android` (public), branch `main`. Pushed from this PC as `marcusrenlund`, a collaborator with write access |
| The desktop app | `C:\MusicPlayer` (C# / WPF). Read-only from here. Its `src/AudioFool.Core` holds the logic to port; its `tests/` hold the tests to port with it |
| Colour tokens | `design/theme-tokens.json` (PS1 Light) and `design/theme-tokens.dark.json` (PS2 Dark), the desktop's files copied unchanged |
| Mobile sizes | `design/mobile-tokens.json` |
| Screens | `design/screens/` (24 PNGs: both devices, both themes); `design/mockup/` holds the design-tool files they came from. Read those for icon paths; don't try to build from them |

## Rules that must not slip

- **Use the central theme.** Colours, shadows and radii come from the two desktop token files; sizes from `mobile-tokens.json`. No hex values or magic numbers in screens. A new value goes into a token file first.
- **Keep the desktop colour files identical to the desktop's.** If a colour is needed that the desktop doesn't have, add it to `mobile-tokens.json` → `color`, with a Light and a Dark value. The parity test (same keys in both files) must pass.
- **Every colour follows a theme switch.** Read colours from the theme at draw time; never hold on to one.
- **Check both themes and both sizes.** Every visual change is rendered in Light and Dark, at 384 × 832 dp (S24) and 375 × 667 dp (M21), with the screenshot tests, and compared with `design/screens/`.
- **Port, don't reinvent.** Where the desktop has a rule (artist order, release dates, search, play order, DoP, the output readout, scrobbling), port it from `AudioFool.Core` together with its tests, and say where it came from in a comment.
- **A missing folder is not a deleted library.** The SD card can be removed at any time. Unreachable folders keep their tracks, shown as unavailable.
- **No on-screen back buttons.** System back and predictive back handle navigation.
- **Keep the PlayStation logo asset** (`PlayStation-Logo-1994.png`). Don't replace or redraw it.
- **The playback colour means playback** (`accent.playback`): Play/Pause, the seekbar, the now-playing row. Artist names use `accent.blue`. No colours outside the roles in the spec.
- **No visible scrollbars.**

## Devices

Neither device is connected to this PC. Don't plan on `adb` reaching a phone.

- **Builds go to the user as APKs.** Attach each test build to a GitHub pre-release. The user installs it on the S24 or the M21 and reports back. End each session with a short list of what to try on each device.
- **One signing key from the start.** Every APK the user gets, test builds included, is signed with the same release keystore, kept outside the repo. A build signed with a different key won't install as an update; the user would have to uninstall, which wipes the library cache and settings.
- **The emulator covers what it can**: scanning sample music, navigation, playback, gapless, the media session, lock screen and notification, resume. Verify there before handing over a build.
- **Only the real devices** can check the full ~26,000-track library and its start times, SD card removal, Bluetooth and headset buttons, unplug pausing, and anything through a USB DAC (Bit-Perfect, DoP, the readout). Those go on the device lists in `design/progress.md`: *To test on the S24* and *To test on the M21*.

## How we work

- Ask before big choices: a new library or dependency, a change to the spec, anything that touches stored data, and a release. Otherwise build.
- Stay within the change asked for. Don't restyle other parts.
- Verify by running it (unit tests, screenshot tests, the S24), not by reasoning about the code.
- When done: list what changed and anything that differs from the spec, update the spec if the user changed what it says, add the decisions to `design/progress.md`, and update `HANDOFF.md`.
- Work on `main`. Commit with clear messages and push.
- Releases: confirm the version number and the notes with the user before `gh release create`. Middle number for new features, last for fixes. The signing keystore lives outside the repo.
