# Changelog

All notable changes to this project will be documented in this file.

- `Additions` - New features or functionality.
- `Changes` - Changes to existing behavior, UI, or functionality.
- `Fixes` - Bug fixes.
- `Other` - Technical changes, dependency updates, refactoring, build changes, or changes that do not fit the other categories.

## [Unreleased]
### Additions
* Read synced lyrics embedded in audio tags (`LYRICS`, `LYRICSENG`, `UNSYNCEDLYRICS`, `SYNCEDLYRICS`, `USLT`, `SYLT`). A sidecar `.lrc` file is still used when no embedded lyrics are found.
* TTML lyrics (embedded or as a `.ttml` file next to the song) with word-level timing, translation and transliteration. TTML parsing is adapted from shouryadixitisverycool/Flamingo.
* Thin glow on the active lyric, stronger on words that are held for a long time. The held-word idea comes from kushagrasinghx/BitChord.
### Changes
* Removed the Xposed-based status bar lyrics feature. Pear no longer needs root or Xposed.
* Renamed the app from Flamingo to Pear.
* Transliteration now shows above the lyric line, and translation below it.
* In TTML lyrics, transliteration now sits above each word and follows the word highlight.
* The word highlight in lyrics moves smoothly from word to word. Only the current line is white, and played lines fade back to grey.
* Changed the application ID to `rotatsu.yos.music.player`. Nightly builds use `rotatsu.yos.music.player.nightly` and show as "Pear Nightly", so they can be installed next to the release app.
* Calmer Now Playing background: lower saturation and a slightly darker tone.
* Longer top handle in Now Playing, placed a little higher.
* The playback progress bar is thinner and gets thicker while you drag it.
* Dragging the playback bar is relative: it starts from the current position and moves from there, instead of jumping to the touch point.
### Other
* Updated TagLib to 1.0.0-alpha25.
