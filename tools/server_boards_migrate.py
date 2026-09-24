"""Prepare a ServerGames/ServerBoards rooms.json for portable ServerBoards tables.

The source is never edited. Missing world anchors require an explicit room-ID map.
"""

import argparse
import copy
import hashlib
import json
import math
import shutil
import subprocess
import tempfile
import uuid
from pathlib import Path


KINDS = {"xiangqi", "gomoku", "chess", "aeroplane", "checkers", "draughts",
         "reversi", "go", "go9", "go13", "connectfour"}
PHASES = {"LOBBY", "STARTING", "PLAYING", "PAUSED", "FINISHED"}


def convert(source, mapping):
    result = copy.deepcopy(source)
    if result.get("schema") != 1 or not isinstance(result.get("rooms"), list):
        raise ValueError("Expected schema 1 rooms.json")
    if not isinstance(mapping, dict) or not isinstance(result.get("returns", {}), dict):
        raise ValueError("Invalid mapping or returns")
    seen_rooms, seen_tables, seen_players = set(), set(), set()
    for room in result["rooms"]:
        room_id = str(uuid.UUID(room["id"]))
        kind, capacity, table = room["kind"], room["capacity"], room["table"]
        if room_id in seen_rooms or table in seen_tables:
            raise ValueError("Duplicate room ID or table index")
        seen_rooms.add(room_id)
        seen_tables.add(table)
        if kind not in KINDS or room.get("phase") not in PHASES:
            raise ValueError(f"Unsupported room kind or phase: {room_id}")
        if not isinstance(table, int) or not 0 <= table <= 127:
            raise ValueError(f"Invalid table index: {room_id}")
        valid_capacity = ({2, 3, 4, 6} if kind == "checkers" else
                          {2, 3, 4} if kind == "aeroplane" else {2})
        if capacity not in valid_capacity or not isinstance(room.get("history"), list):
            raise ValueError(f"Invalid capacity or history: {room_id}")
        seats = room.get("seats")
        if not isinstance(seats, list) or len(seats) > capacity:
            raise ValueError(f"Invalid seats: {room_id}")
        for seat in seats:
            player = str(uuid.UUID(seat["id"]))
            if not seat["bot"]:
                if player in seen_players:
                    raise ValueError(f"Player seated twice: {player}")
                seen_players.add(player)
        if "anchorWorld" not in room:
            if room_id not in mapping:
                raise ValueError(f"Missing explicit anchor for room {room_id}")
            anchor = mapping[room_id]
            room["anchorWorld"] = str(uuid.UUID(anchor["world"]))
            for source_key, target_key in (("x", "anchorX"), ("y", "anchorY"), ("z", "anchorZ")):
                value = anchor[source_key]
                if not isinstance(value, (int, float)) or not math.isfinite(value):
                    raise ValueError(f"Invalid {source_key} for room {room_id}")
                room[target_key] = value
        else:
            uuid.UUID(room["anchorWorld"])
            for key in ("anchorX", "anchorY", "anchorZ"):
                if not isinstance(room.get(key), (int, float)) or not math.isfinite(room[key]):
                    raise ValueError(f"Invalid {key} for room {room_id}")
    if set(mapping) - seen_rooms:
        raise ValueError("Mapping names a room absent from the source")
    result.setdefault("returns", {})
    return result


def verify(source, mapping, output):
    return convert(source, mapping) == output


def replay(candidate, jar):
    if not jar.is_file():
        raise ValueError(f"Rules JAR not found: {jar}")
    with tempfile.TemporaryDirectory(prefix="boards-migration-") as directory:
        path = Path(directory) / "rooms.json"
        path.write_text(json.dumps(candidate, ensure_ascii=False), encoding="utf-8")
        result = subprocess.run([shutil.which("java") or "java", "-cp", str(jar),
                                 "dev.server.boards.RoomReplayVerifier", str(path)],
                                capture_output=True, text=True)
        if result.returncode:
            raise ValueError("Rules replay rejected migration candidate:\n" + result.stderr.strip())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("mapping", type=Path, help="JSON object keyed by room ID; use {} for already anchored data")
    parser.add_argument("output", type=Path)
    default_jar = Path(__file__).resolve().parents[1] / "server-boards-1.2.1.jar"
    if not default_jar.is_file():
        default_jar = Path(__file__).resolve().parents[1] / "target" / "server-boards-1.2.1.jar"
    parser.add_argument("--jar", type=Path, default=default_jar, help="the exact ServerBoards JAR intended for installation")
    parser.add_argument("--check", action="store_true", help="compare an existing output without editing it")
    args = parser.parse_args()
    if args.source.resolve() == args.output.resolve():
        parser.error("Source and output must differ")
    source_bytes = args.source.read_bytes()
    source = json.loads(source_bytes)
    mapping = json.loads(args.mapping.read_text(encoding="utf-8"))
    expected = convert(source, mapping)
    replay(expected, args.jar)
    if args.check:
        if not verify(source, mapping, json.loads(args.output.read_text(encoding="utf-8"))):
            raise ValueError("Output differs from source plus explicit anchors")
    else:
        with args.output.open("x", encoding="utf-8", newline="\n") as target:
            json.dump(expected, target, ensure_ascii=False, indent=2)
            target.write("\n")
    print(f"rooms={len(expected['rooms'])} source_sha256={hashlib.sha256(source_bytes).hexdigest()} "
          f"output_sha256={hashlib.sha256(args.output.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
