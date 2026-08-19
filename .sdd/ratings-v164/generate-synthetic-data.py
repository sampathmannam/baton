"""
Baton v1.6.4 synthetic data generator.

Generates a large, diverse fixture (50+ persons, 200+ instructions, all
status/priority/direction/urgency types, edge cases for text length,
emoji, unicode, sensitive flags, vault mode).

Output: writes the new fixture to app/src/main/assets/synthetic-data.json
        (overwrites the v1.6.2 fixture) AND a copy to
        .sdd/ratings-v164/synthetic-data.json for git-tracking.

Usage:
  cd baton-v2-integration
  python .sdd/ratings-v164/generate-synthetic-data.py
  # then rebuild + reinstall the APK and trigger "Load test data" in
  # Settings → Developer on the device.
"""

import json
import uuid
import random
import datetime
import os
from collections import Counter

random.seed(42)  # reproducible

NOW = datetime.datetime(2026, 8, 19, 14, 0, 0, tzinfo=datetime.timezone(datetime.timedelta(hours=5, minutes=30)))
ANCHOR_DAY = NOW.date()
USER_ID = "user-local-test"

# ---------- helpers ----------

def iso(dt):
    """Format datetime as ISO 8601 with +05:30 offset (Indian Standard Time)."""
    return dt.strftime("%Y-%m-%dT%H:%M:%S+0530")

def days_ago(n, hour=10, minute=0):
    return NOW - datetime.timedelta(days=n, hours=(NOW.hour - hour), minutes=(NOW.minute - minute))

def in_future(n):
    return NOW + datetime.timedelta(days=n)

def short_uuid():
    return str(uuid.uuid4())

# ---------- domain vocab (Tamil + Telugu + Hindi + English) ----------

FIRST_NAMES = [
    # English / transliterated Indian
    "K. Suresh", "G. Swapna", "K. Ramana", "M. Lavanya", "M. Ravi Kumar",
    "P. Rajeshwar Rao", "B. Srinivas", "T. Anitha", "V. Mallesh",
    "K. Mahesh", "Ramesh", "Lakshmi Devi", "D. Suresh Babu",
    "A. Venkateshwarlu", "B. Ramesh Naidu", "Ch. Srinivasa Rao",
    "D. Ramakrishna", "E. Venkata Rao", "G. Suresh Kumar",
    "J. Ramesh Babu", "K. Srinivasa Reddy", "M. Suresh Reddy",
    "N. Venkatesh", "P. Suresh Kumar", "R. Srinivasa Rao",
    "S. Ramesh Babu", "T. Venkata Ramana", "V. Suresh Babu",
    # Tamil
    "மு. சுரேஷ்", "கோ. ஸ்வப்நா", "வெ. ராமணா",
    "சு. லவண்யா", "ரா. ரவி குமார்",
    # Hindi
    "अ. राजेश्वर राव", "बी. श्रीनिवास", "सु. अनीता",
    "वी. मल्लेश", "के. महेश", "रमेश कुमार",
    "लक्ष्मी देवी", "द. सुरेश बाबू",
    # Edge cases
    "  Whitespace  Edge  ",  # leading/trailing whitespace
    "A" * 50,  # very long name
    "X" * 200,  # extremely long name (should truncate or wrap)
    "🚨 Urgent Person 🚨",  # emoji
    "O'Brien Jr.",  # apostrophe
    "Person-with-many-hyphens-and-some-special-chars",  # special chars
    "李明 (Chinese)",  # CJK
    "الاسم العربي",  # Arabic RTL
    "हिन्दी नाम",  # Hindi Devanagari
]

DESIGNATIONS = [
    "Sub-Inspector (SI)", "Sub-Inspector (Traffic)", "Sub-Inspector (Women)",
    "Inspector", "Inspector (Law & Order)", "Inspector (Crime)",
    "Deputy Superintendent of Police (DSP)", "Additional Superintendent of Police",
    "Superintendent of Police (SP)", "Circle Inspector (CI)",
    "Station House Officer (SHO)", "Head Constable",
    "Constable", "Sub-Inspector (Cyber Crime)", "Inspector (Vigilance)",
    "Assistant Commissioner of Police (ACP)", "Deputy Commissioner of Police (DCP)",
    "Tehsildar", "Revenue Divisional Officer (RDO)",
    "Witness (protected)",  # edge case
    "",  # empty
    "X" * 100,  # very long
    "🚔 Officer",  # emoji
    "Officer — Cyber Wing",  # em-dash
]

STATIONS = [
    "Warangal Town Police Station", "Subedari Police Station",
    "Jangaon Circle", "District Headquarters, Warangal",
    "District Traffic Wing, Warangal", "Warangal Tehsil Office",
    "Cyber Crime Police Station", "Women's Police Station, Warangal",
    "Innam Police Station", "Kompally Police Station",
    "",  # empty
    "Station-with-a-very-long-name-that-might-wrap-on-mobile-screens",
]

INSTRUCTION_TITLES = [
    "Verify the FIR copy from Innam PS",
    "Get status update on land dispute case",
    "Submit weekly crime report by Friday",
    "Coordinate with K. Mahesh on chain snatching cases",
    "Review the CCTV footage from Main Road",
    "Brief me on the 2024 murder appeal status",
    "Phone call back to Inspector Ramesh — urgent",
    "Pull the call detail records for accused #4",
    "Check the seized vehicle list — 7 cars, 2 bikes",
    "Forward the post-mortem report to magistrate",
    "Witness protection — Lakshmi Devi address change",
    "Ramesh informant's monthly payout (₹12,000)",
    "Submit chargesheet for chain snatching accused — deadline 22 Aug",
    "Verify the bail application status of accused #2",
    "Get the IO's statement on record",
    "Schedule a meeting with the SP — land dispute",
    "Get the weapon seizure memo signed by DSP",
    "Update the FSL report status",
    "Brief the SHO before the magistrate visit",
    "Coordinate with Cyber Cell on the phishing case",
    "Confirm the identity parade date",
    "Get the IO to file the 164 statement tomorrow",
    "Check the SAKAUTO seizure case — IO on leave",
    "Submit the 41 CrPC report",
    "Brief me on the 41A notice compliance",
    "Phone back SHO Ramesh — bail matter",
    "Verify the 50 CrPC compliance for all accused",
    "Get the medical report from GH Warangal",
    "Confirm the witness list for chain snatching case",
    "Get the seizure panchnama signed by IO",
    "Submit the IO's confidential report",
    "Brief me on the SIT status — chain snatching",
    "Phone SHO on the new chain snatching complaint",
    "Get the IO to verify the alibi of accused #5",
    "Submit the daily diary report by 6 PM",
    "Phone SHO — urgent: chain snatching at Innam",
    "Confirm the IO for the 2024 murder appeal",
    "Get the statement of the new informant",
    "Coordinate with Cyber Cell on the UPI fraud case",
    "Brief the new constables on chain snatching patterns",
    "Submit the weekly chain snatching report",
    "Phone SHO — chain snatching at Subedari PS",
    "Get the seizure memo for the 2 bikes",
    "Confirm the IO for the 7-car seizure case",
    "Brief the IO on the chain snatching case — DSP wants update",
    "Phone SHO — chain snatching at Kompally",
    "Get the 2024 murder appeal status from HC",
    "Submit the monthly crime statistics report",
    "Phone SHO — chain snatching at Innam PS again",
    "Get the IO's report on the 2024 murder appeal",
    # Edge cases
    "  Whitespace Title  ",  # leading/trailing
    "T" * 200,  # very long title
    "🚨 URGENT — chain snatching 🚨",  # emoji
    "தமிழ் தலைப்பு",  # Tamil title
    "हिन्दी शीर्षक",  # Hindi title
    "1234567890",  # numbers only
    "...",  # just dots
    "",  # empty title (edge case)
    "Same as body",  # used in body too
    "Single character.",  # very short
    "Query?",  # ends with ?
]

INSTRUCTION_BODIES = [
    "Need signed copy of FIR 217/2026 from Innam PS before the IO files the chargesheet. Check the seizure list is also attached.",
    "Land dispute at Jangaon — 3 accused arrested, 2 absconding. Need the 164 statement from the eyewitness by EOW.",
    "Weekly crime report should include chain snatching statistics, cyber crime numbers, and pending investigation summary.",
    "K. Mahesh (DSP) wants the chain snatching case status by EOD. He's reviewing all 7 cases tonight.",
    "CCTV footage from Main Road (4 cameras) for the chain snatching case. Need it pulled and given to the IO for analysis.",
    "Murder appeal — Sessions Court issued notice for status. Need the IO's reply brief by 25 Aug.",
    "Inspector Ramesh called about the 2024 murder appeal. He's worried about the IO's response. Need to call back today.",
    "CDR for accused #4 in the chain snatching case. Court order is on file. Get the IO to file the application.",
    "Seized vehicles: 7 cars (3 white, 2 red, 1 black, 1 silver) and 2 bikes (1 Royal Enfield, 1 Bajaj). Need full list with VINs.",
    "Post-mortem report from GH Warangal — need it forwarded to the magistrate for the chain snatching case.",
    "Lakshmi Devi's address needs to change for witness protection. Coordinate with the Witness Protection Cell.",
    "Ramesh (informal) — monthly payout ₹12,000. Need it cleared by 25 Aug. He's reliable on chain snatching cases.",
    "Chargesheet filing deadline is 22 Aug for the chain snatching accused (3 of them). Need all documents ready by 20 Aug.",
    "Accused #2 filed for bail. Need the IO to prepare the objection brief. Court hearing is on 23 Aug.",
    "IO's statement on record for the 2024 murder appeal — need to coordinate with the IO before EOW.",
    "SP wants a meeting on the land dispute. Schedule before the 25 Aug deadline. He'll be at the District HQ.",
    "Weapon seizure memo (3 countrymades, 1 imported) — need DSP's signature on the memo by EOD.",
    "FSL report for the chain snatching case is pending. Follow up with the FSL — 6-week turnaround expected.",
    "Magistrate visit scheduled for 28 Aug. Brief the SHO before then. He'll need a status on the chain snatching cases.",
    "Cyber Cell is taking up the phishing case. Coordinate with the IO — they'll need 3 days to process the complaints.",
    "Identity parade for the chain snatching case — schedule for 26 Aug at 10 AM at District HQ. Need 5 dummy suspects.",
    "164 statement from the new informant in the 2024 murder appeal. IO needs to record it by 22 Aug.",
    "SAKAUTO seizure case — IO is on leave. Need a substitute IO assigned by 20 Aug.",
    "41 CrPC report due for 3 cases. Need to be filed by 22 Aug. Coordinate with the IO.",
    "41A notice compliance — need status update on 5 accused. They should be informed of the chargesheet filing deadline.",
    "SHO Ramesh called about a bail matter. Urgent. Need to call back within 1 hour.",
    "50 CrPC compliance for all 5 accused in the chain snatching case. Need confirmation by 24 Aug.",
    "Medical report from GH Warangal for accused #3. He's been in hospital since 18 Aug.",
    "Witness list for chain snatching case — 5 eyewitnesses, 1 informant. Need to confirm availability by 25 Aug.",
    "Seizure panchnama for the 7 cars. IO needs to sign it before the chargesheet is filed.",
    "IO's confidential report on the chain snatching case — 7 accused, 3 in custody, 4 absconding.",
    "SIT status on the chain snatching cases. Need a brief from the SIT before the SP meeting on 28 Aug.",
    "SHO called — new chain snatching complaint at Innam PS. 2 accused, 1 victim. Need to file FIR by EOD.",
    "IO to verify the alibi of accused #5. He's claiming he was at a wedding in Karimnagar on 15 Aug.",
    "Daily diary report — need it submitted by 6 PM every day. DSP wants it on his desk at 7 PM.",
    "URGENT: chain snatching at Innam PS. 3 cases in 2 hours. Need to brief the SP before the news reaches him.",
    "IO for the 2024 murder appeal — need to confirm. Sessions Court is asking for the IO's name by 23 Aug.",
    "Statement from the new informant — he's nervous. Need the IO to record it before he changes his mind.",
    "Cyber Cell taking up the UPI fraud case. They need 3 days. Need to brief the complainant before 24 Aug.",
    "New constables joining on 25 Aug. Need to brief them on chain snatching patterns. 2-hour session.",
    "Weekly chain snatching report — 7 cases this week, 12 arrests. Need to send to DSP by Friday 6 PM.",
    "Chain snatching at Subedari PS. 1 accused arrested. Need to file the FIR and brief the IO.",
    "Seizure memo for 2 bikes (1 RE, 1 Bajaj). Need to file with the magistrate before 24 Aug.",
    "IO for the 7-car seizure case — need confirmation. He's been transferred to Cyber Cell last week.",
    "DSP wants a brief on the chain snatching case. Need to prepare a 2-page summary with statistics.",
    "Chain snatching at Kompally PS. 1 accused, 1 victim. Need to file FIR and brief the IO by EOD.",
    "2024 murder appeal status from High Court. Need the next hearing date and the IO's response deadline.",
    "Monthly crime statistics report — need to send to the SP by 25 Aug. Includes 7 sections of data.",
    "Chain snatching at Innam PS again. 2 cases in 1 hour. Need to alert the SP before it gets to the press.",
    "IO's report on the 2024 murder appeal. He's been working on it for 2 weeks. Need to chase him for status.",
    # Edge cases
    "",  # empty body
    "B" * 500,  # very long body
    "🚨 URGENT 🚨 chain snatching at Innam PS. 3 cases. 1 victim is a doctor. Media may pick up. Need to brief SP within 1 hour. Coordinate with IO. Brief the press cell. 🚨",  # emoji + very long
    "தமிழ் உடல் — chain snatching at Innam PS. 3 cases. Need to brief the SP in Tamil.",  # Tamil body
    "हिन्दी शरीर — chain snatching at Innam PS. 3 cases. Need to brief the SP in Hindi.",  # Hindi body
    "Title only",  # body == title
    "T",  # single char body
]

CAPTURE_TEXTS = [
    "suresh bhayya — verify the FIR copy from Innam PS, chargesheet filing is on me by EOD",
    "suresh — bhayya call back, urgent bail matter",
    "swapna — verify the bail application status of accused #2",
    "ramana — K. Mahesh DSP wants the chain snatching case status by EOD",
    "rajeshwar rao — land dispute at Jangaon, 3 accused arrested",
    "anitha — weekly crime report by Friday 6 PM",
    "informal ramesh — monthly payout ₹12,000, clear by 25 Aug",
    "lakshmi devi — witness protection address change",
    "🚨 URGENT 🚨 chain snatching at Innam PS. 3 cases in 2 hours. Brief SP within 1 hour.",
    "அவசரம் — chain snatching at Innam PS, 3 cases",  # Tamil
    "अर्जेंट — chain snatching at Innam PS, 3 cases",  # Hindi
    "short",  # very short
    "X" * 300,  # very long
    "",  # empty (just a photo/voice)
]

TAG_NAMES = [
    ("chain-snatching", "topic"),
    ("land-dispute", "topic"),
    ("cyber-crime", "topic"),
    ("murder-appeal", "topic"),
    ("witness-protection", "topic"),
    ("urgent", "topic"),
    ("deadline", "topic"),
    ("follow-up", "topic"),
    ("warrant", "topic"),
    ("informant", "topic"),
    ("2024-case", "topic"),
    ("media-sensitive", "topic"),
    ("Innam PS", "station"),
    ("Subedari PS", "station"),
    ("Jangaon Circle", "station"),
    ("District HQ", "station"),
]

# ---------- builders ----------

def make_person(idx, name_override=None):
    name = name_override or random.choice(FIRST_NAMES)
    designation = random.choice(DESIGNATIONS)
    station = random.choice(STATIONS)
    last_interaction = days_ago(random.randint(0, 90))
    is_sensitive = random.random() < 0.10  # 10% sensitive
    vault_mode = "hidden" if random.random() < 0.15 else "visible"
    return {
        "id": short_uuid(),
        "name": name,
        "designation": designation,
        "station": station,
        "phone": f"+91-871-2345{idx:03d}",
        "userId": USER_ID,
        "createdAt": iso(days_ago(random.randint(60, 365))),
        "updatedAt": iso(days_ago(random.randint(0, 30))),
        "isSensitive": is_sensitive,
        "syncStatus": "SYNCED",
        "tier": random.choice(["Active", "Active", "Active", "Quiet", "Dormant"]),
        "cadenceOverrideDays": None,
        "lastInteractionAt": int(last_interaction.timestamp() * 1000),
        "vaultMode": vault_mode,
    }

def make_instruction(persons, idx):
    p = random.choice(persons)
    direction = random.choice(["INCOMING", "INCOMING", "OUTGOING", "OUTGOING", "OUTGOING"])  # more OUTGOING
    status = random.choices(
        ["OPEN", "OPEN", "OPEN", "OPEN", "OPEN", "OPEN", "OPEN",  # 70% OPEN
         "DONE", "DONE",  # 20% DONE
         "CARRIED_OVER",  # 5%
         "DROPPED"],  # 5%
        k=1,
    )[0]
    source = random.choice(["TEXT", "TEXT", "TEXT", "VOICE", "PHOTO", "OCR"])
    priority = random.choices(["NORMAL", "NORMAL", "NORMAL", "HIGH", "URGENT"], k=1)[0]
    urgency = random.choices(
        ["normal", "normal", "normal", "normal", "normal", "normal", "normal", "normal", "normal",
         "worry", "worry", "worry_with_date"],  # 25% worry
        k=1,
    )[0]
    title = random.choice(INSTRUCTION_TITLES)
    raw_text = random.choice(INSTRUCTION_BODIES)
    captured = days_ago(random.randint(0, 30), hour=random.randint(8, 18), minute=random.randint(0, 59))
    updated = captured + datetime.timedelta(hours=random.randint(0, 72))
    completed = None
    dropped_reason = None
    review_at_epoch_day = None
    if status == "DONE":
        completed = iso(updated + datetime.timedelta(hours=random.randint(1, 48)))
    elif status == "DROPPED":
        dropped_reason = random.choice([
            "Already handled by K. Mahesh",
            "Duplicate of an earlier instruction",
            "Witness turned hostile",
            "Accused absconded — case cold",
            "Chargesheet filed — closed",
        ])
    if urgency == "worry_with_date":
        review_at_epoch_day = (ANCHOR_DAY + datetime.timedelta(days=random.randint(1, 14))).toordinal()
    due_at = None
    if random.random() < 0.30:
        due_at = iso(days_ago(random.randint(-7, 14)))  # some overdue, some future
    return {
        "id": short_uuid(),
        "personId": p["id"],
        "direction": direction,
        "status": status,
        "source": source,
        "priority": priority,
        "title": title,
        "rawText": raw_text,
        "dueAt": due_at,
        "capturedAt": iso(captured),
        "createdAt": iso(captured),
        "updatedAt": iso(updated),
        "isSensitive": p["isSensitive"] and random.random() < 0.5,
        "syncStatus": "SYNCED",
        "completedAt": completed,
        "droppedReason": dropped_reason,
        "nextActionAt": None,
        "caseType": None,
        "urgency": urgency,
        "reviewAtEpochDay": review_at_epoch_day,
    }

def make_capture(persons, instructions):
    if random.random() < 0.7:
        # text-only capture, sometimes linked to an instruction
        ins = random.choice(instructions) if instructions else None
        p = next((p for p in persons if p["id"] == ins["personId"]), None) if ins else None
        return {
            "id": short_uuid(),
            "mode": "TEXT",
            "rawText": random.choice(CAPTURE_TEXTS),
            "audioUri": None,
            "imageUri": None,
            "personId": p["id"] if p else None,
            "processed": True,
            "linkedInstructionId": ins["id"] if ins and random.random() < 0.5 else None,
            "createdAt": iso(days_ago(random.randint(0, 30))),
            "syncStatus": "SYNCED",
            "ocrText": None,
            "calendarEventId": None,
            "urgency": "normal",
            "reviewAtEpochDay": None,
        }
    else:
        # voice or photo capture
        p = random.choice(persons)
        return {
            "id": short_uuid(),
            "mode": random.choice(["VOICE", "PHOTO", "OCR"]),
            "rawText": None,
            "audioUri": f"content://com.baton.app.fileprovider/audio/{short_uuid()}.m4a" if random.random() < 0.5 else None,
            "imageUri": f"content://com.baton.app.fileprovider/images/{short_uuid()}.jpg" if random.random() < 0.7 else None,
            "personId": p["id"],
            "processed": random.random() < 0.7,
            "linkedInstructionId": None,
            "createdAt": iso(days_ago(random.randint(0, 30))),
            "syncStatus": "SYNCED",
            "ocrText": random.choice(CAPTURE_TEXTS) if random.random() < 0.3 else None,
            "calendarEventId": None,
            "urgency": "normal",
            "reviewAtEpochDay": None,
        }

def make_tag(name, kind):
    return {
        "id": short_uuid(),
        "name": name,
        "kind": kind,
        "color": random.choice(["#5B5FCF", "#A78BFA", "#34D399", "#F59E0B", "#EF4444", "#3B82F6"]),
        "usageCount": random.randint(0, 20),
        "lastUsedAt": iso(days_ago(random.randint(0, 60))),
        "userId": USER_ID,
        "createdAt": iso(days_ago(random.randint(60, 365))),
        "updatedAt": iso(days_ago(random.randint(0, 30))),
        "syncStatus": "SYNCED",
    }

# ---------- main ----------

def build():
    print("Building v1.6.4 synthetic fixture...")
    # 50 persons total
    persons = []
    # Keep the 12 originals (to maintain the same Person names people have already seen)
    with open("app/src/main/assets/synthetic-data.json", encoding="utf-8") as f:
        old = json.load(f)
    for p in old["persons"]:
        persons.append(p)
    # Add 38 new persons
    for i in range(38):
        persons.append(make_person(len(persons)))
    # Add a few specific edge-case persons at the end (deterministic)
    edge_names = [
        "  Whitespace Edge  ",
        "X" * 50,
        "🚨 Urgent Person 🚨",
        "李明 (Chinese)",
        "الاسم العربي",
    ]
    for n in edge_names:
        persons.append(make_person(len(persons), name_override=n))
    # Total = 12 + 38 + 5 = 55 persons

    # 200 instructions
    instructions = [make_instruction(persons, i) for i in range(200)]
    # Make sure at least 3 are worries (per v1.6.2 spec)
    for ins in instructions[:3]:
        ins["urgency"] = "worry"
        ins["reviewAtEpochDay"] = (ANCHOR_DAY + datetime.timedelta(days=random.randint(1, 7))).toordinal()
    # Make sure at least 1 is overdue
    instructions[3]["dueAt"] = iso(days_ago(2))
    instructions[3]["status"] = "OPEN"
    # Make sure at least 1 is high priority
    instructions[4]["priority"] = "URGENT"
    instructions[4]["urgency"] = "worry_with_date"
    instructions[4]["reviewAtEpochDay"] = (ANCHOR_DAY + datetime.timedelta(days=2)).toordinal()

    # 50 captures
    captures = [make_capture(persons, instructions) for _ in range(50)]

    # All tags from the dictionary (de-duped)
    seen = set()
    tags = []
    for name, kind in TAG_NAMES:
        if (name, kind) not in seen:
            seen.add((name, kind))
            tags.append(make_tag(name, kind))

    counts = {
        "persons": len(persons),
        "instructions": len(instructions),
        "captures": len(captures),
        "tags": len(tags),
        "instructionTags": 0,
    }
    inst_status = Counter(i["status"] for i in instructions)
    inst_urgency = Counter(i["urgency"] for i in instructions)
    inst_priority = Counter(i["priority"] for i in instructions)
    inst_direction = Counter(i["direction"] for i in instructions)
    inst_source = Counter(i["source"] for i in instructions)

    fixture = {
        "schemaVersion": 2,
        "generatedAt": iso(NOW),
        "anchorNow": iso(NOW),
        "counts": counts,
        "persons": persons,
        "instructions": instructions,
        "captures": captures,
        "tags": tags,
    }

    # Summary
    print(f"  Persons:      {len(persons)}")
    print(f"  Instructions: {len(instructions)}")
    print(f"    by status:   {dict(inst_status)}")
    print(f"    by urgency:  {dict(inst_urgency)}")
    print(f"    by priority: {dict(inst_priority)}")
    print(f"    by direction:{dict(inst_direction)}")
    print(f"    by source:   {dict(inst_source)}")
    print(f"  Captures:     {len(captures)}")
    print(f"  Tags:         {len(tags)}")

    return fixture


if __name__ == "__main__":
    fixture = build()
    # Write to assets (overwrite v1.6.2 fixture)
    out_assets = "app/src/main/assets/synthetic-data.json"
    with open(out_assets, "w", encoding="utf-8") as f:
        json.dump(fixture, f, ensure_ascii=False, indent=2)
    # Also write to .sdd for git tracking
    out_sdd = ".sdd/ratings-v164/synthetic-data.json"
    os.makedirs(os.path.dirname(out_sdd), exist_ok=True)
    with open(out_sdd, "w", encoding="utf-8") as f:
        json.dump(fixture, f, ensure_ascii=False, indent=2)
    print(f"\nWrote {out_assets} ({os.path.getsize(out_assets):,} bytes)")
    print(f"Wrote {out_sdd} ({os.path.getsize(out_sdd):,} bytes)")
