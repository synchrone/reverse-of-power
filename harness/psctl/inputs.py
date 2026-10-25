"""Button sequence parsing.

A sequence is whitespace-separated tokens:
    cross            tap a button (default hold)
    down*3           tap it three times
    cross@1.5        hold for 1.5 seconds
    2s / 0.5s        wait
e.g. ``"ps 2s down*2 right cross"``.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

# pyremoteplay's FeedbackEvent.Type names, lower-cased.
BUTTONS = (
    "up", "down", "left", "right",
    "l1", "r1", "l2", "r2", "l3", "r3",
    "cross", "circle", "square", "triangle",
    "options", "share", "ps", "touchpad",
)
ALIASES = {"x": "cross", "o": "circle", "start": "options", "create": "share", "home": "ps"}

DEFAULT_HOLD = 0.1
DEFAULT_GAP = 0.35

_WAIT = re.compile(r"^(\d+(?:\.\d+)?)s$")
_PRESS = re.compile(r"^([a-z0-9]+)(?:@(\d+(?:\.\d+)?))?(?:\*(\d+))?$")


@dataclass(frozen=True)
class Press:
    button: str
    hold: float = DEFAULT_HOLD


@dataclass(frozen=True)
class Wait:
    seconds: float


Step = Press | Wait


def parse_sequence(text: str) -> list[Step]:
    steps: list[Step] = []
    for token in text.lower().split():
        if m := _WAIT.match(token):
            steps.append(Wait(float(m.group(1))))
            continue
        m = _PRESS.match(token)
        if not m:
            raise ValueError(f"Bad token {token!r}; expected e.g. cross, down*3, cross@1.5, 2s")
        button = ALIASES.get(m.group(1), m.group(1))
        if button not in BUTTONS:
            raise ValueError(f"Unknown button {m.group(1)!r}; valid: {', '.join(BUTTONS)}")
        hold = float(m.group(2)) if m.group(2) else DEFAULT_HOLD
        steps.extend([Press(button, hold)] * int(m.group(3) or 1))
    return steps
