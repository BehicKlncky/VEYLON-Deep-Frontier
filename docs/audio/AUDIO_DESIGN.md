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

The intended music policy uses short synthesized phrases separated by at least
45 seconds of silence. A state change fades an active phrase before a new one
can enter; threat may shorten a long exploration wait but cannot create an
unbroken score. Muting music disables new phrases. World changes cancel pending
events and phrases because they describe the outgoing scene, not saved gameplay.

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
