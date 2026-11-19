# Roadmap records

The repository's record of each planned release after 0.9.0. Every version keeps two files:

- `vX.Y.Z/DESIGN.md`: the owner decisions that bind the release, its own decisions (D-01
  onward), what it does not include, its defect ledger and its open owner questions.
- `vX.Y.Z/PROGRESS.md`: one section per milestone, with its status, changed paths, the commands
  actually run and their results, its commits, pending gates and the next step.

The agent prompt packs behind these releases live in `build/release-roadmap/`, which Git
ignores. They were written against `41dbfd6` (0.9.0) on 2026-11-06 and revised on 2026-11-19.
Current source, these records and the owner's later instructions take precedence over them.
Never run `gradlew clean`: it deletes the packs and the evidence logs under `build/`.

## Owner decisions

Only the owner can change these.

| Id | Date | Decision | Binds |
| --- | --- | --- | --- |
| O1 | 2026-11-06 | Only saves written by the current version must open. Saves of earlier releases and of the development generators 4–19 may be refused. The text is the [save compatibility policy](../ARCHITECTURE.md#save-compatibility-policy-owner-decision-2026-11-06). | Every release |
| O2 | 2026-11-06 | Forgotten defects are closed: what earlier work recorded as pre-existing, not fixed or left for later, and is still present, gets fixed unless the owner accepts it. | 0.9.1, then each release's audit |
| O3 | 2026-11-06 | Three save slots. | 0.9.1 |
| O4 | 2026-11-06 | **Infection.** A bite always infects, but only when an infected keeps landing unresisted hits from very close; it then seizes the survivor and bites the forearm in a special animation. Untreated within a set time, the survivor turns in stages into a creature that keeps what they carried, and the player goes on as a new survivor who can hunt that former self. Only a very rare medicine found as loot cures it. An attack can also scratch; an undisinfected scratch infects at 6 %. | 0.10.0; other people in 0.13.0 and 0.14.0 |
| O5 | 2026-11-06 | A graphics release headlined by a longer render distance that uses strong hardware the way other modern games do. | 0.11.0 |
| O6 | 2026-11-19 | VEYLON is not a story game, but it rests on a solid story. [`docs/lore/CANON.md`](../lore/CANON.md) is the reference; the owner approved all of it the same day, with option A for the wilderness world kind, "The Long After" (CANON §15). A new story fact needs the owner. | 0.9.3 onward; audited in 0.12.0 |
| O7 | 2026-11-19 | A realistic, immersive city map in the manner of Project Zomboid's: a centre with tall buildings, shops and institutions such as a gun store, a grocery and a library, and varied apartments. Project Zomboid is a design reference only (urban R9 holds). | 0.9.3, 0.9.4 |
| O8 | 2026-11-19 | The city ends: beyond its suburbs the land turns rural, then natural like the wilderness. Supersedes urban R1. | 0.9.3, 0.9.4 |
| O9 | 2026-11-19 | Intermediate releases 0.9.3 and 0.9.4 come before the infection release. | The order below |

For 0.9.1, O6–O9 mean one rule: fix defects in the city layout code that 0.9.3 replaces, but do
not polish or extend it.

## Versions

Each version starts after the previous one's release-preparation milestone, unless the owner
waives that. Each version's milestone 01 creates its records and revalidates its pack against
the code as it then is. Status lives in each `PROGRESS.md`.

| Version | Title | Records |
| --- | --- | --- |
| 0.9.1 | Safe Saves | [DESIGN](v0.9.1/DESIGN.md), [PROGRESS](v0.9.1/PROGRESS.md) |
| 0.9.2 | Clean Slate | Not yet created |
| 0.9.3 | City Limits | Not yet created |
| 0.9.4 | Landmarks & Backroads | Not yet created |
| 0.10.0 | Bite & Shadow | Not yet created |
| 0.11.0 | Far Horizon | Not yet created |
| 0.12.0 | Roots & Records | Not yet created |
| 0.13.0 | The Living City | Not yet created |
| 0.14.0 | Kin | Not yet created |
| 1.0.0 | Frontier Release | Not yet created |
