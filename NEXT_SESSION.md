# Next Session Notes

Last updated: 2026-07-05

## Current Status
- No known half-finished code change from this handoff update.
- `PROJECT_CONTEXT.md` is the durable project plan and current-state document.
- Current app code is still the local-folder gallery prototype, now split into `MainActivity.kt`, `GalleryModels.kt`, `GalleryStorage.kt`, and `GalleryScreens.kt`.
- Selection and viewer delete now show a confirmation dialog. Delete moves photos into `.recent_deleted` when possible; if direct movement fails, an app-private logical recent-deleted index hides the photo from the main grid and shows it in recent-deleted. Restore now moves the file to the recorded original path whenever the current physical path differs, including logical-delete fallback cases where the file never entered `.recent_deleted`. If the original album folder is gone, restore asks whether to put the photos in the root GalleryTest folder. Permanent delete is a red placeholder only.
- Photo selection move is implemented for Photos and Album Detail via an album picker. It now requires a real physical file move into the target `GalleryTest/<album>` folder; if direct movement fails, it reports 0 moved and does not use `move_index.tsv` to fake album membership.
- Album selection delete is implemented: selected folder albums move their photos to recent-deleted, record restore paths as `<album name>/<file name>`, then empty folders are removed when possible. If the same-name album is recreated before restore, photos restore into it; if not, restore asks before putting them in root `GalleryTest`. Creating an existing album now reports `œ‡≤·“—¥Ê‘⁄`.
- Git version control is now active on branch `main` with remote `origin` at `https://github.com/OKut056/AI-PoweredPhotoGallery.git`.
- The GitHub repo was created with an Android `.gitignore`; build outputs and local files should stay ignored.

## Continue From Here
- New sessions should read `PROJECT_CONTEXT.md`, then this file, then inspect `MainActivity.kt`, `GalleryStorage.kt`, `GalleryScreens.kt`, and `AndroidManifest.xml`.
- Before code changes, run `git status` and avoid mixing unrelated user changes into new work.
- Recommended next product task: add the simple settings page, or implement copy-to-album if you want to finish the remaining file action first.
- Do not add Domain layer yet; first keep shaping the Data/repository side. Add Domain when delete/restore/move/AI workflows coordinate multiple data sources or get reused across screens.
- Use normal Git flow after meaningful changes: `git status`, `git add .`, `git commit -m "..."`, `git push`.

## Watch Out
- Many buttons may still be placeholders even if the UI exists.
- Be careful with destructive file operations. The user may test with real pushed media.
- Do not bring back fake albums or fake thumbnails unless the user asks.
- Do not implement custom C/C++ media decoding/playback. For scale, prefer Android system media APIs, MediaStore, Coil, and Media3/ExoPlayer.
- Current direct file scanning and `BitmapFactory` thumbnail loading are acceptable for the prototype, but may need MediaStore/Coil before testing with large real libraries. Direct physical moves/deletes in the public `Pictures/GalleryTest` tree require all-files access in this prototype; a production version should use MediaStore `RELATIVE_PATH` and user-approved write requests instead.
- Do not rewrite Git history, force-push, reset, or discard changes unless the user explicitly asks.

## Last Verified
- `git status --short` was clean before these Markdown updates.
- `git remote -v` showed `origin https://github.com/OKut056/AI-PoweredPhotoGallery.git` for fetch and push.
- Current branch was `main`.
- Kotlin code was mechanically split by responsibility; behavior should be unchanged.
- `./gradlew.bat :app:compileDebugKotlin` passed after move-to-album and album-delete changes. On 2026-07-06, direct `adb shell am instrument` ran `ExampleInstrumentedTest` successfully: app context, physical album delete restore, logical-delete fallback restore, and physical move-to-album all passed. Gradle `connectedDebugAndroidTest` may fail on UTP dependency download/TLS; direct APK install plus `am instrument` avoids that network path.
