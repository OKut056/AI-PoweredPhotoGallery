# Next Session Notes

Last updated: 2026-07-10

## Start Here

Read `PROJECT_CONTEXT.md`, then inspect current code. Do not infer product rules from old chat memory.

## Current Status

- Workspace-only gallery: `Android/data/com.example.ai_poweredphotogallery/files/Media/`.
- Folder picker is import-only. It copies selected-folder contents into `Media`; it does not create the selected parent folder.
- Child folders inside the selected folder are preserved and become albums if they are top-level folders under `Media`.
- Root-level imported images show in all items, not in more albums.
- Import uses SHA-256 duplicate detection and caches hashes in `context.filesDir/.media_index/hash_index.tsv`.
- Import messages now distinguish imported files, duplicate skips, and other skips.
- Import and trash paths are hardened: imports use temporary files and workspace-bound paths; trash indexes are atomic and self-heal stale entries.
- Imported media sorting preserves embedded capture/creation time when available, otherwise the source modified time; it no longer defaults to the import time.
- Media IDs use collision-free relative paths, all file mutations run off the UI thread, and concurrent storage mutations are serialized.
- Video durations are cached by path/size/mtime. Hidden folders are skipped during traversal, and failed video players release their resources.
- The non-functional sort sheet and fake fast-scroll handle were removed; add them back only with real behavior.
- Delete/restore/permanent delete are app-controlled inside `Media/.recent_deleted`.
- Video import, scanning, duration overlays, thumbnails, and aspect-fit TextureView/MediaPlayer playback with draggable progress controls are implemented inside the same `Media` tree.
- Search buttons open a separate search page. Search currently matches file name, album, path, label, and media type metadata only.
- Selection mode stays active until cancelled or system back exits it first.
- Album selection shows a muted total media count for selected albums.
- The bottom `AI` tab is intentionally blank; AI/chat/feed features are deferred until the core gallery is solid.

## Next Useful Tasks

- Add a media info view: file name, path, size, resolution, duration, hash, created/modified time.
- Improve import UX with progress or failure detail if it becomes the next pain point.
- Make sorting/filtering real: time, name, size, media type.
- Continue polishing delete/restore conflict behavior before adding AI or feed features.

## Before Coding

```powershell
git status --short
```

Do not revert unrelated user changes.
