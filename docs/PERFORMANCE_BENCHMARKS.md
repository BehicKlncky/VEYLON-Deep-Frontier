# Performance benchmarks

Baselines for `PerformanceBenchmarkTest`, which fails its dedicated
`performanceTest` task when a measured figure exceeds its budget. This document
and the `BASELINE_*` constants in that test are a pair: change one without the
other and the numbers stop meaning anything.

Wall-clock budgets are calibrated for one reference machine, so they are tagged
`performance` and deliberately excluded from the portable `test`, `build` and
`releaseArtifacts` paths. Run them explicitly on the reference PC:

```powershell
.\gradlew.bat performanceTest
```

## What is measured

These are **tick** budgets, not frame budgets. Rendering needs a GL context the
test JVM does not have, so what is timed is the simulation half of a frame —
the fast/medium/slow tick buckets, settlement work and save serialization. A
rendering regression will not show up here; `VEYLON_SMOKE` and the graphics
captures cover that side.

Each figure is the **fastest of 7 runs after 3 discarded warm-up runs**. The
minimum is used deliberately: it is the run least disturbed by GC and OS
scheduling, which makes it far more repeatable than a mean without hiding a real
slowdown, because a genuine regression raises the floor too.

The settlement fixture is a fixed-step exception worth making explicit. Before
every timed sample its accumulator is primed to 50 seconds; the production
10-second slow tick must therefore execute exactly one 60-second dormant
resident step, and the test asserts that it did. The minimum can never select an
empty accumulator pass.

## Reference machine

| | |
| --- | --- |
| CPU | 13th Gen Intel Core i7-13850HX |
| OS | Windows 10 Enterprise LTSC 2021 (19044) |
| JDK | 25.0.1+8-LTS-27 |
| Measured | 2026-07-31, v0.5.0 release candidate |

## Baselines

| Benchmark | Scenario | Baseline | Budget | Observed range |
| --- | --- | ---: | ---: | --- |
| `chunk tick` | one fast + medium + slow cycle, 100 chunks loaded | 0.42 ms | 0.92 ms | 0.359–0.404 |
| `entity tick` | one fast + medium + slow cycle, 40 entities alive | 0.45 ms | 0.95 ms | 0.279–0.625 |
| `settlement tick` | one real fixed step, 20 dormant residents | 0.02 ms | 0.52 ms | 0.013 |
| `save` | durable, atomically published `SaveSystem.save` of a typical world | 1.70 ms | 2.20 ms | 1.481–1.603 |
| `load` | `SaveSystem.load` of the same file | 200 ms | 240 ms | 183–201 |

The "typical world" for the save/load pair is 60 loaded chunks, 20 entities, 400
player block edits and a 10-resident settlement, producing an ~11.7 KB file.

Ranges are over five consecutive runs of the task, each of which is itself the
fastest of seven samples. `entity tick` is the noisiest by a distance — a 2.2x
spread with identical code — which is exactly what the additive noise floor
below exists for.

### What moved in v0.5.0, and why these were lowered

| | v0.4.1 | v0.5.0 | |
| --- | ---: | ---: | --- |
| `chunk tick` | 0.50 | 0.42 | chunk lookup cache |
| `entity tick` | 0.55 | 0.45 | chunk lookup cache |
| `settlement tick` | 0.05 | 0.02 | chunk lookup cache |
| `save` | 0.60 | 1.70 | flush-to-device + atomic replacement |
| `load` | 300 | 200 | generator column memo |

Lowering a baseline after an improvement is the point of having one. Left at
300 ms, `load` would have accepted a regression all the way back to the v0.4.1
figure without complaint; at 200 ms, the 240 ms budget catches it.

The save row is the deliberate exception: v0.4.1/v0.5.0 originally timed a
buffered write directly into the destination. The release review made save
publication failure-atomic: serialization now finishes in a sibling temporary
file, flushes it to the device, and only then atomically replaces the last good
save. The extra ~1.1 ms buys a different durability guarantee, so retaining the
old 0.60 ms baseline would compare unlike operations rather than catch a
regression.

The two changes are documented in
[`v0.5.0-technical-baseline-audit.md`](engineering/v0.5.0-technical-baseline-audit.md).
Both were verified bit-identical by `WorldGeneratorFingerprintTest` before the
figures were accepted — a "faster" generator that quietly reshapes terrain is
not an optimization, it is a broken save format.

## How the budgets are derived

    budget = max(baseline × 1.20, baseline + 0.5 ms)

The 20% allowance is the stated regression threshold. The additive floor exists
because 20% of a sub-millisecond figure is smaller than the measurement noise:
repeated runs of `entity tick` moved by 27% and `settlement tick` by 100% on the
reference machine with identical code. Without the floor those two would fail at
random. With it, the small benchmarks still catch a doubling, and `load` — the
only figure large enough for the percentage to bind — is governed by the 20% rule
as intended.

## Reading the numbers

**`save` is over 100× cheaper than `load`, and that is expected.** A save writes a
delta: player state, entities, settlements and the player's block edits, which is
why a played-in world is only kilobytes. A load has to regenerate the terrain
from the world seed before replaying that delta, so it pays full worldgen cost.
Any work to make loading faster belongs in `WorldGenerator`, not `SaveSystem`.

**The tick benchmarks are dominated by fixed costs, not by scale.** At 100 chunks
and 40 entities the whole cycle is under half a millisecond, comfortably inside a
16.7 ms frame. The value of these entries is not the absolute number but the
shape: if adding a system pushes `entity tick` past a millisecond, that system is
doing per-entity work it should not be doing every tick.

## The per-system profile

`TickProfileTest`, also under `performanceTest`, answers a different question:
not "is a tick fast enough" but "what is it spending the time on". That is the
one you need *before* optimizing anything.

It earned its place. The v0.5.0 work opened by picking two hot spots from
reading the code — a 1,331-cell environmental scan and a duplicated loop in the
medium tick — and both measured inside the noise here. The profile found the
real one in a single run: every block query in the engine was boxing a `Long` to
hash a chunk key, which is invisible from these five figures and was over half
the slow tick once traced.

**Its numbers are not comparable with the table above and must never be copied
into it.** Each system there is warmed 200 times and sampled 40; the benchmarks
above take 3 warmups and 7 samples of a whole cycle. The profile measures
fully-JIT-warmed steady state, which is smaller than a realistic frame — read
its shares, not its milliseconds. Its budgets are five times the recorded figure
rather than 20%, because its job is to name a subsystem that got an order of
magnitude slower, not to police drift.

## Re-baselining

Update the table and the test constants together when:

- A deliberate change moves a figure and the new cost is understood and accepted.
  Record what changed and why in the commit, not just the number.
- The reference hardware, JDK or benchmark fixture deliberately changes. Do not
  re-baseline from an arbitrary developer or CI machine; portable jobs do not run
  this task precisely because their timings are not comparable.

Do not re-baseline to make a red build green without first establishing which
change made it slower.

## Audio startup (introduced in v0.5.2)

`AudioPerformanceTest` is tagged performance and measures the full emitted
catalog through production conditioning and signed-16 conversion, without a
sound device. Three warmups, fastest of seven. Startup budget is 1,500 ms;
PCM payload budget is 64 MiB, chosen before adding the longer beds and banks.
These do not alter any simulation/save budget above.

| Stage | Raw synthesis observation | PCM bytes | Warmed conditioning + conversion |
| --- | ---: | ---: | ---: |
| v0.5.0 / 22.05 kHz, 48 buffers | 62.148 ms | 1,858,142 | not measured |
| v0.5.2 / 44.1 kHz, 48 buffers | 62.879 ms | 3,716,306 | 51.066 ms |

Raw observations include Java arrays, exclude native upload, and are not paired
warm benchmark samples. Equivalent float payload doubles from 3,716,284 to
7,432,612 bytes; runtime streams arrays to OpenAL and does not retain a full
float catalog. Driver allocation and Java/native overhead are additional.
Native initialization logs its complete synthesis/upload duration separately.

## Audio occlusion (v0.5.6)

`AudioOcclusionPerformanceTest` measures the full four-ray, 256-probe allowance
against real `World.getBlock` calls across fifteen loaded chunks. Fixture setup
is outside timing; three warmups and fastest of seven batches of 10,000 frames.
Measured 0.002127 ms/frame against 0.10 ms. No gameplay benchmark budget changes.
Initial playback and later reevaluations share the same frame allowance.
Long paths coarsen sampling; this bounded approximation can miss thin distant
walls. Native filter submission is measured separately by the device smoke.

At v0.5.6, VEYLON_AUDIO_QA=1 made the 30-second smoke emit three presentation-only reports
per second, without gameplay noise, damage or world edits. It observed 3/4 peak
rays, 128/256 peak probes, 0.004023 ms mean audio update and 0.398700 ms maximum,
with zero native errors. A focused test/doclint task ran during part of that
smoke, so its 841.8 FPS is an execution check rather than a controlled comparison.
The final release smoke will run without concurrent build work.

## Final audio catalog and music (0.6.0)

A fresh-process comparison uses the same `CatalogMeasure` harness and seed 60600
for the untouched W1 extraction (`debe250`) and final recipes, JDK 25, 512 MiB heap.
It times pure synthesis into a map, excluding conditioning, conversion, upload and
JVM launch; warm results are fastest of seven after three discarded iterations.
The original measurement before editing was 62.148 ms and remains recorded above.

| Same-harness measurement | Original | 0.6.0 |
| --- | ---: | ---: |
| Cold pure synthesis | 70.054 ms | 571.508 ms |
| Warm pure synthesis | 20.136 ms | 392.042 ms |
| Mono signed-16 PCM payload | 1,858,142 bytes | 40,824,424 bytes |
| Equivalent complete float catalog | 3,716,284 bytes | 81,648,848 bytes |
| Buffers | 48 | 122 |

The rate-only W2 change approximately doubled PCM; most final growth comes from
17-37-second decorrelated beds, 54 additional variant takes and six 12-second
phrases. Float totals describe the test catalog, not retained runtime memory.
Driver storage, mixer state, direct buffers and Java overhead are additional;
this is a payload measurement, not a process RSS claim.

`MusicPerformanceTest` bounds the allocation-free director at 0.02 ms per frame,
using three warmups and the fastest of seven 100,000-frame batches. W11 measured
0.000004 ms. Music uses one source, no streaming thread or runtime synthesis;
its six precomputed phrases cost 6,350,400 PCM bytes. The 10,000-second policy
test remains over 90% silent. Full final benchmark and native update figures are
recorded in [v0.6.0-validation.md](engineering/v0.6.0-validation.md).

Current VEYLON_AUDIO_QA pressure emits 24 footsteps, one explosion, three reports
and a scheduled thunder per second without adding gameplay noise/damage. It also
queues distant thunder before isolated load to verify cancellation. Native update
time includes all audio processing and driver calls; the separate ray/director
benchmarks isolate CPU policy work. Final smoke runs have no concurrent build.
