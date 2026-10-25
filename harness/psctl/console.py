"""A Remote Play session to the console: wake, connect, press buttons, grab frames."""

from __future__ import annotations

import asyncio
import logging
import threading
import time

from pyremoteplay.device import RPDevice

from .inputs import DEFAULT_GAP, DEFAULT_HOLD, Wait, parse_sequence
from .receiver import FrameReceiver

_LOGGER = logging.getLogger(__name__)


class ConsoleError(Exception):
    pass


def resolve_user(device: RPDevice, user: str | None) -> str:
    """Pick the profile registered with this console (the only one, or the named one)."""
    registered = device.get_users()
    if user:
        if user not in registered:
            raise ConsoleError(f"Profile {user!r} is not paired with {device.host}; run `import-chiaki`")
        return user
    if len(registered) == 1:
        return registered[0]
    if not registered:
        raise ConsoleError(f"No profile is paired with {device.host}; pair it in chiaki-ng, then run `import-chiaki`")
    raise ConsoleError(f"Several profiles paired ({', '.join(registered)}); pass --user")


class Console:
    """One Remote Play session, run on a private asyncio loop in a background thread.

    Methods are blocking and safe to call from any thread.
    """

    def __init__(self, host: str, user: str | None = None, resolution: str = "720p", fps: str = "low"):
        self.host = host
        self.user = user
        self.resolution = resolution
        self.fps = fps
        self.device = RPDevice(host)
        self.receiver: FrameReceiver | None = None
        self._loop: asyncio.AbstractEventLoop | None = None
        self._thread: threading.Thread | None = None
        self._lock = threading.RLock()

    # --- status / power -------------------------------------------------

    def status(self) -> dict:
        """Console status from the discovery protocol (works without a session)."""
        status = self.device.get_status()
        if not status:
            raise ConsoleError(f"Console at {self.host} did not answer discovery")
        return {
            "host": self.host,
            "name": self.device.host_name,
            "type": self.device.host_type,
            "on": self.device.is_on,
            "status": self.device.status_name,
            "app_name": self.device.app_name,
            "app_id": self.device.app_id,
            "system_version": self.device.system_version,
            "paired_users": self.device.get_users(),
        }

    def wake(self, timeout: float = 60) -> None:
        self.status()
        if self.device.is_on:
            return
        self.device.wakeup(user=resolve_user(self.device, self.user))
        if not self.device.wait_for_wakeup(timeout=timeout):
            raise ConsoleError(f"Console did not wake within {timeout}s")

    def standby(self) -> None:
        with self._lock:
            self.connect()
            self.device.session.standby()
            time.sleep(1)  # let the request leave before the session is torn down
            self.disconnect()

    # --- session ----------------------------------------------------------

    @property
    def connected(self) -> bool:
        session = self.device.session
        return session is not None and session.is_ready and not session.is_stopped

    def connect(self, timeout: float = 30, wake: bool = True) -> None:
        """Start a Remote Play session (no-op if one is live) and wait for the first frame."""
        if self.connected:  # fast path: don't queue behind a running press sequence
            return
        with self._lock:
            if self.connected:
                return
            self.disconnect()
            if wake:
                self.wake()
            elif not self.status()["on"]:
                raise ConsoleError("Console is in rest mode; pass wake=True")
            user = resolve_user(self.device, self.user)

            self._loop = asyncio.new_event_loop()
            self._thread = threading.Thread(target=self._loop.run_forever, name="remoteplay", daemon=True)
            self._thread.start()
            self.receiver = FrameReceiver()
            session = self.device.create_session(
                user, loop=self._loop, receiver=self.receiver, resolution=self.resolution, fps=self.fps
            )
            if session is None:
                raise ConsoleError("Could not create a Remote Play session (see log)")

            started = self._run(self.device.connect(), timeout)
            if not started:
                error = session.error
                self.disconnect()
                raise ConsoleError(f"Remote Play session failed to start: {error} "
                                   "(close any chiaki-ng stream: the console takes one Remote Play client)")
            if not self._run(self.device.async_wait_for_session(timeout), timeout + 5):
                self.disconnect()
                raise ConsoleError("Timed out waiting for the Remote Play stream")
            self.device.controller.start()
            if not self.receiver.first_frame.wait(timeout):
                _LOGGER.warning("Session is up but no video frame arrived within %ss", timeout)

    def disconnect(self) -> None:
        with self._lock:
            if self.device.session is not None:
                self.device.controller.stop()
                if self._loop is not None and self._loop.is_running():
                    # Session teardown cancels loop tasks, so run it on the loop.
                    self._loop.call_soon_threadsafe(self.device.disconnect)
                else:
                    self.device.disconnect()
            if self._loop is not None:
                self._loop.call_soon_threadsafe(self._loop.stop)
                self._thread.join(timeout=5)
                if not self._loop.is_running():
                    self._loop.close()
            self._loop = self._thread = None

    def _run(self, coro, timeout: float):
        return asyncio.run_coroutine_threadsafe(coro, self._loop).result(timeout)

    # --- input / video ----------------------------------------------------

    def press(self, sequence: str, gap: float = DEFAULT_GAP) -> list[str]:
        """Play a button sequence (see psctl.inputs). Returns the steps performed."""
        steps = parse_sequence(sequence)
        with self._lock:
            self.connect()
            controller = self.device.controller
            done = []
            for step in steps:
                if isinstance(step, Wait):
                    time.sleep(step.seconds)
                    done.append(f"{step.seconds:g}s")
                    continue
                controller.button(step.button, "press")
                time.sleep(step.hold)
                controller.button(step.button, "release")
                time.sleep(gap)
                done.append(step.button if step.hold == DEFAULT_HOLD else f"{step.button}@{step.hold:g}")
            return done

    def stick(self, stick: str, x: float, y: float, duration: float = 0.3) -> None:
        """Push a stick to (x, y) in [-1, 1] for `duration` seconds, then recentre."""
        with self._lock:
            self.connect()
            controller = self.device.controller
            controller.stick(stick, point=(x, y))
            time.sleep(duration)
            controller.stick(stick, point=(0.0, 0.0))

    def frame(self, image_format: str = "PNG", max_age: float | None = None) -> bytes:
        """Latest video frame as PNG/JPEG bytes."""
        self.connect()
        image = self.receiver.latest_image(image_format)
        if image is None:
            raise ConsoleError("No video frame received yet")
        age = self.receiver.frame_age
        if max_age is not None and age is not None and age > max_age:
            raise ConsoleError(f"Latest frame is {age:.1f}s old; the stream may have stalled")
        return image

    def session_info(self) -> dict:
        receiver = self.receiver
        return {
            "connected": self.connected,
            "frames": receiver.frame_count if receiver else 0,
            "frame_age": receiver.frame_age if receiver else None,
            "session_error": self.device.session.error if self.device.session else None,
        }
