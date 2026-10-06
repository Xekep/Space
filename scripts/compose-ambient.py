"""Compose Quiet Orbits, an original 96-second stereo ambient loop for Space.

Requires numpy and soundfile. No downloaded samples or sound fonts are used.
Run from the repository root: python scripts/compose-ambient.py
"""

from argparse import ArgumentParser
from pathlib import Path

import numpy as np
import soundfile as sf

SAMPLE_RATE = 44100
SECONDS = 96
SEED = 60317


def frequency(midi: int) -> float:
    # Whole cycles make oscillators continuous across the loop boundary.
    return round(440 * 2 ** ((midi - 69) / 12) * SECONDS) / SECONDS


def compose() -> np.ndarray:
    count = SAMPLE_RATE * SECONDS
    t = np.arange(count, dtype=np.float64) / SAMPLE_RATE
    rng = np.random.default_rng(SEED)
    mix = np.zeros((count, 2), dtype=np.float64)
    chords = [
        [48, 55, 62, 64, 71],  # Cmaj9: spacious, unresolved opening.
        [45, 52, 55, 59, 60],  # Am9: softer, darker middle.
        [41, 48, 55, 57, 64],  # Fmaj9: warm drift.
        [43, 50, 57, 59, 64],  # G6/9: return without a strong cadence.
    ]
    for chord_index, notes in enumerate(chords):
        distance = np.abs((t - (chord_index * 24 + 12) + SECONDS / 2) % SECONDS - SECONDS / 2)
        edge = np.clip((distance - 8) / 8, 0, 1)
        envelope = 0.5 + 0.5 * np.cos(np.pi * edge)
        for voice, note in enumerate(notes):
            hz = frequency(note)
            drift = 0.18 * np.sin(2 * np.pi * (voice + 1) * t / SECONDS)
            breath = 0.8 + 0.2 * np.cos(2 * np.pi * (voice + 2) * t / SECONDS)
            for channel in range(2):
                detune = (1 if channel == 0 else -1) * (voice + 3) / SECONDS
                phase = 2 * np.pi * (hz + detune) * t + drift
                tone = np.sin(phase) + 0.22 * np.sin(phase * 2) + 0.055 * np.sin(phase * 3)
                mix[:, channel] += 0.023 * envelope * breath * tone
        bass = np.sin(2 * np.pi * frequency(notes[0] - 12) * t)
        mix += (0.045 * envelope * bass)[:, None]

    # Six slow, soft lights; deliberately no drums or insistent arpeggio.
    for start, note, pan in [(9, 83, -0.5), (23, 79, 0.45), (39, 76, -0.25),
                             (56, 81, 0.4), (74, 86, -0.45), (89, 79, 0.2)]:
        age = (t - start) % SECONDS
        tail = 1 - np.clip((age - 13) / 7, 0, 1)
        envelope = (1 - np.exp(-age / 0.65)) * np.exp(-age / 4.5) * tail ** 2
        phase = 2 * np.pi * frequency(note) * t
        bell = np.sin(phase) + 0.13 * np.sin(phase * 2) + 0.035 * np.sin(phase * 4)
        mix += (0.026 * envelope * bell)[:, None] * np.array([1 - pan, 1 + pan])

    # Periodic coloured noise adds a barely audible airy texture.
    bins = np.fft.rfftfreq(count, 1 / SAMPLE_RATE)
    shape = np.zeros_like(bins)
    band = (bins > 300) & (bins < 3400)
    shape[band] = np.sin(np.pi * (bins[band] - 300) / 3100) ** 2 / np.sqrt(bins[band])
    for channel in range(2):
        noise = np.fft.irfft(np.fft.rfft(rng.standard_normal(count)) * shape, n=count)
        noise /= np.sqrt(np.mean(noise ** 2))
        mix[:, channel] += 0.0025 * noise * (0.75 + 0.25 * np.cos(2 * np.pi * 3 * t / SECONDS))

    # Wrap reverb tails into the beginning, preserving the infinite-loop ambience.
    dry = mix.copy()
    for index, delay in enumerate([0.137, 0.293, 0.487, 0.811, 1.307, 2.113, 3.419]):
        reflected = dry[:, ::-1] if index % 2 else dry
        mix += np.roll(reflected, round(delay * SAMPLE_RATE), axis=0) * (0.13 * 0.8 ** index)

    mix = np.tanh(mix * 1.5)
    # A tiny 25ms soft join suppresses codec-edge clicks without a silent gap.
    seam = np.clip(np.minimum(t, SECONDS - t) / 0.025, 0, 1)
    mix *= np.sin(seam * np.pi / 2)[:, None] ** 2
    target_rms = 10 ** (-19 / 20)
    mix *= min(target_rms / np.sqrt(np.mean(mix ** 2)), 0.7 / np.max(np.abs(mix)))
    return mix.astype(np.float32)


def main() -> None:
    parser = ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] /
                        'src/app/src/main/res/raw/quiet_orbits.ogg')
    parser.add_argument('--wav', type=Path, help='Optional lossless master for listening or editing.')
    args = parser.parse_args()
    audio = compose()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.wav:
        args.wav.parent.mkdir(parents=True, exist_ok=True)
        sf.write(args.wav, audio, SAMPLE_RATE, subtype='PCM_16')
    # Keep the native Vorbis encoder's per-call stack usage bounded on Windows.
    with sf.SoundFile(args.output, 'w', SAMPLE_RATE, channels=2, format='OGG', subtype='VORBIS') as output:
        for start in range(0, len(audio), 8192):
            output.write(audio[start:start + 8192])
    decoded, rate = sf.read(args.output)
    assert decoded.shape == audio.shape and rate == SAMPLE_RATE
    assert np.isfinite(decoded).all() and np.max(np.abs(decoded)) < 1
    print(f'{args.output}: {SECONDS}s, stereo {rate}Hz, {args.output.stat().st_size / 1024:.0f} KiB')
    print(f'Peak {20 * np.log10(np.max(np.abs(decoded))):.2f} dBFS; '
          f'RMS {20 * np.log10(np.sqrt(np.mean(decoded ** 2))):.2f} dBFS; '
          f'loop jump {np.max(np.abs(decoded[0] - decoded[-1])):.6f}')


if __name__ == '__main__':
    main()
