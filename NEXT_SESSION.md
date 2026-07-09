# Next Session Notes

Last updated: 2026-07-09

## Start Here

Read `PROJECT_CONTEXT.md`, then inspect current code. Do not infer product rules from old chat memory.

## Current Status

- Workspace-only gallery: `Android/data/com.example.ai_poweredphotogallery/files/Media/`.
- Folder picker is import-only. It copies selected-folder contents into `Media`; it does not create the selected parent folder.
- Child folders inside the selected folder are preserved and become albums if they are top-level folders under `Media`.
- Root-level imported images show in all items, not in more albums.
- Delete/restore/permanent delete are app-controlled inside `Media/.recent_deleted`.
- Video import, scanning, duration overlays, thumbnails, and aspect-fit TextureView/MediaPlayer playback with draggable progress controls are implemented inside the same `Media` tree.

## Next Useful Tasks

- Add local AI indexing/search for workspace files.
- Replace placeholder actions only when requested.

## Before Coding

```powershell
git status --short
```

Do not revert unrelated user changes.
