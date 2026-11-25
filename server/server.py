from __future__ import annotations

import asyncio
import json
import os
import secrets
import socket
import subprocess
import time
from typing import Any

import psutil
import websockets

HOST = "0.0.0.0"
PORT = int(os.getenv("FERDECK_PORT", "8765"))
PIN = os.getenv("FERDECK_PIN") or f"{secrets.randbelow(1_000_000):06d}"

# Fixed command allowlist. The Android client sends IDs, never executable paths or shell strings.
COMMANDS: dict[str, list[str]] = {
    "notepad": ["notepad.exe"],
    "calculator": ["calc.exe"],
    "steam": [r"C:\Program Files (x86)\Steam\steam.exe"],
    "browser": ["cmd.exe", "/c", "start", "", "https://www.google.com"],
    "explorer": ["explorer.exe"],
}
ALLOWED_KEYS = {
    "ctrl", "shift", "alt", "windows", "win", "enter", "esc", "tab", "space",
    "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12",
    "up", "down", "left", "right", "home", "end", "delete", "backspace",
    *list("abcdefghijklmnopqrstuvwxyz0123456789")
}


def local_ip() -> str:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 80))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


def gpu_usage() -> float:
    try:
        output = subprocess.check_output(
            ["nvidia-smi", "--query-gpu=utilization.gpu", "--format=csv,noheader,nounits"],
            text=True, timeout=2, stderr=subprocess.DEVNULL
        )
        return float(output.strip().splitlines()[0])
    except (OSError, subprocess.SubprocessError, ValueError, IndexError):
        return -1.0


def send_hotkey(keys: str) -> None:
    import keyboard
    parts = [part.strip().lower() for part in keys.split("+") if part.strip()]
    if not parts or len(parts) > 5 or any(part not in ALLOWED_KEYS for part in parts):
        raise ValueError("Недопустимая комбинация клавиш")
    keyboard.send("+".join("windows" if part == "win" else part for part in parts))


def run_macro(sequence: str) -> None:
    # Example: ctrl+c;wait:250;ctrl+v
    steps = [step.strip() for step in sequence.split(";") if step.strip()]
    if not steps or len(steps) > 24:
        raise ValueError("Макрос должен содержать от 1 до 24 шагов")
    for step in steps:
        if step.startswith("wait:"):
            try:
                milliseconds = int(step.split(":", 1)[1])
            except ValueError as exc:
                raise ValueError("Некорректная задержка макроса") from exc
            if not 0 <= milliseconds <= 5000:
                raise ValueError("Задержка должна быть от 0 до 5000 мс")
            time.sleep(milliseconds / 1000)
        else:
            send_hotkey(step)


def send_media(action: str) -> None:
    import keyboard
    keys = {"play_pause": "play/pause media", "next": "next track", "prev": "previous track", "mute": "volume mute"}
    if action not in keys:
        raise ValueError("Неизвестная media-команда")
    keyboard.press_and_release(keys[action])


def set_master_volume(value: Any) -> None:
    scalar = max(0.0, min(1.0, float(value)))
    try:
        from pycaw.pycaw import AudioUtilities
        AudioUtilities.GetSpeakers().EndpointVolume.SetMasterVolumeLevelScalar(scalar, None)
    except Exception:
        # Fallback approximation using Windows media keys; no shell execution.
        import keyboard
        for _ in range(50):
            keyboard.press_and_release("volume down")
        for _ in range(round(scalar * 50)):
            keyboard.press_and_release("volume up")


async def send_telemetry(websocket: Any) -> None:
    while True:
        await websocket.send(json.dumps({
            "type": "telemetry",
            "cpu": psutil.cpu_percent(interval=None),
            "ram": psutil.virtual_memory().percent,
            "gpu": gpu_usage(),
        }, separators=(",", ":")))
        await asyncio.sleep(1)


async def handler(websocket: Any) -> None:
    authenticated = False
    telemetry_task: asyncio.Task | None = None
    try:
        async for raw in websocket:
            try:
                message = json.loads(raw)
                if not isinstance(message, dict):
                    continue
            except (json.JSONDecodeError, TypeError):
                await websocket.send(json.dumps({"type": "error", "error": "invalid_json"}))
                continue

            if not authenticated:
                if message.get("type") == "auth" and secrets.compare_digest(str(message.get("pin", "")), PIN):
                    authenticated = True
                    await websocket.send(json.dumps({"type": "auth_ok"}))
                    telemetry_task = asyncio.create_task(send_telemetry(websocket))
                else:
                    await websocket.send(json.dumps({"type": "auth_error"}))
                    await websocket.close(code=4001, reason="PIN rejected")
                    return
                continue

            try:
                kind = message.get("type")
                if kind == "hotkey":
                    send_hotkey(str(message.get("keys", ""))[:120])
                elif kind == "macro":
                    run_macro(str(message.get("keys", ""))[:1500])
                elif kind == "media":
                    send_media(str(message.get("action", "")))
                elif kind == "volume":
                    set_master_volume(message.get("value", 0))
                elif kind == "launch":
                    command_id = str(message.get("command", ""))
                    command = COMMANDS.get(command_id)
                    if command is None:
                        raise ValueError("Команда отсутствует в белом списке")
                    subprocess.Popen(command, shell=False)
                else:
                    raise ValueError("Неизвестный тип команды")
                await websocket.send(json.dumps({"type": "ack", "ok": True}))
            except Exception as exc:
                await websocket.send(json.dumps({"type": "ack", "ok": False, "error": str(exc)[:180]}))
    finally:
        if telemetry_task:
            telemetry_task.cancel()
            try:
                await telemetry_task
            except asyncio.CancelledError:
                pass


async def main() -> None:
    psutil.cpu_percent(interval=None)
    print("=" * 54)
    print("FerDeck • Windows companion server")
    print(f"Address : ws://{local_ip()}:{PORT}")
    print(f"Port    : {PORT}")
    print(f"PIN     : {PIN}")
    print("Phone and PC must be connected to the same Wi-Fi.")
    print("Allow Python through Windows Firewall on Private networks.")
    print("Ctrl+C to stop.")
    print("=" * 54)
    async with websockets.serve(handler, HOST, PORT, ping_interval=20, max_size=65536):
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nFerDeck server stopped.")
