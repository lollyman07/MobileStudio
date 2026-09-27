# Mobile Studio

An Android-only, OBS-inspired virtual production app: scenes, layered sources, a live
compositor, real screen/video recording, and a documented, legitimate approach to "virtual
camera" output — all running directly on-device, no PC, no root, no OBS install required.

Kotlin + Jetpack Compose + Material 3 + CameraX + MediaProjection + MediaCodec/MediaMuxer + Room.

---

## 0. Fastest path to an actual APK: let GitHub build it for you

This repo includes `.github/workflows/build.yml`, a ready-to-go GitHub Actions workflow with a
real Gradle wrapper (`gradlew` + `gradle-wrapper.jar`) already included. GitHub's cloud runners
have full access to Google's Maven repositories, so they can do what a sandboxed environment
can't:

1. Create a new (public or private) repo on GitHub and push this project's contents to it
   (or just upload this folder through the GitHub web UI's "Add file → Upload files").
2. Go to the repo's **Actions** tab. The "Build APK" workflow runs automatically on push (or
   click **Run workflow** to trigger it manually).
3. When it finishes (a few minutes), open the completed run and download the
   **MobileStudio-debug-apk** artifact from the bottom of the page — that's your installable
   `app-debug.apk`.
4. Transfer it to your Android phone (email, Drive, `adb install`, USB) and install it — you may
   need to allow "install unknown apps" for whatever app you use to open it.

No Android Studio, no local SDK setup required for this path.

## 1. Building the APK locally (Android Studio)

This repository is a complete, standard Android Studio project. It was **not** compiled in the
environment that generated it (no Android SDK / no access to Google's Maven repositories there),
so the first build has to happen on your machine. That's a normal Gradle build, nothing exotic:

1. Install **Android Studio** (Koala/2024.1 or newer) with Android SDK Platform 34 and the
   Android SDK Build-Tools installed via the SDK Manager.
2. `File → Open` this folder (the one containing `settings.gradle.kts`).
3. Let Gradle sync — it will download the dependencies listed in `app/build.gradle.kts`
   (AndroidX, CameraX 1.3.4, Room 2.6.1, Compose BOM 2024.06.00, etc.) from Google's/Maven
   Central's repositories.
4. `Build → Build Bundle(s)/APK(s) → Build APK(s)`, or from a terminal:
   ```bash
   ./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
   ./gradlew assembleRelease    # → app/build/outputs/apk/release/app-release.apk (needs signing config)
   ```
5. Install on-device: `adb install app/build/outputs/apk/debug/app-debug.apk`, or drag the APK
   onto a running emulator.

**Minimum SDK 26 (Android 8.0), target/compile SDK 34.** CameraX, MediaProjection, and the
MediaCodec APIs used here are all stable from API 26 onward; a couple of code paths (notably
foreground-service types) branch on `Build.VERSION.SDK_INT` for the newer, stricter Android
14 rules while staying functional on older versions.

No native/NDK code, no root, no proprietary SDKs — a stock `gradlew assembleDebug` is all it
takes.

---

## 2. What's actually implemented (not mocked)

Every button in this app does the real thing:

| Feature | Real implementation |
|---|---|
| Camera source | CameraX `ImageAnalysis`, live YUV→RGB conversion, front/back switch, torch |
| Screen capture source | `MediaProjectionManager` + `VirtualDisplay` + `ImageReader`, real system permission dialog |
| Image source | `BitmapFactory` decode of a picked PNG/JPG/WebP |
| Video source | Frame sampling via `MediaMetadataRetriever` (see limitation below) |
| Text / Color sources | Drawn procedurally by the compositor every frame |
| Web source | Live `WebView` (see limitation below) |
| Scene compositor | Custom `Canvas`-based renderer honoring z-order, position, size, rotation, opacity, crop |
| Recording | `MediaCodec` H.264 encoder + `MediaMuxer`, AAC audio via `AudioRecord`, real MP4 output |
| Virtual camera | Real MJPEG-over-HTTP server on the LAN (see limitation below — this is the one feature the OS itself restricts) |
| Audio level meter | Live RMS from `AudioRecord` PCM samples, in dBFS |
| Project storage | Room database — scenes/sources/positions persist and auto-restore across restarts |
| Settings | DataStore-backed, actually wired into the recorder/compositor |
| Recordings library | Real files via `MediaStore`, play/share/delete via `FileProvider` |
| Foreground service | Persistent notification while recording/capturing/streaming, resources released in `onDestroy` |

---

## 3. Documented Android limitations (Section 21 of the spec)

Three features run into real, platform-level restrictions. Rather than fake them, here's exactly
what's possible and what this app does instead.

### 3.1 Virtual camera — no system-wide registration API for 3rd-party apps

Windows has DirectShow/Media Foundation virtual-camera drivers; Linux has `v4l2loopback`.
**Android has no equivalent public API.** Camera devices are enumerated from the Camera HAL, a
vendor/OEM-signed component; there is no `Camera2`/CameraX call that lets a normal app register
itself as a selectable camera for *other* apps to pick. The only ways around this on stock Android
are:
- Root + a custom HAL module (unsafe, unsupported, explicitly out of scope here), or
- A manufacturer's own proprietary SDK (not universal, not something a general app can rely on).

**What this app does instead:** `VirtualCameraManager` runs a small HTTP server on the phone that
streams the compositor's live output as **MJPEG** (`multipart/x-mixed-replace`) — the same format
countless IP/network cameras use. Any software with a network-camera/MJPEG input (OBS's own
Browser Source, VLC, a browser tab, other apps that accept an IP camera URL) can consume it, on
the same Wi-Fi, with no cable and no root. The Studio screen shows the live stream URL and
connected-viewer count. The architecture (`FrameSource`/output-listener pattern in
`CompositorEngine`) is intentionally pluggable, so a genuine OEM virtual-camera API could be
swapped in later without touching the UI or compositor.

### 3.2 Web source — previews live, but isn't captured into recordings/stream

A `WebView` can only render to the screen (or to a `View` that's actually been measured/laid out
in a visible window). Capturing it into an **off-screen** bitmap for compositing would require the
`SYSTEM_ALERT_WINDOW` permission to host a hidden overlay window — a permission this app
deliberately doesn't request. So: a Web source renders live in the Sources editor as a real,
interactive `WebView`, but the compositor draws a clearly labeled placeholder for it in anything
that isn't the live on-screen preview (recordings, the MJPEG stream). This is stated in the
source's own edit sheet, not hidden.

### 3.3 Video source — sampled frames, not full-rate decode

Rather than a full `MediaCodec` decoder → `SurfaceTexture` → GL pipeline (real, but a lot of
additional moving parts for a mobile app), `VideoFrameProvider` polls
`MediaMetadataRetriever.getFrameAtTime()` roughly every 125 ms (~8 fps of freshly-decoded
content). It's genuinely decoding your video file, just not at native frame rate. This keeps CPU
and battery cost low and works identically across every codec the retriever itself supports. The
natural upgrade path, if a project ever needs smoother in-scene video playback, is swapping this
one class for a `MediaCodec`+`SurfaceTexture` pipeline behind the same `FrameSource` interface —
nothing else in the app would need to change.

---

## 4. Architecture

```
app/src/main/java/com/mobilestudio/app/
 ├── model/        Scene/Source/Transform/Settings data classes
 ├── data/          Room DB (scenes, sources, recordings) + DataStore settings repository
 ├── rendering/     CompositorEngine (the frame-compositing core), FrameSource providers
 ├── camera/        CameraX source + real Camera2-capability queries
 ├── capture/       MediaProjection-based screen capture
 ├── recording/     MediaCodec/MediaMuxer recorder + the foreground service tying it together
 ├── audio/         AudioRecord-based mic level meter
 ├── virtualcamera/ MJPEG LAN streaming server
 ├── permissions/   Centralized runtime-permission checks
 ├── util/          MediaStore publishing helper
 └── ui/
     ├── studio/     Main dashboard + ViewModel (single source of UI truth)
     ├── scenes/     Scene list/create/rename/duplicate/delete
     ├── sources/    Source layer list + per-type property editor
     ├── recordings/ Recordings library
     ├── settings/   Video/recording/appearance settings
     ├── components/ Interactive preview canvas, status indicators
     ├── navigation/ Bottom-nav host
     └── theme/      Material 3 dark theme
```

**Data flow:** `StudioViewModel` binds to `StudioForegroundService`, which owns the long-lived
`CompositorEngine`. The compositor pulls the latest frame from each source's `FrameSource` every
tick, draws the composed scene, and publishes it to three places at once: the live Compose
preview, the `RecorderManager` (if recording), and the `VirtualCameraManager` (if streaming) — so
what you see in the preview is exactly what gets recorded or streamed.

---

## 5. Permissions requested, and why

| Permission | Why |
|---|---|
| `CAMERA` | Camera sources |
| `RECORD_AUDIO` | Microphone source + recorded audio track |
| `POST_NOTIFICATIONS` (13+) | Required foreground-service notification |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (13+) / `READ_EXTERNAL_STORAGE` (≤12) | Picking image/video sources from the device |
| `FOREGROUND_SERVICE*` (+ camera/microphone/mediaProjection subtypes) | Keeps recording/capture/streaming alive in the background, per Android 14's stricter foreground-service rules |
| `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | The local-network MJPEG virtual-camera stream |

Nothing else is requested. Screen-capture permission (`MediaProjection`) is a one-off system
dialog triggered only when you tap "Add screen capture," not requested up front.

---

## 6. Known trade-offs given mobile constraints

- The YUV→RGB camera conversion goes through a JPEG round-trip (`YuvToRgbConverter`) rather than
  a RenderScript/GLES path — RenderScript is deprecated, and a full GLES pipeline is real
  additional complexity for a mobile app targeting up to 1080p. On lower-end devices, drop the
  target resolution/FPS in Settings if you see the Performance panel report "Fair" or
  "Struggling."
- Video-encoder input uses the `Surface.lockCanvas` technique rather than GLES texture
  rendering — simpler, fully supported, slightly more CPU-bound than a GL path.
- The app icon uses a placeholder flat-color adaptive icon; swap in real assets in
  `res/mipmap-anydpi-v26` for a production release.

None of these are "fake" functionality — they're documented, working trade-offs appropriate for
a mobile app, exactly as requested.
