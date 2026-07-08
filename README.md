# Kick Predict

An Android app that predicts match outcomes for five football leagues — **EPL, LaLiga, Serie A,
Bundesliga, K League** — and visualises the result, with first-class support for the **Galaxy Z Fold 3**
(cover screen and unfolded main screen).

Built with **Kotlin + Jetpack Compose**, **MVVM + Clean Architecture**, Compose Navigation, Retrofit/OkHttp
(API-ready) and Room (team/form caching). Dark-first UI with a lime-green accent.

## Architecture

Single `:app` module, organised by Clean Architecture layer:

```
com.kickpredict
├── domain          # pure Kotlin, no Android deps
│   ├── model       # LeagueType, Match, TeamProfile, HeadToHead, PredictionResult …
│   ├── engine      # PredictionEngine (Poisson win/draw/away % + Confidence + expected score)
│   ├── calibration # Calibrator + CalibrationDefaults (real-data league scoring rates)
│   ├── repository  # MatchRepository interface
│   └── usecase     # GetPredictedMatch(es)UseCase
├── data
│   ├── mock        # MockDataProvider — the 4 offline test scenarios
│   ├── local       # Room: KickPredictDatabase, TeamDao, TeamEntity, Converters
│   ├── remote      # Retrofit PredictionApi + DTOs (live path, stubbed)
│   └── repository  # MatchRepositoryImpl
├── di              # AppContainer (manual DI)
└── presentation
    ├── theme       # dark + lime-green Material 3 theme
    ├── navigation  # KickPredictNavHost
    ├── matchlist   # list screen + ViewModel
    ├── detail      # responsive prediction screen + ViewModel
    └── components  # ConfidenceBadge, PredictionDonutChart, ProbabilityGauges, PowerComparisonReport
```

## Prediction engine

`domain/engine/PredictionEngine.kt` is pure, side-effect-free Kotlin. It produces:

1. **Win / draw / away probabilities** from a **Poisson scoreline model**. Each side's expected goals
   (λ) are built from data-calibrated league scoring rates × the teams' own scoring rates (dampened by a
   0.6 exponent), then skewed by a quality index (`0.45·rating + 0.25·position + 0.30·form`), league
   rules and the matchup correction. The full 9×9 score grid is summed into H/D/A, with the level
   diagonal scaled by the league's draw-inflation factor. The most-likely **scoreline** falls out for
   free (shown as "예상 스코어" in the UI).
   - Bundesliga: extra home boost · Serie A: high draw inflation · LaLiga: amplified position-gap ·
     EPL / K League: fatigue penalty for ≤4 days rest.
2. **Confidence Score** ("AI 예상 적중 확률", 0–100) = `0.25·data-volume + 0.35·win-prob-gap +
   0.20·schedule-stability + 0.20·H2H-consistency`, expanded ×1.25 around the 50% midpoint. This is how
   much the engine trusts *this* prediction, independent of who it favours.

### Real-data calibration

`domain/calibration/Calibrator.kt` derives each league's `LeagueCalibration` (home/away goal averages +
draw inflation) from historical results via maximum-likelihood averages; draw inflation = observed draw
rate ÷ Poisson-implied draw rate. With no dataset wired here, the engine falls back to `CalibrationDefaults`
(realistic per-league values). The engine takes a `CalibrationProvider`, so swapping in `DataCalibration`
computed from real fixtures is a one-liner.

### Matchup — 상성 / bogey teams (recency- & venue-weighted)

A side that has been winning the head-to-head gets a λ boost (`±0.42 · H2H-bias`) and its opponent a
matching cut — strong enough for a weaker "bogey team" (천적) to swing a fixture it would lose on paper
(scenario D). The bias is a weighted average of past meetings where:
- **recency** — the i-th most-recent meeting is weighted `0.7^i`, and
- **home/away venue** — a meeting played at *this fixture's* ground is additionally ×1.6.

So the same 2–0–2 tally reads differently by recency (recent home wins → higher home%) and by venue
(home wins earned at this ground count for more). `matchupBias` is exposed on `PredictionResult` and shown
as a 🔥 **상성 banner** (record + most-recent-first chips, ringed = at this venue) plus a **상성 chip** on
each match card.

### Test scenarios (from `MockDataProvider`)

| Scenario | League   | Setup                                   | Result (H/D/A) | Confidence | 예상 |
|----------|----------|-----------------------------------------|----------------|------------|------|
| A        | EPL      | 1st (home) vs 20th (away)               | 92 / 7 / 1     | **97% Very High** | 3–0 |
| B        | Serie A  | two even, draw-prone sides              | 34 / 37 / 29   | **58% Moderate**  | 1–1 |
| C        | K League | rested home vs tired away (rain, injury)| 44 / 29 / 27   | **32% Low**       | 1–1 |
| D        | EPL      | bogey home vs top-2 (2 visitors injured)| 43 / 29 / 28   | **59% Moderate** (상성) | 1–1 |

### Context variables — injuries · lineup · weather

`MatchContext` (on each `Match`) carries per-team availability (`keyPlayersInjured`, `lineupStrengthPercent`)
and `Weather`. Availability scales that side's λ (~0.6–1.0); adverse weather dampens both λ (rain 0.94 …
snow 0.85), which raises the draw probability. Each live variable (fatigue, injuries, adverse weather)
also lowers the **Confidence Score** by one step. Shown as "경기 변수" chips in the report.

### Confidence hit-rate calibration

`domain/calibration/ConfidenceCalibrator.kt` fits a reliability curve from historical
`PredictionRecord`s via isotonic regression (pool-adjacent-violators over confidence bins), so a
systematically over-confident engine ("90% but right 60%") is pulled down to its true hit rate. The
engine takes a `ConfidenceCalibration` (identity by default); swap in a fitted one to re-map scores.

### Offline-first data

`MatchRepositoryImpl` is offline-first: try the live `PredictionApi`, cache the payload to Room
(`fixture_cache`), and on failure fall back to the last cache — or bundled `MockDataProvider` fixtures if
the cache is empty, so the UI is never blank. (No live API host is wired, so the app runs off the mock
seed here.)

## Fixtures list — teams, crests, filtering

- **Real clubs + Korean names:** `TeamProfile` carries `koreanName` and club colours; the sample
  catalog (`data/mock/Teams.kt`) holds real clubs across all five leagues. Team marks are **generated
  in-app** (`presentation/components/TeamCrest.kt`) — a club-colour disc with the short code, so there
  are **no copyrighted logo assets**.
- **League filter:** chip row (전체 + each league).
- **Round / week grouping:** toggle between 라운드별 (by matchday) and 주별 (by Monday-anchored week);
  the list renders section headers with match counts.
- **Date-range (period) search:** a `DateRangePicker` dialog filters fixtures by kickoff date.
- Data: the four tuned scenarios (A–D, now real clubs with identical stats) plus a deterministic
  multi-round schedule (`MockDataProvider`) so the filters have real data.

## Foldable / responsive UI

The detail screen uses `BoxWithConstraints` with a 600dp breakpoint:

- **Cover screen (narrow):** single scrolling column.
- **Unfolded main screen (wide):** two-pane — left = power-comparison report, right = win/draw/away
  donut chart + gauges + Confidence badge.

All widths are proportional (`fillMaxWidth`, `weight`, aspect ratios); no fixed component widths, so the
layout survives the fold/unfold transition. `resizeableActivity="true"` and `configChanges` in the
manifest keep state across the posture change.

## Build & run

Requires the **Android SDK** (compileSdk 35) and a device/emulator — ideally a Fold 3 or the
resizable/foldable emulator.

```bash
./gradlew :app:assembleDebug              # build the debug APK
./gradlew :app:installDebug               # install on a connected device/emulator
./gradlew :app:testDebugUnitTest          # engine/calibration unit tests (JVM)
./gradlew :app:connectedDebugAndroidTest  # Compose UI tests (needs a device/emulator)
```

Open each of the three scenario cards, then fold/unfold to see the layout switch between the stacked and
two-pane presentations.

> **Toolchain (verified building):** AGP 8.7.3, Gradle 8.11.1, **Kotlin 2.2.21** + **KSP 2.2.21-2.0.5**,
> Compose BOM 2024.12.01, compileSdk/targetSdk 35, minSdk 26. Room's annotation processor needs
> **KSP1** (`ksp.useKSP2=false` in `gradle.properties`) — KSP2 currently trips on "unexpected jvm
> signature V". `:app:testDebugUnitTest` (13 tests) and `:app:assembleDebug` both pass.

## Networking

Retrofit/OkHttp are wired (`data/remote`) for a future live fixtures API. The app currently runs entirely
off `MockDataProvider`, so the three scenarios work with no network. To go live, fetch via `PredictionApi`
inside `MatchRepositoryImpl` and map DTOs with `MatchDto.toDomain()`.
