# VEYLON — Asset Pipeline (solo-dev workflow)

Everything lives under `src/main/resources/assets/` and is loaded from the
classpath by `com.veylon.gfx.ResourceManager`. Missing assets never crash the
game: textures fall back to a magenta/black checker and are reported at startup.

```
assets/
  textures/blocks/   optional PNG overrides for block tiles (64×64)
  fonts/             SourceSans3-*.ttf + license
  shaders/           *.vert / *.frag GLSL files (edit + relaunch, no build step)
```

## Block materials (the common case)

1. Every visible `BlockType` maps to a `BlockMaterial` in
   `com.veylon.gfx.MaterialRegistry` by **stable string id** (e.g. `"grass"`,
   `"log"`). The mapping is explicit — new BlockType ⇒ add one `register(...)`
   line, or the startup validator will name what's missing.
2. A `BlockMaterial` declares: `top`, `side`, `bottom` tile ids, variant count,
   tint mode (NONE / FOLIAGE), alpha-cutout, emissive (0..1), roughness (0..1).
3. Tile ids resolve to layers of one 64×64 `TextureArray`. For each tile id the
   loader first looks for `assets/textures/blocks/<tileId>.png` (any square PNG,
   resampled to 64×64); if absent, `ProceduralTextures.generate(tileId, variant)`
   produces a deterministic tile. **To replace programmer textures with authored
   art later, just drop PNGs in that folder — no code changes.**
   Variants use `<tileId>_2.png`, `<tileId>_3.png`… (variant 1 = base name).
4. Natural variants are chosen per block from a world-position hash — stable,
   deterministic, no save impact.

## Shaders

GLSL under `assets/shaders/`. Loaded by filename (`chunk.vert`, `chunk.frag`,
`sky.frag`, `water.frag`, `post_bright.frag`, `post_blur.frag`,
`post_final.frag`, `entity.vert`…). Compile errors print the file name and the
info log. `#include` is not supported — keep shaders flat.

## Entity / prop models

`com.veylon.gfx.model.ModelPart` = cuboid with pivot, offset, size, color,
emissive, children. Species builders live in `CreatureModels` /`NpcModels` /
`HeldItemModels`; procedural animation in `Animator` (state-driven clips: walk,
run, stalk, attack, rest, death…). To add a creature: build its part tree in
`CreatureModels`, map its `CreatureType`, optionally add a pose case in
`Animator`. ~40 lines for a new quadruped.

## Item icons

`IconAtlas` builds one atlas at startup:
- Items that place blocks → isometric mini-cube composited from the block's real
  top/side textures.
- Everything else → category template (tool silhouettes, food, medical, gear,
  material pouch…) tinted with the item's color, plus per-item overrides.
To customize a single icon: add a case in `IconAtlas.drawSpecific()`.

## Particles

`ParticleSystem` (simulation, unchanged data layout) + `ParticleRenderer`
(instanced quads, 2 draw calls: alpha + additive). Sprites are procedural
(circle-soft, streak, flake). New effect = new emitter method on ParticleSystem
choosing color/size/life/sprite/blend.

## Fonts

`FontRenderer` bakes Source Sans 3 at load (Latin-1 + Turkish ranges) into one
atlas per size bucket. UI code uses logical pixels; UI scale multiplies at draw.

## Budgets

- Terrain tiles 64×64; UI icons 32×32 in-atlas (authored logic at 128 grid);
  models ≤ ~40 cuboids; particle cap 4000 (instanced, cheap).
