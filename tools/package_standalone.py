"""Package the private standalone JAR only after local tests and runtime probes pass."""

import hashlib
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


boards = Path(__file__).resolve().parents[1]
jar = boards / "target" / "server-boards-1.2.1.jar"
receipt = Path(sys.argv[1]).resolve()
result = json.loads(receipt.read_text(encoding="utf-8"))
if not result.get("pass") or len(result.get("boots", [])) != 5 or not all(b["pass"] for b in result["boots"]):
    raise SystemExit("Five-stage runtime receipt is not passing")
runtime = receipt.parent
prefix = json.loads((runtime / "migration-prefix-check.json").read_text(encoding="utf-8"))
if not prefix.get("pass") or prefix.get("source_events") != 21 or prefix.get("rooms") != 4:
    raise SystemExit("Migration history prefix was not verified")
rejection = (runtime / "legacy-rejected.txt").read_text(encoding="utf-8")
if "event 3 cannot replay" not in rejection:
    raise SystemExit("Unreplayable legacy room was not identified")

totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
reports = list((boards / "target" / "surefire-reports").glob("TEST-*.xml"))
for report in reports:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, 0))
if totals != {"tests": 120, "failures": 0, "errors": 0, "skipped": 0}:
    raise SystemExit(f"JUnit results not ready: {totals}")

with zipfile.ZipFile(jar) as artifact:
    plugin = artifact.read("plugin.yml").decode("utf-8")
    if "version: 1.2.1" not in plugin or "ServerGames" in plugin:
        raise SystemExit("JAR metadata still mentions ServerGames")
    if any(name.startswith("dev/server/games/") for name in artifact.namelist()):
        raise SystemExit("ServerGames classes were bundled")
    if any(name in artifact.namelist() for name in ("dev/server/boards/BoardProvider.class",
                                                  "dev/server/boards/ServerGamesBridge.class")):
        raise SystemExit("ServerGames adapter was bundled")
    for required in ("dev/server/boards/BoardWindow.class", "menus/catalog.yml",
                     "dev/server/boards/RoomReplayVerifier.class"):
        if required not in artifact.namelist():
            raise SystemExit(f"Missing {required}")

digest = hashlib.sha256(jar.read_bytes()).hexdigest()
verification = {
    "version": "1.2.1", "jar_sha256": digest, "junit": totals,
    "python_migration_tests": 4, "runtime": {
        "purpur": "26.2-2622", "five_boots_passed": True,
        "standalone_create_and_restore": True, "server_games_2_0_3_menu_entry": True,
        "server_menu_0_7_1_entry": True, "replayable_legacy_rooms": 4,
        "legacy_actions_preserved": prefix["source_events"],
        "unreplayable_legacy_room_rejected": True,
        "jvm_probe_option": "-XX:TieredStopAtLevel=1",
    },
    "client_visual_test": False, "production_deployed": False,
}
deliverables = boards / "deliverables"
deliverables.mkdir(exist_ok=True)
(deliverables / "verification.json").write_text(json.dumps(verification, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(deliverables / "SHA256SUMS.txt").write_text(f"{digest}  server-boards-1.2.1.jar\n", encoding="ascii")
package = deliverables / "server-boards-1.2.1-standalone.zip"
with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED) as archive:
    archive.write(jar, jar.name)
    for name in ("README.md", "MIGRATION.md", "FEATURES.md", "LICENSE"):
        archive.write(boards / name, name)
    archive.write(boards / "tools" / "server_boards_migrate.py", "tools/server_boards_migrate.py")
    archive.write(deliverables / "verification.json", "verification.json")
    archive.write(deliverables / "SHA256SUMS.txt", "SHA256SUMS.txt")
print(json.dumps({"package": str(package), "sha256": hashlib.sha256(package.read_bytes()).hexdigest(),
                  "jar_sha256": digest, "junit": totals, "runtime_pass": True}, indent=2))
