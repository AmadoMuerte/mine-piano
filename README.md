<p align="center"><img src="assets/logo.png" alt="Mine Piano" width="240"></p>

**English** | [Русский](README.ru.md)

# Mine Piano

Client-side Fabric piano mod for Minecraft 26.3 (Java 25), with a real SoundFont engine and Simple Voice Chat transmission.

## Features

- Real-time piano UI played with keyboard or mouse.
- Own minimal SF2 sampler: 48 kHz mono audio in 20 ms blocks.
- 10 instrument presets.
- Optional transmission to other players through Simple Voice Chat.
- Settings are saved to `config/mine-piano/config.json`.

## Requirements

- Fabric Loader and Fabric API.
- [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) 2.6.x is optional. It is required only to transmit piano audio to other players; without it, piano audio stays local.

## Installation

Download the jar from [GitHub Releases](https://github.com/AmadoMuerte/mine-piano/releases) and place it in `.minecraft/mods`. Install Fabric API as well. Install Simple Voice Chat 2.6.x if you want to transmit piano audio to other players.

## Controls

Open the piano with `P` by default (`MISC` category; key `key.minepiano.open`).

### MIDI keyboard

- Connect a MIDI keyboard to play while the piano window is open. MIDI is read only while that window is open; closing it releases the device and stops all notes. No extra dependency or driver is needed.
- MIDI notes `0..127` (all octaves) sound with their real velocity, and the sustain pedal (CC64) is supported. The three-octave on-screen keyboard auto-scrolls to the octave played; notes `120..127` sound but cannot be drawn there.
- Select the MIDI device and toggle MIDI input on or off in piano settings. These settings are saved to `config/mine-piano/config.json`.

> **Note:** MIDI support is currently in testing (beta). Behaviour and settings may change in a future release.

| Control | How |
| --- | --- |
| Piano keys | FL-style three-octave layout (see below) |
| Mouse | Click a key to play; drag across keys for glissando |
| Octave | `‹ Octave Down` / `Octave Up ›` buttons in the piano window (default `C4`) |
| Sustain | `Space`, or the Sustain toggle in the window header |
| Close | `Escape` or the `×` button (sends note-off for all active notes) |

Default key layout (three octaves):

| Octave | Keys |
| --- | --- |
| Lower | `Z S X D C V G B H N J M` |
| Middle | `, Q 2 W 3 E R 5 T 6 Y 7 U` |
| Upper | `I 9 O 0 P [ ] \ L . ; /` |

The `⚙` button opens settings (master volume, preset, Voice Chat transmission, key rebinding); the `⌄` button collapses the bottom controls. Change the piano key layout in settings. It is saved to `config/mine-piano/config.json`.

## Presets

Presets switch live for new notes and are saved in `config/mine-piano/config.json`. Existing notes finish naturally. Instrument selection is combined with gain, attack, release, sustain, detune, and low-pass voicing.

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

## Audio notes

Mine Piano uses its own minimal SF2 sampler, not Minecraft noteblock sounds: 48 kHz mono, 20 ms blocks, pooled voices, and 0 allocations per block. Typical local latency is about 35–55 ms. Through Simple Voice Chat, piano frames are merged with the processed microphone and transmitted as the player's normal voice. Normal proximity, distance, group, and server relay rules apply.

Keyboard and mouse notes use one fixed strike level. MIDI notes use their own velocity to determine loudness.

## Build

```sh
direnv allow
./gradlew build
./gradlew test
./gradlew runClient
```

Nix flake provides JDK 25 and p7zip. Gradle wrapper is 9.5.1. The first build downloads the SoundFont archive. Build offline with:

```sh
./gradlew build -Psoundfont=/path/to/upright.sf2 -PgmSoundfont=/path/to/GeneralUser-GS.sf2
```

`downloadDevMods` fetches Simple Voice Chat into `run/mods` for development.

## Limitations

- Remote listeners receive piano through Voice Chat jitter buffer, adding expected 50–100 ms.
- Voice transmission requires a working Simple Voice Chat client and running microphone thread. It is unavailable when voice chat is disabled or no usable microphone starts that thread.
- Piano audio mixes with the real microphone and still transmits when the player is muted or push-to-talk is not held. Disable “Transmit to Voice Chat” in piano settings for local-only playback.
- `javax.sound.sampled` may fall back to 44.1 kHz or clock-paced output when no audio device exists.
- Bundled instruments favor moderate artifact size over studio-library detail.

## License

MIT. See [LICENSE](LICENSE). Bundled SoundFont assets are covered in [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## Credits

Author: [AmadoMuerte](https://amadomuerte.ru)
