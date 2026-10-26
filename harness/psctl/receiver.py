"""Video receiver that keeps the latest decoded Remote Play frame."""

from __future__ import annotations

import io
import threading
import time

import av
from pyremoteplay.receiver import QueueReceiver


class FrameReceiver(QueueReceiver):
    """Keeps the most recent video frames; drops audio.

    pyremoteplay 0.7.6 builds its decoder with PyAV's pre-13 flag names
    (``Flags.LOW_DELAY``), which no longer exist, so the decoder is built here.
    """

    def __init__(self, max_frames: int = 2):
        super().__init__(max_frames=max_frames)
        self.first_frame = threading.Event()
        self.frame_count = 0
        self.last_frame_at: float | None = None

    def _get_video_codec(self):
        self._video_decoder = make_decoder(self._session.codec)

    def _get_audio_codec(self, header: bytes):
        pass

    def handle_audio_data(self, buf: bytes):
        pass

    def handle_video(self, frame: av.VideoFrame):
        super().handle_video(frame)
        self.frame_count += 1
        self.last_frame_at = time.monotonic()
        self.first_frame.set()

    def latest_image(self, image_format: str = "PNG", quality: int = 85) -> bytes | None:
        """Return the latest frame encoded as PNG or JPEG, or None before the first frame."""
        frame = self.get_latest_video_frame()
        if frame is None:
            return None
        out = io.BytesIO()
        options = {"quality": quality} if image_format.upper() == "JPEG" else {}
        frame.to_image().save(out, format=image_format, **options)
        return out.getvalue()

    @property
    def frame_age(self) -> float | None:
        """Seconds since the last decoded frame."""
        if self.last_frame_at is None:
            return None
        return time.monotonic() - self.last_frame_at


def make_decoder(codec_name: str) -> av.CodecContext:
    """Low-latency decoder context, as pyremoteplay configures it, with current PyAV names."""
    flags = av.codec.context.Flags
    flags2 = av.codec.context.Flags2
    ctx = av.codec.Codec(codec_name, "r").create()
    ctx.flags = flags.low_delay
    ctx.flags2 = flags2.fast
    ctx.thread_type = av.codec.context.ThreadType.AUTO
    return ctx
