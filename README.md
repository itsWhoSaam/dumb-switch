# Dumb Switch

A launcher that is dumb by default. Dumb Switch replaces the home screen with a clock and a
five-app allowlist — **Phone, Messages, Maps, Camera, Clock** — so picking up the phone hands you
the essentials instead of the feed. The friction design is the product: nothing is hidden at the
OS level, and the Pixel Launcher stays installed as the escape hatch.

This scaffold ships the **dumb home** (full-bleed clock + date + exactly the five allowlisted
entries) and the **home-role gate** (first-run `ROLE_HOME` request). The timed smart-mode escape —
a long-press-and-confirm 30-minute window into the full app list that decays back to dumb — is the
next change.

## Sideload

1. **Get the APK** — every green CI run publishes `app-debug.apk` as a workflow artifact
   (Actions → CI → latest run → Artifacts). To build locally instead: `./gradlew assembleDebug`.
2. **On the phone** — enable Settings → About phone → tap *Build number* 7×, then enable
   *USB debugging* under Developer options.
3. **Install** — `adb install -r app-debug.apk`, or copy the APK onto the phone and open it
   (allow *Install unknown apps* for your browser or file manager).
4. **Set as home** — the first launch asks to make Dumb Switch the default home; the home chooser
   (press Home after install) also lists it. Accept once — it never re-prompts while the role is held.

## Two-minute verification

- Home screen shows the clock, the date, and exactly five entries.
- Each entry opens the real app: Phone, Messages, Maps, Camera, Clock.
- Settings → Apps → Default apps → *Home app* lists Dumb Switch; switching back to the Pixel
  Launcher there is the by-design bypass (v0 is launcher-only enforcement).

## Scope

v0 is launcher-only: no in-app content blocking, no notification (DND) changes, no uninstall
protection, no allowlist editor. The stronger enforcement rungs — overlay blocker (v1) and
device-owner lock task (v2) — are separate decisions.

## Toolchain

Kotlin, single `:app` module, Gradle Kotlin DSL, `minSdk 33` / `targetSdk 35` / `compileSdk 35`,
JDK 17. CI (GitHub Actions, `ubuntu-latest`) runs `./gradlew assembleDebug test lint` on every PR
and uploads the debug APK.
