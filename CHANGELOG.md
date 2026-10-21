# Changelog

## 0.1.0 - 2026-10-21

First release, extracted from the Study Desk stick app.

- Fullscreen WebView shell for Fire OS 7 and 8 (minSdk 28) with a Leanback launcher entry and banner.
- Remote keys (D-pad, Select, Back, Menu, media keys) forwarded to the page as DOM keyboard events; the key map is documented and pinned by tests.
- Back goes to the page first, then through WebView history, then leaves; holding Back always leaves.
- Configurable page: setup screen driven by the remote, plus `--es url`, `--ez reset` and `--es back` adb extras. The full URL, path and query included, is kept once the page loads.
- Error screen with reasons in plain words, automatic retry with backoff, Retry now and Change address.
- Built-in demo page (`docs/demo/index.html`) that shows the last key events received.
- Fullscreen support for in-page video players.
