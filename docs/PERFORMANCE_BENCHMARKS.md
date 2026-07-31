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
| `save` | `SaveSystem.save` of a typical world | 0.60 ms | 1.10 ms | 0.510–0.645 |
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
| `save` | 0.60 | 0.60 | unchanged |
| `load` | 300 | 200 | generator column memo |

Lowering a baseline after an improvement is the point of having one. Left at
300 ms, `load` would have accepted a regression all the way back to the v0.4.1
figure without complaint; at 200 ms, the 240 ms budget catches it.

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

**`save` is 500× cheaper than `load`, and that is expected.** A save writes a
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
