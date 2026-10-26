"""Drive a PS4/PS5 over Remote Play (pyremoteplay) for integration tests and agents."""

from .console import Console, ConsoleError

__all__ = ["Console", "ConsoleError"]
