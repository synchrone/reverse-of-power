"""psctl: drive a PS4/PS5 over Remote Play for tests and agents.

Setup (once):  pair in chiaki-ng, then import-chiaki
Every run:     serve   (holds the session), then press / shot against it
One-off check: smoke
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
import urllib.error
import urllib.request

from .console import Console, ConsoleError

DEFAULT_SERVER = "http://127.0.0.1:8765"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="psctl", description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default=os.environ.get("PS_HOST"), help="console IP (env PS_HOST)")
    parser.add_argument("--user", default=os.environ.get("PS_USER"),
                        help="pyremoteplay profile name, if more than one is paired (env PS_USER)")
    parser.add_argument("--server", default=os.environ.get("PSCTL_SERVER", DEFAULT_SERVER),
                        help=f"psctl serve URL for press/shot (env PSCTL_SERVER, default {DEFAULT_SERVER})")
    parser.add_argument("-v", "--verbose", action="store_true")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("discover", help="find consoles on the LAN")

    chiaki = sub.add_parser("import-chiaki", help="reuse the consoles paired in chiaki-ng (macOS)")
    chiaki.add_argument("--chiaki-profile", help="chiaki-ng profile, if not the default one")
    chiaki.add_argument("--plist", help="read this .plist instead of `defaults export com.chiaki.Chiaki`")
    chiaki.add_argument("--name", default="chiaki-ng", help="pyremoteplay profile name to store it under")
    chiaki.add_argument("--account-id", help="base64 PSN account ID, if chiaki-ng's settings lack it")

    sub.add_parser("status", help="console power state and running title")
    sub.add_parser("wake", help="wake from rest mode")
    sub.add_parser("standby", help="put into rest mode")

    serve = sub.add_parser("serve", help="hold a session open and expose the HTTP control API")
    serve.add_argument("--bind", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=8765)
    _stream_args(serve)

    press = sub.add_parser("press", help="send a button sequence via the server, e.g. 'ps 2s down*2 cross'")
    press.add_argument("sequence", nargs="+")
    press.add_argument("--gap", type=float, default=0.35, help="pause after each press (s)")

    shot = sub.add_parser("shot", help="save the latest frame via the server")
    shot.add_argument("-o", "--output", default="frame.png")
    shot.add_argument("--max-age", type=float, help="fail if the latest frame is older than this (s)")

    screen = sub.add_parser("screen", help="name the screen the console shows (via the server), or of a saved frame")
    screen.add_argument("frame", nargs="?", help="classify this saved frame instead of the live one")

    reset = sub.add_parser("reset", help="close Decades and relaunch it to an empty lobby (via the server)")
    reset.add_argument("--timeout", type=float, default=300)

    learn = sub.add_parser("learn-screen", help="store the reference for a screen from a saved frame")
    learn.add_argument("name", help="screen name from psctl/screens.py")
    learn.add_argument("frame", help="a 1280x720 frame (e.g. from `shot`) showing that screen")

    smoke = sub.add_parser("smoke", help="one-off: wake, connect, optionally press, save a frame, disconnect")
    smoke.add_argument("--press", dest="sequence", help="button sequence to send before the screenshot")
    smoke.add_argument("-o", "--output", default="smoke.png")
    _stream_args(smoke)

    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.WARNING,
                        format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    try:
        return COMMANDS[args.command](args)
    except ConsoleError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


def _stream_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--resolution", default="720p", choices=["360p", "540p", "720p", "1080p"])
    parser.add_argument("--fps", default="low", choices=["low", "high"], help="low=30, high=60")


def _console(args, **kwargs) -> Console:
    if not args.host:
        raise ConsoleError("Console IP needed: pass --host or set PS_HOST (find it with `discover`)")
    return Console(args.host, args.user, **kwargs)


def cmd_discover(args) -> int:
    from pyremoteplay.ddp import search

    devices = search()
    for device in devices:
        print(f"{device.get('host-ip')}\t{device.get('host-type')}\t{device.get('host-name')}"
              f"\t{device.get('status')}\t{device.get('running-app-name') or ''}")
    if not devices:
        print("No consoles answered. Same subnet? Console on or in rest mode with network wake enabled?",
              file=sys.stderr)
        return 1
    return 0


def cmd_import_chiaki(args) -> int:
    from .chiaki import ChiakiImportError, import_into_profiles, load_macos_settings, parse_settings

    try:
        account_id, hosts = parse_settings(load_macos_settings(args.chiaki_profile, args.plist))
    except ChiakiImportError as error:
        raise ConsoleError(str(error)) from error
    account_id = args.account_id or account_id
    if not account_id:
        raise ConsoleError("chiaki-ng's settings have no PSN account ID; pass the one you registered with "
                           "as --account-id (base64)")
    if not hosts:
        raise ConsoleError("chiaki-ng has no registered consoles; pair one in chiaki-ng first")
    profile = import_into_profiles(args.name, account_id, hosts)
    for host in hosts:
        print(f"imported {host.host_type} {host.nickname or '?'} (MAC {host.mac}) into profile {profile.name!r}")
    return 0


def cmd_status(args) -> int:
    print(json.dumps(_console(args).status(), indent=2))
    return 0


def cmd_wake(args) -> int:
    console = _console(args)
    console.wake()
    print(json.dumps(console.status(), indent=2))
    return 0


def cmd_standby(args) -> int:
    _console(args).standby()
    return 0


def cmd_serve(args) -> int:
    from .server import serve

    console = _console(args, resolution=args.resolution, fps=args.fps)
    try:
        console.connect()
    except ConsoleError as error:
        print(f"warning: {error}; the first request will retry", file=sys.stderr)
    serve(console, args.bind, args.port)
    return 0


def cmd_press(args) -> int:
    body = {"sequence": " ".join(args.sequence), "gap": args.gap}
    print(json.dumps(_request(args.server, "/press", body), indent=2))
    return 0


def cmd_shot(args) -> int:
    path = "/frame.png" + (f"?max_age={args.max_age}" if args.max_age is not None else "")
    data = _request(args.server, path, raw=True)
    with open(args.output, "wb") as out:
        out.write(data)
    print(args.output)
    return 0


def cmd_screen(args) -> int:
    if args.frame:
        from PIL import Image

        from .screens import classify, scores

        frame = Image.open(args.frame)
        result = {"screen": classify(frame), "scores": {k: round(v, 2) for k, v in sorted(scores(frame).items())}}
    else:
        result = _request(args.server, "/screen")
    print(json.dumps(result, indent=2))
    return 0


def cmd_reset(args) -> int:
    result = _request(args.server, "/reset", {"timeout": args.timeout}, timeout=args.timeout + 30)
    print(json.dumps(result, indent=2))
    return 0


def cmd_learn_screen(args) -> int:
    from PIL import Image

    from .screens import SCREENS, learn

    if args.name not in SCREENS:
        raise ConsoleError(f"Unknown screen {args.name!r}; known: {', '.join(sorted(SCREENS))}")
    for path in learn(args.name, Image.open(args.frame)):
        print(path)
    return 0


def cmd_smoke(args) -> int:
    console = _console(args, resolution=args.resolution, fps=args.fps)
    started = time.monotonic()
    try:
        before = console.status()
        print(f"console: {before['name']} ({before['type']}), {before['status']}, "
              f"running: {before['app_name'] or '-'}")
        console.connect()
        print(f"session up in {time.monotonic() - started:.1f}s")
        if args.sequence:
            print("pressed:", " ".join(console.press(args.sequence)))
            time.sleep(1)
        with open(args.output, "wb") as out:
            out.write(console.frame())
        info = console.session_info()
        print(f"frames received: {info['frames']}, saved {args.output}")
        print(f"running: {console.status()['app_name'] or '-'}")
    finally:
        console.disconnect()
    return 0


def _request(server: str, path: str, body: dict | None = None, raw: bool = False, timeout: float = 120):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(server + path, data=data, method="POST" if data else "GET")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = response.read()
    except urllib.error.HTTPError as error:
        raise ConsoleError(f"{path}: {error.read().decode(errors='replace')}") from error
    except urllib.error.URLError as error:
        raise ConsoleError(f"psctl server not reachable at {server} ({error.reason}); start `serve`") from error
    return payload if raw else json.loads(payload)


COMMANDS = {
    "discover": cmd_discover,
    "import-chiaki": cmd_import_chiaki,
    "status": cmd_status,
    "wake": cmd_wake,
    "standby": cmd_standby,
    "serve": cmd_serve,
    "press": cmd_press,
    "shot": cmd_shot,
    "screen": cmd_screen,
    "reset": cmd_reset,
    "learn-screen": cmd_learn_screen,
    "smoke": cmd_smoke,
}

if __name__ == "__main__":
    sys.exit(main())
