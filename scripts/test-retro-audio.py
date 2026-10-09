"""Run with python scripts/test-retro-audio.py (numpy/soundfile)."""
import importlib.util
from pathlib import Path
import unittest
import numpy as np

spec=importlib.util.spec_from_file_location("retro_audio",Path(__file__).with_name("retro-audio.py"))
retro=importlib.util.module_from_spec(spec)
spec.loader.exec_module(retro)

class RetroMusicTest(unittest.TestCase):
    def test_band_limited_loop_keeps_bass_and_rejects_alias_and_dac_images(self):
        rate=44100
        time=np.arange(rate)/rate
        bass=.2*np.sin(2*np.pi*220*time)
        high=.1*np.sin(2*np.pi*10500*time)
        source=np.stack([bass+high,bass-high],axis=1).astype(np.float32)
        result=retro.process(source,rate)
        spectrum=np.abs(np.fft.rfft(result,axis=0))**2
        frequencies=np.fft.rfftfreq(rate,1/rate)
        self.assertEqual(source.shape,result.shape)
        self.assertTrue(np.isfinite(result).all())
        self.assertLess(np.max(np.abs(result)),.98)
        self.assertLess(spectrum[frequencies>=7000].sum()/spectrum.sum(),1e-7)
        self.assertLess(spectrum[525].sum()/spectrum.sum(),1e-7)
        self.assertGreater(spectrum[220].sum()/spectrum.sum(),.99)
        self.assertLess(np.max(np.abs(result[0]-result[-1])),.02)
    def test_silence_and_pure_out_of_band_input_are_not_amplified(self):
        silence=np.zeros((44100,2),dtype=np.float32)
        self.assertTrue(np.array_equal(silence,retro.process(silence,44100)))
        signal=.2*np.sin(2*np.pi*10500*np.arange(44100)/44100)
        result=retro.process(np.stack([signal,signal],axis=1),44100)
        self.assertLess(np.sqrt(np.mean(result**2)),1e-4)

if __name__=="__main__": unittest.main()
