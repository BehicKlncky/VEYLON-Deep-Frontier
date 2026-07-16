# VEYLON — Art Direction

Stylized voxel terrain + articulated cuboid props/creatures. Low-res (64×64)
hand-noise textures, soft but directional lighting, restrained palette. Never
photoreal; silhouettes and color temperature do the storytelling.

## Visual language (color families)

| Domain | Palette | Notes |
|---|---|---|
| Nature | desaturated greens `#6a8f4f`, soil browns `#7a5a3a`, cold stone `#8a8c92` | never candy-saturated |
| Safe space / fire | warm amber `#ffb347`, orange `#ff7a1a` | campfires, torches, lit windows |
| Human tech | worn off-white `#d8d4c8`, dark gray `#3a3d42`, safety orange `#e8641e` accents | crash pods, scrap |
| Veylon tech / beacon | cyan `#39d0d8`, teal `#1e8f96` emissive | the "rescue" color |
| Ancient ruins | cold stone `#585c6e` + restrained violet `#8a6fd0` / cyan energy | resonant cores |
| Toxic / corrupted | yellow-green `#a8c832` | ONLY used for danger |
| Blood / injury | dark readable red `#8f1a1a` | not neon |
| Night | dark navy ambient, warm firelight contrast | navigable, silhouette-first |

## Biome identities

| Biome | Ground/rock | Vegetation silhouette | Fog/sky | Accent | Motif |
|---|---|---|---|---|---|
| Meadow | vivid-ish grass / gray stone | round leafy trees, tall grass | clear warm blue | wildflower white | open rolling hills |
| Pine Forest | dark grass / mossy stone | tall narrow pines | cool blue-green, denser fog | deep green | tree columns |
| Rocky Highlands | bare stone / gravel | sparse scrub | thin dry haze | slate blue | exposed rock strata |
| Wet Marsh | murky grass / clay | low bushes, reeds | green-gray mist | murky teal | standing water |
| Cold Ridge | snow / ice | frosted pines | pale white-blue, bright | ice cyan | snowfields |
| Dry Scrubland | sand / dried clay | thorn bushes | warm yellow haze | ochre | heat shimmer flats |

Foliage tint is biome+season driven (uniform, shader-side): Wet = lush,
Frost = desaturated gray-green + frost on top faces, Dry = yellowed.

## Simulation states must be visible

- **Wet**: top surfaces darken ~25% and get a specular kick while raining.
- **Snow/frost**: cold biomes/season whiten upward faces (shader accent).
- **Ashfall**: global desaturation + gray flakes + darker sky.
- **Toxic fog**: short yellow-green fog, sickly grade, floating motes.
- **Fire**: warm point light, smoke + embers, scorched (ASH) blocks stay black.
- **Night**: cool blue ambient; block lights are warm amber — warm/cool contrast.
- **Dawn/dusk**: low warm sun, long shadows, orange horizon band.

## Lighting rules

- One sun/moon directional + hemisphere ambient (sky color above, ground bounce
  below). Vertex AO everywhere. One PCF shadow map.
- Sun color: 5500K noon → 2600K at horizon; moon: dim steel blue.
- Block light (torch/fire/beacon) is warm `(1.0, 0.62, 0.28)` except beacon/core
  cyan handled by emissive textures.
- Exposure slightly darker at night; bloom only from genuine emissives.

## Restraint checklist (every feature must pass)

- Does it read at 100% UI scale, 720p, in fog, at night?  
- Is saturation ≤ the palette table?  
- Bloom only on emissives; fog never hides gameplay inside 12 m; camera effects
  optional and subtle.
