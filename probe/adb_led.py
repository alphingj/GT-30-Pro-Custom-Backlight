#!/usr/bin/env python3
"""Controlled ADB probe for the Infinix GT 30 Pro rear LED controller."""

from __future__ import annotations

import argparse
import datetime as dt
import shlex
import subprocess
import sys
from pathlib import Path


DEVICE = "143373155G113822"
COMMAND_NODE = "/sys/led/led/tran_led_cmd"
PDLC_NODE = "/sys/devices/platform/odm/odm:tran_led_core/tran_pdlc_cmd"
LIGHTING_SETTING = "tran_led_lighting_setting"
OFF_FRAME = "00 01 00 00 00 00 00 00"
EFFECTS = {
    "off": OFF_FRAME,
    "on": "00 01 00 01 00 00 00 00",
    "game-launch": "00 01 00 22 00 00 00 00",
    "notification": "00 01 00 0f 00 00 00 00",
    "music-preview": "00 01 00 37 00 00 00 00",
    "charging": "00 01 00 1a 00 00 00 00",
    "camera-record": "00 01 00 21 00 00 00 00",
}


def adb(*args: str, check: bool = True) -> str:
    command = ["adb", "-s", DEVICE, *args]
    result = subprocess.run(command, text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError(result.stderr.strip() or "ADB command failed")
    return (result.stdout + result.stderr).strip()


def log(message: str) -> None:
    timestamp = dt.datetime.now().astimezone().isoformat(timespec="seconds")
    Path("probe/adb-led.log").open("a", encoding="ascii").write(
        f"{timestamp} {message}\n"
    )


def status() -> None:
    print(adb("get-state"))
    print(adb("shell", "id"))
    for node in (COMMAND_NODE, PDLC_NODE):
        command = f"ls -lZ {shlex.quote(node)}; cat {shlex.quote(node)}"
        print(f"[{node}]\n{adb('shell', command, check=False)}")
    print("[service]")
    print(adb("shell", "service list | grep -i tranled", check=False))


def send(frame: str) -> None:
    parts = frame.split()
    if len(parts) != 8 or any(not 0 <= int(part, 16) <= 255 for part in parts):
        raise ValueError("frame must contain exactly eight hexadecimal bytes")

    # The OEM implementation writes the same textual frame to the command node.
    escaped = shlex.quote(" ".join(part.lower() for part in parts))
    command = f"printf '%s' {escaped} > {shlex.quote(COMMAND_NODE)}"
    result = adb("shell", command, check=False)
    log(f"frame={frame} result={result!r}")
    print(result or "command sent")


def lighting(enabled: bool) -> None:
    value = "1" if enabled else "0"
    previous = adb("shell", "settings", "get", "global", LIGHTING_SETTING)
    adb("shell", "settings", "put", "global", LIGHTING_SETTING, value)
    current = adb("shell", "settings", "get", "global", LIGHTING_SETTING)
    log(f"setting={LIGHTING_SETTING} previous={previous!r} value={value} current={current!r}")
    print(f"{LIGHTING_SETTING}: {previous} -> {current}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "command", choices=("status", "on", "off", "effect", "frame")
    )
    parser.add_argument("value", nargs="?")
    args = parser.parse_args()

    try:
        if args.command == "status":
            status()
        elif args.command == "on":
            lighting(True)
        elif args.command == "off":
            lighting(False)
        elif args.command == "effect":
            if args.value not in EFFECTS:
                raise ValueError(f"effect must be one of: {', '.join(EFFECTS)}")
            send(EFFECTS[args.value])
        elif args.command == "frame":
            if not args.value:
                raise ValueError("frame requires eight hexadecimal bytes in quotes")
            send(args.value)
    except (RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
