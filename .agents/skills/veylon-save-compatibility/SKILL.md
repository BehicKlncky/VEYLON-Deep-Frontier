---
name: veylon-save-compatibility
description: Preserve VEYLON save compatibility when changing serialized state, enum ordinals, save/load code or seeded world generation.
---

# VEYLON save compatibility

All paths below are relative to the repository root. Start with the save/load section of `docs/ARCHITECTURE.md`, then the relevant code in `src/main/java/com/veylon/save/`. Read only the contributor rules in `docs/DEVELOPING.md` that apply to the changed state.

## Design the change

- Inspect `SaveSystem.java` and `V3ExtensionSections.java` before changing serialization. The current writer uses binary v3 and loads v2; preserve the frozen base layout and existing legacy migration behavior.
- Persisted enums are append-only. Check `SerializedEnumOrderTest` before editing block, item, biome, affliction, settlement or entity-state enums.
- Decide whether each new field is persistent or transient and how it resets on new world, load and, for NPCs, dormancy/reactivation. Do not persist temporary state just because a field exists.
- Extend through the existing section mechanism with a stable ID, bounded payload and explicit absent-section defaults. Inspect the current reader's handling of unknown IDs and unsupported versions rather than assuming they are equivalent.
- Existing compatibility distinction: older readers can skip a new optional ID such as `world.fragments`; readers that know `world.bodies` but reject its newer version can reject the entire save. Document both old-save loading and older-build reading behavior when relevant.
- Terrain and settlement structure are regenerated from seed/version plus deltas. Preserve legacy generation and planning order-independence; intentional new terrain behavior must follow the existing generator-version mechanism.
- Retain input validation: bounded counts via `readCount`, validated ordinals, finite/bounded numeric values and explicit rejection of malformed sections.
- Retain durable publication through a sibling temporary file, flush and atomic replacement. A failed save must preserve the previous file; do not replace this with a direct overwrite.

## Verify the affected behavior

Use the existing fixtures and production save/load path. Select tests according to the change:

| Change | Relevant existing coverage |
| --- | --- |
| Base reader/writer or migration | `SaveMigrationTest`, `HistoricalV020SaveCompatibilityTest`, `CorruptSaveResilienceTest` |
| Extension state | Relevant section tests: `BodiesSectionTest`, `FragmentsSectionTest`, `GameModeSectionTest`, `CreativeControlsSectionTest` |
| Ordinals | `SerializedEnumOrderTest` |
| Terrain or RNG | `WorldGeneratorFingerprintTest`, `WorldSeedDeterminismTest` |
| Session reset/load over a live world | `GameLoopIntegrationTest` |

For save-package coverage on Windows:

```powershell
.\gradlew.bat test --tests "com.veylon.save.*" --console=plain
```

Use `./gradlew` on macOS. Run other relevant classes with their actual package-qualified names, then the portable build for code changes. Add meaningful round-trip, missing-section/default or corruption coverage where the change creates a new case; never regenerate authentic historical fixtures merely to make tests pass.

Document the compatibility result and persistent/transient decision in the existing architecture/development docs when they change. In the handoff distinguish verified old-save loading, newer-save downgrade behavior and any untested direction.
