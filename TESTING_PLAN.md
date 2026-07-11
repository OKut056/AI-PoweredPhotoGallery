# Testing Plan

Last updated: 2026-07-11

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

If an emulator or device is connected, also run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Gradle may need permission to write to `C:\Users\lll\.gradle`.

On this Windows setup, `:app:testDebugUnitTest` can fail before running assertions if the Java path with `Program Files` is split incorrectly by the Gradle test executor. Treat that as an environment failure, not an app test failure, unless the test report shows assertion failures.

The current machine has a malformed quoted Java entry in `PATH`. Run Gradle tests with a process-local cleanup; this does not change system settings:

```powershell
$env:PATH=$env:PATH.Replace(';"D:\Program Files\java\jdk-21\bin;D:\Program Files\java\jdk-21\jre\bin;"','')
$env:CLASSPATH=''
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

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
11. Search by file name, album name, path fragment, and media type terms such as `video`.

## Test Media Setup

Push media into the app workspace if direct import is not enough:

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
- media info page displays path, size, type, duration/dimensions, and hash
- sort/filter behavior once real sorting/filtering is implemented
