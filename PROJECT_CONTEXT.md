# AI-Powered Photo Gallery Project Context

> Handoff document for future Codex sessions. Read this file first, then inspect the current code. Do not infer product requirements from code alone.

Last updated: 2026-07-05

## 1\. Product Goal

This repository is an Android-first smart photo gallery app. The near-term goal is to imitate the core browsing experience of the iQOO/vivo OriginOS gallery, then add local indexing and AI features.

The app should feel like a system gallery replacement, not an AI demo.

Core product direction:

* Android first. Windows can wait.
* Main tabs: Photos, Albums, AI.
* UI reference: iQOO/vivo OriginOS 5 gallery behavior and screenshots provided by the user.
* Do not use vivo/iQOO proprietary assets, fonts, logos, or exact icon files.
* Current implementation may approximate system icons with Compose/Material icons or text symbols.
* The long-term app should not modify original media unless the user explicitly performs a destructive action.

Original requirement document:

```text
D:\\source\\智能相册软件需求文档.docx
```

Important local UI reference screenshots:

```text
E:\\test\\照片1.jpg
E:\\test\\照片2.jpg
E:\\test\\照片3.jpg
E:\\test\\相册1.jpg
E:\\test\\相册2.jpg
E:\\test\\相册3.jpg
E:\\test\\相册4.jpg
E:\\test\\查看图片1.jpg
E:\\test\\查看图片2.jpg
```

`查看图片1.jpg` is the visible-controls photo viewer reference. `查看图片2.jpg` is the hidden-controls full black viewer reference.

## 2\. Current Technical Shape

Project type:

* Android Kotlin project.
* UI is Jetpack Compose. The earlier single-file prototype has been mechanically split by responsibility.
* Main app code is currently split across:

```text
app/src/main/java/com/example/ai\_poweredphotogallery/MainActivity.kt          # app shell/state routing
app/src/main/java/com/example/ai\_poweredphotogallery/GalleryModels.kt         # constants/models
app/src/main/java/com/example/ai\_poweredphotogallery/GalleryStorage.kt        # test-folder scanning/loading/recent-deleted moves
app/src/main/java/com/example/ai\_poweredphotogallery/GalleryScreens.kt        # Compose screens/components
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/ai\_poweredphotogallery/ui/theme/Theme.kt
```

Current dependencies/architecture:

* No Room database yet.
* No Coil dependency yet.
* No MediaStore query layer currently in use. The prototype currently requests all-files access and uses direct `File` moves for `/sdcard/Pictures/GalleryTest`; production/gallery-scale storage should move to MediaStore `RELATIVE_PATH` updates instead of relying on all-files access.
* No Media3/ExoPlayer video layer yet.
* No AI/network layer yet.
* No Domain layer yet. Add it only when business workflows coordinate multiple data sources, are reused by multiple screens, or ViewModel/app state starts owning multi-step rules. Likely future candidates: delete/restore, move, AI tagging, cleanup suggestions.
* Images are decoded directly from files with `BitmapFactory` / `ImageBitmap`.
* Some Chinese display strings in Kotlin are written as Unicode escapes to avoid Windows/editor encoding issues.
* Do not plan to write custom C/C++ image/video decoding code. For production-scale media performance, prefer Android system media APIs and mature libraries: MediaStore for media discovery, Coil for image loading/caching, and Media3/ExoPlayer for video playback.

## 3\. Test Media Directory

The app currently reads images directly from this emulator/device path:

```text
/sdcard/Pictures/GalleryTest/
```

Code constants:

```kotlin
private const val GalleryRootName = "GalleryTest"
private const val AllAlbumName = "\\u5168\\u90e8\\u9879\\u76ee" // 全部项目
```

How to prepare emulator test images from the PC:

```powershell
adb shell mkdir -p /sdcard/Pictures/GalleryTest
adb push "D:\\some-local-folder\\." /sdcard/Pictures/GalleryTest/
```

Subfolders under `/sdcard/Pictures/GalleryTest/` become albums. For example:

```text
/sdcard/Pictures/GalleryTest/Travel/a.jpg
/sdcard/Pictures/GalleryTest/Memes/b.png
```

The Albums page should show `全部项目`, `Travel`, and `Memes`.

Currently scanned image extensions:

```text
jpg, jpeg, png, webp, gif
```

Videos are not implemented yet.

## 4\. Current Implemented Behavior

### App shell

* Bottom navigation has three tabs: Photos / Albums / AI.
* Bottom navigation hides when:

  * an album detail page is open,
  * a placeholder page is open,
  * the photo viewer is open,
  * a selection action bar is active.

### Permission and loading

* Manifest includes `READ\_MEDIA\_IMAGES` and `MANAGE_EXTERNAL_STORAGE` for the current direct-folder prototype.
* On launch, the app requests image permission.
* The app creates `/sdcard/Pictures/GalleryTest/` if possible.
* After permission is granted, it recursively scans `GalleryTest`.
* Empty state tells the user to push images to `/sdcard/Pictures/GalleryTest`.

### Photos page

* Shows real images from `GalleryTest`, recursively.
* Groups images by date using file modified time.
* Opens the photo viewer on tap.
* Long press enters selection mode.
* Selection mode exits automatically when no item remains selected.
* When selected, the bottom nav moves away and `SelectionActionBar` appears.
* Selection delete asks for confirmation, then moves selected photos into `/sdcard/Pictures/GalleryTest/.recent_deleted/` when possible and records original relative paths for restore. If a photo is displayed under an album, restore targets `<album name>/<file name>` even when a logical move index is involved. If direct file movement fails under scoped-storage constraints, the app records a logical recent-deleted entry in app-private storage and filters it out of the main grid.
* Selection move opens an album picker and physically moves selected photos into the chosen `GalleryTest` subfolder. If physical movement fails, the action reports 0 moved; do not fake a move with `move_index.tsv`. Share / more are still placeholders.
* Pinch gesture changes grid density.
* Right-side fast-scroll handle position is tied to grid scroll progress.
* `BoxWithConstraints` is intentionally used to know available height for the fast-scroll offset.

### Albums page

* Shows `全部项目` plus real subfolders under `GalleryTest`.
* Does not intentionally add fake system folders anymore.
* Album cover uses the first real photo if present.
* Empty folders show color fallback covers.
* Long press enters album selection mode.
* Album selection delete moves photos from selected album folders into recent-deleted and records the original restore path as `<album name>/<file name>`. Empty folders are removed when possible; non-empty folders may remain if direct file movement is blocked and logical recent-deleted is used.
* Add button creates a subfolder under `/sdcard/Pictures/GalleryTest/<album name>` and reports when the album already exists.
* `清理建议` opens a blank placeholder page.
* `最近删除` opens a real recent-deleted page and shows the current deleted-photo count.
* Three-dot menu exposes `设置`, which opens a blank placeholder page.

### Album detail page

* `全部项目` opens all photos.
* Other albums open photos whose parent folder matches the album name.
* Tapping a photo opens the viewer scoped to that album.
* Long press enters selection mode.
* Selection delete moves selected photos into recent-deleted.
* Selection move opens an album picker and physically moves selected photos into the chosen album. No logical-move fallback should be used; folder albums represent real subfolders under `GalleryTest`.
* Sort sheet exists visually, but sorting is not wired to real data yet.

### Photo viewer

Implemented from the user-provided `查看图片1.jpg` and `查看图片2.jpg` references.

* Tap a photo in Photos or Album Detail to open.
* Image immediately fits inside a full-screen black image area.
* Controls are overlays; they must not make the image become smaller.
* Single tap toggles top/bottom controls.
* Controls visible:

  * white top bar with back, date/time, favorite placeholder, info placeholder,
  * white bottom thumbnail strip,
  * bottom actions: share/edit/more placeholders; delete asks for confirmation and moves the current photo to recent-deleted.
* Controls hidden:

  * pure black background,
  * fitted image only.
* Double tap zooms/restores.
* Pinch zoom and pan are supported.
* Thumbnail strip can change the current image.

### AI page

* Placeholder only.
* Future scope: chat, image search, edit image, generate image, batch tagging.
### Recent deleted page

* Shows photos moved into `/sdcard/Pictures/GalleryTest/.recent_deleted/` plus logical recent-deleted entries recorded in app-private storage when direct movement failed.
* Restore moves selected photos back to their recorded original relative path under `GalleryTest`.
* If the original album folder no longer exists, restore asks whether to place the photos in the root `GalleryTest` folder. If the user recreates a same-name album before restoring, the photo restores into that recreated folder. This also applies to logical recent-deleted entries where the physical file never moved into `.recent_deleted`; restore moves the file from its current physical path to the recorded original path when those paths differ.
* If the original filename already exists, restore writes a numbered filename instead of overwriting.
* Permanent delete is intentionally a red placeholder button only.

## 5\. Important Non-Implemented Work

These are still placeholders or missing:

* Real copy/share/edit/favorite/info behavior.
* Permanent delete from recent-deleted is placeholder only.
* Real cleanup suggestions.
* Real settings page.
* Real import picker for single image or folder.
* Real scoped storage handling for Android 11+ beyond the current direct test directory.
* MediaStore integration.
* Database/index layer.
* AI tagging and AI configuration.
* Local search.
* Video scanning and playback.
* Persisted sorting/filtering.
* Broader tests. Current instrumentation coverage includes app context plus recent-deleted restore for physical album deletion and logical-delete fallback.

Do not assume a feature is implemented because a button exists. Many buttons currently show a toast or open a blank page.

## 6\. Recommended Next Tasks

The next useful development work should continue from the current app state, not from the older fake-data plan.

Priority order:

1. Add a simple settings page:

   * show current import root `/sdcard/Pictures/GalleryTest/`,
   * add refresh/rescan,
   * optionally show app/version/debug info.
2. Add import/picker flow:

   * first minimal path can stay test-oriented,
   * later use Android Storage Access Framework or MediaStore properly.
3. Add video support only after image browsing is stable.
4. Add Room/local index only when real file operations and browsing behavior are stable.
5. Add AI tagging after the local index exists.

## 7\. Build and Verification

Useful commands:

```powershell
.\\gradlew.bat :app:compileDebugKotlin
.\\gradlew.bat :app:assembleDebug
```

Last known good state before this document update:

* `:app:compileDebugKotlin` passed.
* `:app:assembleDebug` passed.
* Debug APK path:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Codex sandbox note:

* Gradle writes to user-level caches such as `C:\\Users\\lll\\.gradle`.
* In this environment, Gradle may be blocked in the normal sandbox.
* If a Gradle command fails because of sandbox permissions, rerun it with escalation instead of changing project files.

## 8. Version Control

Git version control has been initialized for this project.

Current Git state:

```text
branch: main
remote: origin https://github.com/OKut056/AI-PoweredPhotoGallery.git
```

The GitHub repository was created with an Android `.gitignore`. Build outputs and local machine files such as `app/build/`, `.gradle/`, `.idea/`, `.kotlin/`, and `local.properties` should not be committed.

Useful daily Git flow:

```powershell
git status
git add .
git commit -m "Describe the change"
git push
```

For larger feature work, prefer a feature branch from `main`, for example:

```powershell
git switch main
git pull
git switch -c feature/settings-page
```

Then commit and push that branch:

```powershell
git add .
git commit -m "Add settings page"
git push -u origin feature/settings-page
```

Keep `main` as the stable branch that should compile.

## 9\. Development Constraints for Future Sessions

* Start by reading this file, then inspect `MainActivity.kt`, `GalleryModels.kt`, `GalleryStorage.kt`, and the screen file relevant to the task.
* Do not bring back fake albums or fake thumbnails unless the user asks for mock data.
* Keep changes small and product-visible.
* Prefer Compose/native Android APIs before adding dependencies.
* If adding Coil later, use it only to simplify real image/video loading and caching.
* If adding Room later, use it only once the app needs persistent indexing/search/AI metadata.
* If adding video later, use Android media APIs / Media3 rather than implementing playback or codecs manually.
* Do not build custom native image/video codecs. The app should rely on Android, device hardware codecs, and mature libraries for decoding/rendering.
* Be careful with destructive file operations. The user is testing with real pushed media.
* If web references are needed for OriginOS/iQOO UI, search directly. If nothing reliable is found, say so and rely on user screenshots.
* Current screenshots from the user should be treated as stronger UI references than guesses from web results.

## 10\. Quick Mental Model

This is currently a functional local-folder gallery prototype:

```text
PC media files
  -> adb push
  -> /sdcard/Pictures/GalleryTest/
  -> app scans files directly
  -> Photos page / Albums page / Album detail / Photo viewer
```

The app is not yet a full Android gallery replacement. The next stage is making the existing real-file UI actions actually operate on those files in a reversible, testable way.


## 11. Cross-Session Handoff Rules

This section exists because the user may switch between multiple Codex conversations. Every future session should follow these rules.

### Read order for every new session

1. Read `PROJECT_CONTEXT.md` first.
2. Read `NEXT_SESSION.md` if it exists.
3. Inspect the current code, especially `MainActivity.kt`, `GalleryStorage.kt`, `GalleryScreens.kt`, and `AndroidManifest.xml`.
4. Only then decide the next change.

Do not rely on chat memory from another conversation. Treat the Markdown files and current code as the source of truth.

### When to update Markdown

Update `PROJECT_CONTEXT.md` when a change affects durable project truth, for example:

* a feature moves from placeholder to implemented,
* the next-task priority changes,
* a new architectural decision is made,
* the test media path, permissions, or build flow changes,
* a previous assumption becomes wrong.

Do not update `PROJECT_CONTEXT.md` for tiny code cleanup, formatting, or a failed experiment that was reverted.

### Temporary handoff file

Use `NEXT_SESSION.md` for short-lived reminders to the next conversation.

Create or update it when work stops with unfinished context, for example:

* a task is half implemented,
* a bug is known but not fixed,
* a command failed and still matters,
* the next step is obvious from the current attempt but not obvious from code alone.

If there is no active unfinished work, `NEXT_SESSION.md` should say that clearly.

Future Codex sessions should prefer this format:

```markdown
# Next Session Notes

Last updated: YYYY-MM-DD

## Current Status
- ...

## Continue From Here
- ...

## Watch Out
- ...

## Last Verified
- command/result
```

### Git/GitHub guidance

Git is active and connected to GitHub, but it does not replace these Markdown handoff files.

* Use Git commits to record exact code changes.
* Use GitHub for backup, sync across machines, and easier review.
* Use `PROJECT_CONTEXT.md` to record product intent and decisions that are not obvious from code.
* Use `NEXT_SESSION.md` to record temporary in-progress state between conversations.
* Before changing code, check `git status` so user changes are not accidentally mixed into unrelated work.
* Do not rewrite history, force-push, reset, or discard user changes unless the user explicitly asks.

### Language preference

The user prefers Chinese conversation. Future assistant responses should use Chinese unless the user asks otherwise. Code identifiers can stay English.
