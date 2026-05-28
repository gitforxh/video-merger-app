# Video Merger App

A simple Android app that lets you pick multiple videos and merge them into one MP4 in sequence.

## What it does

- Select up to 20 videos from the system picker
- Keep their selected order
- Export one merged MP4 to `Movies/VideoMergerApp`

## Notes

- This is intentionally simple, not fully polished.
- It uses AndroidX Media3 Transformer for sequential composition.
- In a real production version, I would add:
  - drag-to-reorder
  - background export with notifications
  - better progress reporting
  - validation for mismatched codecs/resolutions
  - cancel / retry handling

## Build

Open `video-merger-app` in Android Studio Hedgehog+ / Iguana+ and let Gradle sync.
