# Fire TV Key Bridge

A minimal Fire TV (Fire OS) app that opens any web app fullscreen in a WebView and hands the remote's D-pad, Select, Back, Menu and media keys to the page as ordinary keyboard events.

## The problem

Silk, the Fire TV browser, keeps the remote for itself. Measured on a Fire TV stick running Fire OS 8: only Play/Pause ever reached a web page. D-pad, Select and Back never did, so a TV-first web app (one that listens for `ArrowUp`, `Enter`, `Escape`) cannot be driven by the remote at all.

## What this does

Key Bridge is one activity with one WebView. It catches each remote button before the WebView sees it and dispatches a matching `keydown` and `keyup` into the page. Your web app needs no changes and no special SDK: it listens for the keys it would listen for on a keyboard.

- You choose the page: type the address with the remote on first run, or set it from a computer with one adb command. The full URL is kept, path and query included, and remembered once it has loaded.
- Back goes to the page first. If the page calls `preventDefault()` on it, the app stays. If not, the WebView goes back through its history, and at the first page the app leaves. Holding Back always leaves.
- If the page cannot be reached, an error screen says why in plain words, retries by itself (5, 10, 20, then every 30 seconds) and offers Retry now and Change address.
- No Compose, no AndroidX, no runtime dependencies. The debug APK is about 2.4 MB.

## Key map

Everything in this table is sent as a `keydown` then a `keyup`, with `key`, `code`, `keyCode` and `which` set. The table is pinned by `KeyMapTest`.

| Remote button (Android key) | `key` / `code` | `keyCode` |
|---|---|---|
| D-pad Up (`DPAD_UP`) | `ArrowUp` | 38 |
| D-pad Down (`DPAD_DOWN`) | `ArrowDown` | 40 |
| D-pad Left (`DPAD_LEFT`) | `ArrowLeft` | 37 |
| D-pad Right (`DPAD_RIGHT`) | `ArrowRight` | 39 |
| Select (`DPAD_CENTER`), `ENTER`, `NUMPAD_ENTER`, gamepad A | `Enter` | 13 |
| Back (`BACK`), gamepad B | `Escape` (default) or `Backspace` | 27 or 8 |
| Menu (`MENU`, the three-line button) | `ContextMenu` | 93 |
| Play/Pause (`MEDIA_PLAY_PAUSE`) | `MediaPlayPause` | 179 |
| Play (`MEDIA_PLAY`) | `MediaPlay` | 415 |
| Pause (`MEDIA_PAUSE`) | `MediaPause` | 19 |
| Stop (`MEDIA_STOP`) | `MediaStop` | 178 |
| Next (`MEDIA_NEXT`) | `MediaTrackNext` | 176 |
| Previous (`MEDIA_PREVIOUS`) | `MediaTrackPrevious` | 177 |
| Rewind (`MEDIA_REWIND`) | `MediaRewind` | 412 |
| Fast-forward (`MEDIA_FAST_FORWARD`) | `MediaFastForward` | 417 |

Volume, Home and power are not in the table: they stay with the system. Keys from a keyboard or a Bluetooth keyboard are not touched; the WebView gets them as usual.

Events go to the focused element, or `body` if nothing has focus, and bubble up to `document` and `window`. Back is sent as `Escape` by default because `Backspace` would delete text in a focused input. To send `Backspace` instead, see below.

The page can detect the shell: `window.keyBridge.version` is set after load, `<html>` gets a `data-key-bridge` attribute, and the user agent ends with `FireTVKeyBridge/0.1.0`.

## Build

You need JDK 17 or newer and the Android SDK (platform 34). Put the SDK path in `local.properties` (`sdk.dir=...`) or set `ANDROID_HOME`.

```sh
./gradlew assembleDebug testDebugUnitTest      # Windows: gradlew.bat
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. The wrapper downloads Gradle 8.14 on first use.

## Sideload

On the stick: Settings, My Fire TV, Developer options, turn on ADB debugging. Find its address under About, Network.

```sh
adb connect <stick-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open "Key Bridge" from Your Apps & Channels (it has a launcher banner on the Fire TV home row).

## Set the page

With the remote: first launch shows "Which page should open?". Select on the box opens the on-screen keyboard. Type an address and choose Open. `10.0.0.5:3000/app` and `my-pc.local` get `http://`; a public name like `app.example.com` gets `https://`. To change it later, use the `url` or `reset` extras below (a working page keeps every remote key, so there is no on-remote shortcut), or let the page fail: the error screen offers Change address.

From a computer, once:

```sh
adb shell am start -n dev.xkazm04.keybridge/.KeyBridgeActivity --es url "'https://example.com/tv?room=1'"
```

The single quotes inside the double quotes keep `?` and `&` away from the device shell. The address is saved after the page has loaded, so a typo is never kept. Other extras:

```sh
adb shell am start -n dev.xkazm04.keybridge/.KeyBridgeActivity --ez reset true      # forget the saved address
adb shell am start -n dev.xkazm04.keybridge/.KeyBridgeActivity --es back backspace  # send Back as Backspace
```

The address is stored in the app's private SharedPreferences, query string included. Do not put secrets in it that you would not keep on the device.

## Demo page

`docs/demo/index.html` has no dependencies. It lists the last 12 key events it received, with `key`, `code` and `keyCode`, and says whether it is running inside Key Bridge.

- On the stick: choose "Try the demo" on the setup screen. The page is built into the APK.
- Or serve it: `python -m http.server 8000 --directory docs/demo`, then point the app at `<your-computer-ip>:8000`.
- In a desktop browser: open the file. The same keys on a keyboard show the same events.

Back on the demo is taken once, then lets go: press Back twice within 3 seconds to see the app leave.

## Limitations

- Not yet run on a physical stick from this repo; the logic is covered by JVM unit tests and the injected script was run against a stub DOM. Please report what you see on your device.
- Events are synthetic. They fire your key handlers, but they do not type into a text field and do not move browser focus on their own. Apps that rely on native Tab focus or the caret need their own handling, or can use an on-screen keyboard.
- `event.isTrusted` is `false`. A few APIs (for example requesting fullscreen from a handler) may refuse untrusted events; media can play without a gesture.
- Clear-text http is allowed on purpose, for pages served on a LAN.
- Single page at a time: links open in the same WebView and external schemes are not handled.
- The launcher banner is a drawn glyph without text.
- Minimum Fire OS 7 (Android 9, API 28). Older sticks are not supported.

## Credits

Extracted from Study Desk, built for the Amazon Developer hackathon 2026 (Build, Ship, Shape).

## License

MIT. See [LICENSE](LICENSE).
