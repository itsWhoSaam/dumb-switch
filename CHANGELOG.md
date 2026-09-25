# Changelog

All notable changes to Dumb Switch. The friction design is the product: dumb by default,
smart mode is a timed escape that always decays back.

## 0.2.0 — 2026-09-24

Four features file off v0.1's sharpest edges — hardcoded apps, zero enforcement, loud
notifications, debug-only distribution — without touching the friction thesis: each one
either adds friction to leaving dumb mode or quiets the pull toward it, and none claims
to be unbypassable.

- **Allowlist editor** — a Settings row in the smart list opens an editor over every
  launchable app; saving persists an ordered list that the next home press renders, with
  real labels. The dumb home always keeps at least one app — empty saves are rejected —
  and the dumb home itself never grows UI.
- **Overlay guard (opt-in)** — an accessibility service that covers a non-allowlisted
  foreground app with a black screen: clock, "Dumb mode", and one button that returns to
  the dumb home. It stays off until enabled in Android's Accessibility settings, and
  smart mode no-ops it. Switch-off-able by design — it raises the cost of the feed,
  it does not hide it.
- **Quiet dumb mode (opt-in)** — with the toggle on, landing on the dumb home applies a
  priority-only interruption filter; starting smart mode restores the filter that was
  active before muting, so a DND you set yourself is never stomped by a blind reset.
- **Signed releases** — `v*` tags now run a release workflow that publishes a signed
  `app-release.apk` with CHANGELOG notes, versionCode 2 / versionName 0.2.0. The release
  signature differs from the CI debug signature — see README "Release installs" for the
  one-time uninstall.

## 0.1.0 — 2026-09-24

First installable release: a replacement launcher for the Pixel 7 Pro that is dumb by default.

- **Dumb home** — full-bleed clock, date, and exactly five allowlisted entries:
  Phone, Messages, Maps, Camera, Clock.
- **Timed smart-mode escape** — long-press "Smart mode", confirm, and every launchable app is
  available for 30 minutes under a live countdown. No early cancel; the countdown is the friction.
- **Decay to dumb** — mode is re-derived from the persisted escape timestamp on every resume,
  so the next home press after expiry lands back in dumb mode.
- **Home-role gate** — first run asks once (`ROLE_HOME`) to make Dumb Switch the default home.
- **Launcher-only enforcement** — nothing is blocked at the OS level;
  Settings → Default apps is the by-design bypass.

## 0.2.0 — Unreleased

- **Allowlist editor** — a Settings row at the top of the smart list opens an editor where any
  launchable app can be checked onto the dumb home. The list persists in SharedPreferences and
  renders in saved order on the next home press; the v0 five remain the default until the first
  save. Saving an empty list is rejected — the dumb home always keeps at least one app.
