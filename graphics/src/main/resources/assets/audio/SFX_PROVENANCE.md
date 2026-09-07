# Sound Effect Provenance

All WAV files below `sfx/ui` and `sfx/battle`, except `sfx/battle/block.wav` and
`sfx/battle/cursed_speech.wav`, are
original procedural sounds generated for this project by
`packaging/generate-sfx.py`. They use synthesized pulse, triangle, saw, sine,
and deterministic noise waveforms. No samples, ROM data, or sound files from
Pokemon or another game are included.

The direction is the short, bright, bit-crushed character of handheld-era RPG
interfaces and battles. The checked-in WAV files are authoritative; running the
generator recreates them from the same synthesis inputs. It intentionally leaves
the externally sourced block effect untouched.

`sfx/battle/block.wav` is Kenney's `impactPlate_heavy_004.ogg`, converted to
44.1 kHz mono 16-bit PCM WAV and soft-limited by approximately +7 dB with 5%
peak headroom. It comes from
[Impact Sounds 1.0](https://kenney.nl/assets/impact-sounds), created by Kenney
and released under [Creative Commons Zero (CC0 1.0)](https://creativecommons.org/publicdomain/zero/1.0/).
Attribution is not required, but the source is recorded here for traceability.

`sfx/battle/cursed_speech.wav` is a short dark, resonant synthesized spell
activation selected from qubodup's `fout-02.flac` in the [3 dark magic
spells](https://opengameart.org/content/3-dark-magic-spells) collection. The
original download is [`dark_magic.7z`](https://opengameart.org/sites/default/files/dark_magic.7z);
the page identifies qubodup as author and releases the collection under
[Creative Commons Zero (CC0 1.0)](https://creativecommons.org/publicdomain/zero/1.0/).
It was converted from FLAC stereo, 44.1 kHz, 24-bit to mono, 44.1 kHz, 16-bit
PCM WAV with macOS `afconvert`. This is a CC0 synthesized substitute, not an
actual anime sample or a voice clone of Inumaki.

During research, the CC0 Kenney UI Audio pack and the CC-BY 3.0 Little Robot
Sound Factory 8-Bit Sound Effects Library were also evaluated. Neither pack is
copied, remixed, or embedded in the generated files.
