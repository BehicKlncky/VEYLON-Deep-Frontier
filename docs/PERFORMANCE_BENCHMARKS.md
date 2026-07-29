# Performance benchmarks

Baselines for `PerformanceBenchmarkTest`, which fails the build when a measured
figure exceeds its budget. This document and the `BASELINE_*` constants in that
test are a pair: change one without the other and the numbers stop meaning
anything.

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

## Reference machine

| | |
| --- | --- |
| CPU | 13th Gen Intel Core i7-13850HX |
| OS | Windows 10 Enterprise LTSC 2021 (19044) |
| JDK | 25.0.1+8-LTS-27 |
| Measured | 2026-07-29, v0.4.1 branch |

## Baselines

| Benchmark | Scenario | Baseline | Budget | Observed range |
| --- | --- | ---: | ---: | --- |
| `chunk tick` | one fast + medium + slow cycle, 100 chunks loaded | 0.50 ms | 1.00 ms | 0.425–0.471 |
| `entity tick` | one fast + medium + slow cycle, 40 entities alive | 0.55 ms | 1.05 ms | 0.385–0.491 |
| `settlement tick` | one settlement slow tick, 20 dormant residents | 0.05 ms | 0.55 ms | 0.010–0.021 |
| `save` | `SaveSystem.save` of a typical world | 0.60 ms | 1.10 ms | 0.534–0.593 |
| `load` | `SaveSystem.load` of the same file | 270 ms | 324 ms | 250–266 |

The "typical world" for the save/load pair is 60 loaded chunks, 20 entities, 400
player block edits and a 10-resident settlement, producing an ~11.7 KB file.

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

## Re-baselining

Update the table and the test constants together when:

- A deliberate change moves a figure and the new cost is understood and accepted.
  Record what changed and why in the commit, not just the number.
- You are running on hardware materially different from the reference. Prefer
  `VEYLON_SKIP_BENCHMARKS=1` over re-baselining to a slower machine, because
  lowering the bar for one machine removes the guard for everyone.

Do not re-baseline to make a red build green without first establishing which
change made it slower.
