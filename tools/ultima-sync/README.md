# Ultima sync test tools

Ultima (Phisher repo) syncs through a Firebase Realtime Database (`<url>sync/<key>/...json`, GET/PUT/DELETE, SSE on `manifest.json?alt=sse`).
These tools test it without a real database:

- `java MockFirebase.java 9099 <dumpDir>` - in-memory stand-in (also streams SSE). In Ultima's "Configure App Sync" turn on
  "Use Custom Firebase Database", URL `http://127.0.0.1:9099/`, any key.
- `java Dec.java <dumpDir>/sync_<key>_categories_<name>.json` - prints a pushed category (`data` is `gz:` + base64 gzip of the JSON).
- `java AndroidSim.java` / `AndroidSimExt.java` - play the Android app: newer resume_watching + bookmarks, or an Android style
  extension list with one more plugin. The desktop must pull it (SSE), refresh Home / Library and install the plugin.
- `realkey.ps1`, `realtype.ps1` - real keyboard input (keybd_event) for the dev instance (`-Port`), refuses when it is not the foreground window.

Never point these at the portable `data` folder without a backup of `shared_prefs`: Ultima mirrors the cloud onto the local store
(keys of a synced category that the cloud payload lacks are removed).
