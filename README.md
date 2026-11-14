# Steady

A personal pacing app for long COVID and ME/CFS. The long-term goal: warn you 1–2 days before a
PEM crash, so you can rest before it arrives, with as little input from you as possible.

The research behind it: [PEM Early Warning Research](https://claude.ai/artifact/8qfGbYRujD75u8iQG7d2mt)
(private report, 10 November 2026).

**Not a medical device.** It is a personal tool that learns your own pattern.

**Using it?** Read [GUIDE.md](GUIDE.md): what to do each day, why, for how long, and when to check in.
The same guide is in the app: *Your plan: what to do and why*.

## Phase 1: Learn (0.1.0 to 0.3.0)

Steady gives no warnings yet. It collects the data that warnings need, and answers one question:
*does my body show anything in the days before a crash?*

Each day:

| When | What you do | Effort |
|---|---|---|
| Morning | Wet the Polar H10, put it on, lie down, tap **Start morning reading**. Steady beeps, measures 60 s lying, beeps twice for you to stand, measures 60 s standing, and beeps when done. | ~2½ min |
| Evening | Tap one button on the notification: **Good, Okay, Bad, Crash**. | 1 tap |
| All day | Nothing. The phone counts steps. | none |

What it measures:

- **HRV** (RMSSD from the H10's beat-to-beat intervals, lying still) and **resting heart rate**.
- **Rise on standing**: mean heart rate over the last 30 s standing minus resting heart rate.
- **Steps** from the phone's own step counter.
- **Your rating**. Crash ratings are the labels that teach the app your pattern.

What it shows:

- **Learning your normal**: progress to 14 good mornings.
- **Trends**: each signal with your normal range as a band, crash days in red, bad days in amber.
- **Before a crash**: once 2 crashes are logged, plain sentences about how each signal compared
  with ordinary days, 1–3 days before a crash started.
- **Export**: three CSV files (days, readings, raw beats). Everything else stays on the phone.

## Also in 0.3.0

- **Widget** (`Status.kt`, `Today.kt`): up to three lines of what needs doing (morning reading,
  rating, a check-in or update). Settings → Widget → *Add to home screen*. Redrawn on every data
  change, when the app opens, and by alarms at midnight, noon and 4 pm.
- **Check-in notification**: one notification when a check-in becomes due (once each).
- **E-ink look** (Settings → Look), after focus-tracker's E-ink theme: black on white, 3 dp
  borders, no colour (crash days hatched densely, bad days lightly, your normal as dashed lines,
  Crash marked ⚠ with a heavy border), no ripples or spinners, bold to-dos in the widget, and the
  reading countdown moves in 5-second steps so the panel redraws less. The beeps stay: they are
  how the reading works with your eyes closed.
- **Android 11** (minSdk 30) for the Hisense A9: the old Bluetooth permissions and Location for
  scanning there.

## How it works

- `analysis/Hrv.kt`: artefact rejection (out of 300–2000 ms, or more than 20% off the median of its
  neighbours), RMSSD, mean and peak heart rate, reading quality.
- `analysis/Patterns.kt`: your normal = median and robust spread (1.4826 × MAD) of the previous 60
  days, crash days left out. Crash start = a crash day after 2 non-crash days. Lead-up = mean
  z-score 1, 2 and 3 days before each crash start, against ordinary days.
- `analysis/Insights.kt`: turns the lead-up into sentences.
- `sensor/`: the H10 via the standard Bluetooth heart-rate service (0x180D / 0x2A37), which carries
  RR intervals. Polar's own SDK needs Android 13, and this phone runs Android 12. Also a demo strap.
- `ReadingSession.kt`: settle 20 s → lying 60 s → standing 60 s, with beeps and buzzes.
- `data/Store.kt`: one SQLite file. Raw beats are kept so readings can be re-analysed later.
- `reminders/`: the evening rating notification (four buttons) and an optional morning nudge.
- `steps/`: the phone step counter, read every 15 minutes by WorkManager.

## Build

    source tools/env.sh
    gradle testDebugUnitTest      # analysis, parser and step tests
    gradle assembleDebug
    gradle assembleRelease        # signed if a key is set up (STEADY_SIGNING, see app/build.gradle.kts)

## Updates and releases

Steady checks GitHub for a newer release when it opens, at most once a day, and offers it on the
home screen (`Updater.kt`, the same updater as odo). For that to work, each release must:

- be tagged `vX.Y.Z` and published (not a draft or pre-release),
- have exactly one `.apk` asset, built with `gradle assembleRelease`,
- be signed with the same key, and have a higher `versionCode`.

To test an update, build an older-numbered copy: `gradle assembleRelease -Psteady.versionCode=1 -Psteady.versionName=0.1.9`.

Exported health data for check-ins goes in `my-data/`, which git ignores.

## Next phases

1. **Learn** (done): readings, ratings, steps, trends, lead-up analysis.
2. **Warn**: morning green/amber/red from your normal and the last 3 days' load; an evening
   "plan tomorrow" note; an all-day sensor (Polar Verity Sense) for live heart-rate-ceiling buzzes.
3. **Personalise**: a personal model once ~8 crash starts are logged; "are you in a crash?" check;
   a hit-rate and false-alarm report.
4. **Experiment**: heart rate up while still, screen time, upright time.
