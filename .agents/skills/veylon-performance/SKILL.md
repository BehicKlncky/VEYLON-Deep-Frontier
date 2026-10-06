---
name: veylon-performance
description: Investigate VEYLON frame or tick regressions, allocations and benchmark budgets; use for performance work, not routine builds.
---

# VEYLON performance

All paths below are relative to the repository root. Read `docs/PERFORMANCE_BENCHMARKS.md` for the relevant measurement and `docs/DEVELOPING.md` for native QA parameters; avoid loading unrelated release records.

## Choose the measurement

- Simulation cost: `src/test/java/com/veylon/qa/TickProfileTest.java` locates subsystem costs; `PerformanceBenchmarkTest.java` measures whole-cycle and durable save/load budgets.
- Rain: `src/test/java/com/veylon/engine/RainPerformanceTest.java`, run in its isolated worker through `rainPerformanceTest`.
- Audio: relevant `*PerformanceTest` under `src/test/java/com/veylon/engine`; native submission additionally requires an audio QA run.
- Ragdolls/fragments: allocation tests under `src/test/java/com/veylon/entity`; runtime capacity invariants live in `src/test/java/com/veylon/qa/RuntimeBoundsTest.java`.
- Rendering/FPS: use a native `VEYLON_SMOKE` or deterministic scene/capture run. Headless tick timings do not measure rendering.

## Workflow

1. Identify a reproducible symptom and measure before changing code. Record revision, hardware, OS/JDK and fixture settings; compare before/after on the same host and scenario.
2. Profile the affected subsystem before choosing an optimization. Keep simulation RNG, generator output and gameplay semantics unchanged unless the task explicitly changes them.
3. Run the selected measurement. Windows commands (use `./gradlew` on macOS):

   ```powershell
   .\gradlew.bat performanceTest --console=plain
   .\gradlew.bat performanceTest --tests "com.veylon.qa.TickProfileTest" --console=plain
   .\gradlew.bat rainPerformanceTest --console=plain
   ```

   Choose the command needed; do not run all three automatically. `performanceTest` depends on `rainPerformanceTest`, even when filtering the parent task. The main performance task excludes rain-tagged tests to preserve calibration.
4. Keep timed measurements and native smoke runs free of concurrent builds or benchmarks. Preserve/restore task-specific `VEYLON_*` environment overrides. Inspect the final smoke report; an early window exit is not a pass.
5. Verify affected correctness/allocation invariants and run the portable build after code changes. For worldgen optimization, include `WorldGeneratorFingerprintTest` so faster generation cannot silently reshape saved worlds.
6. Record measured values, budgets and failures separately. Update the existing benchmark document only with relevant new evidence, identifying the host and revision.

## Interpretation

- Whole-cycle benchmarks use 3 warmups and the minimum of 7 samples. The per-system profile uses different warmup/sampling rules: its milliseconds cannot replace the baseline table.
- Reference-machine budgets are hardware-specific. The historical durable-save failure on a secondary machine is evidence about that run, not permission to dismiss a new failure. Check a comparable baseline before attributing it to hardware.
- Re-baseline only when a deliberate cost/fixture/hardware change is understood and accepted; update documented baselines and test constants together. Do not weaken assertions to hide regressions.
- Finish with a concise before/after comparison, preserved correctness checks and any measurement that remains unavailable. Do not claim FPS improvements from tick-only results.
