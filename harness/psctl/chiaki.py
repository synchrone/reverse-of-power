"""Import consoles already paired in chiaki-ng into a pyremoteplay profile.

chiaki-ng keeps its pairing in Qt settings (org "Chiaki", app "Chiaki", or
"Chiaki-<profile>" for named profiles). On macOS that is the preferences domain
com.chiaki.Chiaki, where Qt flattens "registered_hosts/1/rp_key" into the key
"registered_hosts.1.rp_key" and stores QByteArray values as data.

Field formats (chiaki-ng gui/src/host.cpp, lib/src/regist.c):
    rp_regist_key  16 bytes: the registration response's RegistKey hex, parsed, NUL-padded
    rp_key         16 bytes
    server_mac      6 bytes
    target          int; >= 1000000 means PS5
    settings/psn_account_id  base64 PSN account ID
pyremoteplay wants RegistKey/RP-Key as hex strings, keyed by the console MAC
as discovery reports it (12 upper-case hex digits).
"""

from __future__ import annotations

import plistlib
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

from pyremoteplay.profile import Profiles, UserProfile

PS5_TARGET_MIN = 1000000

_HOST_KEY = re.compile(r"^registered_hosts[./](\d+)[./](\w+)$")


class ChiakiImportError(Exception):
    pass


@dataclass
class ChiakiHost:
    nickname: str
    mac: str
    host_type: str
    regist_key: str
    rp_key: str


def domain_for(profile: str | None) -> str:
    return "com.chiaki.Chiaki" + (f"-{profile}" if profile else "")


def load_macos_settings(profile: str | None = None, plist_path: str | None = None) -> dict:
    """Read chiaki-ng's settings: `defaults export` (current, via cfprefsd) or a .plist file."""
    if plist_path:
        with open(plist_path, "rb") as handle:
            return plistlib.load(handle)
    domain = domain_for(profile)
    try:
        exported = subprocess.run(["defaults", "export", domain, "-"], check=True, capture_output=True).stdout
    except FileNotFoundError:
        fallback = Path.home() / "Library" / "Preferences" / f"{domain}.plist"
        if not fallback.exists():
            raise ChiakiImportError(f"`defaults` not found and no {fallback}; pass --plist") from None
        return load_macos_settings(plist_path=str(fallback))
    except subprocess.CalledProcessError as error:
        raise ChiakiImportError(
            f"No chiaki-ng settings domain {domain} ({error.stderr.decode().strip()}). "
            "Wrong --chiaki-profile?") from error
    return plistlib.loads(exported)


def _flatten(settings: dict, prefix: str = "") -> dict:
    flat = {}
    for key, value in settings.items():
        name = f"{prefix}/{key}" if prefix else str(key)
        if isinstance(value, dict):
            flat.update(_flatten(value, name))
        else:
            flat[name.replace(".", "/")] = value
    return flat


def _as_bytes(value) -> bytes:
    if isinstance(value, bytes):
        return value
    if isinstance(value, str) and value.startswith("@ByteArray(") and value.endswith(")"):
        return value[len("@ByteArray("):-1].encode("latin-1")
    raise ChiakiImportError(f"Expected binary data, got {type(value).__name__}: {value!r}")


def parse_settings(settings: dict) -> tuple[str | None, list[ChiakiHost]]:
    """Return (base64 PSN account id, registered hosts) from chiaki-ng settings."""
    flat = _flatten(settings)
    account_id = flat.get("settings/psn_account_id") or None

    entries: dict[int, dict] = {}
    for key, value in flat.items():
        if m := _HOST_KEY.match(key.replace("/", ".")):
            entries.setdefault(int(m.group(1)), {})[m.group(2)] = value

    hosts = []
    for index in sorted(entries):
        entry = entries[index]
        if not {"rp_regist_key", "rp_key", "server_mac"} <= entry.keys():
            continue
        regist_raw = _as_bytes(entry["rp_regist_key"]).split(b"\0", 1)[0]
        rp_key = _as_bytes(entry["rp_key"])
        mac = _as_bytes(entry["server_mac"])
        if not regist_raw or len(rp_key) != 16 or len(mac) != 6:
            raise ChiakiImportError(f"Registered host #{index} has malformed keys")
        hosts.append(ChiakiHost(
            nickname=str(entry.get("server_nickname", "")),
            mac=mac.hex().upper(),
            host_type="PS5" if int(entry.get("target", 0)) >= PS5_TARGET_MIN else "PS4",
            regist_key=regist_raw.hex(),
            rp_key=rp_key.hex(),
        ))
    return account_id, hosts


def import_into_profiles(name: str, account_id: str, hosts: list[ChiakiHost]) -> UserProfile:
    """Write (or update) a pyremoteplay user profile holding the chiaki-ng pairings."""
    profiles = Profiles.load()
    existing = profiles.get_user_profile(name)
    data = dict(existing.data) if existing else {"hosts": {}}
    data["id"] = account_id
    data.setdefault("hosts", {})
    for host in hosts:
        data["hosts"][host.mac] = {
            "type": host.host_type,
            "data": {"RegistKey": host.regist_key, "RP-Key": host.rp_key,
                     "Mac": host.mac, "Nickname": host.nickname},
        }
    profile = UserProfile(name, data)
    profiles.update_user(profile)
    profiles.save()
    return profile
