# SESSION-002 Handoff — Make Headless Verification Platform-Neutral

## Identity
- Objective: Allow compilation, unit tests, and doclint to run on Linux without weakening Windows/macOS packaging rejection.
- Audit finding(s): AUD-040 (portable CI / platform-neutral verification). This session closes the *build-configuration* half; the PR-CI half is SESSION-003.
- Starting commit: `8a4458b3310b6ad2bef5d35fb908ef9fc4bf1388` (`main`, `git describe` = `v0.5.0`, project version `0.5.0`) — the SESSION-001 baseline, unmoved.
- Ending commit / PR: **None — uncommitted.** Three tracked files modified in the working tree plus this handoff. Operator rule 2 asks for a dedicated branch and one intentional commit; the session prompt did not authorize committing and the repository is on `main`. See *Unresolved Issues*.
- Agent/reviewer: Claude Opus 5 (agent) / human reviewer pending
- Date: 2026-08-02
- Worktree state at start/end: Start — tracked files clean, untracked `ROADMAP.md` and `docs/production-readiness/` (SESSION-001). End — `M README.md`, `M build.gradle`, `M docs/DEVELOPING.md`, plus the same two untracked paths (now also containing this file). `git diff --check` exit 0. HEAD unmoved.
- `build.gradle` SHA-256 at end: `9253222fed3f6868776f5326d07c272fe1f569d5797cfc553eea3ad3fae36099`

## Summary of Changes

**What behavior/control changed.** Platform validation moved from a *configuration-time throw* to a *value plus an execution-time task gate*.

Before, `build.gradle:29-31` evaluated `(!isWindows && !isMacOs) || (!isArm64 && !isX64)` during project evaluation and threw `Unsupported release platform`. Because that ran at configuration time, it killed **every** task on a non-Windows/macOS host — `help` and `tasks` included — so no headless verification was possible at all. Reproduced verbatim before the change:

```
* Where: Build file '…\build.gradle' line: 30
* What went wrong:
A problem occurred evaluating root project 'veylon'.
> Unsupported release platform: windows 10 / riscv64
```

After the change:

1. `resolveReleasePlatform(osName, osArch)` is a pure function returning `supported` plus `platformClassifier`, `nativeClassifier` and `appImageDirectoryName`, all `null` when unsupported. Nothing throws during configuration.
2. The five `runtimeOnly` LWJGL natives coordinates are added **only** when a native classifier exists. An unsupported host resolves *no* natives rather than a substituted classifier.
3. A single `requireReleasePlatform` task throws an actionable message at execution time, and every task that needs host natives or produces a host package declares it as a dependency: `run`, `installDist`, `distZip`, `distTar`, `assembleDist`, `fatJar`, `cleanJpackage`, `jpackage`, `appImageZip`, `releaseArtifacts`. On an unsupported host each of those additionally carries a `doFirst` throw, so `-x requireReleasePlatform` cannot bypass the gate.
4. `verifyPlatformContract` (new, wired into `check`) pins the whole os/arch → classifier mapping, the natives actually present on the resolved runtime classpath, and the gate wiring.

**Why this is the smallest safe implementation.** The classifier expressions, their exact values, the natives coordinates, the jpackage/app-image layout, the macOS `CFBundleVersion` offset and the `ditto`/`PlistBuddy` branch are byte-for-byte unchanged; only their *guard* moved. No task was renamed, retyped, or removed, and no task was made conditional on the platform at configuration time — an unsupported host still sees the same task set, it just cannot execute the packaging half. Two smaller alternatives were rejected: deleting the guard entirely (an unsupported host would then silently assemble a distribution with zero natives), and registering stub tasks only on unsupported hosts (a larger diff that re-indents ~100 lines of packaging configuration and makes the two platforms structurally different).

`build` fails on an unsupported host **by design**: the `application`/`distribution` plugins put `distZip` and `distTar` in `assemble`, so `build` requests packaging. `check` is the portable gate and is what Linux CI must call. This is documented in both `README.md` and `docs/DEVELOPING.md`.

**Explicit non-goals respected.** No Linux game distribution and no Linux LWJGL runtime support — an unsupported host gets *no* natives, not Linux ones. No gameplay change (no file under `src/` was touched). No dependency version change (`lwjglVersion = '3.3.6'`, `jomlVersion = '1.10.8'`, toolchain 25, wrapper 9.1.0 + pinned SHA-256 all unmodified). No CI workflow change (`.github/workflows/release-build.yml` untouched — that is SESSION-003). No package redesign.

## Files Changed
| Path | Change | Reason |
|---|---|---|
| `build.gradle` | Modified (+177 / −25) | The change itself: pure platform resolution, conditional natives, `requireReleasePlatform` gate, `verifyPlatformContract` |
| `docs/DEVELOPING.md` | Modified (+16) | New "Platform support" subsection: what runs anywhere, what needs Windows/macOS, why `check` and not `build` |
| `README.md` | Modified (+11) | "Build & distribute" now states verification is platform-neutral and gives the portable command |
| `docs/production-readiness/SESSION-002.md` | Added | This handoff |

No file under `src/`, no workflow, no wrapper file, no `gradle.properties`, and no `settings.gradle` was modified. `settings.gradle` and `gradle/wrapper/*` were inspected and needed no change.

## Tests Added or Updated
| Test | Type | Production path exercised | Defect it detects |
|---|---|---|---|
| `verifyPlatformContract` — supported mapping table | Gradle contract check, runs inside `check` | `resolveReleasePlatform` for the 6 shipped os/arch combinations | A classifier or app-image directory silently changed for Windows x64/arm64 or macOS x64/arm64 |
| `verifyPlatformContract` — unsupported mapping table | as above | `resolveReleasePlatform` for 8 unsupported combinations (Linux x3, FreeBSD, SunOS, Windows x86, Windows riscv64, macOS ppc) | A fabricated classifier for a platform VEYLON does not ship — the "no fake Linux classifier" rule |
| `verifyPlatformContract` — resolved-classpath natives | as above | `configurations.runtimeClasspath` as actually resolved | Foreign or missing natives jars; asserts exactly 5 jars all matching the host classifier, or zero on an unsupported host |
| `verifyPlatformContract` — gate wiring | as above | `taskDependencies` of all 10 gated tasks | A packaging task that lost its dependency on `requireReleasePlatform` |
| `verifyPlatformContract` — distribution-group sweep | as above | every task with `group == 'distribution'` | A **newly added** packaging task whose author forgot the gate |

These are Gradle-level checks rather than JUnit tests because the contract is about Gradle task wiring and dependency resolution, which a JUnit test in `src/test` cannot observe without a Gradle TestKit dependency (a new dependency, out of scope). The suite count is therefore unchanged at 302.

### Mutation evidence (each check was proven to fail when the defect is introduced)
Per §2.4, each assertion was verified by reintroducing the defect it guards and confirming a red build. All four mutations were reverted; the final `git diff` (below) contains none of them.

| Mutation | Result |
|---|---|
| A — `nativeClassifier` returns `'natives-linux'` instead of `null` when unsupported | **FAILED**, 8 violations: `Linux / amd64 must resolve to unsupported with no classifier but resolved to supported=false null / natives-linux / null` (and 7 more) |
| B — `dependsOn requireReleasePlatform` removed from the wiring loop | **FAILED**, 10 violations: `Task run needs the host platform but does not depend on requireReleasePlatform` … through `releaseArtifacts` |
| C — a new `Zip` task registered in group `distribution` without the gate | **FAILED**: `Distribution tasks missing from the release-platform gate: [mutationPackageTask]` |
| D — an extra `runtimeOnly "org.lwjgl:lwjgl-glfw::natives-macos"` on a Windows host | **FAILED**, 2 violations: `Runtime classpath carries natives for another platform: [lwjgl-glfw-3.3.6-natives-macos.jar]` and `Expected 5 natives-windows jars on the runtime classpath, found 6` |

## Commands Executed

Two environments. **Windows** = this host (Windows 10 Enterprise LTSC 19044, Oracle JDK 25.0.1+8-LTS-27, Gradle 9.1.0), commands in PowerShell 7 / Git Bash from the repository root. **Linux** = `eclipse-temurin:25-jdk` (Temurin 25.0.3+9, Linux 6.6.87.2-microsoft-standard-WSL2 x86_64) under Docker 28.5.1, run against an isolated copy of the worktree in the session scratchpad with `GRADLE_USER_HOME=/work/.gradle-home`, so no container-owned file could land in the user's repository. The copy's `build.gradle` was verified byte-identical to the final worktree file (SHA-256 `9253222f…`).

| Command | Environment | Exit code | Result / report link |
|---|---|---:|---|
| `.\gradlew.bat -Dos.arch=riscv64 help --no-daemon --console=plain` (**before** the change) | Windows | **1** | Root-cause proof: `A problem occurred evaluating root project 'veylon'. > Unsupported release platform: windows 10 / riscv64` at `build.gradle` line 30 |
| `.\gradlew.bat -Dos.name=Linux ... help` (**before** the change) | Windows | 1 | Rejected as a simulation method: fails earlier in the Gradle launcher (`Unable to find the 'java' executable`), never evaluates the script |
| `sh ./gradlew clean check --no-daemon --console=plain` | **Linux** | **0** | `BUILD SUCCESSFUL in 3m 2s`; `Platform contract verified for linux / amd64 (supported=false, natives=none)`; `build/docs/javadoc/index.html` produced |
| JUnit XML aggregation over `build/test-results/test/TEST-*.xml` | **Linux** | 0 | **61 classes, 302 tests, 0 failures, 0 errors, 0 skipped** |
| `sh ./gradlew releaseArtifacts` | **Linux** | **1** | `> Task :requireReleasePlatform FAILED` with the intended message |
| `sh ./gradlew jpackage` | **Linux** | **1** | `> Task :requireReleasePlatform FAILED`, same message |
| `sh ./gradlew appImageZip -x requireReleasePlatform` | **Linux** | **1** | Gate excluded on purpose; `> Task :cleanJpackage FAILED` — the belt-and-braces `doFirst` still refuses |
| `sh ./gradlew build` | **Linux** | **1** | Fails at `requireReleasePlatform` because `assemble` pulls `distZip`/`distTar`. Intended; `check` is the portable gate |
| `sh ./gradlew run` | **Linux** | **1** | Fails at the gate with the actionable message instead of an LWJGL `UnsatisfiedLinkError` |
| `sh ./gradlew javadoc dependencies` | **Linux** | **0** | Javadoc and dependency inspection both work headless |
| `sh ./gradlew dependencies --configuration runtimeClasspath` | **Linux** | 0 | Five LWJGL modules + JOML resolve; **no `natives-*` entry of any kind** |
| `.\gradlew.bat clean check --rerun-tasks --no-daemon --console=plain` | Windows | **0** | `BUILD SUCCESSFUL in 1m 36s`; `Platform contract verified for windows 10 / amd64 (supported=true, natives=natives-windows)` |
| `.\gradlew.bat clean releaseArtifacts --no-daemon --console=plain` | Windows | **0** | `BUILD SUCCESSFUL in 1m 47s`; 18 tasks; full Windows packaging path intact |
| `.\gradlew.bat -Dos.arch=riscv64 clean check --rerun-tasks` | Windows | **0** | Simulated-unsupported headless gate: `BUILD SUCCESSFUL in 1m 38s`, 61/302/0/0/0 with zero natives on the classpath |
| `.\gradlew.bat -Dos.arch=riscv64 releaseArtifacts` / `jpackage` / `run` / `appImageZip -x requireReleasePlatform` | Windows | **1** each | Same four negative results as Linux, from the same code path |
| `.\gradlew.bat -Dos.arch=riscv64 tasks --group=distribution` | Windows | 0 | Configuration succeeds on an unsupported host; task descriptions read `windows 10/riscv64 (unsupported)`, not `null` |
| `.\gradlew.bat verifyPlatformContract` ×5 (baseline + mutations A–D) | Windows | 0, then **1**×4 | Mutation table above |
| `git status --short`, `git diff --check`, `git diff --stat`, `diff` of the container copy | Windows | 0 | Clean; see *Diff Review* |

`-Dos.arch=riscv64` is the Windows-side simulation of an unsupported host: it drives the identical `supported == false` branch of `resolveReleasePlatform` while leaving Gradle's own platform detection intact. It is a **simulation of the code path, not of Linux**; the real Linux evidence above is the authority, and the two agree on every outcome.

## Validation Results

### Targeted tests
`verifyPlatformContract` was run standalone on Windows five times (clean plus each of the four mutations) — see the mutation table. Each negative packaging path was invoked individually on both Linux and simulated-unsupported Windows rather than only through the aggregate `releaseArtifacts`.

### Full portable check — PASS on both platforms
- **Linux** `./gradlew clean check` → exit 0, `BUILD SUCCESSFUL in 3m 2s`. **61 classes / 302 tests / 0 failures / 0 errors / 0 skipped**, aggregated from the JUnit XML, identical to the SESSION-001 Windows baseline. Doclint ran under `-Xdoclint:all,-missing` with no warnings or errors and produced `build/docs/javadoc/index.html`.
- **Windows** `.\gradlew.bat clean check --rerun-tasks` → exit 0, `BUILD SUCCESSFUL in 1m 36s` (SESSION-001 recorded 1m 37s — no meaningful change; `verifyPlatformContract` adds well under a second).

The Linux run is the first time the suite has ever executed with **zero LWJGL natives on the runtime classpath**. It passing is independent evidence for the claim in `docs/DEVELOPING.md` that the suite is genuinely headless: not one of the 302 tests touches a native entry point.

### Performance — NOT RUN
Out of scope for this session. No production code, benchmark, or budget was touched, so the SESSION-001 figures remain the current reference. This host *is* the documented reference machine, so a future session can still produce comparable numbers.

### GL / audio / manual / platform validation — NOT RUN (GL), RUN (packaging)
No GL smoke was run; nothing in this change reaches the renderer, and `src/` is untouched. Windows packaging *was* validated end-to-end, which is the platform risk this change actually carries:

| Check | Result |
|---|---|
| `build/distributions/veylon-0.5.0-windows-x64.zip` | Produced, 54,427,086 bytes, SHA-256 `ded94d195193d7a25e6a127ab742880a838a745f06b846a1d15be9c93b56d7eb` |
| Release-zip classifier | `windows-x64` — unchanged, matches the workflow's `matrix.platform` expectation |
| Natives in `build/install/veylon/lib/` | Exactly 5: `lwjgl`, `lwjgl-glfw`, `lwjgl-openal`, `lwjgl-opengl`, `lwjgl-stb`, all `3.3.6-natives-windows.jar` |
| `build/jpackage/Veylon/app/Veylon.cfg` native references | `natives-windows.jar` only — no foreign classifier |
| `build/jpackage/Veylon/Veylon.exe` | Present |

The release workflow's own validation steps (`Veylon.exe` present, `runtime/release` present, `matrix.native` string in `Veylon.cfg`, `--enable-native-access=ALL-UNNAMED` present) all still hold for the Windows leg. **macOS was not executed** — see *Remaining Limitations*.

### Security / supply-chain validation
Not applicable beyond two passive observations: the Gradle wrapper bootstrapped against its pinned SHA-256 on both Windows and inside the Linux container, and the dependency set is unchanged (verified by `dependencies --configuration runtimeClasspath` on both platforms — identical module graph, differing only in the natives artifacts that no longer resolve on an unsupported host).

### Artifact hashes / IDs
`veylon-0.5.0-windows-x64.zip` — SHA-256 `ded94d195193d7a25e6a127ab742880a838a745f06b846a1d15be9c93b56d7eb`. This is a **local diagnostic artifact only**; per §2.4 it carries no release authority. It is not comparable to any v0.5.0 published hash (jars embed build timestamps) and is recorded only as evidence that the Windows packaging path still completes.

## Acceptance Criteria Status
| Criterion | Pass / Fail / Not Run | Evidence |
|---|---|---|
| Linux can run `check` | **Pass** | Real `eclipse-temurin:25-jdk` container, `./gradlew clean check` exit 0, 61/302/0/0/0, javadoc produced |
| Unsupported packaging fails only when requested | **Pass** | `check`, `javadoc`, `dependencies`, `compileJava` all exit 0 on Linux; `releaseArtifacts`, `jpackage`, `appImageZip`, `run`, `build` each exit 1 at `requireReleasePlatform`. Nothing fails until a packaging/run task is asked for |
| Windows/macOS task semantics and classifiers unchanged | **Pass** | Classifier expressions and values byte-identical; Windows `releaseArtifacts` exit 0 producing `veylon-0.5.0-windows-x64.zip` with exactly the 5 `natives-windows` jars. macOS is code-identical but **unexecuted** — see Limitations |
| No fallback silently packages the wrong natives | **Pass** | Unsupported hosts resolve zero natives (proven by `dependencies` output on Linux, and asserted by `verifyPlatformContract`); mutation D proves a foreign classifier is caught |
| Task-scoped rejection is proven | **Pass** | Four negative commands on real Linux, each exit 1 with the intended message; the `-x requireReleasePlatform` bypass also fails; mutation B proves the wiring assertion is live |
| Error wording is actionable | **Pass** | Names the host, states the supported set, states there is no classifier, and names the tasks that *do* work: "run compileJava, test, check, javadoc or dependencies instead" |
| Automated/scriptable contract check added | **Pass** | `verifyPlatformContract` inside `check`, running on both platforms; all five assertion groups mutation-tested |
| Diff free of accidental native-classifier changes | **Pass** | See *Diff Review* |

## Diff Review
- `git diff --check`: exit 0, no output.
- `git status --short`: `M README.md`, `M build.gradle`, `M docs/DEVELOPING.md`, `?? ROADMAP.md` (pre-existing), `?? docs/production-readiness/` (SESSION-001 plus this file).
- `git diff --stat`: `README.md +11`, `build.gradle +177/−25`, `docs/DEVELOPING.md +16`. 3 files, 204 insertions, 25 deletions.
- Classifier audit of the diff: the only lines containing a classifier string are the new `resolveReleasePlatform` map (values identical to the deleted expressions) and the `verifyPlatformContract` expectation table. The three `description = "… ${platformClassifier} …"` → `${platformLabel}` edits change **task descriptions only**; `archiveClassifier = platformClassifier` and the macOS `"distributions/veylon-${project.version}-${platformClassifier}.zip"` path are untouched.
- Generated/untracked files: `build/` was regenerated by the Windows runs and is gitignored; it currently holds the diagnostic release artifacts from `releaseArtifacts`. The Linux container wrote only into the scratchpad copy at `…/scratchpad/linux-check/`, outside the repository. `saves/veylon.sav` and `veylon_graphics.properties` were not touched (the game was never launched — `run` was only invoked on hosts where it is gated off).
- Unintended changes found and removed: **the four mutation edits**, each reverted immediately after its red run. Verified absent by reading the full `git diff` afterwards and by the byte-identical SHA-256 between the reviewed worktree file and the copy the Linux validation actually consumed.

## Assumptions Made
- **`build` is allowed to fail on an unsupported host.** Evidence: the roadmap's required Linux test is `./gradlew clean check`, and the `application` plugin puts `distZip`/`distTar` in `assemble`, so `build` genuinely requests packaging. Impact: if the operator wants Linux `build` to succeed, the gate must come off `distZip`/`distTar`/`assembleDist` — at the cost of a distribution archive containing no natives. Documented in both docs so SESSION-003 writes `check` into the workflow.
- **`run` should be gated even though it is not a release task.** Evidence: it needs the same natives; ungated it would fail deep inside LWJGL with an `UnsatisfiedLinkError` instead of a message naming the cause. Impact: none on supported hosts.
- **`-Dos.arch=riscv64` is a faithful simulation of the unsupported branch.** Evidence: it drives the identical `supported == false` path and produced outcomes matching real Linux on every one of the six comparable commands. Impact: none — real Linux evidence is presented as the authority and the simulation only as a Windows-runnable convenience.
- **A Gradle-level contract check is the right form.** Evidence: the contract concerns task dependency wiring and configuration resolution, not application behavior; asserting it from JUnit would require adding a Gradle TestKit dependency, which the non-goals exclude. Impact: the checks live in `build.gradle` and run via `check` rather than appearing in the 302-test count.

## Unresolved Issues
- **This work is uncommitted and no dedicated branch was created.** Operator rule 2 asks for a dedicated branch and one intentional commit per session; the prompt did not authorize committing and the repository is on `main`. Severity: low. Owner: operator.
- **No roadmap status-table update was made.** Row 002 in §7 still reads `Not Started`, and the AUD-040 row in the closure table still reads `Open — not verified`. Editing them was not authorized by this session's prompt, and AUD-040 is in any case only half-closed until SESSION-003. Severity: low. Owner: operator.
- **The original independent audit is still absent from the repository** (carried over from SESSION-001, §2.2 item 3). It did not constrain this session, which worked entirely from `build.gradle`. Severity: medium process gap. Owner: operator.

## New Risks Discovered
- **`build.gradle` `appImageZip` (non-macOS branch) sets `archiveClassifier = platformClassifier`, which is `null` on an unsupported host.** Gradle accepts a null `Property` assignment, so the task would produce `veylon-0.5.0.zip` — the same name `distZip` uses — if it could ever run. It cannot: it is gated twice (`dependsOn requireReleasePlatform` plus a `doFirst` throw), and both were proven on real Linux. Recorded as a latent sharp edge for whoever next edits that block, not as a live defect. No AUD ID proposed.
- **The distribution-group sweep in `verifyPlatformContract` keys on `group == 'distribution'`.** A future packaging task registered with a different group (or no group) and left out of `releasePlatformTaskNames` would escape both the sweep and the list. `run` is exactly such a case today and is handled by being listed explicitly. Mitigation is a code-review habit rather than an automated one; noted so it is not mistaken for total coverage.
- No new runtime, save-format, or gameplay risk: `src/` was not modified.

## Remaining Limitations
| Gate | Status | Reason | Owning session |
|---|---|---|---|
| Linux headless `check` | **Closed by this session** | Real `eclipse-temurin:25-jdk` container run, exit 0, 302 tests | — |
| macOS build/package/validate | **Unavailable** | No macOS hardware. The macOS branch (`ditto`, `PlistBuddy`, `CFBundleVersion` offset, `Veylon.app`) is *code-identical* to before and its classifiers are asserted by `verifyPlatformContract`, but no macOS task was executed. The tag-triggered workflow's macOS legs remain the first real exercise | Release/CI sessions |
| Windows ARM64 | **Unavailable** | Host is x64. `windows-arm64` / `natives-windows-arm64` is mapping-asserted only | Release/CI sessions |
| Linux ARM64 | **Not run** | The container was x86_64. `linux/aarch64` is mapping-asserted as unsupported but not executed | — |
| Branch/PR CI | **Unavailable** | Still no PR workflow; this session deliberately did not add one | SESSION-003 |
| `performanceTest` | **Not run** | Out of scope; no code or budget touched | — |
| GL smoke | **Not run** | Out of scope; renderer untouched | — |
| Real Linux CI runner (GitHub-hosted) | **Not run** | Validated in a local container, not on `ubuntu-latest`. The container matches the JDK 25 / Gradle 9.1.0 shape a runner would use, but toolchain provisioning on a hosted runner is unproven | SESSION-003 |
| Original audit cross-check | **Blocked** | Primary audit document absent from the repository | Operator |

**Platform I could not validate: macOS** (both x64 and arm64) — no hardware. Also unvalidated by execution: Windows ARM64, Linux ARM64, and a GitHub-hosted Linux runner.

## Supported verification matrix (roadmap handoff requirement)

| Task | Windows x64/arm64 | macOS x64/arm64 | Any other platform (incl. Linux) |
|---|---|---|---|
| `compileJava`, `test`, `javadoc`, `check`, `dependencies` | ✅ | ✅ | ✅ |
| `verifyPlatformContract` | ✅ | ✅ | ✅ |
| `run` | ✅ | ✅ | ❌ gated |
| `assemble` / `build` / `distZip` / `distTar` / `installDist` | ✅ | ✅ | ❌ gated |
| `fatJar` / `jpackage` / `appImageZip` / `releaseArtifacts` | ✅ | ✅ | ❌ gated |

**Exact Linux command for SESSION-003:**

```bash
./gradlew clean check --no-daemon --console=plain
```

Requires a JDK 25 (the toolchain has no auto-provisioning resolver configured). Do **not** use `build` on Linux — it pulls `distZip`/`distTar` through `assemble` and is gated off.

## Rollback Instructions
- Commit(s) to revert: none exist yet. If committed and later withdrawn, reverting that single commit restores the configuration-time throw and with it the Linux block (AUD-040 reopens in full).
- Persistent data/artifact compatibility implications: **none.** No save version, generator version, enum order, seeded outcome, dependency version, Java/OpenGL baseline, or public command seam changed. Release artifact names, classifiers and contents are unchanged on supported platforms — verified by building `veylon-0.5.0-windows-x64.zip` with the correct 5 `natives-windows` jars.
- Recovery verification command: `.\gradlew.bat clean check --rerun-tasks --no-daemon --console=plain` — must remain exit 0 with 302/61/0/0/0; and on Linux `./gradlew clean check` must remain exit 0 with the same counts.

## Suggested Follow-up Work
- SESSION-003 should call `check` (not `build`) and can rely on `verifyPlatformContract` running inside it as the platform regression gate.
- When macOS hardware or a macOS CI leg is next available, execute `releaseArtifacts` there once to convert the macOS branch from *asserted* to *executed*; this session could only assert it.

No unrelated improvement is proposed and nothing outside AUD-040's build-configuration scope was changed.

## Recommended Next Session
- Session ID: **SESSION-003 — Add Required Pull-Request CI**
- Why it is unblocked: SESSION-003's stated precondition is "SESSION-002 verified on Linux." Linux `clean check` now passes on a real Linux JDK 25 host with 302/302 tests and zero pre-existing failures, and the exact command is recorded above. Formal `Verified` status still requires the human review below.
- Artifacts/context it must read: this handoff (especially *Supported verification matrix*); `docs/production-readiness/SESSION-001.md`; `ROADMAP.md` §SESSION-003; `.github/workflows/release-build.yml`; `build.gradle` (`verifyPlatformContract` and `requireReleasePlatform`); `docs/DEVELOPING.md` "Platform support". Re-check for a repository instruction file (`AGENTS.md`, `.agents/**`) at session start — at this session's start `.agents/` was still empty and no `AGENTS.md` or repo-level `CLAUDE.md` existed.

## Agent Completion Statement
- Agent Completed: **Yes** — every acceptance criterion was executed and passed, including the real-Linux runs the session exists to enable, and each new assertion was mutation-tested.
- Human Review Required: **Yes**
- Verified: **No** — must remain No until the human reviewer signs below.

Scope note for the reviewer: this session closes only the *build-configuration* half of AUD-040. AUD-040 stays open until SESSION-003 lands a required PR check with a real green run and a deliberate red proof. The reviewer should independently confirm the macOS packaging branch by reading the diff, since no macOS task was executed anywhere in this session.

## Human Review Decision
- Reviewer:
- Date:
- Decision: Verified / Rework Required / Blocked / Rejected
- Reproduction performed:
- Residual risk accepted (if allowed by §11):
- Signature/reference:
