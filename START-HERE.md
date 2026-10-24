# AudioFool Android — handoff

Copy everything in this folder into `C:\AudioFool-Android` (an empty clone of `github.com/mpaulrenlund/AudioFool-Android`), keeping the layout:

```
C:\AudioFool-Android\
  CLAUDE.md                      rules for every Claude Code session
  PlayStation-Logo-1994.png      the logo asset, unchanged
  design\
    android-spec.md              screens, sizes and behaviour (source of truth)
    mobile-tokens.json           mobile sizes and the two mobile-only colours
    theme-tokens.json            PS1 Light, copied from the desktop
    theme-tokens.dark.json       PS2 Dark, copied from the desktop
    progress.md                  decisions so far; sessions add to it
    session-prompts.md           the five build sessions, in order
    screens\                     24 PNGs: S24 and M21, Dark and Light, six screens
    mockup\                      the design files (open AudioFool Android.dc.html in a browser)
```

Then install Android Studio (for the SDK, the build tools and the emulator), open Claude Code in `C:\AudioFool-Android`, and paste Session 1 from `design/session-prompts.md`. No phone needs connecting: builds come to you as signed APKs on GitHub pre-releases, and you install them yourself.
