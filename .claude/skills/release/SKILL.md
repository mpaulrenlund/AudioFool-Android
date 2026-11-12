---
name: release
description: Build and publish a test build of AudioFool for Android as a GitHub pre-release with the signed APK - version bump, tests, signed release build, signer and key checks, emulator check, notes with a "to try" list for each phone, then the upload. Use whenever the user says "build 0.x.y", "prepare 0.x.y", "show the notes", "publish it", "release it", "new build" or "let's get this on the phone" in this repo, even when they don't name a version number.
---

# Release AudioFool (Android)

The user tries every build on a Galaxy S24 Ultra and a FiiO M21, installing it from the
release page. It happens in two parts with a stop between them, because the upload
publishes a tag and an APK anyone may download:

- **Build** (the user says "build 0.x.y" or "prepare 0.x.y"): steps 1 to 6, ending with
  the notes shown in chat.
- **Publish** (the user says "publish it"): steps 7 and 8. Never publish on your own.

Every Gradle command needs the environment in `HANDOFF.md` under *This PC's quirks*
(Android Studio's JDK, the Zscaler trust store, the short loopback folder) and the sandbox
off. `export MSYS_NO_PATHCONV=1` before any `adb` command with a device path.

## 1. Agree the version

Middle number for new features, last number for fixes. If the user didn't name one,
propose it with one alternative and wait. Then bump `versionName` and `versionCode` in
`app/build.gradle.kts`: `versionCode` goes up by one on **every** build, published or
not (read the current value from the file).

## 2. Test, build, check and copy: one script

```bash
tools/build.sh
```

Run it with the sandbox off (Gradle's daemon needs its sockets). It sets the Java
environment, runs the Core tests, the app's unit tests and the screenshot tests, builds
the signed release APK, and prints one line per step. Stop on any failure; the full
Gradle output is in `build/ship.log`. After a deliberate visual change, re-record the
screenshots first (`./gradlew :app:recordRoborazziDebug`, with the environment in
`docs/build-and-emulator.md`) and compare them with `design/screens/` in both themes at
both sizes.

## 3. What the script checked, and why it matters

- **signer**: the APK is signed with the release key. A build signed with any other key
  won't install as an update, and the user would have to uninstall, which wipes the
  library cache and settings.
- **last.fm**: the APK carries **no** Last.fm key or secret (since 2026-11-12: the user types
  them on each phone, spec 7, so the repo and its releases can be public). The script looks
  for the values kept in `~/.gradle/gradle.properties` in every file inside the APK, without
  printing them. Never print them yourself, and never upload an APK this check hasn't passed.

## 4. Where the APK went

The script copies it to `dist/AudioFool-<v>.apk` (`dist/` is ignored by git) and prints
its SHA-256; keep that for step 7. If it says a different APK with this version is
already in `dist/`, the version wasn't bumped: go back to step 1 rather than overwrite a
build that may already be published.

## 5. Look at it on the emulator

Install the build on the emulator (it should update over the last one and keep the
library) and check what this build changed. Run this in a subagent and bring back pass or
fail and the picture paths.

## 6. Write the notes and show them

Written for the user: what changed in plain words, then a numbered **To try on the S24**
and **To try on the M21** (what only the real phones can show). Show them in chat and
save them as `dist/notes-<v>.md`. Commit the bump as `<v> (versionCode N)` and push, and
note the full commit SHA. Then stop and wait for "publish it".

## 7. Publish

Upload only the APK in `dist/` that `tools/build.sh` passed in step 2 (its **last.fm** line
said no key). Over USB with `adb install -r` is the fallback, only if the user asks.

```bash
gh release create v<v> dist/AudioFool-<v>.apk -R mpaulrenlund/AudioFool-Android --target <full SHA of the bump commit> --prerelease --title "AudioFool <v>" --notes-file dist/notes-<v>.md
```

`--target` takes a branch or a full SHA, not a short one. Then download the APK back
(`gh release download`) and check its SHA-256 matches the one in `dist/`.

## 8. Record it and report

- `design/progress.md`: the session, the decisions, and the new items under *To test on
  the S24* / *To test on the M21*. `HANDOFF.md`: the latest build and what it changed, in
  a few lines. Commit and push.
- Report in a few lines: version and versionCode, the release link, that the APK
  carries no Last.fm key and the hashes matched, and the list of what to try on each phone.
