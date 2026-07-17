# VEYLON — Asset Provenance & Licenses

Every asset in the repository, its origin, and its license. Update this file
whenever an asset is added or replaced.

| Asset | Path | Origin | License |
|---|---|---|---|
| Source Sans 3 Regular | `src/main/resources/assets/fonts/SourceSans3-Regular.ttf` | Adobe Fonts, github.com/adobe-fonts/source-sans (release branch, fetched 2026-07-14) | SIL OFL 1.1 (see `LICENSE-SourceSans3.md` alongside) |
| Source Sans 3 Semibold | `src/main/resources/assets/fonts/SourceSans3-Semibold.ttf` | same | SIL OFL 1.1 |
| All block/terrain textures (incl. 0.3.0 basalt, sulfur/saltpeter ores, glow fungus, ladder, lantern, marker, keg, gate, stone brick, bell, cage tiles) | generated at runtime by `com.veylon.gfx.ProceduralTextures` | original, written for this project | project-owned; no repository-wide redistribution license declared |
| All UI icons (incl. 0.3.0 bow/firearm/bomb/ammo/powder icons) | generated at runtime by `com.veylon.gfx.IconAtlas` | original | project-owned; no repository-wide redistribution license declared |
| All sounds (incl. 0.3.0 gunshots, explosions, fuses, bells, bow, reload) | synthesized at startup by `com.veylon.engine.AudioManager` | original DSP code, no samples | project-owned; no repository-wide redistribution license declared |
| All particle sprites | generated at runtime by `com.veylon.gfx.ParticleRenderer` | original | project-owned; no repository-wide redistribution license declared |
| All entity/prop models | code-defined cuboids in `com.veylon.gfx.model.*` | original | project-owned; no repository-wide redistribution license declared |
| All shaders | `src/main/resources/assets/shaders/*` | original | project-owned; no repository-wide redistribution license declared |

Rules:
- No assets copied from copyrighted games, ever.
- Third-party assets must be OFL/CC0/commercially licensed with the license file
  committed next to the asset and a row added here.
- PNG overrides dropped into `assets/textures/blocks/` must get a row here.
