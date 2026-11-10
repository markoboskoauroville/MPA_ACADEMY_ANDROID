# MPA Academy (Mantra), Android

What it is: the Android app of the Mantreshwar Percussions Academy (mpa-academy.pages.dev). It holds the Exercise Lab's exercises as
collections (categories as in the Lab), played with the same drum and voice samples, speeds 16/1 to 16/32 (default 16/4), cells with
accents, numbers and spoons, the lit cell following the sound, notes. Teacher Mantreshwar, student Natasha, never "Marko" in the app.

Started 10.11.2026 (request in momentaryupdates.md). Version 1 holds one exercise: PH01 Basic Phrasing over Malfuf 1 (Lab kind
custom:PHRASING, base 20, 16 rows of 2 bars, each row twice). Data: android/app/src/main/assets/exercises.json, generated from
~/Developer/MPA/lessons/phrasing-malfuf.mjs. Add an exercise = add it to a category in that file (and its samples if new strokes).

Build: only GitHub Actions (.github/workflows/android.yml), on a push to main, release v<appVersion> (android/gradle.properties) Latest,
signed with the secret ANDROID_DEBUG_KEYSTORE_B64. Never gradlew on the Mac. Public and unpromoted: no README, no description.
Sound: the site's own samples, streamed through AudioTrack; the light follows playbackHeadPosition.

v2 (10.11.2026): `am start -n com.mantra.mpaacademy/.MainActivity --ez pin true` asks the launcher (Nova) to pin the icon to the home screen (ShortcutManager.requestPinShortcut, the launcher shows its own Add dialog). v3: own adaptive launcher icon (the academy's frame-drum mark from the site's favicon.svg on #0B0D10, plus a monochrome layer), used for the pinned shortcut too.

v4 to v12 (10.11.2026, requests 4 to 11 in momentaryupdates.md): icon = the website's icon-512.png (frame drum) as the adaptive launcher icon on #0B0D10; layout copied from the site (Barlow, Barlow Condensed, IBM Plex Mono from res/font, the house palette, code tag, pipes, pills, cells with accent bar, numbers above, spoons below, phrasing row labels "7+1", "Reverse dynamics:"). TELEPROMPTER is the default (site lessons.js play/moveTo/midScroll/clampTop, request 217-221): the reading line is the vertical middle of the scroll area; the play marker sits on the LINE being played, level with it, and the page scrolls (380 ms) so that line is on the reading line (clamped, so with few rows the rows stay still and only the marker moves); PROMPTER pill switches it off (then scroll only when the line is out of view). Each phrase = two lines of 8 cells (one bar per line); the big row label sits in the front slot on the other line than the marker and swaps with it. Top bar: MPA ACADEMY (tap = settings with the line "(c) 2026 Mantra Productions") | speeds 16/1..16/32 (default 16/4) | LOOPS - n + (live, 0 = forever, remembered) | PORTRAIT/LANDSCAPE (rotates; default follows the device). Pin extra: `am start -n com.mantra.mpaacademy/.MainActivity --ez pin true`. Website side: request 227 made phrasing rows wrap after 8 cells (.cells.phr, app.css v201); the site deploy via GitHub Actions fails (account billing), use ~/Developer/MPA/website/deploy.sh --site-only.

## v13 to v17 (10.11.2026, master session, no agents)
- v13/v14: only the current phrase (two lines) on screen; its label (7+1, 3+5 ...) next to the title; play continues in sequence from the phrase shown (Player startRow).
- v15: the big label beside the cells removed (the title shows it).
- v16: every control in the top frame: exercise name as title + phrase label, speeds, loops, VOICE | DRUM; the lower frame shows only the exercise and the play button. VOICE by default, prompter always on (no switch), notes and base speed removed from view.
- v17: no exercise code (PH01) anywhere.
- Checked on the Pixel 7 (v17, landscape): home without code; player top frame and lower frame as above. Phrase advance checked on the emulator at 16/32, loops 1 (muted). Not checked: sound by ear; in landscape the second line sits at the bottom edge and needs the prompter scroll while playing.
