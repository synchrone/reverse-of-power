from __future__ import annotations

from dataclasses import dataclass
from functools import cache
from pathlib import Path

from PIL import Image, ImageChops, ImageStat

REFERENCES = Path(__file__).with_name("screens")
FRAME_SIZE = (1280, 720)


@dataclass(frozen=True)
class Region:
    box: tuple[int, int, int, int]
    limit: float
    width: int = 64

    def signature(self, frame: Image.Image) -> Image.Image:
        x0, y0, x1, y1 = self.box
        height = max(4, round(self.width * (y1 - y0) / (x1 - x0)))
        return frame.convert("L").crop(self.box).resize((self.width, height), Image.Resampling.BILINEAR)


SCREENS: dict[str, tuple[Region, ...]] = {
    "close_game_menu": (Region((244, 84, 586, 130), 2.2, width=128), Region((244, 455, 586, 545), 4)),
    "update_menu": (Region((244, 455, 586, 545), 4),),
    "home": (Region((112, 80, 232, 200), 20),),
    "language": (Region((310, 200, 560, 255), 12),),
    "autosave": (Region((160, 250, 1120, 430), 8),),
    "getting_started": (Region((430, 95, 850, 165), 15),),
    "lobby_tip": (Region((380, 95, 900, 165), 15),),
    "lobby_star": (Region((360, 200, 920, 560), 25),),
    "lobby_organ": (Region((440, 50, 840, 640), 25),),
}

LOBBY = frozenset({"lobby_star", "lobby_organ", "lobby_tip"})
PROMPTS = frozenset({"language", "autosave", "getting_started"})
REQUIRED = frozenset({"home", "close_game_menu", "update_menu", "lobby_star", "lobby_organ"})


def missing() -> list[str]:
    return sorted(name for name, regions in SCREENS.items()
                  if not all((REFERENCES / f"{name}.{index}.png").exists() for index in range(len(regions))))


def scores(frame: Image.Image) -> dict[str, float]:
    frame = _normalized(frame)
    absent = set(missing())
    return {
        name: max(_distance(region.signature(frame), _reference(name, index)) / region.limit
                  for index, region in enumerate(regions))
        for name, regions in SCREENS.items()
        if name not in absent
    }


def classify(frame: Image.Image) -> str | None:
    ranked = scores(frame)
    best = min(ranked, key=ranked.__getitem__, default=None)
    return best if best is not None and ranked[best] < 1 else None


def learn(name: str, frame: Image.Image) -> list[Path]:
    frame = _normalized(frame)
    REFERENCES.mkdir(exist_ok=True)
    paths = []
    for index, region in enumerate(SCREENS[name]):
        path = REFERENCES / f"{name}.{index}.png"
        region.signature(frame).save(path)
        paths.append(path)
    _reference.cache_clear()
    return paths


def _normalized(frame: Image.Image) -> Image.Image:
    return frame if frame.size == FRAME_SIZE else frame.resize(FRAME_SIZE, Image.Resampling.BILINEAR)


def _distance(a: Image.Image, b: Image.Image) -> float:
    return ImageStat.Stat(ImageChops.difference(a, b)).mean[0]


@cache
def _reference(name: str, index: int) -> Image.Image:
    return Image.open(REFERENCES / f"{name}.{index}.png").convert("L")
