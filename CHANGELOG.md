# Changelog

All notable user-facing changes to VEYLON: Deep Frontier are recorded here.

## [0.2.0] - 2026-07-16

### Added

- Procedural 64×64 terrain material system with stable visual IDs, variants,
  biome/season tinting and documented PNG override support.
- Forward PBR-lite rendering with vertex AO, directional lighting, PCF shadows,
  wet/frost response and gamma-correct HDR output.
- Procedural sky, animated depth-tinted water, bloom, tonemapping, FXAA and
  gameplay-state color/vignette effects.
- Articulated procedural models and animation states for all creatures, NPC
  roles and first-person held items.
- Instanced particle rendering, mining cracks, tracks, blood, beacon and weather
  effects.
- Source Sans 3 UI rendering, generated icons for all 70 items, redesigned HUD,
  title/loading/death/victory presentation and persistent graphics options.
- Deterministic capture scenes, OpenGL diagnostics, render statistics, automated
  save/load smoke testing and a configurable `VEYLON_MIN_FPS` release gate.
- Unit tests protecting graphics settings, procedural textures, stable visual IDs,
  window sizing and serialized enum ordering.
- Self-contained native packages for Windows x64, macOS Apple Silicon and macOS
  Intel, built on matching GitHub-hosted runners.

### Changed

- Default presentation now starts at a title screen instead of entering a world
  immediately.
- Chunk meshes now use textures, normals, lighting, AO and dedicated water data.
- Far GPU chunk meshes are released according to the configured render distance.
- Release tooling now selects LWJGL natives by operating system and CPU architecture
  and produces a self-contained app-image ZIP for each supported target.

### Compatibility

- Save format remains binary v2; `BlockType` and `ItemType` serialized order is
  unchanged from v0.1.0.
- Windows 10/11 and macOS (Apple Silicon or Intel) are supported; OpenGL 3.3 Core
  remains the platform/API baseline.
- The regular distribution and fat JAR require JDK 25. The `-windows.zip`
  and `-macos-*.zip` app-images include their own Java runtime.

### Known limitations

- Exact 2560×1440 visual validation was not available on the 1080p validation
  display; 2560-wide layout and exact fullscreen 1920×1080 were tested.
- Lighting is not flood-filled, water uses full cells, and entity animation is
  procedural cuboid animation rather than skeletal animation.
- NVIDIA may emit one-time shader-state recompilation performance messages;
  validation observed no OpenGL errors or steady-state frame-time impact.
- macOS packages are not yet Developer ID signed or notarized, so Gatekeeper may
  require explicit first-launch approval.

## [0.1.0]

- Initial playable survival simulation release.
