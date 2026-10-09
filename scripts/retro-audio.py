"""Bake a quieter retro DAC colour into the original ambient loop (numpy/soundfile).

Default input/output are the packaged OGG files. No synthesis or tempo change.
Music uses band-limited 11.025 kHz sample-and-hold and signed 9-bit quantisation; event clips
use the stronger 7.35 kHz / 8-bit profile in audio/RetroAudio.kt.
"""
from argparse import ArgumentParser
from pathlib import Path
import numpy as np
import soundfile as sf


def low_pass_loop(pcm: np.ndarray, rate: int) -> np.ndarray:
    """Zero-phase circular FIR: no delay, and the loop seam gets the same filtering.

    3.4 kHz cutoff removes DAC images/whistle. The input filter prevents aliasing;
    the output filter reconstructs the staircase and removes quantisation hiss.
    All processing happens while baking the asset, never on the phone.
    """
    taps = 257
    offsets = np.arange(taps) - taps // 2
    cutoff = min(3400.0, rate * .35)
    weights = 2 * cutoff / rate * np.sinc(2 * cutoff / rate * offsets) * np.kaiser(taps, 8)
    weights /= weights.sum()
    kernel = np.zeros(len(pcm))
    np.add.at(kernel, offsets % len(pcm), weights)
    response = np.fft.rfft(kernel)
    return np.fft.irfft(np.fft.rfft(pcm, axis=0) * response[:, None], n=len(pcm), axis=0)


def process(pcm: np.ndarray, rate: int) -> np.ndarray:
    filtered = low_pass_loop(pcm, rate)
    hold = max(1, round(rate / 11025))
    indices = np.arange(len(pcm)) // hold * hold
    wet = np.round(np.clip(filtered[indices], -1, 1) * 255) / 255
    result = low_pass_loop(filtered * .15 + wet * .85, rate)
    rms = np.sqrt(np.mean(pcm.astype(np.float64) ** 2))
    actual = np.sqrt(np.mean(result ** 2))
    # Do not amplify out-of-band-only inputs back into audible noise.
    result *= min(1.05, rms / max(actual, 1e-12), .98 / max(np.max(np.abs(result)), 1e-12))
    return result.astype(np.float32)


def main():
    root = Path(__file__).resolve().parents[1] / 'src/app/src/main/res/raw'
    parser = ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=root / 'quiet_orbits.ogg')
    parser.add_argument('--output', type=Path, default=root / 'quiet_orbits_retro.ogg')
    parser.add_argument('--preview', type=Path, help='Optional first 12 seconds as WAV')
    args = parser.parse_args()
    pcm, rate = sf.read(args.source, dtype='float32', always_2d=True)
    result = process(pcm, rate)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Small writes also avoid large native stack allocations in some Windows Vorbis builds.
    with sf.SoundFile(args.output, 'w', samplerate=rate, channels=result.shape[1], format='OGG', subtype='VORBIS') as output:
        for start in range(0, len(result), 8192):
            output.write(result[start:start+8192])
    if args.preview:
        args.preview.parent.mkdir(parents=True, exist_ok=True)
        sf.write(args.preview, result[:rate*12], rate, subtype='PCM_16')
    print(f'{len(result)/rate:.3f}s, {rate} Hz, {result.shape[1]} channels, peak={np.max(np.abs(result)):.3f}')


if __name__ == '__main__':
    main()
