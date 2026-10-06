---
name: veylon-release
description: Prepare VEYLON versions, release notes and native packages, or diagnose release packaging; use when release work is requested.
---

# VEYLON release preparation

All paths below are relative to the repository root. Read `build.gradle`, `.github/workflows/release-build.yml`, the top of `CHANGELOG.md` and the latest relevant `docs/releases/` note. Follow the Git conventions in root `AGENTS.md`.

## Prepare a consistent version

1. Establish the requested target version and included changes from the task and Git history. If the target is unspecified, prepare the change inventory before asking for the missing version; do not invent one.
2. Update `build.gradle` and `src/test/java/com/veylon/qa/ReleaseVersionTest.java` together. Search README and relevant release/package references for the previous version; preserve historical version references and compatibility statements.
3. Move only the included `[Unreleased]` entries into the new dated changelog section and keep `[Unreleased]` above it. Create `docs/releases/vX.Y.Z.md`: the tag workflow uses this exact path as the release body.
4. Update README's current release and artifact examples. Update architecture/development docs only where behavior changed; derive any suite counts from actual reports. Explain save compatibility in both directions where relevant.

## Validate and package

```powershell
.\gradlew.bat releaseArtifacts --no-daemon --console=plain
```

Use `./gradlew` on macOS. This task includes the portable build, tests/doclint, fat JAR and app-image ZIP; it does not run hardware-calibrated performance gates. Use `--rerun-tasks` when a fresh execution is needed for release evidence rather than relying on up-to-date outputs.

- Build on each target platform: Windows x64, macOS arm64, macOS x64. A Windows package does not validate a macOS package. Follow the workflow's archive checks for launcher, bundled runtime, native classifier and Java options.
- Preserve `--enable-native-access=ALL-UNNAMED` and macOS `-XstartOnFirstThread`. macOS packaging intentionally offsets the internal bundle major version while restoring the public marketing version; do not normalize those two values to match.
- Use the QA section of `docs/DEVELOPING.md` for relevant native smoke/captures. Record actual environment, commands and final results; separate checks that passed from checks unavailable on this host.
- Run performance gates when the release scope requires them, using `veylon-performance`. A portable build passing is not evidence that performance or native visual checks passed.
- Confirm expected ZIP names in `build/distributions/` against the workflow and target version. Report local artifact paths and remaining platform checks.

## Git and publication

- Historical release preparation uses `chore(release): prepare vX.Y.Z`, followed by `merge: release vX.Y.Z` for release merges. Follow this naming only for actions included in the user's request.
- Add a factual `Validation:` paragraph to a substantial release commit. Do not add `Co-Authored-By:` trailers or agent signatures; do not change Git identity or rewrite old commits to remove credits.
- Preparing files/packages does not itself authorize committing, merging, tagging, pushing or publishing. Honor authorization already given in the session; do not ask again for an authorized action.
- A pushed `v*` tag triggers the platform matrix and automatic GitHub Release publication. Before an authorized tag push, verify the tag equals `v` plus the project version and the release note exists. Do not use a tag push as a dry run.
