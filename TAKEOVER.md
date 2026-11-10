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
