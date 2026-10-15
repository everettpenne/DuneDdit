# DuneDdit: Seekur

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
