import sys
import unittest
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from server_boards_migrate import convert, verify


class MigrateRoomsTest(unittest.TestCase):
    def room(self, kind="connectfour"):
        return {
            "id": str(uuid.uuid4()), "kind": kind, "capacity": 2, "table": 0,
            "seed": 12, "phase": "LOBBY", "revision": 1,
            "seats": [{"id": str(uuid.uuid4()), "name": "A", "bot": False}],
            "history": [],
        }

    def test_adds_only_explicit_anchor_and_preserves_history(self):
        room = self.room()
        room["history"] = [{"seat": 0, "action": "drop:3"}]
        source = {"schema": 1, "rooms": [room], "returns": {}}
        anchor = {"world": str(uuid.uuid4()), "x": 10.5, "y": 82, "z": -5.5}
        migrated = convert(source, {room["id"]: anchor})
        self.assertEqual(room["history"], migrated["rooms"][0]["history"])
        self.assertEqual(anchor["world"], migrated["rooms"][0]["anchorWorld"])
        self.assertNotIn("anchorWorld", room)
        self.assertTrue(verify(source, {room["id"]: anchor}, migrated))

    def test_refuses_missing_anchor_and_unsupported_kind(self):
        room = self.room()
        source = {"schema": 1, "rooms": [room], "returns": {}}
        with self.assertRaises(ValueError):
            convert(source, {})
        room["kind"] = "mahjong"
        with self.assertRaises(ValueError):
            convert(source, {room["id"]: {"world": str(uuid.uuid4()), "x": 0, "y": 80, "z": 0}})

    def test_refuses_duplicate_player_in_another_room(self):
        first = self.room()
        second = self.room("chess")
        second["table"] = 1
        second["seats"][0]["id"] = first["seats"][0]["id"]
        source = {"schema": 1, "rooms": [first, second], "returns": {}}
        mapping = {room["id"]: {"world": str(uuid.uuid4()), "x": 0, "y": 83, "z": 0}
                   for room in (first, second)}
        with self.assertRaisesRegex(ValueError, "Player seated twice"):
            convert(source, mapping)

    def test_rejects_modified_output(self):
        room = self.room()
        source = {"schema": 1, "rooms": [room], "returns": {}}
        mapping = {room["id"]: {"world": str(uuid.uuid4()), "x": 0, "y": 80, "z": 0}}
        migrated = convert(source, mapping)
        migrated["rooms"][0]["history"].append({"seat": 0, "action": "drop:0"})
        self.assertFalse(verify(source, mapping, migrated))


if __name__ == "__main__":
    unittest.main()
