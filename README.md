# Mine Piano

Client-side Fabric piano mod for Minecraft 26.2 (Java 25).

## Requirements

- Fabric Loader and Fabric API
- [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) 2.6.x is required to transmit piano audio to other players. Without it, piano audio remains local.

## Playing

Open piano with `P` by default (`MISC` category; key `key.minepiano.open`).

| Control | Default |
| --- | --- |
| Piano keys | `A=C`, `W=C#`, `S=D`, `E=D#`, `D=E`, `F=F`, `T=F#`, `G=G`, `Y=G#`, `H=A`, `U=A#`, `J=B`, `K=C` (next octave) |
| Mouse | Click to play; drag for glissando |
| Octave | `Z` down, `X` up |
| Sustain | `Space` |
| Close | `Escape` (sends note-off for all active notes) |

Change piano key layout in settings. It is stored in `config/mine-piano/config.json`.

## Audio

Mine Piano uses its own minimal SF2 sampler, not Minecraft noteblock sounds: 48 kHz mono, 20 ms blocks, pooled voices, and 0 allocations per block. Typical local latency is about 35–55 ms. Through Simple Voice Chat, piano frames are merged with the processed microphone and transmitted as the player's normal voice. Normal proximity, distance, group, and server relay rules apply.

Keyboard and mouse notes use one fixed internal strike level. MIDI velocity remains internal to the sampler API.

### Presets

Presets switch live for new notes and are saved in `config/mine-piano/config.json`. Existing notes finish naturally. Instrument selection is combined with gain, attack, release, sustain, detune, and low-pass voicing:

| Preset | Instrument | Bank / program | Voicing: gain / attack / release / sustain / detune / low-pass |
| --- | --- | --- | --- |
| Realistic | FreePats upright | 0 / 0 | 0.82 / 1.00 / 1.00 / 1.00 / 0.0 / 0.00 |
| Grand | GeneralUser acoustic grand | 0 / 0 | 0.82 / 1.00 / 1.00 / 1.00 / 0.0 / 0.00 |
| Bright | GeneralUser bright acoustic | 0 / 1 | 0.80 / 0.90 / 0.90 / 1.00 / 0.0 / 0.00 |
| Electric | GeneralUser electric piano | 0 / 4 | 0.88 / 0.85 / 1.15 / 0.95 / 0.0 / 0.05 |
| Harpsichord | GeneralUser harpsichord | 0 / 6 | 0.76 / 0.55 / 0.55 / 0.70 / 0.0 / 0.00 |
| Music Box | GeneralUser music box | 0 / 10 | 0.72 / 0.65 / 0.90 / 0.75 / 0.0 / 0.00 |
| Vibraphone | GeneralUser vibraphone | 0 / 11 | 0.78 / 0.90 / 1.30 / 0.92 / 0.0 / 0.04 |
| Organ | GeneralUser church organ | 0 / 19 | 0.70 / 0.70 / 1.10 / 1.00 / 0.0 / 0.02 |
| Melancholic | FreePats upright | 0 / 0 | 0.78 / 1.35 / 1.75 / 0.72 / -4.0 / 0.28 |
| Concert | GeneralUser acoustic grand | 0 / 0 | 0.86 / 0.92 / 2.40 / 1.06 / 1.5 / 0.03 |

## Build

```sh
direnv allow
./gradlew build
./gradlew test
./gradlew runClient
```

Nix flake provides JDK 25 and p7zip. Gradle wrapper is 9.5.1. First build downloads SoundFont archive; build offline with:

```sh
./gradlew build -Psoundfont=/path/to/upright.sf2 -PgmSoundfont=/path/to/GeneralUser-GS.sf2
```

`downloadDevMods` downloads Simple Voice Chat into `run/mods` for development.

## Libraries and assets

No third-party Java runtime libraries are bundled. Only compile-time dependency is Simple Voice Chat API (`de.maxhenkel.voicechat:voicechat-api:2.6.24`), not bundled. Instrument is self-written SF2 sampler.

Bundled assets:

- FreePats Upright Piano KW (small): https://freepats.zenvoid.org/Piano/UprightPianoKW/UprightPianoKW-small-SF2-20190703.7z
- GeneralUser GS: https://raw.githubusercontent.com/mrbumpy409/GeneralUser-GS/main/GeneralUser-GS.sf2

See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## Limitations

- Client-only. Real-client runtime is not verified here because no OpenGL/Vulkan context is available.
- Remote listeners receive piano through Voice Chat jitter buffer, adding expected 50–100 ms.
- Voice transmission requires a working Simple Voice Chat client and running microphone thread. It is unavailable when voice chat is disabled or no usable microphone starts that thread.
- Piano audio mixes with the real microphone and still transmits when the player is muted or push-to-talk is not held. Disable “Transmit to Voice Chat” in piano settings for local-only playback.
- `javax.sound.sampled` may fall back to 44.1 kHz or clock-paced output when no audio device exists.
- Bundled instruments favor moderate artifact size over studio-library detail.
- No piano roll, sequencer, MIDI, or song saving.
