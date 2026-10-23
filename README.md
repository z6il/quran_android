<div align="center">

<img src="https://raw.githubusercontent.com/z6il/quran_android/main/app/src/madani/res/drawable-xxhdpi/icon.png" alt="Quran app icon"/>

# Quran (z6il edition)

[![Latest release](https://img.shields.io/github/v/release/z6il/quran_android?include_prereleases&sort=semver)](https://github.com/z6il/quran_android/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/z6il/quran_android/total?logo=Github)](https://github.com/z6il/quran_android/releases)

A Madani mushaf Quran app for Android, built on the open-source
[Quran for Android](https://github.com/quran/quran_android), with Abdullah Al-Mousa
(عبدالله الموسى) added as a reciter.

**[Download the latest APK](https://github.com/z6il/quran_android/releases/latest)**

</div>

## About this fork

This is a fork of [quran/quran_android](https://github.com/quran/quran_android), the
open-source code behind the Quran for Android app. All of the reading, search,
translation and audio features come from that project and its contributors. This
fork adds a reciter and fixes on top of it.

It installs as its own app (`com.z6il.quran`), so it sits alongside the official
Quran app instead of replacing it. If you want the official app, get it from
[Google Play](https://play.google.com/store/apps/details?id=com.quran.labs.androidquran)
or the [upstream releases](https://github.com/quran/quran_android/releases/latest).

## What's different from the original

### New reciter: Abdullah Al-Mousa (عبدالله الموسى)

Abdullah Al-Mousa is the reciter this fork adds. He plays gapless (whole surahs, with
highlighting that follows the verse being recited).

- Audio comes from [mp3quran.net](https://mp3quran.net), and verse timings from
  mp3quran's timing data.
- **Surah 17:** mp3quran replaced its recording in September 2026 without updating
  the timings, so this fork plays the earlier recording, which the timings were made
  for. It's re-hosted under
  [releases](https://github.com/z6il/quran_android/releases/tag/audio-mousa-v1). Its
  timings were cross-checked against two independent sources.
- **Surah 5:** mp3quran also replaced this recording, and the earlier one is missing
  verses 11–17, so it can't be used. This surah plays mp3quran's current recording,
  re-timed against the audio with forced alignment. A few verse changes may be off by
  a second or two.
- The timing database is published under
  [releases](https://github.com/z6il/quran_android/releases/tag/audio-db-v1), and the
  app downloads it from there.

### Reciters from the official app

The open-source code includes only some of the official app's reciters; the full list
in the Play Store app comes from a part of its code that isn't public. This fork
restores these official-app reciters, using the same public audio sources:

**Gapless:**

| Reciter | Reciter |
| --- | --- |
| Muhammad Siddiq Al-Minshawi | Mahmoud Khalil Al-Husary |
| Mishary Rashid Al-Afasy | Maher Al-Muaiqly |
| Saood Ash-Shuraym | Abu Bakr Ash-Shatri |
| Yasser Ad-Dussary | Nasser Al-Qatami |
| Fares Abbad | Hani Ar-Rifai |

**Verse by verse:**

| Reciter | Reciter |
| --- | --- |
| Abdullah Basfar | Abd Al-Basit Abd As-Samad (murattal) |
| Abd Al-Basit Abd As-Samad (mujawwad) | Abdur-Rahman As-Sudais |
| Saad Al-Ghamdi | Ali Al-Hudhaify |
| Muhammad Ayyoub | Muhammad Jibreel |
| Mohammad Al-Tablawy | Muhammad Siddiq Al-Minshawi (mujawwad) |
| Mahmoud Khalil Al-Husary (mujawwad) | |

Gapless verse timings were checked against the real audio lengths, and reciters
whose timings did not match were left out so highlighting does not drift.

### Fixes

- **Audio update checks.** A filter in `AudioUtils.kt` did the opposite of what its
  comment said, so the background audio update check skipped exactly the
  verse-by-verse reciters it should have covered.
- **Search suggestions** had the official app's package name hard-coded, which broke
  them in any build with a different package name, including upstream debug builds.
  They now follow the package name the app is built with.
- **Launcher shortcuts** open this app rather than the official one.

### Build

- Separate application ID (`com.z6il.quran`).
- Release builds are made without Firebase (`-PdisableFirebase`), so this app sends
  no analytics or crash reports.

## Building

You need JDK 21 to build (JDK 25 to run the unit tests) and the Android SDK.

```sh
# debug build, installs as com.z6il.quran.debug
./gradlew assembleMadaniDebug

# signed release build, without Firebase
./gradlew -PdisableFirebase assembleMadaniRelease
```

Release signing reads `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`
from Gradle properties, for example in `~/.gradle/gradle.properties`. Keep those out
of the repository.

In Android Studio, open the top-level directory and choose the `madaniDebug` build
variant.

## Keeping up with upstream

This fork tracks `quran/quran_android` as the `upstream` remote. To pull in new
upstream work:

```sh
git fetch upstream
git merge upstream/main
```

Conflicts are most likely in
`pages/madani/src/main/java/com/quran/data/page/provider/madani/MadaniPageProvider.kt`,
where the reciter list lives.

## Non-profit use

From the original project: Quran for Android costs money to run. The pages, audio
and translations are hosted on servers that volunteers pay for every month, and the
data itself is the work of scholars, organizations and reciters who share it freely
for the benefit of the ummah.

This fork follows the same rule. It has no ads or in-app purchases, and its code may
not be used to make money from the app. Please keep any use non-profit.

## License

The code is licensed under [GPL 3](LICENSE), like the original, which means any
modified version must also be open source. The data (pages, audio, translations)
is under its authors' own licenses, typically
[CC BY-NC-ND](https://creativecommons.org/licenses/by-nc-nd/2.0/).

## Credits

All credit for the app goes to the [Quran for Android](https://github.com/quran/quran_android)
project and its [contributors and translators](CONTRIBUTORS.md). May Allah reward them.

- Madani page images from the [quran images project](https://github.com/quran/quran.com-images).
- Qaloon images used with permission of Nous Memes Editions Et Diffusion (Tunisia).
- Naskh images used with permission of SHL Info Systems.
- Translation, tafsir and Arabic data from [quranenc](https://quranenc.com) and
  [King Saud University](https://quran.ksu.edu.sa); a few translations from
  [tanzil](http://tanzil.net).
- Recitations from [quranicaudio.com](https://quranicaudio.com),
  [everyayah.com](https://everyayah.com) and [mp3quran.net](https://mp3quran.net).

The original project's open-source dependencies include AndroidX, Kotlin, Material
Components, OkHttp, RxJava, Moshi, Dagger, Timber, dnsjava and NumberPicker.
