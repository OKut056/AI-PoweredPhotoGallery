# AI-Powered Photo Gallery Project Context

Read this before code. Current code is the implementation truth; this file records product decisions.

Last updated: 2026-07-10

## Product Direction

- Current phase: private media workspace, not a full public system gallery replacement.
- Current priority: finish the core gallery foundation before AI/chat/feed features.
- Workspace root: `Android/data/com.example.ai_poweredphotogallery/files/Media/`.
- Use one root folder named `Media`; do not create default `Pictures` or `Movies` subfolders.
- Do not scan the public system gallery by default. No photo/media read permission is needed for the current workspace-only flow.
- The bottom `AI` tab is intentionally blank until its product design is decided.
- Search is a separate page opened from the search buttons. Current search is local metadata search over workspace files, not image recognition.
- Future AI recognition and short-video/feed features should run on this controlled workspace unless the product direction changes later.

## Import Rules

- Import uses Android system pickers.
- Folder import copies only image/video files inside the selected folder into `Media`.
- Folder import does not create/import the selected parent folder itself.
- Child subfolders are preserved. Example: selecting `GalleryTest` copies `GalleryTest/a.jpg` to `Media/a.jpg`, and `GalleryTest/Sub/b.jpg` to `Media/Sub/b.jpg`.
- File import supports one or more selected image/video files and copies them into `Media`.
- Import is copy-only for now. Cut/move import is not implemented.
- Import skips image/video files whose SHA-256 content hash already exists in the current `Media` workspace, even if the file name is different.
- Hashes are cached in `context.filesDir/.media_index/hash_index.tsv` with path, size, modified time, and SHA-256. The cache is refreshed from file size/mtime and pruned on the next import.
- Imports are serialized. Provider names are normalized, every destination is verified to remain inside `Media`, and content is copied to a hidden temporary file before it is renamed into place.
- Imported media preserves its own time for sorting: embedded image/video creation time first, source modified time second, and import time only as a final fallback.
- Import result messages distinguish imported files, duplicate skips, and other skips.

## Albums And Display

- The app displays images and videos inside the workspace: images `jpg, jpeg, png, webp, gif`; videos `mp4, m4v, mov, mkv, webm, 3gp`.
- Video files are scanned, shown with duration overlays, and played in the detail viewer with an aspect-fit TextureView/MediaPlayer player, center play button, draggable progress bar, and bottom play/pause control.
- Top-level subfolders under `Media` appear as albums / more albums.
- Files directly under `Media` appear in all items, not as a separate folder album.
- Hidden folders and dot-prefixed folders are ignored.

## Selection Behavior

- Selection mode remains active even when nothing is selected.
- In selection mode, the top bar shows select-all, selected count, and cancel actions.
- Media grids select all visible/current media for the current screen: photos, all items, album detail, or recent deleted.
- Album selection selects albums. The top bar also shows a muted total media count for selected albums.
- System back exits selection mode first instead of leaving the current screen.

## Delete / Restore

- Delete is app-controlled for workspace files: move to `Media/.recent_deleted`.
- Trash metadata is stored under `context.filesDir/.recent_deleted` and updated with atomic file replacement. Missing trash targets are pruned on scan so an interrupted delete cannot hide the original media.
- Restore moves files back to the recorded relative path and recreates missing folders inside `Media`.
- Permanent delete removes selected files from app recent-deleted after confirmation.
- Android system gallery trash integration is not part of the current workspace-only design.

## Deferred Product Areas

- Do not build the `AI` tab until its product design is decided.
- Do not add chat-first UI, AI recognition, or short-video/feed features while the core gallery foundation is still being finished.
- Do not bring back public MediaStore scanning unless the user explicitly changes direction.
- Do not bring back SAF choose-folder scanning as a live library source. Folder picker is import-only.
- Do not request `READ_MEDIA_IMAGES` for the current workspace-only behavior.

## Main Files

```text
app/src/main/java/com/example/ai_poweredphotogallery/MainActivity.kt
app/src/main/java/com/example/ai_poweredphotogallery/GalleryModels.kt
app/src/main/java/com/example/ai_poweredphotogallery/GalleryStorage.kt
app/src/main/java/com/example/ai_poweredphotogallery/GalleryScreens.kt
app/src/main/java/com/example/ai_poweredphotogallery/AiSearch.kt
app/src/main/AndroidManifest.xml
```

## Next Useful Tasks

- Add a media info view: file name, path, size, resolution, duration, hash, created/modified time.
- Improve import UX only where it helps the core gallery: progress, failure details, and import-complete navigation.
- Make sorting/filtering real: time, name, size, media type.
- Keep deletion/restore behavior conservative and data-loss resistant.

## Test Media

For emulator/device tests, push files into the app-specific workspace:

```text
/sdcard/Android/data/com.example.ai_poweredphotogallery/files/Media/
```

Example:

```powershell
adb shell mkdir -p /sdcard/Android/data/com.example.ai_poweredphotogallery/files/Media
adb push "D:\some-local-folder\." /sdcard/Android/data/com.example.ai_poweredphotogallery/files/Media/
```

Subfolders become albums:

```text
Media/Travel/a.jpg
Media/Memes/b.png
```

## Build

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:assembleDebug
```

Gradle may need sandbox escalation because it writes to `C:\Users\lll\.gradle`.

## Git

```text
branch: main
remote: origin https://github.com/OKut056/AI-PoweredPhotoGallery.git
```

Do not reset, force-push, rewrite history, or discard user changes unless explicitly asked.
