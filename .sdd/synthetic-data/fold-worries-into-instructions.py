"""v1.6.2 fixture fix: drop the `worries` array and fold the entries
into the `instructions` list with `urgency` set. The FixtureLoader
no longer has a WorryDto class; worries are now instructions with
urgency == "worry" or "worry_with_date".

This script rewrites the SAME source JSON in place (overwrites the
generator output and the bundled asset) so the SHA stays
deterministic.

Usage:
  python fold-worries-into-instructions.py <fixture.json>
"""
import json, sys, os
from collections import OrderedDict

if len(sys.argv) < 2:
    print(__doc__)
    sys.exit(2)

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as f:
    data = json.load(f, object_pairs_hook=OrderedDict)

worries = data.pop("worries", [])
existing_ids = {i["id"] for i in data["instructions"]}
merged = 0
for w in worries:
    if w["id"] in existing_ids:
        # instruction of same id already present; skip
        continue
    # Promote to a full instruction row
    ins = OrderedDict()
    ins["id"] = w["id"]
    ins["personId"] = w.get("personId")
    ins["direction"] = "INCOMING"  # worries are things to attend to
    ins["status"] = "ACTIVE"
    ins["source"] = "TEXT"
    ins["priority"] = "WATCH"
    ins["title"] = w.get("title", "")
    ins["rawText"] = w.get("rawText", "")
    ins["dueAt"] = None
    ins["capturedAt"] = w.get("capturedAt")
    ins["createdAt"] = w.get("capturedAt")
    ins["updatedAt"] = w.get("capturedAt")
    ins["isSensitive"] = False
    ins["syncStatus"] = "SYNCED"
    ins["completedAt"] = None
    ins["droppedReason"] = None
    ins["nextActionAt"] = None
    ins["caseType"] = "Worry"
    ins["urgency"] = w.get("urgency", "worry")
    ins["reviewAtEpochDay"] = w.get("reviewAtEpochDay")
    data["instructions"].append(ins)
    existing_ids.add(ins["id"])
    merged += 1

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)
    f.write("\n")
print(f"merged {merged} worries into instructions; new instructions count: {len(data['instructions'])}")
print(f"wrote: {path}")
