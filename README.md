# Mine Piano

Client-side Fabric piano mod for Minecraft 26.2 (Java 25).

## Requirements

- Fabric Loader and Fabric API
- [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat) 2.6.x is required for positional audio and recommended for the full experience. No server plugin is needed: Mine Piano sends audio through its client API. Without it, piano audio remains local.

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

Mine Piano uses its own minimal SF2 sampler, not Minecraft noteblock sounds: 48 kHz mono, 20 ms blocks, pooled voices, and 0 allocations per block. Typical local latency is about 35–55 ms. Voice Chat frames use `createLocationalAudioChannel(...).play(short[960])`; Simple Voice Chat 2.6.x synchronously copies each frame before enqueueing.

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
- `javax.sound.sampled` may fall back to 44.1 kHz or clock-paced output when no audio device exists.
- Bundled small SoundFont is not concert-grand quality.
- Custom `.sf2` config path loads at startup; restart after changing it.
- No piano roll, sequencer, MIDI, or song saving.
