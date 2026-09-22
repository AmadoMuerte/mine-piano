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

Velocity scales amplitude with a squared response: `(velocity / 127)^2`. This follows the energy-like response used by many MIDI instruments, keeps low velocities controllable, and makes medium and high strikes clearly distinct.

### Presets

Presets switch live and are saved in `config/mine-piano/config.json`. They share the bundled CC0 Upright Piano samples and change voicing without adding per-sample allocations:

| Preset | Character |
| --- | --- |
| Realistic | Neutral response and envelope |
| Warm | Softer top end, gentler attack, longer release |
| Bright | Fast attack, open top end, stronger touch response |
| Melancholic | Slow attack, subdued sustain, long dark tail |
| Soft | Quiet trim, slow attack, light touch response |
| Dark | Strong low-pass filtering and longer release |
| Music Box | Very fast attack, short tail, low sustain, sharp tuning |
| Detuned | Alternating per-layer detune for an out-of-tune character |
| Concert | Full sustain and the longest release |
| Vintage | Filtered, loose tuning, compressed touch response |

FreePats did not provide another small SF2 variant whose exact archive, extracted-file hashes, permissive license, and combined packaged size could all be verified from this build host. Therefore all ten presets intentionally use voicing variations of the pinned bundled instrument rather than silently increasing the artifact or shipping an unverified asset.

## Build

```sh
direnv allow
./gradlew build
./gradlew test
./gradlew runClient
```

Nix flake provides JDK 25 and p7zip. Gradle wrapper is 9.5.1. First build downloads SoundFont archive; build offline with:

```sh
./gradlew build -Psoundfont=/path/to/piano.sf2
```

`downloadDevMods` downloads Simple Voice Chat into `run/mods` for development.

## Libraries and assets

No third-party Java runtime libraries are bundled. Only compile-time dependency is Simple Voice Chat API (`de.maxhenkel.voicechat:voicechat-api:2.6.24`), not bundled. Instrument is self-written SF2 sampler.

Bundled asset is FreePats Upright Piano KW (small), CC0 1.0. See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## Limitations

- Client-only. Real-client runtime is not verified here because no OpenGL/Vulkan context is available.
- Remote listeners receive piano through Voice Chat jitter buffer, adding expected 50–100 ms.
- Voice transmission requires a working Simple Voice Chat client and running microphone thread. It is unavailable when voice chat is disabled or no usable microphone starts that thread.
- Piano audio mixes with the real microphone and still transmits when the player is muted or push-to-talk is not held. Disable “Transmit to Voice Chat” in piano settings for local-only playback.
- `javax.sound.sampled` may fall back to 44.1 kHz or clock-paced output when no audio device exists.
- Bundled small SoundFont is not concert-grand quality.
- Custom `.sf2` config path loads at startup; presets still switch its voicing live.
- No piano roll, sequencer, MIDI, or song saving.
