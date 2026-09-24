# Changelog

All notable changes to Dumb Switch. The friction design is the product: dumb by default,
smart mode is a timed escape that always decays back.

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
