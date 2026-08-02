# SESSION-003 Handoff — Add Required Pull-Request CI

## Identity
- Objective: Add a branch/PR workflow that enforces the portable quality gate on every change.
- Audit finding(s): AUD-040. SESSION-002 closed the *build-configuration* half (Linux can run `check`). This session closes the *workflow* half. **AUD-040 is not fully closed by this session** — see *Acceptance Criteria Status* and *Unresolved Issues*.
- Starting commit: `8a4458b3310b6ad2bef5d35fb908ef9fc4bf1388` (`main`, `v0.5.0`) — unmoved by this session.
- Branch: `session/003-pr-ci`
- Ending commits:
  - `387e7bf237167bc01eb3a41941426782080999c8` — `build(platform): make headless verification platform-neutral` (SESSION-002's previously uncommitted work; see *Assumptions Made*)
  - `4d5b7f6653446a4bdc6b04ee10cdc1364ec95170` — `ci(pr): gate every change on the portable Linux check`
  - plus the doc commit(s) carrying this file
- Pull request: **[#1](https://github.com/BehicKlncky/VEYLON-Deep-Frontier/pull/1)** — `ci: add required pull-request check (AUD-040 / SESSION-003)`, open, base `main`.
- Agent/reviewer: Claude Opus 5 (agent) / human reviewer pending
- Date: 2026-08-02
- Worktree state at end: clean except pre-existing untracked `ROADMAP.md`. `git diff --check` exit 0.
- Status check name created: **`Portable check`** (exact string, as reported by GitHub's status API).

## Summary of Changes

**What control changed.** Before this session the repository had exactly one workflow, `.github/workflows/release-build.yml`, triggered only by `workflow_dispatch` and `v*` tags. Nothing verified a change on the way in; a regression could merge and stay merged until someone cut a release and packaging finally noticed. `.github/workflows/pr-check.yml` adds one `ubuntu-latest` job that runs the portable gate — `./gradlew clean check` on JDK 25, i.e. compilation, the 302 deterministic tests, doclint and `verifyPlatformContract` — on every pull request against `main` and every push to `main`.

**Why `check` and not `build`.** The `application`/`distribution` plugins put `distZip`/`distTar` in `assemble`, and SESSION-002 deliberately gates packaging off on non-Windows/macOS hosts. `build` therefore *must* fail on a Linux runner by design. `check` is the portable gate. This is exactly the command SESSION-002 handed off.

**Least privilege, item by item.**

| Property | Setting | Why |
|---|---|---|
| Token | `permissions: contents: read` at workflow level, nothing added at job level | Nothing here writes repository state |
| Event | `pull_request`, **never** `pull_request_target` | Untrusted fork code runs with a read-only token and no access to repository secrets |
| Secrets | none referenced by any step | Asserted by the structural validator, not just by reading |
| Checkout | `persist-credentials: false` | No step pushes, tags, or calls the API, so the credential is not left in `.git/config` |
| Timeout | `timeout-minutes: 30` | Bounds a hung run; observed cold-cache duration is 3m04s |
| Concurrency | `group: ${{ github.workflow }}-${{ github.ref }}`, `cancel-in-progress: ${{ github.event_name == 'pull_request' }}` | Superseded PR pushes stop; every commit that lands on `main` keeps its own recorded result |
| Failure artifacts | `if: failure()` only, `if-no-files-found: ignore`, `retention-days: 7` | A green run uploads nothing; a failure that never reached `:test` still does not break the upload step |

**Wrapper validation, both halves.** `gradle/actions/wrapper-validation@v6` checks the committed `gradle-wrapper.jar` against Gradle's published checksums. A four-line `grep` step then asserts that `gradle-wrapper.properties` still carries a 64-hex `distributionSha256Sum` and `validateDistributionUrl=true`, so the pin that commit `33c750d` established cannot be silently dropped. The two cover different artifacts: the JAR in the repository, and the distribution that JAR downloads.

**Explicit non-goals respected.** No performance gate (`performanceTest` is never invoked), no GL/hardware gate, no packaging (`releaseArtifacts`, `jpackage`, `distZip`, `appImageZip` never invoked), no signing, no release-publishing change — `release-build.yml` is byte-for-byte untouched. No dependency version change. No action SHA pinning (Sessions 048–049); action versions follow the repository's existing latest-major-tag convention. **No file under `src/` is modified by this branch.** No repository setting was mutated.

## Files Changed
| Path | Change | Reason |
|---|---|---|
| `.github/workflows/pr-check.yml` | Added (+83) | The change itself |
| `docs/DEVELOPING.md` | Modified (+14 in this session's commit) | New "Continuous integration" subsection: what runs, that it reproduces locally, and the exact required-check string |
| `docs/production-readiness/SESSION-003.md` | Added | This handoff |

Carried on the same branch as the SESSION-002 precondition commit: `build.gradle` (+202/−25), `README.md` (+11), `docs/DEVELOPING.md` (+16), `docs/production-readiness/SESSION-001.md`, `docs/production-readiness/SESSION-002.md`.

`.github/workflows/release-build.yml`, every file under `src/`, `settings.gradle`, `gradle.properties` and all wrapper files are unmodified.

## Tests Added or Updated

No JUnit test was added; the suite count is unchanged at **302**. Verification of a workflow is structural, so it takes two forms:

| Check | Type | What it catches |
|---|---|---|
| `actionlint` (Docker `rhysd/actionlint:latest`, digest `sha256:b1934ee5…`) | Workflow schema + expression + embedded-shellcheck lint | Invalid schema, bad `${{ }}` contexts, shell bugs in `run:` blocks |
| `validate_workflows.py` (36 assertions, scratchpad) | Structural assertions over the parsed YAML tree | Permission escalation, `pull_request_target`, a secret reference, a lost timeout/concurrency block, `build`/`releaseArtifacts`/`performanceTest` creeping into the command, upload not gated on failure, a changed check name, drift in `release-build.yml`'s triggers |

The validator asserts against the *parsed tree*, not the text, so reformatting cannot fool it. It is a scratchpad tool, not a committed one — committing a Python linter would add a toolchain the repository does not otherwise have. Its full output is reproduced in *Validation Results*.

**The real test of a CI gate is a red run**, and that was performed against live GitHub rather than simulated — see *Negative proof*.

## Commands Executed

| Command | Environment | Exit | Result |
|---|---|---:|---|
| `.\gradlew.bat clean check --no-daemon --console=plain` | Windows 10 LTSC 19044, Oracle JDK 25.0.1, Gradle 9.1.0 | **0** | `BUILD SUCCESSFUL in 1m 38s`; `Platform contract verified for windows 10 / amd64` |
| JUnit XML aggregation over `build/test-results/test/TEST-*.xml` | Windows | 0 | **61 classes, 302 tests, 0 failures, 0 errors, 0 skipped** |
| `docker run --rm -v <repo>:/repo:ro rhysd/actionlint:latest -color -verbose` | Docker 28.5.1 | **0** | `Found 0 errors in 2 files` — both `pr-check.yml` and `release-build.yml` |
| `python validate_workflows.py` | Windows, PyYAML 6.0.3 | **0** | `ALL 36 STRUCTURAL CHECKS PASSED` |
| `sha256sum gradle/wrapper/gradle-wrapper.jar` | Windows | 0 | `76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3` — identical to the hash the CI wrapper-validation step accepted |
| `gh api …/branches/main/protection` | GitHub | **1** | `Branch not protected (HTTP 404)` — recorded *before* and *after*; nothing was changed |
| `gh api …/rulesets` | GitHub | 0 | `[]` — no rulesets exist |
| `gh repo view --json viewerPermission` | GitHub | 0 | `ADMIN` — authority exists but was deliberately not used |
| `git push -u origin session/003-pr-ci`, `gh pr create` | GitHub | 0 | PR #1 |
| `gh run watch 30744535202 --exit-status` | GitHub | **0** | Green run |
| `gh run watch 30744579511 --exit-status` | GitHub | **1** | Red run (intended) |
| `gh pr close 2 --delete-branch` | GitHub | 0 | Disposable branch removed |

The local Gradle command is the *same* command the workflow runs, so a CI failure reproduces locally with no CI-specific setup.

## Validation Results

### `actionlint`
```
verbose: Linting all workflow files in repository: /repo
verbose: Collected 2 YAML files
verbose: Found 0 parse errors in 0 ms for .github/workflows/release-build.yml
verbose: Found 0 parse errors in 0 ms for .github/workflows/pr-check.yml
verbose: Found total 0 errors in 9 ms for .github/workflows/pr-check.yml
verbose: Found total 0 errors in 20 ms for .github/workflows/release-build.yml
verbose: Found 0 errors in 2 files
```

### Structural validator — 36/36
Selected assertions (full list in the run output): `pull_request_target` is NOT used; workflow permissions are exactly `{contents: read}`; the job adds no extra permissions; no step references a secret; checkout does not persist credentials; exactly one Gradle invocation and it is `clean check`; no `releaseArtifacts` / `jpackage` / `performanceTest` / `distZip` / `appImageZip` anywhere; upload runs only on `failure()`; both the HTML report and the JUnit XML are uploaded; the job display name is exactly the branch-protection string `Portable check`; `release-build.yml` is still tag-only.

### Green run — PASS
**<https://github.com/BehicKlncky/VEYLON-Deep-Frontier/actions/runs/30744535202>** (run `30744535202`, job `91487774951`, event `pull_request`, PR #1)

| Step | Result | Notes |
|---|---|---|
| Check out source | success | |
| Validate Gradle wrapper | success | `✓ Found known Gradle Wrapper JAR files: 76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3` |
| Verify wrapper distribution is pinned | success | |
| Set up JDK 25 | success | `Resolved Java 25.0.3+9 from tool-cache`, Temurin, `/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/25.0.3-9/x64`. `gradle cache is not found` — cold cache, as expected on the first run |
| Run portable verification | success | Gradle 9.1.0 downloaded and bootstrapped through the pinned checksum; `Platform contract verified for linux / amd64 (supported=false, natives=none)`; **`BUILD SUCCESSFUL in 2m 47s`**, 8 actionable tasks |
| Upload failure reports | **skipped** | Correct — a green run uploads nothing |

Job wall time 3m04s against a 30-minute timeout. The `linux / amd64 (supported=false, natives=none)` line is the load-bearing one: this is a real GitHub-hosted Linux runner executing the whole suite with **zero LWJGL natives on the runtime classpath**, which is the outcome SESSION-002 could previously only demonstrate in a local container.

PR #1 status: `mergeStateStatus=CLEAN`, check `Portable check` = `SUCCESS`.

### Negative proof — the check goes red
Branch `tmp/003-ci-negative-proof` (`session/003-pr-ci` + one impossible assertion in `PathfinderTest.findsAStraightPathOnFlatGround`), PR #2.

**<https://github.com/BehicKlncky/VEYLON-Deep-Frontier/actions/runs/30744579511>** (run `30744579511`, job `91487890302`) — conclusion **`failure`**.

- `Run portable verification`: **failure** at `:test`.
- `Upload failure reports`: **success** — artifact `portable-check-reports`, 149,243 bytes, 144 files.
- Downloading and aggregating that artifact's JUnit XML gives **61 classes / 302 tests / 1 failure / 0 errors / 0 skipped**, naming:
  ```
  com.veylon.ai.PathfinderTest > findsAStraightPathOnFlatGround()
  org.opentest4j.AssertionFailedError: deliberate SESSION-003 CI negative proof ==> expected: <0> but was: <10>
  ```

So the failure artifact is genuinely diagnostic, and the CI-side test count (302) matches the Windows and container baselines exactly.

**The finding that matters:** GitHub reported PR #2 as `mergeable=MERGEABLE`, `mergeStateStatus=UNSTABLE`. `UNSTABLE` means *a check failed but the merge is not blocked* — because no branch protection rule makes `Portable check` required. **A red check does not block a merge today.** This is the honest state of AUD-040 after this session and the reason the manual step below is not optional.

Cleanup: PR #2 closed, branch `tmp/003-ci-negative-proof` deleted on the remote and locally. `git log -S "deliberate SESSION-003 CI negative proof" -- src/` over `session/003-pr-ci` returns **0** commits; the deliberate failure exists nowhere reachable. The run and its artifact remain viewable for audit.

### Concurrency cancellation — demonstrated live
Two commits were pushed to `session/003-pr-ci` in quick succession while PR #1 was open:

Run [`30744839169`](https://github.com/BehicKlncky/VEYLON-Deep-Frontier/actions/runs/30744839169) (head `0f904e3`) was still running when commit `9f6d354` was pushed to the same PR. Its conclusion is **`cancelled`** — superseded, not failed.

Both runs shared concurrency group `PR check-refs/pull/1/merge`, so the older was cancelled by the newer push, which is exactly what `cancel-in-progress: ${{ github.event_name == 'pull_request' }}` is for. `push`-to-`main` runs are excluded by that expression and so each keeps its own recorded result. Later doc-only commits on this branch produce ordinary runs; PR #1's current check conclusion is on the Actions tab and in the PR thread.

### Performance / GL / packaging — NOT RUN
Out of scope. No production code, benchmark, budget, renderer path or packaging task was touched by this session's commit. SESSION-001's figures remain the reference.

### Security / supply-chain
- The workflow token is `contents: read` and no step reads a secret (asserted, not assumed).
- `pull_request` rather than `pull_request_target`, so fork code cannot obtain a writable token or secrets.
- The committed wrapper JAR is validated against Gradle's published checksums on every run, and its local SHA-256 matches what CI accepted.
- Actions are pinned to major tags, not commit SHAs. This is a **known residual risk**, deliberately deferred to Sessions 048–049 by the roadmap's non-goals. `gradle/actions` is a new third-party action for this repository (`actions/*` are first-party); its inclusion should be a conscious human decision at review.

## Acceptance Criteria Status
| Criterion | Result | Evidence |
|---|---|---|
| Workflow triggers for PRs | **Pass** | Run `30744535202`, event `pull_request`, PR #1 |
| Permissions are read-only unless justified | **Pass** | `permissions: {contents: read}`, no job-level addition, no secret reference; validator assertions |
| Stale runs cancel | **Pass** | See *Concurrency cancellation* |
| Failures expose reports | **Pass** | Run `30744579511` uploaded `portable-check-reports` (144 files) naming the failing test and assertion |
| Negative proof produces a red check | **Pass** | Run `30744579511` conclusion `failure`; `Portable check` = `FAILURE` on PR #2 |
| Wrapper validated | **Pass** | `gradle/actions/wrapper-validation@v6` accepted `76805e32…`, plus the distribution-pin assertion |
| Timeouts configured | **Pass** | `timeout-minutes: 30`; observed 3m04s |
| YAML/action validation | **Pass** | `actionlint` 0 errors; 36/36 structural assertions |
| Local equivalent of the CI command runs | **Pass** | `.\gradlew.bat clean check` exit 0, 61/302/0/0/0 |
| **Named check is required by branch protection** | **NOT DONE** | `gh api …/branches/main/protection` → 404 `Branch not protected`; `…/rulesets` → `[]`. Manual step below |
| **Negative proof blocks merge** | **NOT MET** | PR #2 was `MERGEABLE` / `UNSTABLE` with a failing check. Blocked only once the criterion above is satisfied |

Two criteria are unmet, both for the same reason, and both resolved by one manual action.

## REQUIRED MANUAL STEP — make `Portable check` a required check

**This session did not change any repository setting.** The agent holds `ADMIN` on the repository (`gh repo view --json viewerPermission` → `ADMIN`), so it *could* have; it did not, because the session brief specifies this as a manual step and it is an outward-facing settings change on a public repository. Nothing below is assumed — the pre-state was read from the API and is recorded above.

The exact string to require is **`Portable check`** (the job's `name:`). It is already selectable, because a status check only appears in GitHub's picker after it has reported at least once, and it has now reported twice.

**Option A — UI (classic branch protection).**
1. Repository → **Settings** → **Branches** → **Add branch protection rule**.
2. Branch name pattern: `main`.
3. Tick **Require a pull request before merging**.
4. Tick **Require status checks to pass before merging**, and (recommended) **Require branches to be up to date before merging**.
5. In the search box type `Portable check` and select it.
6. **Create**.

**Option B — API, equivalent to the above.**
```bash
gh api --method PUT repos/BehicKlncky/VEYLON-Deep-Frontier/branches/main/protection \
  --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "checks": [{ "context": "Portable check" }]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": { "required_approving_review_count": 0 },
  "restrictions": null
}
JSON
```
All four top-level keys are mandatory in this API even when null. `"strict": true` is the "require branches to be up to date" box.

**Verify afterwards — do not take the call's success as proof:**
```bash
gh api repos/BehicKlncky/VEYLON-Deep-Frontier/branches/main/protection \
  --jq '.required_status_checks.checks, .required_status_checks.strict'
```
must list `Portable check`, and re-opening a PR with a failing check must then report `mergeStateStatus=BLOCKED` rather than the `UNSTABLE` observed above.

**Order of operations, both ways.** Do not create the rule before `pr-check.yml` is merged to `main`, or every PR waits forever on a check that no workflow on the base branch produces. Conversely, per the roadmap's rollback note, if `pr-check.yml` is ever reverted the rule must be removed in the same change.

## Diff Review
- `git diff --check`: exit 0.
- `git status --short` at end: only `?? ROADMAP.md`, which was already untracked before the session.
- `git diff --stat main..session/003-pr-ci`: 6 files, 793 insertions, 25 deletions — of which this session authored `.github/workflows/pr-check.yml` (+83) and 14 lines of `docs/DEVELOPING.md`; the rest is the SESSION-002 precondition commit and its handoff docs.
- No file under `src/` appears in the branch diff. `release-build.yml` does not appear either.
- Unintended changes found and removed: the deliberate test failure, which lived only on the deleted `tmp/003-ci-negative-proof` branch and is provably absent from `session/003-pr-ci`.
- Side effect worth noting: `git fetch --prune` removed three stale *local remote-tracking* refs (`origin/refactor/tech-debt-0.4.0`, `origin/refactor/v0.4.1-stability`, `origin/release/v0.5.0-technical-baseline`). Those branches had already been deleted on GitHub before this session — confirmed against `gh api …/branches`, which lists only `main` and `session/003-pr-ci`. Pruning cannot delete a remote branch, and all corresponding *local* branches are intact.

## Assumptions Made
- **The SESSION-002 work had to be committed on this branch.** Evidence: it was uncommitted, and on `main` as it stands Gradle throws `Unsupported release platform` during *configuration* on Linux, so `ubuntu-latest` cannot configure the build at all — a PR containing only the workflow would have produced a red run for a reason unrelated to the workflow. It is a separate, clearly-labelled commit so a reviewer can evaluate the two independently. Impact: PR #1 carries both halves of AUD-040.
- **`Portable check` is the right required-check string.** Evidence: read back from GitHub's own status rollup on both PRs, not inferred from the YAML. Impact: if the job's `name:` is ever edited, the branch rule silently stops matching and must be updated with it.
- **Latest-major action tags, not SHAs.** Evidence: matches `release-build.yml`'s existing convention; SHA pinning is explicitly assigned to Sessions 048–049. Impact: a tag can move under us; residual supply-chain risk recorded above.
- **`ROADMAP.md` stays untracked.** Evidence: it has been untracked since SESSION-001 and neither prior session committed it; nothing authorised a 300 KB planning document into the repository. Impact: none on CI.
- **The workflow also runs on pushes to `main`.** Evidence: the roadmap's scope says "PR/push verification workflow". Impact: the merged state is verified, not only the pre-merge preview; `cancel-in-progress` is switched off for it so each commit on `main` keeps its own result.

## Unresolved Issues
- **`Portable check` is not a required check.** Severity: **high** — until this is done, AUD-040 is not closed and a red check does not block a merge (proven, not assumed). Owner: operator. Fix: the manual step above.
- **PR #1 is open, not merged.** The workflow only gates changes once it is on `main`. Severity: medium. Owner: operator.
- **No roadmap status-table update.** Rows 002/003 in §7 and the AUD-040 row in the closure table are unchanged; editing them was not authorised and `ROADMAP.md` is untracked anyway. Severity: low. Owner: operator.
- **The original independent audit is still absent from the repository** (carried from SESSION-001/002). Severity: medium process gap. Owner: operator.

## New Risks Discovered
- **The required-check string is coupled to the job's `name:`.** Renaming the job breaks the branch rule silently — the rule waits for a check that will never report, and PRs hang instead of failing loudly. Recorded in `docs/DEVELOPING.md` so the coupling is visible at the point of edit. No AUD ID proposed.
- **`gradle/actions` is the repository's first non-`actions/*` action.** Unpinned by tag, per the roadmap's deferral. A compromised tag would run in a job that holds only a read-only token and no secrets, which bounds but does not eliminate the exposure. Sessions 048–049 own the fix.
- **Cold-cache duration is 3m04s of a 30-minute budget.** Ample headroom now; if the suite grows, the timeout is the thing to revisit, not the runner.
- No new runtime, save-format or gameplay risk: this session's commit touches no file under `src/`.

## Remaining Limitations
| Gate | Status | Reason | Owning session |
|---|---|---|---|
| Linux headless `check` on a real GitHub runner | **Closed by this session** | Run `30744535202`, exit 0, zero natives | — |
| Red check on a broken change | **Closed by this session** | Run `30744579511`, conclusion `failure`, diagnostic artifact | — |
| Required check / merge blocking | **NOT DONE** | Repository setting; deliberately not mutated | Operator (manual step above) |
| macOS build/package/validate | **Unavailable** | No macOS hardware; not in this session's scope either | Release/CI sessions |
| Windows ARM64 | **Unavailable** | Host is x64 | Release/CI sessions |
| `performanceTest` | **Not run** | Out of scope; hardware-calibrated, cannot live on a shared runner | Sessions 055–056 |
| GL smoke | **Not run** | Out of scope; needs a GPU | Sessions 055–056 |
| Fork-PR behaviour | **Not executed** | Configured correctly (`pull_request`, read-only token, no secrets) and asserted structurally, but no fork PR was opened to observe it | Future |
| Action SHA pinning | **Not done** | Explicit non-goal | Sessions 048–049 |

**Not validated by execution:** fork-PR behaviour, macOS, Windows ARM64, and the merge-blocking behaviour of a required check (which cannot be observed until the rule exists).

## Rollback Instructions
- To withdraw: revert `4d5b7f6` (deletes `pr-check.yml` and the CI doc section). If the branch rule has been created by then, **remove the rule in the same change**, or `main` acquires a permanently unsatisfiable required check.
- Reverting `387e7bf` as well restores the configuration-time platform throw and reopens AUD-040 in full; the workflow cannot function without it.
- No persistent-data implication: no save version, generator version, enum order, seeded outcome, dependency version, Java/OpenGL baseline or public command seam changed.
- Recovery verification: `.\gradlew.bat clean check --no-daemon --console=plain` must remain exit 0 at 61/302/0/0/0.

## Suggested Follow-up Work
- Merge PR #1, then apply the branch rule, then verify with the two commands above. In that order.
- Sessions 048–049 should pin `actions/checkout`, `actions/setup-java`, `actions/upload-artifact` and `gradle/actions/wrapper-validation` to commit SHAs across **both** workflows.
- Sessions 055–056 own the performance and GL gates; they must not be attached to this job, which is deliberately hardware-independent.
- A future session could publish the JUnit XML as a PR check annotation, which would surface the failing test name in the PR without downloading the artifact. Not required by AUD-040.

## Recommended Next Session
- Session ID: the next implementation milestone in `ROADMAP.md`, **once the manual branch-protection step is done**.
- Why the gate matters first: every later session's "no regression" claim leans on this check actually being enforced. Until the rule exists it is advisory.
- Artifacts/context the next session must read: this handoff; `docs/production-readiness/SESSION-002.md` (*Supported verification matrix*); `.github/workflows/pr-check.yml`; `docs/DEVELOPING.md` "Continuous integration". `.agents/` is still empty and there is still no `AGENTS.md` or repo-level `CLAUDE.md`; re-check at session start.

## Agent Completion Statement
- Agent Completed: **Partial.** Every criterion within the agent's authority was executed and passed, including the real green run and the real red run. The two remaining criteria — required check, merge blocking — are a repository-settings change this session deliberately did not make.
- Human Review Required: **Yes**
- Verified: **No.** Per the roadmap's completion gate ("Green real run plus enforced required check, otherwise `Human Review Required`"), this session's status is **Human Review Required**, and **AUD-040 stays open** until `gh api …/branches/main/protection` shows `Portable check` and a failing check is observed reporting `BLOCKED`.

## Human Review Decision
- Reviewer:
- Date:
- Decision: Verified / Rework Required / Blocked / Rejected
- Reproduction performed:
- Branch rule applied and verified (command output attached):
- Residual risk accepted (if allowed by §11):
- Signature/reference:
