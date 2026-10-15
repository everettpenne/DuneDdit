# DuneDdit: Seekur

<img src="docs/icon.png" alt="Seekur app icon: an orange sandworm arching out of a dune under twin moons" width="128" align="right" />

**Seekur** is an Android Reddit client with iOS 26-style liquid glass. It is a port of [SwiftDdit / Lurker](https://github.com/SilverMarcs/SwiftDdit)
(a SwiftUI client by Zabir Raihan) to Kotlin and Jetpack Compose, and uses the same glass library as Uno Launcher
([Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)) so surfaces, toggles, buttons and menus bend and blur what is behind them the way iOS's do.

`DuneDdit` is the project (repository); `Seekur` is the app.

## What it does (as Lurker does)

- **Feed**: Home, Popular and All, with Best / Hot / New / Controversial / Top (hour to all time) sorting, endless scrolling, pull to refresh, and a
  switcher between the front pages. Optionally remembers the last feed and sort.
- **Posts**: text (Reddit markdown), images, galleries, GIFs, Reddit videos (HLS), YouTube, link cards and crossposts; NSFW media blurred until tapped;
  save, share, vote (applied at once, taken back if Reddit refuses).
- **Comments**: the full thread with depth bars, tap to collapse, the eight sort orders, inline images and GIFs, replies that appear immediately.
- **Subreddits and users**: browse, search inside a subreddit, subscribe or follow, view about info.
- **Profile**: Inbox, Saved, favourites, and every subscribed subreddit in alphabetical sections with a filter.
- **Search**: subreddits and posts.
- **Save on this device**: a setting that makes the bookmark keep a post on your phone only (with no account needed to read it later), leaving your Reddit saved list alone.
- **Accounts**: several Reddit accounts, signed in on Reddit's own page; the session cookie is stored encrypted in the Android Keystore.
- **Links**: Reddit links (`reddit.com`, `redd.it`) open inside the app.

## Read this first: how it talks to Reddit

Like Lurker, Seekur is **not** built on Reddit's official OAuth API. It signs in on Reddit's website, keeps your `reddit_session` cookie, and reads
the website's JSON (`/r/<sub>/hot.json`, ...). That has consequences:

- **Reddit refuses anonymous requests** (HTTP 403), so Seekur needs a signed-in account to show anything but errors.
- **It can break or be blocked** whenever Reddit changes or enforces its rules. Reddit's terms govern use of its site; using this against them is your call.
- Requests carry the system WebView's own user-agent (the browser you signed in with), not an invented one.
- Your cookie never leaves the phone except to reddit.com. Backups are off, so it is not copied off the device either.

Moving to the official API (OAuth with your own client id) would be the sanctioned route and a reasonable next step.

## Status

A first port, built and unit-tested but **not yet exercised against live Reddit** (the development environment could not sign in). The data layer is
tested against Reddit-shaped fixtures and a mock server (parsing, media extraction, comment flattening, URL building, write requests, error mapping).
The UI was checked on an emulator with fixture posts. Expect rough edges when real data arrives.

## Install on your phone

Seekur is not on Google Play. Install the APK from the **[Releases page](https://github.com/everettpenne/DuneDdit/releases/latest)**.
It needs **Android 12 or newer** (API 31); Android 13 or newer for the full glass lens.

1. On your phone, open the latest release and download **`Seekur-<version>.apk`** (and, if you want to check it, `SHA256SUMS.txt`).
2. Open the downloaded file. Android will say it can't install apps from this source: tap **Settings** and allow your browser (or the Files app) to
   **install unknown apps**, then go back and tap **Install**. (On GrapheneOS this is the per-app "Install unknown apps" setting; turn it back off afterwards.)
3. Open **Seekur**, go to **Profile › Settings › Accounts › Add account**, and sign in on Reddit's page. Home and Saved need an account.

To update, install a newer APK over the old one; it is signed with the same key, so your accounts and settings stay.

**Check the download (optional but sensible):** the release lists the APK's SHA-256. Compare it with `sha256sum Seekur-<version>.apk`, and the signing
certificate with `apksigner verify --print-certs Seekur-<version>.apk`; the certificate SHA-256 is
`c76163ce927f65df5869fe7b67f7e00ac81c8a254a5fc830590adef159724ff4`. Only install an APK from this repository's Releases page.

**From a computer instead:** enable *Developer options › USB debugging*, then `adb install Seekur-<version>.apk`.

**Build it yourself:** see below. A debug build (`./gradlew :app:installDebug`) is signed with your own debug key, so it will not install over the release one;
uninstall one before installing the other.

## Build

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:installDebug        # build and install on a connected device
```

Requirements: JDK 17+, Android SDK 36, Android 12 (API 31) or newer on the device (the glass effect needs Android 13 for the lens; on 12 it blurs without bending).

## Layout

```
app/src/main/java/com/everettpenne/seekur/
  data/        Reddit client (api/), accounts and secure storage (auth/), models, media extraction (media/), settings
  ui/glass/    the liquid glass kit: surfaces, controls, menus, sheets, tab bar
  ui/screens/  feed, post and comments, profile, search, inbox, settings, accounts and sign-in
  ui/components/ post card, media views, rich text
  util/        formatting, markdown, link routing
```

## Licence

MIT. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
