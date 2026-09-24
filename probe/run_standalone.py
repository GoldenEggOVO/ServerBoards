"""Run five isolated localhost Purpur boots for standalone and optional entries."""

import json
import shutil
import subprocess
import time
import zipfile
from pathlib import Path


boards = Path(__file__).resolve().parents[1]
root = boards.parent
source = root / "table-games" / "tabletop-runtime"
stamp = time.strftime("%Y%m%d-%H%M%S")
runtime = boards / "target" / f"standalone-smoke-{stamp}"
runtime.mkdir(parents=True)
shutil.copy2(source / "purpur-2622.jar", runtime / "purpur-2622.jar")
for directory in ("libraries", "versions", "cache"):
    if (source / directory).is_dir():
        shutil.copytree(source / directory, runtime / directory)
(runtime / "eula.txt").write_text("eula=true\n", encoding="utf-8")
(runtime / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=25617\nlevel-name=boards_smoke\n"
    'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
    "online-mode=false\nview-distance=2\nsimulation-distance=2\n"
    "spawn-protection=0\nenable-rcon=false\nenable-query=false\n",
    encoding="utf-8",
)
plugins = runtime / "plugins"
plugins.mkdir()
candidate = boards / "target" / "server-boards-1.2.1.jar"
if not candidate.is_file():
    raise FileNotFoundError(candidate)
shutil.copy2(candidate, plugins / candidate.name)
paper_api = root / ".tools" / "m2" / "io" / "papermc" / "paper" / "paper-api" / "26.2.build.111-stable" / "paper-api-26.2.build.111-stable.jar"
kyori = root / ".tools" / "m2" / "net" / "kyori"
compile_classpath = ";".join(str(path) for path in (
    paper_api,
    kyori / "adventure-key" / "5.2.0" / "adventure-key-5.2.0.jar",
    kyori / "adventure-api" / "5.2.0" / "adventure-api-5.2.0.jar",
))
classes = runtime / "probe-classes"
classes.mkdir()
subprocess.run([shutil.which("javac"), "-encoding", "UTF-8", "-cp", compile_classpath,
                "-d", str(classes), str(boards / "probe" / "BoardsStandaloneProbe.java")], check=True)
with zipfile.ZipFile(plugins / "BoardsStandaloneProbe.jar", "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("plugin.yml", "name: BoardsStandaloneProbe\nversion: 1\nmain: dev.server.boards.probe.BoardsStandaloneProbe\napi-version: '26.2'\ndepend: [ServerBoards]\n")
    for path in classes.rglob("*.class"):
        archive.write(path, path.relative_to(classes).as_posix())

evidence = []
for boot, marker in ((1, "BOARDS_STANDALONE_CREATE_PASS"), (2, "BOARDS_STANDALONE_RESTORE_PASS"),
                     (3, "BOARDS_SG_MENU_PASS"), (4, "BOARDS_MENU_PASS"),
                     (5, "BOARDS_MIGRATION_PASS")):
    if boot == 3:
        games_jar = root / "table-games" / "server-games" / "target" / "server-games-2.0.3.jar"
        shutil.copy2(games_jar, plugins / games_jar.name)
    if boot == 4:
        menu_jar = root / "server-menu" / "target" / "server-menu-0.7.1.jar"
        essentials_jar = source / "plugins" / "EssentialsX-2.22.1-20260829.175939-22.jar"
        shutil.copy2(menu_jar, plugins / menu_jar.name)
        shutil.copy2(essentials_jar, plugins / essentials_jar.name)
    if boot == 5:
        for optional in (games_jar, menu_jar, essentials_jar):
            (plugins / optional.name).unlink()
        data = plugins / "ServerBoards" / "rooms.json"
        shutil.copy2(data, runtime / "created-rooms-backup.json")
        world_id = json.loads(data.read_text(encoding="utf-8"))["rooms"][0]["anchorWorld"]
        legacy = source / "plugins" / "ServerGames" / "rooms.json"
        full_legacy = json.loads(legacy.read_text(encoding="utf-8"))
        legacy_rooms = full_legacy["rooms"]
        mapping = {room["id"]: {"world": world_id, "x": 32.5 + room["table"] * 16,
                                "y": 83, "z": 32.5} for room in legacy_rooms}
        mapping_path = runtime / "explicit-anchors.json"
        mapping_path.write_text(json.dumps(mapping, indent=2), encoding="utf-8")
        candidate_path = runtime / "candidate-rooms.json"
        migrate = boards / "tools" / "server_boards_migrate.py"
        rejected = subprocess.run([shutil.which("python"), str(migrate), str(legacy), str(mapping_path),
                                   str(candidate_path)], capture_output=True, text=True)
        if rejected.returncode == 0 or "event 3" not in rejected.stderr or candidate_path.exists():
            raise RuntimeError("Invalid legacy replay was not rejected before output")
        (runtime / "legacy-rejected.txt").write_text(rejected.stderr, encoding="utf-8")
        # This isolated fixture contains one known invalid historical game. Keep the full
        # source untouched; exercise the four independently replayable games as a separate input.
        replayable = dict(full_legacy)
        replayable["rooms"] = [room for room in legacy_rooms if room["kind"] != "aeroplane"]
        replayable_path = runtime / "replayable-legacy-fixture.json"
        replayable_path.write_text(json.dumps(replayable, ensure_ascii=False), encoding="utf-8")
        replayable_mapping = {room["id"]: mapping[room["id"]] for room in replayable["rooms"]}
        mapping_path.write_text(json.dumps(replayable_mapping, indent=2), encoding="utf-8")
        subprocess.run([shutil.which("python"), str(migrate), str(replayable_path), str(mapping_path),
                        str(candidate_path)], check=True)
        subprocess.run([shutil.which("python"), str(migrate), str(replayable_path), str(mapping_path),
                        str(candidate_path), "--check"], check=True)
        shutil.copy2(candidate_path, data)
    out = runtime / f"boot-{boot}.stdout.log"
    with out.open("w", encoding="utf-8") as log:
        process = subprocess.Popen([shutil.which("java"), "-Xms512M", "-Xmx2G", "-XX:TieredStopAtLevel=1",
                                    *(["-Dboards.probe.sgmenu=true"] if boot == 3 else
                                      ["-Dboards.probe.menu=true"] if boot == 4 else
                                      ["-Dboards.probe.migration=true"] if boot == 5 else []),
                                    "-Dterminal.jline=false", "-Dterminal.ansi=false",
                                    "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                                    "-jar", "purpur-2622.jar", "nogui"],
                                   cwd=runtime, stdin=subprocess.PIPE, stdout=log,
                                   stderr=subprocess.STDOUT, text=True,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        deadline = time.monotonic() + 180
        found = False
        try:
            while time.monotonic() < deadline and process.poll() is None:
                latest = runtime / "logs" / "latest.log"
                content = latest.read_text(encoding="utf-8", errors="replace") if latest.exists() else ""
                if marker in content:
                    found = True
                    break
                if "BOARDS_STANDALONE_PROBE_FAIL" in content:
                    break
                time.sleep(1)
            if process.poll() is None:
                process.stdin.write("stop\n")
                process.stdin.flush()
                process.wait(timeout=40)
        except (subprocess.TimeoutExpired, OSError):
            process.kill()
            process.wait()
        content = (runtime / "logs" / "latest.log").read_text(encoding="utf-8", errors="replace")
        (runtime / f"boot-{boot}.log").write_text(content, encoding="utf-8")
        prefix_preserved = True
        if boot == 5:
            original_rooms = {room["id"]: room for room in json.loads(candidate_path.read_text(encoding="utf-8"))["rooms"]}
            restored_rooms = {room["id"]: room for room in json.loads(data.read_text(encoding="utf-8"))["rooms"]}
            prefix_preserved = original_rooms.keys() == restored_rooms.keys() and all(
                restored_rooms[room_id]["history"][:len(room["history"])] == room["history"]
                for room_id, room in original_rooms.items())
            (runtime / "migration-prefix-check.json").write_text(
                json.dumps({"pass": prefix_preserved, "rooms": len(original_rooms),
                            "source_events": sum(len(room["history"]) for room in original_rooms.values()),
                            "restored_events": sum(len(room["history"]) for room in restored_rooms.values())}, indent=2),
                encoding="utf-8")
        evidence.append({"boot": boot, "pass": found and process.returncode == 0 and prefix_preserved,
                         "exit_code": process.returncode,
                         "plugins": [line for line in content.splitlines() if "Enabling Server" in line or "Enabling BoardsStandaloneProbe" in line],
                         "markers": [line for line in content.splitlines() if "BOARDS_" in line or "Error occurred while enabling" in line]})
        if not evidence[-1]["pass"]:
            break

receipt = {"runtime": str(runtime), "pass": len(evidence) == 5 and all(item["pass"] for item in evidence),
           "boots": evidence, "client_visual_test": False, "production_deployed": False}
(runtime / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(receipt, ensure_ascii=False, indent=2))
raise SystemExit(0 if receipt["pass"] else 1)
