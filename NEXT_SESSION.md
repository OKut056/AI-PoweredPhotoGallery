# Next Session Notes

Last updated: 2026-07-08

## Start Here

Read `PROJECT_CONTEXT.md`, then inspect current code. Do not infer product rules from old chat memory.

## Current Status

- Workspace-only gallery: `Android/data/com.example.ai_poweredphotogallery/files/Media/`.
- Folder picker is import-only. It copies selected-folder contents into `Media`; it does not create the selected parent folder.
- Child folders inside the selected folder are preserved and become albums if they are top-level folders under `Media`.
- Root-level imported images show in all items, not in more albums.
- Delete/restore/permanent delete are app-controlled inside `Media/.recent_deleted`.
- Video import is allowed, but video display/playback is still not implemented.

## Next Useful Tasks

- Add video scanning/playback in the existing `Media` tree.
- Add local AI indexing/search for workspace files.
- Replace placeholder actions only when requested.

## Before Coding

```powershell
git status --short
```

Do not revert unrelated user changes.
