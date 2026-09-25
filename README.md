# Dumb Switch

A launcher that is dumb by default. Dumb Switch replaces the home screen with a clock and a
five-app allowlist — **Phone, Messages, Maps, Camera, Clock** — so picking up the phone hands you
the essentials instead of the feed. The friction design is the product: nothing is hidden at the
OS level, and the Pixel Launcher stays installed as the escape hatch.

v0.1.0 ships the **dumb home** (full-bleed clock + date + exactly the five allowlisted entries),
the **home-role gate** (first-run `ROLE_HOME` request), and the **timed smart-mode escape** — a
long-press-and-confirm 30-minute window into the full app list that always decays back to dumb.

## Sideload

1. **Get the APK** — every green CI run publishes `app-debug.apk` as a workflow artifact
   (Actions → CI → latest run → Artifacts). To build locally instead: `./gradlew assembleDebug`.
2. **On the phone** — enable Settings → About phone → tap *Build number* 7×, then enable
   *USB debugging* under Developer options.
3. **Install** — `adb install -r app-debug.apk`, or copy the APK onto the phone and open it
   (allow *Install unknown apps* for your browser or file manager).
4. **Set as home** — the first launch asks to make Dumb Switch the default home; the home chooser
   (press Home after install) also lists it. Accept once — it never re-prompts while the role is held.

## Release installs

Release APKs come from GitHub Releases: pushing a `v*` tag runs the release workflow,
which builds a signed `app-release.apk` and publishes it with the matching CHANGELOG
section. Available from v0.2.0.

The release signature differs from the CI debug signature, so Android refuses to update a
debug install in place — the first release install needs a **one-time uninstall of the
debug build** first. The escape timestamp and the allowlist live in app data and reset
with that uninstall; both are trivially reconfigured (let the escape decay, or open the
editor in smart mode and save the list again).

The keystore is committed with its password documented here — a conscious trade-off for
a personal, sideload-only project: signature stability across machines and CI runs with
zero secret plumbing, traded against anyone being able to build same-signature APKs. If
the repo ever ships to Play or gains an audience, rotate to GitHub Actions secrets.

- Keystore: `config/release.keystore`, alias `dumb-switch` (PKCS12, RSA 2048)
- Password: `dumb-switch-release` — also stored in `gradle.properties` as
  `RELEASE_STORE_PASSWORD`

## Verify on device

Two minutes with the APK sideloaded. In order:

1. **Home chooser lists Dumb Switch.** Press Home after install; the chooser offers Dumb Switch
   and the Pixel Launcher. Accept Dumb Switch (or answer the first-run role prompt) — it never
   re-prompts while the role is held.
2. **Dumb home is the default.** The screen shows the clock, the date, and exactly five entries —
   Phone, Messages, Maps, Camera, Clock — and no drawer, search, or feed. Each entry opens the
   real app.
3. **Escape works.** Long-press "Smart mode", confirm in the dialog: the full app list appears
   with a countdown banner ticking down from 30:00. The countdown cannot be cancelled early.
4. **It decays back.** While smart mode is running, let the timer expire (or check the banner's
   "Smart mode over" state). The running list stays until then; the **next home press** lands on
   the dumb home again.

By design (v0 is launcher-only): Settings → Apps → Default apps → *Home app* switches back to the
Pixel Launcher, and non-allowlisted apps stay reachable from any other surface — v0 reshapes the
default, it does not enforce at the OS level.

## Overlay guard (v0.2, opt-in)

Off by default. In smart mode, open **Settings** (top of the app list) and tap "Dumb mode guard —
enable in Accessibility settings", then enable **Dumb mode guard** in the system list. Once
enabled, any app outside the dumb home is covered by a black screen — clock, "Dumb mode", one
**Home** button — while dumb mode is active. Smart mode covers nothing, and the service does
nothing at all until you enable it.

The guard is bypassable by design (findings log F2): it listens for window changes only, never
reads screen content, and stays switch-off-able in system settings. Turning it off in
Settings → Accessibility — or switching launchers, or disabling the app — removes the friction
entirely. It raises the cost of the feed; it does not hide it.

## Scope

The allowlist is configurable in-app (v0.2), and the overlay guard is optional (above). Still
out of scope: notification (DND) changes, uninstall protection, and device-owner lock task. The
launcher reshapes the default; it does not enforce at the OS level.

## Toolchain

Kotlin, single `:app` module, Gradle Kotlin DSL, `minSdk 33` / `targetSdk 35` / `compileSdk 35`,
JDK 17. CI (GitHub Actions, `ubuntu-latest`) runs `./gradlew assembleDebug test lint` on every PR
and uploads the debug APK.
