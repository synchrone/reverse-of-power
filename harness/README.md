# Console harness (`psctl`)

Drives the real PS5/PS4 over **Remote Play** so tests and agents can press buttons
and see the screen, without a capture card or a physical controller. Built on
[pyremoteplay](https://github.com/ktnrg45/pyremoteplay); pairing is reused from
chiaki-ng.

Only one Remote Play client can be connected at a time: **close any chiaki-ng
stream before using psctl** (the chiaki-ng app itself can stay open).

## Setup (macOS, once)

Needs Python 3.12+ (PyAV 19) and the Xcode command line tools (`netifaces` builds from source).

```bash
cd harness
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
alias psctl='.venv/bin/python -m psctl'

psctl discover                 # find the console IP
export PS_HOST=192.168.1.x
psctl import-chiaki            # copy chiaki-ng's pairing into ~/.pyremoteplay/.profile.json
psctl status                   # power state + running title, and that the import matches this console
```

`import-chiaki` reads `defaults export com.chiaki.Chiaki` (pass `--chiaki-profile NAME`
for a non-default chiaki-ng profile, or `--plist FILE`). If chiaki-ng's settings
have no PSN account ID, pass the one you registered with as `--account-id`.

macOS asks the terminal app for **Local Network** access the first time;
without it discovery and Remote Play silently time out
(System Settings → Privacy & Security → Local Network).

## First test run

```bash
psctl smoke -o smoke.png                       # wake, connect, save a frame, disconnect
.venv/bin/python -m unittest -v tests.test_remote_play   # stream + PS-button input check
psctl serve                                    # hold a session; open http://127.0.0.1:8765
```

`tests.test_remote_play` opens and closes the Control Center with the PS button
and circle, and saves every frame under `harness/out/<timestamp>/`.

The browser remote at `http://127.0.0.1:8765` shows the live frame and takes
arrows / Enter (cross) / Backspace (circle) / Escape (PS). Use it to walk to
Knowledge Is Power and start a game with phones joining over Wi-Fi.

If something fails, rerun with `-v` (e.g. `psctl -v smoke`) for pyremoteplay's debug log.
- `Profile is not registered with host`: the MAC from `import-chiaki` doesn't match `status`
- `Auth Failed`: a chiaki-ng stream is still connected, or the imported keys are wrong
- No frames: try `--resolution 540p`

## Driving it (tests and agents)

With `psctl serve` running, any process can act on the console:

```bash
psctl press "ps 2s down*2 right cross"   # tap, repeat (*N), hold (cross@1.5), wait (2s)
psctl shot -o frame.png --max-age 1      # latest frame; fails if the stream stalled
curl -s localhost:8765/status            # console status, running title, frame count
```

HTTP API (`psctl/server.py`): `GET /status`, `GET /frame.png|.jpg`, `GET /screen`, `POST /press
{"sequence": "..."}`, `POST /stick {"stick","x","y","duration"}`, `POST /reset {"timeout": 300}`,
`POST /connect`, `/disconnect`, `/wake`, `/standby`. Actions reconnect automatically if the session dropped.

## Getting back to a clean lobby

`psctl reset` (`POST /reset`) returns Decades to an empty lobby from any state, including a hung or
abandoned match and a game whose protocol side has crashed. It holds PS for the home screen, opens OPTIONS on the
Decades tile, picks **Close Game** only if the menu is recognized as the running-game menu (Delete
sits a few rows below, so nothing else is ever pressed there), relaunches, answers first-boot prompts
(language, autosave, Getting Started) and stops once a lobby screen is recognized — about 45 s.
Decades must be the most recently played game, which every test run keeps true. The JVM integration
tests call it before and after each test.

Screens are recognized by comparing small grayscale crops of the frame with reference images in
`psctl/screens/`; `psctl screen` names the live screen and prints per-screen scores (below 1 is a
match). The references are crops of the game's and the console's own artwork, so they are not
committed: create them once per machine by saving a frame of each screen with `shot` and storing it
with `psctl learn-screen NAME frame.png` (names and crop boxes are in `psctl/screens.py`; `home` is
the home screen with the Decades tile focused). The reset refuses to run until `home`,
`close_game_menu`, `update_menu`, `lobby_star` and `lobby_organ` exist; the first-boot prompts and
`lobby_tip` are optional. Re-learn a screen the same way if an update changes it.

A protocol crash shows nothing on screen: a bad message kills the game's server-side parser thread
while the frontend keeps animating, with no error dialog, so the lobby looks normal but no client is
given a player slot any more. Only restarting the game recovers it. psctl cannot see this; the JVM
tests detect it with a probe client before each test and then call the reset.

If presses stop having any effect while video still flows, the console is holding a stale Remote
Play session; the reset reconnects, retrying every 15 s until the console drops it (about a minute).

Buttons: `up down left right cross circle square triangle l1 r1 l2 r2 l3 r3
options share ps touchpad` (aliases: `x o start create home`).

From Python, `psctl.Console` is the same thing in-process:

```python
from psctl import Console
console = Console("192.168.1.x")
console.connect()
console.press("ps 1s circle")
open("frame.png", "wb").write(console.frame())
console.disconnect()
```
