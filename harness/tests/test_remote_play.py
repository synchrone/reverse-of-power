"""Remote Play checks against the real console (PS_HOST must be set).

Run from harness/:  PS_HOST=192.168.1.x python -m unittest -v tests.test_remote_play
Frames are saved under harness/out/<timestamp>/ for inspection.
"""

import io
import os
import time
import unittest
from pathlib import Path

from PIL import Image, ImageChops, ImageStat

from psctl import Console

OUT = Path(__file__).resolve().parent.parent / "out" / time.strftime("%Y%m%d-%H%M%S")


def _image(png: bytes) -> Image.Image:
    return Image.open(io.BytesIO(png)).convert("RGB")


def _bottom_diff(a: Image.Image, b: Image.Image, share: float = 0.3) -> float:
    """Mean per-pixel difference (0-255) over the bottom `share` of the frame."""
    box = (0, int(a.height * (1 - share)), a.width, a.height)
    return sum(ImageStat.Stat(ImageChops.difference(a.crop(box), b.crop(box))).mean) / 3


class RemotePlayTest(unittest.TestCase):
    console: Console

    @classmethod
    def setUpClass(cls):
        host = os.environ.get("PS_HOST")
        if not host:
            raise RuntimeError("Set PS_HOST to the console IP (see `python -m psctl discover`)")
        OUT.mkdir(parents=True, exist_ok=True)
        cls.console = Console(host, os.environ.get("PS_USER"))
        cls.console.connect()

    @classmethod
    def tearDownClass(cls):
        cls.console.disconnect()

    def save(self, name: str, png: bytes) -> Image.Image:
        (OUT / f"{name}.png").write_bytes(png)
        return _image(png)

    def test_console_reports_status(self):
        status = self.console.status()
        print(f"\n{status['name']} ({status['type']} {status['system_version']}), "
              f"running: {status['app_name'] or '-'} {status['app_id'] or ''}")
        self.assertTrue(status["on"])

    def test_video_stream_is_live(self):
        before = self.console.session_info()["frames"]
        time.sleep(2)
        info = self.console.session_info()
        self.assertGreater(info["frames"], before, "no new frames in 2s")
        self.assertLess(info["frame_age"], 1.0)
        frame = self.save("stream", self.console.frame())
        self.assertEqual(frame.size, (1280, 720))

    def test_ps_button_toggles_control_center(self):
        # A short PS press opens the PS5 Control Center along the bottom of the
        # screen, over any game or the home screen; circle closes it again.
        before = self.save("cc-0-before", self.console.frame(max_age=1))
        self.console.press("ps 1.5s")
        opened = self.save("cc-1-open", self.console.frame(max_age=1))
        self.console.press("circle 1.5s")
        closed = self.save("cc-2-closed", self.console.frame(max_age=1))

        open_diff, closed_diff = _bottom_diff(before, opened), _bottom_diff(before, closed)
        print(f"\nbottom-strip diff: open {open_diff:.1f}, closed {closed_diff:.1f} (frames in {OUT})")
        self.assertGreater(open_diff, 8, "PS press did not visibly change the screen")
        self.assertGreater(open_diff, 2 * closed_diff, "circle did not close the Control Center")


if __name__ == "__main__":
    unittest.main()
