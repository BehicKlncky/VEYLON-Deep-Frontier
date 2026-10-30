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
| Abandoned city | Rooms, halls, cellars and tunnels each ring as what they are; far-off metal, shutters, glass and water by district; the tells of what lives there (see the last section) |

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
| City room (urban 22) | 0.45 | 0.20 | 0.55 | 0.60 | 0.55 | 0.70 |
| City hall (urban 22) | 1.40 | 0.28 | 0.65 | 0.70 | 0.85 | 0.90 |
| City basement (urban 22) | 1.05 | 0.30 | 0.45 | 0.55 | 0.90 | 0.85 |
| City tunnel (urban 22) | 2.30 | 0.34 | 0.55 | 0.65 | 1.00 | 0.75 |

`ReverbPresets` is executable authority. Depth above five blocks selects shallow
underground, at least eighteen selects deep cave; depth takes precedence over
forest or constructed flooring. Gain and decay converge at 1.2 per second.

## Variant palette

Four independent takes cover 18 frequent sounds (five surfaces, three block hits, break/place, melee hit/swing, bow release, arrow/bullet impacts, flap and two firearms). Every accepted playback advances its own bank; wraparound cannot repeat the previous take. Extra PCM is 1,061,046 bytes, bringing 116 buffers to 34,474,024 bytes. No new asset or decoder is involved. Repetition fatigue still requires human listening acceptance.

## Voice pressure

Twenty-four one-shot sources allow roughly eight concurrent actors with three overlapping actions each, with only eight additional native sources over the old pool. Sixteen persistent ambience sources are independent. Four classes rank backgrounds/footsteps, ordinary actions, important reports/UI/rewards/predators, then critical explosion/hurt/alarm. Admission and replacement scan exactly 24 entries. When exhausted, the lowest priority, quietest distance-adjusted and oldest voice yields; a lower class never displaces a higher class or its pending replacement.

Reuse follows a 30 ms raised-cosine release and a submitted zero-gain update before stop/rebind. The replacement uses its existing 4 ms PCM attack. One pending request per source caps memory and latency; later equally important events may supersede pending ones during extreme pressure. At low frame rates reuse waits for the next frame. Native execution and envelope tests do not substitute for a listening check for clicks on every driver.

## Burning bodies

A burning animal or person is heard by its fire, not by a voice. Three original buffers are synthesized last, from their own generator so no earlier buffer changes: `BodyCrackle` (0.35 s of uneven dry clicks over a low roar), `BodyFlare` (0.55 s: a rush of air sweeping from bright to dark over a 68 Hz thump) and `BodySizzle` (0.7 s of fluttering high-passed hiss). `BodyFireEffects` hears the four burning bodies nearest the listener within 24 blocks, the player's own included; each crackles 1.5 to 5 times a second as its flames grow, catching is heard once as a flare and a douse by water or rain once as a sizzle, at most four such events a pass. Crackles are the fire's ambience (ambience slider, background priority, never displacing combat); flare and sizzle are ordinary effects. Within 8 blocks the loudest burning body also takes over the positioned fire loop when it is louder than any burning block or campfire, so a burning player hears the roar around them. Everything ends with the flames: one-shots are shorter than a second and the loop gain is recomputed every medium tick; a new world forgets which bodies were heard. A device without audio, and every headless test, plays them as silence.

## People in hiding

Civilians hiding in an urban hidden refuge (urban package 16) give themselves away with three original buffers, synthesized last from their own generator so no earlier buffer changes: `SurvivorCOUGH` (0.55 s: two breathy bursts with a little 150 Hz throat, the second cut short), `SurvivorSHUFFLE` (0.75 s: three low scuffs and a dull knock) and `SurvivorWHISPER` (0.95 s: four unvoiced syllables through a resonance that jumps between 700 and 1,600 Hz). Every nine seconds the nearest of them within ten blocks of a player who is not in the cellar with them plays the next of the three, positioned at that person so the occlusion path muffles it through the wall; each carries from 2 to 18 blocks at gain 0.6 on the SFX bus with ordinary priority. Fear silences them. The first sound of a refuge not yet found is also logged once a session, for players without sound. They are presentation only, quieter than a Breacher's blow (`SurvivorSoundsTest`), and a device without audio, like every headless test, plays them as silence.

A salvaged generator in a refuge (urban package 19) has three original buffers, synthesized last from their own generator so no earlier buffer changes: `UtilitySTART` (1.1 s: two pull-cord rattles, the second catching into ragged firing strokes), `UtilityRUN` (1.0 s: fifteen whole firing cycles, a low chug with valve tick and exhaust hiss, so one play runs into the next) and `UtilitySTOP` (0.9 s: misfires as the strokes slow and coast to a stop). The run cue is replayed each time it ends while the generator runs; all three are positioned at the generator and carry from 3 to 40 blocks at gain 0.7 on the SFX bus. They are presentation only: the noise threats hear is the generator's own `WorldNoise` event, emitted by the utility rules every three seconds, and a device without audio, like every headless test, plays the cues as silence (`UtilitySoundsTest`).

## Weather propagation

Thunder uses the computed lightning position without consuming any additional simulation random values. One voxel represents four acoustic metres at 343 m/s: a 40-voxel strike arrives after 0.466 s, a 100-voxel strike after 1.166 s. Travel delay, supplemental gain `1/(1+d/100)` and HF gain `0.12+0.88/(1+d/30)` use the listener distance at the flash; OpenAL panning and inverse-distance attenuation continue following the live listener. Unloaded strike columns use listener height for audio and retain the original gameplay early return.

Sixteen pending strikes is a hard cap. Overflow keeps nearer events; reset on new world, successful load, failed frontend load, title return and shutdown discards pending events. This transient queue is deliberately absent from saves: loading never replays an old flash. Sheltered rain's six wide emitters and runtime droplets converge at 3/s to 0.15 direct HF transmission in addition to the existing 0.45 gain and environment reverb. Unsupported EFX devices retain dry fallback.

## Player mix controls

The title AUDIO button and pause shortcut V open the same live editor: master 85%, SFX 100%, ambience 100%, music 50%, mute off by default. Four draggable sliders also support arrow keys in 5% steps. Apply/F5 saves `veylon_audio.properties` beside graphics settings through AppPaths; Back/Escape restores the previous mix. Mute preserves levels. Music at zero disables music events; SFX and ambience remain independent, and runtime droplets/crackles belong to ambience. Native source gain and active steal fades respond on the next frame without restarting buffers.

## Adaptive phrase palette

Six original motifs use additive sine partials around D3 (146.832 Hz), a quiet octave pedal, four staggered notes, soft attacks and a 12-second outer envelope. Calm uses open minor intervals; night descends an octave; threat and deep caves use low unresolved intervals; the first night introduces a vulnerable minor phrase; the repaired beacon resolves upward. The source gain is 0.22 before the music slider and master. Music has one dedicated relative source, a dry route and no competition with gameplay voices.

Scene precedence is nearby threat/combat, repaired beacon within 36 blocks, depth at least 18 blocks, first night, later nights, then calm. Threat observation checks at most 64 creatures and 64 NPCs per medium tick, ignores dead/abstract travelers, uses a 24-block proximity radius and 48 blocks for current player combat targets, and never plans a settlement or generates a chunk. First-night ownership is session-local and resets on load; it is not a new save field. No playback or synthesis thread is added.

Six music buffers add 6,350,400 PCM bytes. The final catalog has 122 buffers / 40,824,424 PCM bytes; complete warmed synthesis/conditioning/encoding measured 494.648 ms at W11. The pure director benchmark measured 0.000004 ms/update versus 0.02 ms. The 10,000-second deterministic schedule stays over 90% silent. These establish resource and timing behavior, not listener approval of the composition.

## The abandoned city (urban milestone 22)

A generated city should sound abandoned, not empty. Wind finds loose metal, a
shutter slaps, glass lets go somewhere, water slops against a wall, a floor
settles overhead; closer, the city says what lives in it and where. Every sound
here is original project DSP. `CitySounds` synthesizes them last, from its own
seed, so no earlier buffer changes a sample.

### Rooms, cellars, halls and tunnels

Every interior of a generated city used to take the 2.6-second stone-structure
reverb. The four city zones at the end of the reverb table above (zones are never
saved) apply as follows:

| Zone | Where |
| --- | --- |
| City room | A furnished room or corridor indoors above the street |
| City hall | A room of 48 cells or more (a shop floor, warehouse, office or stair hall), a station concourse |
| City basement | Anything indoors below the street datum, hidden cellars included |
| City tunnel | A transit tunnel or a service passage |

`AudioSceneState.urbanZone` decides from what the medium tick already observes:
the place underfoot, sky exposure, the shelter evaluation, depth below the street
datum and the one building plan around the listener. The street and the roofs
are open; a covered porch is a shelter. Worlds that are not generated cities keep
their classification.

### Weather and insects by where the listener stands

How much of the weather outside reaches a city listener: all of it in the street,
0.6 under a porch, 0.16 deep in a room rising to 0.55 beside its window (from the
sky light at the ear: 0.12 deep inside, up to 0.72 at a pane), 0.06 in a cellar
and 0.03 in a tunnel. Rain and wind scale by it, from one bounded sky-light read
per medium tick. A cellar has its own low room tone on the cave channel (0.45)
with its drips. Night insects keep to the overgrown housing and the flooded
quarter (and to cities planned before districts) and are heard indoors only as
much as the weather is; there are none in a cellar.

### The city's own sounds

`CitySoundscape`, owned by `AmbienceSystem`, runs on the emitter pass (every
0.12 s) in a generated city:

- **Far off**, every 6 to 15 seconds and sooner in wind, a sound of the district
  underfoot, 26 to 46 blocks away in a random direction between the street and
  the third storey: shutters in the housing, glass downtown, metal grinding in the
  yards and along the cordon, flood water lapping in the flooded quarter, a little
  falling masonry everywhere. Not in cellars or tunnels. Walls muffle it through
  the occlusion filter like any positioned sound.
- **Indoors**, every 10 to 22 seconds, a floor settling overhead or a pipe
  knocking, 2 to 5 blocks away; below the street, pipes every 9 to 18 seconds.
- **What lives here**: a Veilbound that has not noticed anyone breathes every 5 to
  10 seconds where it stands (within 22 blocks), and falls silent into its own
  notice and strike cues once it hunts; a Veilmaw folded in its growth throbs
  every 4 to 7 seconds (within 24); a person turning on the survivor barks once
  where they stand (within 40), and again only after 12 seconds of not hunting;
  a fed work lamp hums within 6 blocks.

Distant and indoor sounds use the ambience bus at background priority and never
take a voice from a fight; the breath and throb are ordinary effects, the bark an
important one. Per pass at most 64 people and 64 creatures are examined, 8 of
each kind of tell followed for its timer, and 2 sounds started.

### Presentation never becomes simulation

The soundscape draws only its own presentation generator, seeded from the world
seed with its own salt and reset with the world. It reads entity state and never
a simulation generator, and it never emits `WorldNoise`: no sound of the city can
draw a threat or change a roll. `CitySoundscapeTest` runs two sessions of the same
city whose soundscapes draw different streams: they play different sounds at
different times, and their simulations, including the noise the AI hears, match
frame by frame.

### Footsteps

Every wilderness block keeps its step. The city's metal (sheet metal, metal
doors, rails, wrecks, lockers and machines) clanks; rubble, and debris at the
feet, crunch; the shaped stair flights, which stepped like grass, sound like
concrete. Two new banks hold four takes each. A step is as loud as what the AI
hears of it: crouched 0.45 of a walk, sprinting 1.45.

### What the survivor hears and what the AI hears

The two are not one physical model, but they follow the same rules: louder
actions carry farther for both, and walls and closed doors cost both.

| Action | The survivor hears | The AI hears (`WorldNoise` radius, weight) |
| --- | --- | --- |
| A crouched step | A faint step (gain 0.16) | Nothing |
| A walking step | A step (0.35) | 10 blocks, 0.15 |
| A sprinting step | A loud step (0.51) | 22 blocks, 0.38 |
| Searching a container | Rummaging at the container | 12 blocks, 0.3 |
| Opening a wooden door / a metal door / a window | Creak and latch / clank / slide, positioned | 8 / 12 / 5 blocks, 0.15 / 0.25 / 0.08 |
| Trying a locked handle | A rattle | 10 blocks, 0.25 |
| Forcing a door / breaking glass | A crack / shattering, heard far off | 36 / 28 blocks, 0.7 / 0.6 |
| A generator starting / running | Pull cords and chug (3 to 40 blocks) | 34 / 28 blocks, 0.6 / 0.5 |
| A Breacher's blow on a door | A heavy thud | 16 blocks, 0.6 |
| A gunshot | A report heard far off (14 to 220 blocks) | The weapon's radius (musket 90, flintlock 70) |
| The city's own sounds | Everything in the soundscape | Nothing |

Through walls the AI loses 8 blocks of range for each solid cell on the direct
path, 6 for a closed door, 4 for glass and nothing for an open door; non-player
sounds weaker than 0.6 reach no AI listener. The survivor hears the same walls as
a muffling of the direct path: three solid samples muffle a sound fully, to 45%
gain and 8% treble, when the device has EFX.

### Dry fallback and budgets

Without EFX (or with `VEYLON_NO_EFX=1` for device QA) there is no reverb and no
occlusion filter: zones are ignored and every city sound plays dry with OpenAL's
distance attenuation. The catalogue now holds 167 buffers and 43,443,964 PCM
bytes; the city added 19 buffers (8 footstep takes, 11 cues) and 1,118,376 bytes.
Signal tests prove waveform properties and policy only. Whether the city sounds
right on speakers and headphones, with and without EFX, is the listening pass of
the consolidated human playtest that D-80 schedules after package 22.
