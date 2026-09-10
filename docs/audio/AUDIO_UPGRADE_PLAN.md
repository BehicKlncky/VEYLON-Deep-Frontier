# Signal & Silence — audio upgrade plan

Recorded 2026-09-09, starting from `8a4458b` (v0.5.0). All work, branches and
annotated milestone tags stay local. No dependencies or audio assets are added.

## Baseline, before changing synthesis

The unmodified `build` passed (302 portable tests / 61 classes; doclint included).
The extracted recipes run headlessly with presentation seed 60600. Their complete
sample counts, hashes, means, peaks, RMS and windowed spectral measurements are
in [baseline-0.5.0.txt](baseline-0.5.0.txt). Extraction preserves arithmetic and RNG
order. Measurements include synthesis and Java array allocation, exclude OpenAL
upload and device initialization; the cold observed invocation was **62.148 ms**.

| Quantity | Baseline |
| --- | ---: |
| Rate / format | 22,050 Hz, mono signed 16-bit |
| Buffers / total samples | 48 / 929,071 |
| PCM payload | 1,858,142 bytes |
| Equivalent float payload | 3,716,284 bytes |
| Rain / wind / fire | 2.5 / 4 / 3 seconds |
| Cave / crickets / beacon | 4 / 2 / 2 seconds |
| Simultaneous one-shots / ambience | 16 / 6 |

`AudioManager.java` at the baseline: upload (:629) clamps before scaling, so
the measured pistol peak **1.83938**, musket **1.35528**, explosion **1.34857**,
hit **1.08486** and bell **1.11907** lose their peaks. Grass has mean
**-0.0126259**; stone footsteps have a **0.386336** first/last step.
`rainLoop` (:825) inserts single-sample clicks, `fireLoop` (:857) bakes pops,
and `cricketsLoop` (:891) bakes chirp timing. The wind loop (:841) repeats a
four-second gust envelope. These are measured defects, not passing quality claims.

Windowed DFT probes (64 uniformly spaced probes per band, middle 8,192 samples)
give rain 100–1,000 Hz energy 0.000178188 versus 2–10 kHz 0.000034321;
wind gives 0.000092816 versus 0.000000861. These are probe sums rather than
integrated band powers. Tests use Hz, and a known sine verifies the instrument.

Pre-integration benchmarks passed: chunk 0.404/0.92 ms, entity 0.339/0.95 ms,
settlement 0.013/0.52 ms, save 1.469/2.20 ms, load 198.662/240 ms
(measured/budget). Existing fixtures and budgets stay unchanged.

## Phases and dependencies

| Tag | Phase | Dependency | Definition of done | Status |
| --- | --- | --- | --- | --- |
| v0.5.1 | Characterize, isolate DSP, guard PCM | baseline | Headless signal checks, negative fixtures, recorded baseline | automated checks passed; build gate below |
| v0.5.2 | 44.1 kHz | 1 | Hz-preserving filters, duration/spectral tests, timing and bytes | verified |
| v0.5.3 | Layered ambience | 2 | Continuous beds, runtime events/gusts, intensity changes spectrum | automated checks verified |
| v0.5.4 | Spatial ambience | 3 | Decorrelated weather, positioned fire, smooth gains | automated checks verified |
| v0.5.5 | Environment reverb | 4 | Six interpolated presets, detected EFX, dry fallback | automated and native checks verified |
| v0.5.6 | Occlusion | 5 | Fixed query/update caps, low-pass and air absorption | headless and native checks verified |
| v0.5.7 | Variants | 6 | Four distinct takes of repeated sounds, non-repeating selection | pending |
| v0.5.8 | Priority voices | 7 | Tested priority/quietness/age ordering, softened stealing | pending |
| v0.5.9 | Weather realism | 8 | Distance-delay thunder and sheltered filtering, reset safety | pending |
| v0.5.10 | Settings | 9 | Persistent live buses and mute, title/pause editor | pending |
| v0.5.11 | Sparse music | 10 | State-led synthesized phrases, silence intervals, mute and bounds | pending |

Every phase requires a full passing build before its explicit merge. Final
validation additionally requires two repeated portable suites, performanceTest,
releaseArtifacts, diff checks and an attempted 30-second fixed-seed GPU/audio
smoke. Listening acceptance (including an unidentifiable period over 60 seconds)
must be reported separately from automated signal evidence; code tests cannot
prove a human listening result.

## Implementation rules

Keep world-state gain computation in `AmbienceSystem`; extract bounded audio
collaborators as needed. OpenAL remains on the frame thread. Pure policy and DSP
are testable without native initialization. Presentation RNG never enters
`WorldBootstrap.reseedSimulation`. Cross-world audio queues are transient and
reset with the world; no save-format change. Retain dry playback without EFX.

Document final measured limits, rather than predicting startup or frame costs.

### v0.5.1 evidence

Characterization committed in `debe250` before conditioning. The focused suite
contains 57 audio cases (including 48 dynamic buffer checks); doclint passes.
The earlier full extraction build passed in 1m 33s. Final milestone build runs
after the 0.5.1 version and version assertion change. Original recipe defects
remain in the historical record; runtime PCM now has zero endpoints and DC
below 0.00001 with a 0.88 peak ceiling. Internal repeating ambience events are
addressed in phase 3, after the rate migration.

Final v0.5.1 build: PASS, 359 tests / 63 classes, zero failures; 1m 33s.

### v0.5.2 evidence

`AudioFilters` names 24 migrated cutoffs in Hz. All constant one-poles,
variable stave/swish poles, event probabilities, fade lengths and gulp modulation
were audited. Matched-pole conversion preserves time constants; it does not claim
an exact analog -3 dB point near Nyquist. A 1 kHz response test measures within
0.01 linear amplitude of half power at both rates. Chirp phase now integrates
frequency; its 2.4–4.2 kHz band dominates low and near-Nyquist probes by over 100x.

The catalog is 1,858,153 samples / 3,716,306 PCM bytes / 7,432,612 equivalent
float bytes. Raw synthesis invocation: 62.879 ms (baseline invocation 62.148 ms;
these are cold observations, not a performance improvement). Warmed synthesis,
conditioning and PCM conversion: 51.066 ms, budget 1,500 ms. PCM budget: 64 MiB,
chosen ahead of the long beds, decorrelation, variants and music additions.
Actual native startup separately logs synthesisAndUploadMs. Listening equivalence
remains unverified until a device/listener pass.

Final v0.5.2 build: PASS, 363 tests / 64 classes, zero failures; 1m 34s.

### v0.5.3 evidence

`AmbienceBeds.Bed` declares eight continuous textures, 17-37 seconds, with Hz
cutoffs and RMS targets on each enum value. Four separate event recipes have
finite envelopes; looping textures contain no event envelopes. `AmbientEvents`
limits scheduling to four events per frame and chooses new gust targets every
2-7 seconds, independently of buffer duration. A 60-second deterministic test
finds irregular rain intervals and verifies the event cap. The half-second RMS
stationarity test rejects baked gusts or event envelopes in every bed.

54 buffers: 19,653,778 PCM bytes. Complete warmed synthesis/conditioning/conversion:
164.846 ms / 1,500 ms budget. World-reset and return-to-title wiring cancel the
presentation state. Spectral and scheduling tests plus doclint pass. A human
60-second loop-identification test remains unperformed; no listening approval
is inferred from the deterministic schedule.

Final v0.5.3 build: PASS, 374 tests / 65 classes, zero failures; 1m 35s.

### v0.5.4 evidence

`SpatialAmbience.EMITTERS` fixes 16 looping sources. Each weather layer has
three independent buffers at front/left/right, allocated 1/sqrt(3) gain each;
correlation tests measure absolute correlation below 0.02. Fire is world-relative
and both its bed and pops use the nearest audible position. `FireAudioLocator`
checks at most 2,197 local cell candidates plus 220 burning cells, only when
audio is enabled and the existing heat gain is audible. It does not generate
chunks or scan the save-wide campfire map. Tests cover nearest selection,
positive fuel, negative coordinates, range and unchanged loaded-chunk counts.

62 buffers: 33,412,978 PCM bytes. Warmed synthesis/conditioning/conversion:
255.480 ms / 1,500 ms; memory remains below 64 MiB. Existing exponential gain
crossfades apply to all emitters. Spatial listening and source-transition taste
remain unverified until a real listener pass.

Final v0.5.4 build: PASS, 387 tests / 67 classes, zero failures; 1m 35s.

### v0.5.5 evidence (2026-09-10)

`EfxProcessor` detects ALC_EXT_EFX before allocating a standard-reverb effect and
one auxiliary slot. `ReverbPresets` supplies six parameter sets, exponentially
interpolated and submitted at most every 0.05 seconds. `AudioSceneState` reads
one registered settlement and one floor block; it never invokes lazy planning.
Headless tests cover every zone, parameter limits, smooth interpolation,
no-extension native-free calls and unchanged world registration/loaded chunks.

Two actual 30-second, 1280x720, seed-20260910 smoke runs passed isolated save/load,
all hard limits and zero GL/KHR errors:

| Run | avg FPS | p95 / p99 ms | synthesis + upload ms | OpenAL |
| --- | ---: | ---: | ---: | --- |
| VEYLON_NO_EFX=1 | 876.4 | 1.49 / 1.69 | 377.642 | initialized, dry fallback logged once |
| EFX detected | 869.1 | 1.51 / 1.69 | 387.267 | initialized, zero native errors |

The EFX run measured 26,074 audio updates: mean 0.003228 ms, max 0.153200 ms.
Hardware: NVIDIA RTX 1000 Ada Generation Laptop GPU, OpenGL 3.3, driver 595.95.
Logs are local in ignored build/audio-work. Actual acoustic listening remains
unverified; initialization and error-free submission do not prove aesthetic tuning.

Final v0.5.5 build: PASS, 393 tests / 69 classes, zero failures; 1m 37s.

### v0.5.6 evidence

`AudioOcclusion` caps one segment at 64 probes; `AcousticSources` shares four
rays between initial submissions and frame reevaluations, with a 0.10-second
per-source refresh interval. Full occlusion retains 0.45 broadband and 0.08 HF
gain; interpolation is eight per second. EFX air absorption is 1 for positional
sources and 0 for relative sources. Missing EFX remains dry. World reset clears
the query reference, and the listener now updates before frame acoustics.

Tests cover open/blocked segments, negative floor coordinates, zero length and
100,000-block paths, shared limits and disabled native filters. Real loaded-world
ray benchmark: 0.002127/0.10 ms. All unchanged simulation/save budgets pass.
The explicit native audio smoke records 3/4 peak rays, 128/256 probes and zero
OpenAL/GL/KHR errors, with all hard limits. See PERFORMANCE_BENCHMARKS for timing
and concurrent-work caveat. Coarse long rays may miss thin barriers; exact
long-distance diffraction/propagation is outside this bounded approximation.

W6 full build passed in 1m 38s: 396 tests / 70 classes, including doclint.

### W7 evidence

Four takes in each of 18 banks add 54 buffers and 1,061,046 PCM bytes. The catalog now contains 116 buffers / 34,474,024 PCM bytes. Independent waveforms, equal durations and 1,000 non-repeating selections pass headless checks. Warmed synthesis/conditioning/encoding measures 275.810 ms against 1,500 ms. Subjective fatigue testing remains unverified.

W7 full build: PASS in 1m 38s, 452 tests / 71 classes.
