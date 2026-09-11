# Signal & Silence — sonic direction

Veylon should sound inhabited before it sounds scored. The player needs to hear
footing, weather, nearby fire and approaching danger. Music is an occasional
response to a situation; long gaps are part of its arrangement. Every waveform
is original project DSP. No recordings, sound fonts or decoding libraries.

## Situations and biomes

| Place / situation | Direction |
| --- | --- |
| Meadow | Broad rain when exposed; separate, irregular insect calls after dark; restrained open-air reflections |
| Pine forest | More absorbed high-frequency reflections and a shorter diffuse tail than stone spaces |
| Marsh | Rain detail and night insects establish wetness; avoid another permanent tonal drone |
| Rocky highlands | Air movement has a midrange body and upper hiss; gust changes are independent of loop periods |
| Cold ridge | Wind carries the scene; snow does not reuse liquid-rain transients |
| Scrubland | Open, quiet space between events; do not add meadow crickets indiscriminately |
| Shallow underground | Low ventilation bed, isolated drips and a modest stone decay |
| Deep caves | Longer dark reverb and sparse low, unresolved musical intervals; world events remain readable |
| Shelter | Rain loses direct brightness while the enclosure contributes reflections; fire has a physical position |
| Fortress / ruin | Stone reflections distinguish enclosed scale from open terrain |
| Threat | Reports, impacts and warning calls lead; the music response remains below those cues |
| First night | A brief vulnerable phrase, once per world session when the first night is encountered |
| Repaired beacon | A restrained consonant signal phrase responds to proximity; the audible beacon retains its own identity |

This table records intent, not listening approval. Each implemented phase and
its available evidence is recorded in [AUDIO_UPGRADE_PLAN.md](AUDIO_UPGRADE_PLAN.md).

## Mix hierarchy and targets

1. Immediate danger, damage and important interface feedback.
2. Local physical action: weapons, impacts, footing and positioned fire.
3. Environmental beds and sparse environmental details.
4. Music, with silence between phrases.

Individual prepared buffers have absolute mean below 0.00001, peak at most
0.88 full scale, and RMS between 0.001 and 0.5. These are linear PCM engineering
guards, not LUFS mastering claims. Existing six ambience channel multipliers are
0.65 / 0.50 / 0.55 / 0.40 / 0.30 / 0.35 for rain / wind / fire / cave /
insects / beacon; added layers share their parent channel's allocation.
Decorrelated emitters divide that allocation to avoid gaining loudness merely
by adding width. Loudness and perceived balance still require listening on the
target device. OpenAL's output mixer and driver may add their own processing.

Weather intensity should change the ratio of low body to high sizzle, in
addition to the existing state-driven volume. A heavier storm gains body;
drizzle leaves more space around pitched droplets. Loops contain continuous
texture only. Drops, fire pops, cave drips and insect chirps are scheduled by
the presentation RNG at runtime, with bounded work each frame.

## Silence and transitions

Music uses 12-second synthesized phrases separated by 120-180 seconds of silence.
The first phrase waits 12 seconds after world entry. A changed mood must remain
stable for two seconds, then the current phrase releases over two seconds and
another full silence interval begins. Threat cues from gameplay retain priority;
music never bypasses the silence interval. Setting music to zero releases the
current phrase over two seconds and prevents any new one. World changes stop
and reset the director immediately because it describes the outgoing scene.

Gain, weather timbre and environment transitions use smoothing on the existing
frame thread. Environmental parameters interpolate instead of switching preset
objects abruptly. No background audio thread is introduced.

## Honest limits

Signal and scheduling tests prove waveform properties and policy, not aesthetic
approval. A listener must still evaluate spatial width, cave scale, indoor rain,
stealing transitions and the ability to identify a loop period over 60 seconds.
The release record must distinguish those checks from GPU/audio initialization.

## Reverb parameters now implemented

Standard EFX reverb, one slot. Columns are decay seconds, gain, HF gain,
HF decay ratio, density and diffusion; all except seconds are linear factors.

| Zone | Decay | Gain | HF gain | HF ratio | Density | Diffusion |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Open | 0.35 | 0.06 | 0.95 | 0.85 | 0.35 | 0.50 |
| Forest | 0.75 | 0.16 | 0.45 | 0.50 | 0.80 | 0.85 |
| Shallow underground | 1.65 | 0.28 | 0.60 | 0.65 | 0.85 | 0.90 |
| Deep cave | 3.80 | 0.40 | 0.50 | 0.55 | 1.00 | 1.00 |
| Shelter | 0.65 | 0.22 | 0.35 | 0.50 | 0.60 | 0.70 |
| Large stone structure | 2.60 | 0.35 | 0.70 | 0.80 | 1.00 | 1.00 |

`ReverbPresets` is executable authority. Depth above five blocks selects shallow
underground, at least eighteen selects deep cave; depth takes precedence over
forest or constructed flooring. Gain and decay converge at 1.2 per second.

## Variant palette

Four independent takes cover 18 frequent sounds (five surfaces, three block hits, break/place, melee hit/swing, bow release, arrow/bullet impacts, flap and two firearms). Every accepted playback advances its own bank; wraparound cannot repeat the previous take. Extra PCM is 1,061,046 bytes, bringing 116 buffers to 34,474,024 bytes. No new asset or decoder is involved. Repetition fatigue still requires human listening acceptance.

## Voice pressure

Twenty-four one-shot sources allow roughly eight concurrent actors with three overlapping actions each, with only eight additional native sources over the old pool. Sixteen persistent ambience sources are independent. Four classes rank backgrounds/footsteps, ordinary actions, important reports/UI/rewards/predators, then critical explosion/hurt/alarm. Admission and replacement scan exactly 24 entries. When exhausted, the lowest priority, quietest distance-adjusted and oldest voice yields; a lower class never displaces a higher class or its pending replacement.

Reuse follows a 30 ms raised-cosine release and a submitted zero-gain update before stop/rebind. The replacement uses its existing 4 ms PCM attack. One pending request per source caps memory and latency; later equally important events may supersede pending ones during extreme pressure. At low frame rates reuse waits for the next frame. Native execution and envelope tests do not substitute for a listening check for clicks on every driver.

## Weather propagation

Thunder uses the computed lightning position without consuming any additional simulation random values. One voxel represents four acoustic metres at 343 m/s: a 40-voxel strike arrives after 0.466 s, a 100-voxel strike after 1.166 s. Travel delay, supplemental gain `1/(1+d/100)` and HF gain `0.12+0.88/(1+d/30)` use the listener distance at the flash; OpenAL panning and inverse-distance attenuation continue following the live listener. Unloaded strike columns use listener height for audio and retain the original gameplay early return.

Sixteen pending strikes is a hard cap. Overflow keeps nearer events; reset on new world, successful load, failed frontend load, title return and shutdown discards pending events. This transient queue is deliberately absent from saves: loading never replays an old flash. Sheltered rain's six wide emitters and runtime droplets converge at 3/s to 0.15 direct HF transmission in addition to the existing 0.45 gain and environment reverb. Unsupported EFX devices retain dry fallback.

## Player mix controls

The title AUDIO button and pause shortcut V open the same live editor: master 85%, SFX 100%, ambience 100%, music 50%, mute off by default. Four draggable sliders also support arrow keys in 5% steps. Apply/F5 saves `veylon_audio.properties` beside graphics settings through AppPaths; Back/Escape restores the previous mix. Mute preserves levels. Music at zero disables music events; SFX and ambience remain independent, and runtime droplets/crackles belong to ambience. Native source gain and active steal fades respond on the next frame without restarting buffers.

## Adaptive phrase palette

Six original motifs use additive sine partials around D3 (146.832 Hz), a quiet octave pedal, four staggered notes, soft attacks and a 12-second outer envelope. Calm uses open minor intervals; night descends an octave; threat and deep caves use low unresolved intervals; the first night introduces a vulnerable minor phrase; the repaired beacon resolves upward. The source gain is 0.22 before the music slider and master. Music has one dedicated relative source, a dry route and no competition with gameplay voices.

Scene precedence is nearby threat/combat, repaired beacon within 36 blocks, depth at least 18 blocks, first night, later nights, then calm. Threat observation checks at most 64 creatures and 64 NPCs per medium tick, ignores dead/abstract travelers, uses a 24-block proximity radius and 48 blocks for current player combat targets, and never plans a settlement or generates a chunk. First-night ownership is session-local and resets on load; it is not a new save field. No playback or synthesis thread is added.

Six music buffers add 6,350,400 PCM bytes. The final catalog has 122 buffers / 40,824,424 PCM bytes; complete warmed synthesis/conditioning/encoding measured 494.648 ms at W11. The pure director benchmark measured 0.000004 ms/update versus 0.02 ms. The 10,000-second deterministic schedule stays over 90% silent. These establish resource and timing behavior, not listener approval of the composition.
