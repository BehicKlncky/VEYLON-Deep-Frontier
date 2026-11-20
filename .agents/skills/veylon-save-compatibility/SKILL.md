---
name: veylon-save-compatibility
description: Keep VEYLON saves strict within one save epoch, and raise the epoch when a change would load a save into another world or state. Use when changing serialized state, enum tables, save/load code or seeded world generation.
---

# VEYLON saves and the save epoch

All paths below are relative to the repository root. The rule is the owner's save policy: a build opens only saves of its own save epoch ("Save compatibility policy", "The save epoch", "The checksum and the summary" and "Slots and backups" in `docs/ARCHITECTURE.md`; decisions D-01, D-15 to D-19, D-21 and D-22 in `docs/roadmap/v0.9.1/DESIGN.md`). Read those, then the code in `src/main/java/com/veylon/save/`. Read only the contributor rules in `docs/DEVELOPING.md` that apply, starting with "Saves and the save epoch".

## What the policy rules out

- No migration path, legacy reader, absence default for a section every save holds, frozen generator version, content or layout revision kept for old saves, downgrade promise or historical save fixture. Do not add one, and remove one you find.
- A refusal test builds a foreign or older header in code (`src/test/java/com/veylon/save/ForeignSaves.java`); it never loads a stored file of an earlier build.

## Design the change

- The header (`SaveHeader`) is magic, format, epoch, seed and generator. Every format keeps magic, format and epoch at the same offsets. `SaveSystem.open` refuses another format or epoch before it builds anything; keep that order.
- Within one epoch the reader accepts exactly what the writer writes: one version per section (per generator where the city's differ), every always-written section present (`V3ExtensionSections.ALWAYS_WRITTEN`), an unknown section id refused, ordinals and stable ids validated. A section written only when it holds something means exactly "nothing" when absent.
- Decide whether each new field is persistent or transient and how it resets on new world, on load and, for NPCs, on dormancy and reactivation. Do not persist temporary state just because a field exists.
- New state goes into a stable-id, length-prefixed section with an int version and bounded counts (`readCount`), not into the core body.
- Persisted enums: append rather than reorder, so ordinals stay stable while an epoch is developed. `SerializedEnumOrderTest` pins the orders.
- Terrain and settlement and city structure are regenerated from seed and generator plus deltas. Only generators 3 (wilderness) and 20 (city) may change on purpose; re-record their pins in `WorldGeneratorFingerprintTest`. Generators 1, 2 and 4–19 stay as they are until 0.9.2 retires them. Keep generation and planning order-independent and seeded.
- Keep durable publication: sibling temporary file, flush, atomic replacement, the writer's refusal of any count or size its reader would refuse. A failed save keeps the previous file; never write the destination directly.
- After the header come the checksum (`SaveChecksum`: CRC-32C of the whole file with its own four bytes read as zero) and the summary (`SaveSummary`, at most 1 KiB, read by the slot picker without a world). A load checks the checksum last, so the reader's own refusals still name their cause. A test that edits save bytes reseals them (`SaveSeal.resealed`); compare two saves of one state with `SaveSeal.withoutSummary`. A new summary field changes the layout like any other; its mode, mark and clock must match the body (D-22).
- The game's saves go through `SaveSlots` (three slots, two rotating backups each, D-21). A failed save keeps the current file and both backups; the slots list, move and delete only their own file names, never `saves/veylon.sav` or a file the game did not write (D-15).

## The epoch guard

`SaveEpochGuardTest` digests the layout (a sample save of each world kind), the stored enum tables and both world kinds' generation, as five recorded inputs.

- It fails while the current epoch is unreleased: check that the change was meant to alter saves, then paste the values it prints into `RECORDED`.
- It fails while `RELEASED` names the current epoch: raise `SaveHeader.EPOCH` by one and record the new epoch's inputs. Never edit or remove a `RELEASED` entry; a release adds its epoch with the digest the passing test prints.
- An appended enum constant counts as a change: a build of the same epoch must read every save of that epoch.

## Verify the affected behavior

Use the production save and load path and fixed seeds. Select tests according to the change:

| Change | Relevant coverage |
| --- | --- |
| Header, refusal, messages | `SaveEpochRefusalTest`, `SaveRefusalRoutingTest` |
| Checksum, summary, slots and backups | `SaveSummaryTest`, `SaveSlotsTest`, `SlotPickerRoutingTest`, `SaveSlotIsolationTest` |
| Core body or extension tail | `CoreSaveRoundTripTest`, `CorruptSaveResilienceTest` |
| A section | its section test, such as `BodiesSectionTest`, `RemainsSectionTest`, `GameModeSectionTest` or `CreativeControlsSectionTest` |
| City sections | `UrbanIntegratedSaveTest`, `UrbanSaveCorruptionTest` |
| Ordinals | `SerializedEnumOrderTest` |
| Terrain, plans or RNG | `WorldGeneratorFingerprintTest`, `WorldSeedDeterminismTest` |
| Anything above | `SaveEpochGuardTest` |
| Session reset or load over a live world | `GameLoopIntegrationTest` |

For the save package on Windows:

```powershell
.\gradlew.bat test --tests "com.veylon.save.*" --console=plain
```

Use `./gradlew` on macOS. Run other relevant classes with their package-qualified names, then the portable build for code changes. Add round-trip, refusal and corruption cases where the change creates a new one.

Document the persistent or transient decision and any new section in `docs/ARCHITECTURE.md` and `docs/DEVELOPING.md` when they change. In the handoff say whether the guard's inputs moved, whether they were re-recorded, and whether the epoch rose.
