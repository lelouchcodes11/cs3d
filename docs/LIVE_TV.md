# Live TV: how the phone does it, how this app does it, and what was wrong
> **Status (2026-10-22, later the same day): rolled back.** The owner reported live streams "completely broken" after the changes in section 3 (rows 1, 3, 4, 5, 6: the proxy
> cancel fix, the HLS and DASH prefetch, the 30 s start rule, the live clock for the seek bar) and asked for the GitHub version of live TV, without any live seeking (this session's and
> the DVR window of the night before). `HlsProxy`, `DashProxy`, the live parts of `MpvPlayer`, `PlayerSession`, the two seek bars and the LIVE pill are the committed versions again;
> `LiveStart`, `DashPrefetch` and their tests are deleted. What stays: the DNS fix (row 2) and the keyboard play/pause icon. A copy of the rolled-back code is NOT in the repository (it was
> kept in the session's scratchpad). The research (sections 1 and 2) and the findings (what each row found) are still true; the fixes of rows 1, 3, 4, 5 and 6 are not in the app. If any
> of them is wanted again, row 1 (leaving a video must not cancel the next channel's requests) is the one that is a plain bug in the committed code.


Written 2026-10-22 after the owner's report "most live channels do not play, some load very slowly, some break after a while, the seek bar and the times are not right".
Sources: the upstream app in `reference/cloudstream` (`ui/player/CS3IPlayer.kt`, `ui/player/live/*`, `PlayerView.kt`), the Media3 pages (live streaming, HLS, customization),
the owner's `app.log`, and simulators (`LiveSim.java`: sliding HLS, `DirSim.java` + ffmpeg `-f dash`: a real live DASH channel with 4 qualities and 2 audios, both with a delay per segment to
act like a far CDN; they live in a session scratchpad and are described at the end).

## 1. How the Android app handles live TV (ExoPlayer / Media3)

**Opening.** One `MediaItem`; HLS and DASH go to `HlsMediaSource` / `DashMediaSource`. The player loads the master playlist (or the MPD) and then **only the streams it plays**: one video
variant (adaptive, from the bandwidth) and one audio rendition. The other qualities and languages are not opened. HLS has "chunkless preparation": it prepares from the master
alone (CODECS attribute), without downloading a segment first. That is why a channel with eight streams starts at once on the phone.

**Where it starts.** `Window.getDefaultPositionMs`, near the live edge. The app sets `DefaultMediaSourceFactory.setLiveTargetOffsetMs(5000)` (`PREFERRED_LIVE_OFFSET`): five
seconds behind the live edge, so there is something in front of the stall. `DefaultLivePlaybackSpeedControl` (0.97 to 1.03) nudges the speed to keep that offset.

**The window and seeking.** A live stream is a timeline with a *window*: `duration` is the length of the window (what the server keeps: 20 s on some channels, 10 minutes on others), `currentPosition` is inside it
(0 = the oldest picture), `seekTo(ms)` takes a position in it, `seekToDefaultPosition()` goes back to the live edge. The seek bar is that window. The default UI shows the position and the
duration of the window and says the position "appears to jump backward each time the window updates" (Media3's own words). The app's `LiveManager` works out "at the live edge" itself: from
`currentLiveOffset` when the stream has it, else from the time the last window update came and its size (`LivestreamChunk`); `LIVE_MARGIN` 6 s. At the live edge `LivePreviewTimeBar` pins the knob at
the end and `PlayerView` hides the remaining time and shows "LIVE". No seek previews on live ("the previews get outdated").

**Staying alive.** `onPlayerError`: `ERROR_CODE_BEHIND_LIVE_WINDOW` (the position fell out of the window: a long pause, a long stall) -> `seekToDefaultPosition()` + `prepare()`;
`PlaylistStuckException` (position ahead of the live window) -> seek to the live head; `IO_NETWORK_CONNECTION_FAILED` with a known duration -> `prepare()` again. `DefaultLoadControl`
(minimum buffer 50 s, play after 2.5 s, after a stall 5 s, 30 s of back buffer): playback starts as soon as 2.5 s are there. A failed load is retried (3 times, growing delay).

**Prefetch.** There is none beyond the buffer of the stream that plays: it keeps loading the *next segments of the chosen variant*; the playlist tracker reloads the live playlist
(HLS) / the MPD (`minimumUpdatePeriod`, with `UTCTiming` clock sync for DASH) on its own schedule. Nothing is fetched for the streams that do not play.

## 2. How this app plays live TV (mpv / ffmpeg behind two local servers)

`MpvPlayer` hands the channel to libmpv. HLS goes through `HlsProxy`, DASH through `DashProxy` (loopback servers that fetch with the app's HTTP client: DNS setting, headers, DRM keys,
repairs). mpv/ffmpeg then does what ffmpeg does, and that differs from ExoPlayer in four ways that matter:

1. **ffmpeg opens every stream of the manifest.** Its HLS demuxer opens every variant and rendition playlist, its DASH demuxer every Representation, one after the other, and reads the first
   segments of each before it shows anything (three segments from the end of an HLS live playlist; for DASH the first listed segment, up to 60 s from the end). With 6 to 8 streams and a CDN that
   answers in 0.5 s that is 6 to 10 s of sequential requests. Measured: 6 HLS variants at 0.6 s a segment 7.6 s; 6 DASH streams at 0.4 s 5.3 to 6.4 s; the owner's log 8 to 11 s.
2. **Its DASH reader cannot seek in a live manifest** (ENOSYS), and its start point is its own (above), nothing like a configurable offset.
3. **Its DASH reader works out live segment numbers from the PC's clock**, asks for segments that the CDN has not published yet (404, then it skips ahead and comes back: the picture slows
   down and the sound repeats). `DashProxy` already holds such requests until the manifest lists the segment, and refuses a repeated one.
4. **No ExoPlayer recovery logic.** What exists here: `MpvPlayer.liveStallCheck` (open the channel again at the live point after a long wait), the retry "without the playlist server".

## 3. What was wrong (found and fixed, in the order they were found)

| # | Finding | Evidence | Fix |
|---|---------|----------|-----|
| 1 | **Zapping killed the next channel.** `MpvPlayer.release()` of the page that is left called `cancelAll()` on `DashProxy`, `HlsProxy` and `RangeProxy`. The page of the next channel is made before the last one is let go of, so the new channel's manifest and first segments were cancelled in the middle of the request and its proxy entry was gone: "IOException Canceled", then 404 from the proxy. mpv gave up on the first try and played the channel straight (no proxy: no DRM-less repairs, not recognised as live: no live bar, previews started on a live stream and crashed the JVM with 0xC0000005 once). | log lines `DashProxy: attempt 1 for init-stream0.m4s: IOException Canceled` followed by `trying again without the playlist server`; reproduced with two `/playurl` in a row; gone after the fix | `release()` cancels only its own stream. A retry without the proxy still knows the source was live (`wasLive`), and no seek previews for any live channel. |
| 2 | **DNS-over-HTTPS blocked on the owner's network** made every first request wait 8 to 40 s (JIO TV pictures and channels). | `curl` to `cloudflare-dns.com` and 1.1.1.1 fail from this PC; log: every host "not known to the chosen DNS service" | DoH timeouts 2 s / 4 s, system DNS first for 5 minutes after 2 misses, system DNS as the fallback for a name the service does not know (see PLAN.md 70d). |
| 3 | **Slow start, HLS.** | 7.6 s for 6 variants | first two segments of every live media playlist + the fMP4 init segment fetched at the same time (`HlsProxy.rewrite`): 1.7 s. |
| 4 | **Slow start, DASH.** | 5.3 to 6.4 s for 6 streams | `DashPrefetch.plan` lists the init segments and the first two media segments of every Representation from the timeline; `DashProxy.startPrefetch` fetches them all at once and serves them from memory (also for `Range: bytes=0-`, which is what ffmpeg sends): 2.4 s. |
| 5 | **Where a live channel starts.** HLS three segments from the end (about 4 to 6 s behind), DASH at the first listed segment (the owner's JIO channels list 10 minutes: up to 60 s behind). The owner asked for: 30 s behind the edge when the channel keeps more than 30 s, else the oldest picture there is. | measured with the simulators | `LiveStart`: the first playlist (HLS: cut at the end) / manifest (DASH: cut at the front, `startNumber` and `t` moved on; every manifest of the first 4 s, because ffmpeg reads it twice when it opens it) is shortened until ffmpeg's own rule lands there; later versions are whole. 7 unit tests. |
| 6 | **The seek bar and the times jumped.** The window's end moves a segment at a time while the position moves smoothly: the knob and the numbers jumped back and forth by a segment each time the playlist was read. | the log of `behind` alternating 4.8 / 5.9 s | one live clock (`PlayerSession.liveEdgeMs`): the end as last read plus the time since (at most 8 s), "behind live" made from it every 250 ms, bar, numbers and the LIVE pill made from that; pinned at the end within 12 s of the edge, as the phone's `LivePreviewTimeBar` does. |

## 4. What is still open

* Quality switching for a live DASH channel (ffmpeg opens all Representations; pruning them would remove the quality menu, so the cost is cut by fetching in parallel instead).
* A live DASH channel cannot be rewound beyond what mpv has buffered (ffmpeg's DASH reader cannot seek in a dynamic manifest).
* Failures that come from the channel (403 from a token wrapper, a ClearKey that is wrong, an expired token): the app shows what the source says; the same link fails on the phone when the token is gone.

## 5. The test rig (recreate when needed)

* `LiveSim.java <dir> <port> <windowSeconds>`: a sliding HLS window from pre-encoded 2 s TS segments (`seg/sN.ts`, `ffmpeg -f segment`), `/master.m3u8` = six variants, `-Ddelay=ms` per segment, `?xs=N` adds EXT-X-START.
* `ffmpeg -re ... -f dash -seg_duration 2 -window_size 30 -use_timeline 1 -adaptation_sets "id=0,streams=v id=1,streams=a" live.mpd` (4 video qualities, 2 audios) and `DirSim.java <dir> <port>` to serve it with `-Ddelay=ms`.
  ffmpeg writes a final *static* manifest when it ends: a test that suddenly plays as a 60 s recording is that.
* The dev server: `/playurl?url=...&ua=...&referer=...` (the player), `/player` (state: window, behind), `/log?lines=N`, `/mpv?name=mute&set=yes`, `/key?name=SPACE`.
  `/player` only updates while the window has the focus.
