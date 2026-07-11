# Testing Plan

Last updated: 2026-07-12

## Start Here

Read `PROJECT_CONTEXT.md`, then this file before testing.

The current priority is core gallery quality: import, duplicate detection, albums, video playback, delete/restore, search, and selection behavior. AI/chat/feed features are deferred.

## What Codex Can Test Automatically

Run these before committing:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Do not run connected instrumentation tests as a routine check. They may reinstall or
uninstall the app and remove the app-specific `Media` workspace. Only run this command
on a disposable emulator with no user media:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Gradle may need permission to write to `C:\Users\lll\.gradle`.

## Current Automated Coverage

`app/src/test/java/com/example/ai_poweredphotogallery/AiSearchTest.kt`

- metadata search matches album and Chinese video terms
- blank query sorts newest first

`app/src/androidTest/java/com/example/ai_poweredphotogallery/ExampleInstrumentedTest.kt`

- app package context loads
- deleting an album moves media to recent deleted and restore recreates the missing album
- restore respects recorded original album path
- deleting a top-level album removes nested empty folders after moving media to trash
- moving a root media file into an album physically moves the file
- importing a renamed duplicate skips it by SHA-256 content hash and reports `duplicateSkipped`
- Java-hash-colliding paths still receive distinct media IDs
- stale trash-index entries are pruned without hiding the original media
- importing media without embedded metadata preserves the source file time
- a new file at a deleted item's original path remains visible
- hidden, internal, and reserved media-category album names are rejected

`app/src/androidTest/java/com/example/ai_poweredphotogallery/ImageDetailsInstrumentedTest.kt`

- image details display a known workspace file name, size, resolution, and path
- the top-right info button opens and closes the details card
- horizontal swipe changes the current image

## Manual Device Smoke Test

Use a real device or emulator after automated tests pass.

1. Install and open the app.
2. Import several image files.
3. Import the same files again under different names.
4. Confirm the toast says imported count and duplicate skip count separately.
5. Confirm the grid does not show duplicate copies.
6. Import a folder with nested child folders.
7. Confirm root files appear in all items and top-level child folders become albums.
8. Open a video and verify thumbnail, duration overlay, playback, pause/play, and progress drag.
9. Delete media, then restore it from recent deleted.
10. Permanently delete a media item from recent deleted only after confirmation.
11. Open an image, swipe left/right, and confirm the bottom thumbnail follows.
12. Toggle the top-right image info button and verify name, time, size, resolution, and path.
13. Search by file name, album name, path fragment, and media type terms such as `video`.

## Test Media Setup

Push media into the app workspace if direct import is not enough:

Use only disposable copies. Uninstall, clear-data, and connected instrumentation testing
can remove the entire app-specific workspace.

```powershell
adb shell mkdir -p /sdcard/Android/data/com.example.ai_poweredphotogallery/files/Media
adb push "D:\some-local-folder\." /sdcard/Android/data/com.example.ai_poweredphotogallery/files/Media/
```

Subfolders become albums:

```text
Media/Travel/a.jpg
Media/Memes/b.png
```

## Next Tests To Add

Add the smallest useful tests first:

- hash index reuses cached hashes when size and modified time are unchanged
- hash index recalculates when an existing file changes
- sort/filter behavior once real sorting/filtering is implemented
