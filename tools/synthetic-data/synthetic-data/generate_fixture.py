"""
Baton v1.6.2 synthetic-data generator.

Produces a JSON fixture that drives a UI/UX test of the v1.6.2
release-candidate. The data is intentionally not clean — it
includes edge cases (title == body, very long rawText, emoji,
empty body, hidden-vault person, sensitive rows) so the QA pass
exercises the failure modes as well as the happy path.

Schemas mirror the v1.6.1 entity models:
  - PersonEntity (Room)
  - InstructionEntity (Room)
  - Capture / CaptureMode
  - Tag / TagKind

Run:
    python .sdd/synthetic-data/generate_fixture.py
"""

from __future__ import annotations

import json
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Optional


# --- Time helpers --------------------------------------------------------

# Anchor "now" to the wall-clock the parent provided.
NOW = datetime(2026, 8, 18, 22, 4, 27, tzinfo=timezone(timedelta(hours=5, minutes=30)))
IST = timezone(timedelta(hours=5, minutes=30))


def iso(dt: datetime) -> str:
    """ISO 8601 with IST offset, second precision."""
    return dt.astimezone(IST).strftime("%Y-%m-%dT%H:%M:%S%z")


def days_ago(n: int, hour: int = 10, minute: int = 0) -> datetime:
    return NOW - timedelta(days=n, hours=NOW.hour - hour, minutes=NOW.minute - minute)


def days_from_now(n: int, hour: int = 10, minute: int = 0) -> datetime:
    return NOW + timedelta(days=n, hours=hour - NOW.hour, minutes=minute - NOW.minute)


def epoch_millis(dt: datetime) -> int:
    return int(dt.timestamp() * 1000)


# --- Static names (no real persons) --------------------------------------

PEOPLE: list[dict[str, Any]] = [
    # --- Junior officers (SHO / SI / CI) — Active/Inner tiers ------------
    {
        "name": "K. Suresh",
        "designation": "Station House Officer (SHO)",
        "station": "Warangal Town Police Station",
        "phone": "+91-871-2345001",
        "tier": "Active",
        "cadenceOverrideDays": None,
        "isSensitive": False,
        "vaultMode": "visible",
    },
    {
        "name": "M. Lavanya",
        "designation": "Sub-Inspector (SI)",
        "station": "Subedari Police Station",
        "phone": "+91-871-2345002",
        "tier": "Inner",
        "cadenceOverrideDays": 7,
        "isSensitive": False,
        "vaultMode": "visible",
    },
    {
        "name": "P. Rajeshwar Rao",
        "designation": "Circle Inspector (CI)",
        "station": "Jangaon Circle",
        "phone": "+91-871-2345003",
        "tier": "Active",
        "cadenceOverrideDays": None,
        "isSensitive": False,
        "vaultMode": "visible",
    },
    {
        "name": "B. Srinivas",
        "designation": "Sub-Inspector (Traffic)",
        "station": "District Traffic Wing, Warangal",
        "phone": "+91-871-2345004",
        "tier": "Active",
        "cadenceOverrideDays": None,
        "isSensitive": False,
        "vaultMode": "visible",
    },
    # --- Senior officers (SP / DSP / Addl SP) — Periodic/Dormant ---------
    {
        "name": "M. Ravi Kumar (IPS)",
        "designation": "Superintendent of Police (SP)",
        "station": "Warangal District Headquarters",
        "phone": "+91-871-2345005",
        "tier": "Periodic",
        "cadenceOverrideDays": 14,
        "isSensitive": False,
        "vaultMode": "visible",
    },
    {
        "name": "T. Anitha",
        "designation": "Deputy Superintendent of Police (DSP)",
        "station": "Warangal Urban Sub-Division",
        "phone": "+91-871-2345006",
        "tier": "Periodic",
        "cadenceOverrideDays": None,
        "isSensitive": False,
        "vaultMode": "visible",
    },
    {
        "name": "K. Ramana",
        "designation": "Additional Superintendent of Police",
        "station": "District Headquarters, Warangal",
        "phone": "+91-871-2345007",
        "tier": "Dormant",
        "cadenceOverrideDays": None,
        "isSensitive": False,
        "vaultMode": "visible",
    },
    # --- Civil administration (MRO / Tehsildar) --------------------------
    {
        "name": "G. Swapna",
        "designation": "Tehsildar",
        "station": "Warangal Tehsil Office",
        "phone": "+91-871-2345008",
        "tier": "Periodic",
        "cadenceOverrideDays": 21,
        "isSensitive": False,
        "vaultMode": "visible",
    },
    {
        "name": "V. Mallesh",
        "designation": "Mandal Revenue Officer (MRO)",
        "station": "Narsampet Mandal",
        "phone": "+91-871-2345009",
        "tier": "Active",
        "cadenceOverrideDays": None,
        "isSensitive": False,
        "vaultMode": "visible",
    },
    # --- Civilians (non-sensitive) ---------------------------------------
    {
        "name": "Ramesh (informal)",  # pseudonym, kept in name field
        "designation": "Local informant",
        "station": "Pan shop near Innam PS",
        "phone": "+91-987-6543210",
        "tier": "Dormant",
        "cadenceOverrideDays": None,
        "isSensitive": False,
        "vaultMode": "visible",
    },
    # --- Sensitive civilians (witness, accused) --------------------------
    {
        # Protected witness in a CrPC 164 statement case
        "name": "Lakshmi Devi",
        "designation": "Witness (protected)",
        "station": "Protective address — do not record",
        "phone": None,  # phone withheld intentionally
        "tier": "Periodic",
        "cadenceOverrideDays": None,
        "isSensitive": True,
        "vaultMode": "visible",
    },
    {
        # Under-trial accused, hidden vault mode
        "name": "K. Mahesh",
        "designation": "Under-trial accused",
        "station": "Central Jail, Warangal",
        "phone": None,
        "tier": "Dormant",
        "cadenceOverrideDays": None,
        "isSensitive": True,
        "vaultMode": "hidden",
    },
]


# --- Instruction catalog -------------------------------------------------

@dataclass
class InstrSpec:
    """Plain-data spec for one instruction row."""
    person_key: Optional[str]      # matches PEOPLE index, or None for free-floating
    status: str                    # OPEN / DONE / DROPPED / CARRIED_OVER
    title: str
    raw_text: str
    captured_days_ago: int
    due_offset_days: Optional[int] # negative = past, positive = future, None = no due
    is_sensitive: bool = False
    direction: str = "OUTGOING"
    source: str = "TEXT"
    priority: str = "NORMAL"
    case_type: Optional[str] = None
    urgency: str = "normal"
    review_offset_days: Optional[int] = None  # for worry_with_date
    completed_offset_days: Optional[int] = None  # days after capture that DONE happened
    dropped_reason: Optional[str] = None


# (person_key, status, title, body, capturedDaysAgo, dueOffsetDays or None, sensitive)
INSTRUCTIONS: list[InstrSpec] = [
    # --- Recent (0-14 days) — should appear in Today / 14d bucket --------
    InstrSpec("suresh", "OPEN", "Verify the FIR copy from Innam PS",
              "Need signed copy of FIR 217/2026 from Innam PS before the IO files the chargesheet. Check the seizure list is also attached.",
              captured_days_ago=1, due_offset_days=2),
    InstrSpec("lavanya", "OPEN", "Brief the IO before the court hearing on 22 Aug",
              "Walk IO through the 164 statement and the witness protection order before the Magistrate's hearing.",
              captured_days_ago=0, due_offset_days=3),
    InstrSpec("mro_mallesh", "OPEN", "Call MRO about the survey numbers",
              "Confirm the survey numbers for the disputed land at Narsampet mandal before sending to the Revenue Divisional Officer.",
              captured_days_ago=2, due_offset_days=1),
    InstrSpec("dsp_anitha", "OPEN", "Send status report to DSP by EOD",
              "Status of bandobast arrangements for the 24 Aug rally at Enumamula. Casualty-vacant posts filled, route plan attached.",
              captured_days_ago=0, due_offset_days=0, priority="HIGH"),
    InstrSpec(None, "OPEN", "Approve the seized vehicle release order",
              "Two-wheeled vehicle seized in FIR 198/2026 — owner has produced documents. Release order needs SP signature.",
              captured_days_ago=3, due_offset_days=4),
    InstrSpec("lavanya", "OPEN", "Coordinate with fire services for the blaze",
              "Blaze reported near Kazipet market, 3 shops gutted. Coordinate with Fire Station for the post-mortem on the cause.",
              captured_days_ago=1, due_offset_days=2, priority="HIGH"),
    InstrSpec("ci_raju", "OPEN", "Review the statement of the protected witness",
              "Fresh statement of Lakshmi Devi recorded at the safe house. Needs the SP's countersign before submission to the Magistrate.",
              captured_days_ago=4, due_offset_days=5, is_sensitive=True),

    # --- 14-30 day bucket ------------------------------------------------
    InstrSpec("suresh", "CARRIED_OVER", "Confirm bandobast plan for 15 Aug",
              "Independence Day bandobast — 12 PCR vans, 4 striking force units, route plan attached. Carried over from last week.",
              captured_days_ago=18, due_offset_days=-5, case_type="Case"),
    InstrSpec("ramesh", "OPEN", "Follow up with Ramesh on the dharna call",
              "Ramesh reported a possible dharna call at Innam on the 28th. Need confirmation by 17 Aug.",
              captured_days_ago=15, due_offset_days=-2),
    InstrSpec("dsp_anitha", "DONE", "Sign the case diary and forward to SP office",
              "Case diary of FIR 192/2026 (chain-snatching) signed and forwarded. Acknowledgement from SP office received.",
              captured_days_ago=22, due_offset_days=-14, completed_offset_days=2),
    InstrSpec(None, "OPEN", "Read the forensic report from FSL",
              "FSL report on the pistol recovered from the accused — serology + ballistics. Pull from district file, summarise for IO.",
              captured_days_ago=14, due_offset_days=10, priority="HIGH"),
    InstrSpec("accused_mahesh", "OPEN", "Note: Mahesh's bail hearing posted to 25 Aug",
              "Confirm the remand extension is filed before the 25 Aug bail hearing at the Sessions Court. Coordinate with the public prosecutor.",
              captured_days_ago=12, due_offset_days=7, is_sensitive=True, case_type="Case"),

    # --- 30-60 day bucket ------------------------------------------------
    InstrSpec("mro_mallesh", "DONE", "Get the medical certificate from GH Warangal",
              "Medical certificate for the dowry case victim pulled from GH Warangal. Filed in case diary, copy retained.",
              captured_days_ago=42, due_offset_days=-30, completed_offset_days=1),
    InstrSpec("tehsildar", "OPEN", "Coordinate with Tehsildar on the land dispute",
              "Joint inspection of the disputed land at Wardhannapet on the 12th. Tehsildar to lead, MRO + SHO Suresh to accompany.",
              captured_days_ago=35, due_offset_days=-10),
    InstrSpec("si_traffic", "DONE", "Check the seizure register at Subedari PS",
              "Cross-check the seizure register entries from 18-22 July against the case diaries. Two discrepancies noted and corrected.",
              captured_days_ago=45, due_offset_days=-35, completed_offset_days=3),
    InstrSpec(None, "DROPPED", "Old note: coordinate with IB for security briefing",
              "Was waiting for the IB officer's security briefing — handled by Addl SP Ramana instead. Drop from list.",
              captured_days_ago=50, due_offset_days=-40, dropped_reason="Handled by Addl SP Ramana — no further action required from me."),
    InstrSpec("dsp_anitha", "DONE", "Review the monthly crime statistics for the district",
              "Reviewed and signed off. Sent to the DIG office on 20 July. Filed copy in the office.",
              captured_days_ago=55, due_offset_days=-45, completed_offset_days=2),
    InstrSpec("ci_raju", "CARRIED_OVER", "File the chargesheet within 60 days as per CrPC 167",
              "FIR 144/2026 (NDPS Act) — chargesheet must be filed before 18 Aug. Carried over from last fortnight.",
              captured_days_ago=50, due_offset_days=-12, case_type="FIR", priority="HIGH"),
    InstrSpec("witness_lakshmi", "DONE", "Approve witness protection request — Lakshmi Devi",
              "Witness protection order approved, safe house allotment done. Acknowledgement from the Witness Protection Cell received.",
              captured_days_ago=38, due_offset_days=-30, is_sensitive=True, completed_offset_days=4),

    # --- 60-90 day bucket ------------------------------------------------
    InstrSpec("sp_ravi", "DONE", "Brief the SP on the highway accident",
              "Highway accident at km 142 on the Warangal-Hyderabad route — 2 casualties. Briefed the SP, press note issued.",
              captured_days_ago=70, due_offset_days=-60, completed_offset_days=2),
    InstrSpec("suresh", "DONE", "Send the remand file to the public prosecutor",
              "Remand file for FIR 102/2026 (robbery) sent to the public prosecutor's office. Acknowledgement received.",
              captured_days_ago=72, due_offset_days=-65, completed_offset_days=1),
    InstrSpec("addlsp_ramana", "OPEN", "Touch base with Addl SP Ramana",
              "Hasn't been in office for a month. Need to know if he's still on the Headquarters post. Will get back from the next month's review.",
              captured_days_ago=80, due_offset_days=None, priority="LOW"),
    InstrSpec(None, "DROPPED", "Old annual report ask — supersede",
              "Was asked to compile the annual report for the district — handed over to the office superintendent. Drop.",
              captured_days_ago=85, due_offset_days=-70, dropped_reason="Handed over to office superintendent."),

    # --- 90+ day bucket (decay test) -------------------------------------
    InstrSpec("lavanya", "DONE", "Coordinate with fire services — old blaze at Enumamula",
              "Old fire incident at Enumamula market from May — closure report filed. Cross-referenced in the quarterly review.",
              captured_days_ago=98, due_offset_days=-90, completed_offset_days=6),
    InstrSpec("dsp_anitha", "DONE", "Sign the office order for SI Lavanya's transfer",
              "Office order signed. SI Lavanya to join Subedari PS on 20 May. Acknowledgement received.",
              captured_days_ago=110, due_offset_days=-100, completed_offset_days=2),
    InstrSpec("sp_ravi", "DONE", "Initial briefing with SP after posting",
              "Met SP M. Ravi Kumar the day after taking over as Additional SP Warangal. Discussed district priorities and pending cases.",
              captured_days_ago=120, due_offset_days=-115, completed_offset_days=0),

    # --- Future-due (no fixed past capture) ------------------------------
    InstrSpec("si_traffic", "OPEN", "Coordinate bandobast for the CM's convoy on 5 Sep",
              "CM's convoy is scheduled to pass through Warangal on 5 Sep. Traffic re-routing + 12 striking force on standby.",
              captured_days_ago=5, due_offset_days=18, priority="HIGH"),
    InstrSpec("mro_mallesh", "OPEN", "Joint survey with MRO — disputed land Wardhannapet",
              "Joint survey of the disputed land scheduled for 28 Aug. Coordinates with the Tehsildar to lead.",
              captured_days_ago=6, due_offset_days=10),

    # --- Worry-box entries (urgency = worry / worry_with_date) -----------
    InstrSpec("accused_mahesh", "OPEN", "Mahesh's co-accused still at large",
              "Co-accused in the same FIR hasn't been traced. Public prosecutor flagged this in the status report.",
              captured_days_ago=3, due_offset_days=None, is_sensitive=True,
              urgency="worry", case_type="Case"),
    InstrSpec("sp_ravi", "OPEN", "Pending: SP's nod on the bandobast overtime budget",
              "Overtime budget for the 15 Aug bandobast hasn't been cleared by the SP's office. May block next month's payments.",
              captured_days_ago=8, due_offset_days=None, urgency="worry_with_date",
              review_offset_days=4),
    InstrSpec(None, "OPEN", "Reminder: pull the 5-yr-old NDPS conviction record",
              "Old NDPS conviction at the Warangal Sessions Court may be relevant for the current NDPS case at Bhongir. Pull and review.",
              captured_days_ago=1, due_offset_days=None, urgency="worry_with_date",
              review_offset_days=10),

    # --- EDGE CASES (intentional messiness) -----------------------------
    # 1. Title == body (test duplicate title/body UI bug)
    InstrSpec("suresh", "OPEN", "Pull FIR 217/2026",
              "Pull FIR 217/2026",
              captured_days_ago=0, due_offset_days=1),
    # 2. Very long rawText (2-4 lines)
    InstrSpec(None, "OPEN", "Bandobast review — multi-point",
              ("Joint review of the 15 Aug bandobast deployment with the CI and the Tehsildar. "
               "Outstanding items: (1) PCR van deployment on the Warangal-Hyderabad route, "
               "(2) striking force units at Enumamula and Subedari, (3) fire services standby, "
               "(4) medical teams at GH Warangal and the area hospital, (5) press cell briefing "
               "scheduled for 14 Aug at 1100 hrs. Action: draft consolidated plan and circulate by EOD 13 Aug."),
              captured_days_ago=2, due_offset_days=1, priority="HIGH"),
    # 3. Emoji in title
    InstrSpec("lavanya", "OPEN", "🚨 Follow up on the protected witness welfare check",
              "Welfare check at the safe house done. 🏠 protected witness is stable. Reschedule next visit to the 22nd.",
              captured_days_ago=2, due_offset_days=3, is_sensitive=True),
    # 4. Empty-ish body (only spaces + a single dash) — uncommon but realistic
    InstrSpec(None, "OPEN", "Misc note to self",
              " - ",
              captured_days_ago=1, due_offset_days=None),
    # 5. Whitespace-heavy title
    InstrSpec("ci_raju", "OPEN", "    Read the remand extension order    ",
              "Remand extension order for FIR 144/2026 received from the Sessions Court. Read and file.",
              captured_days_ago=3, due_offset_days=2, case_type="FIR"),
]


# --- Tags ----------------------------------------------------------------

TAGS: list[dict[str, Any]] = [
    {"name": "FIR",         "kind": "FIR",        "color": "#E53935"},
    {"name": "court",       "kind": "FREE",       "color": "#1E88E5"},
    {"name": "witness",     "kind": "FREE",       "color": "#43A047"},
    {"name": "SP",          "kind": "DESIGNATION","color": "#8E24AA"},
    {"name": "DSP",         "kind": "DESIGNATION","color": "#8E24AA"},
    {"name": "SHO",         "kind": "DESIGNATION","color": "#8E24AA"},
    {"name": "vip-duty",    "kind": "FREE",       "color": "#FB8C00"},
    {"name": "bandobast",   "kind": "FREE",       "color": "#6D4C41"},
    {"name": "bhongir-ps",  "kind": "STATION",    "color": "#546E7A"},
    {"name": "innam-ps",    "kind": "STATION",    "color": "#546E7A"},
    {"name": "case-diary",  "kind": "CASE",       "color": "#3949AB"},
    {"name": "mandal",      "kind": "FREE",       "color": "#00897B"},
]


# --- Captures (raw input) ------------------------------------------------

# These are written before/around the same time as instructions.
# `linked_instruction_idx` is the index into the INSTRUCTIONS list above,
# or None for unprocessed captures.

CAPTURE_SPECS: list[dict[str, Any]] = [
    {
        "mode": "TEXT",
        "raw_text": "suresh bhayya — verify the FIR copy from Innam PS, chargesheet filing is on me by EOD",
        "person_key": "suresh",
        "captured_days_ago": 1,
        "linked_instruction_idx": 0,   # Verify the FIR copy
    },
    {
        "mode": "VOICE",
        "raw_text": "Brief the I O before the twenty second August court hearing at the Magistrate's court, lavanya is leading",
        "person_key": "lavanya",
        "captured_days_ago": 0,
        "linked_instruction_idx": 1,
    },
    {
        "mode": "PHOTO",
        "raw_text": None,  # photo-only, no typed body
        "audioUri": None,
        "imageUri": "content://baton/captures/2026-08-18-fir-217-001.jpg",
        "person_key": None,
        "captured_days_ago": 0,
        "linked_instruction_idx": 4,  # seized vehicle release order (photo of the seizure memo)
    },
    {
        "mode": "TEXT",
        "raw_text": "Unprocessed note — mro mallesh may have the survey numbers from the Narsampet mandal office",
        "person_key": "mro_mallesh",
        "captured_days_ago": 2,
        "linked_instruction_idx": None,  # unlinked — user hasn't tapped "save" yet
    },
    {
        "mode": "VOICE",
        "raw_text": "Need to pull the FSL report on the pistol from the accused, case FIR one four four",
        "person_key": None,
        "captured_days_ago": 14,
        "linked_instruction_idx": 10,  # Read the forensic report
    },
    {
        "mode": "TEXT",
        "raw_text": "Mahesh's bail hearing — twenty fifth Aug, sessions court. Confirm remand extension is filed.",
        "person_key": "accused_mahesh",
        "captured_days_ago": 12,
        "linked_instruction_idx": 11,
    },
    {
        "mode": "PHOTO",
        "raw_text": None,
        "imageUri": "content://baton/captures/2026-08-15-bandobast-plan-pg3.jpg",
        "person_key": "suresh",
        "captured_days_ago": 18,
        "linked_instruction_idx": 7,  # bandobast plan for 15 Aug
    },
]


# --- Build the records ---------------------------------------------------

def _build_persons() -> tuple[list[dict[str, Any]], dict[str, str]]:
    """Returns the JSON list and a name→id map for instruction linking."""
    out: list[dict[str, Any]] = []
    name_to_id: dict[str, str] = {}
    for i, p in enumerate(PEOPLE):
        pid = str(uuid.uuid4())
        # lastInteractionAt is epoch millis of the most recent activity
        # for this person; we wire it to the most recent instruction's
        # capturedAt (or createdAt - 90d if no instructions).
        key = _person_key(i)
        recent = [s for s in INSTRUCTIONS if s.person_key == key]
        if recent:
            li_dt = min((days_ago(s.captured_days_ago) for s in recent),
                        key=lambda d: abs((d - NOW).total_seconds()))
            last_inter = epoch_millis(li_dt)
        else:
            last_inter = epoch_millis(days_ago(90))
        created = days_ago(120)  # backdate person creation
        updated = NOW - timedelta(hours=2)
        out.append({
            "id": pid,
            "name": p["name"],
            "designation": p["designation"],
            "station": p["station"],
            "phone": p["phone"],
            "userId": "user-local-test",
            "createdAt": iso(created),
            "updatedAt": iso(updated),
            "isSensitive": p["isSensitive"],
            "syncStatus": "SYNCED" if not p["isSensitive"] else "PENDING_INSERT",
            "tier": p["tier"],
            "cadenceOverrideDays": p["cadenceOverrideDays"],
            "lastInteractionAt": last_inter,
            "vaultMode": p["vaultMode"],
        })
        name_to_id[key] = pid
    return out, name_to_id


def _person_key(index: int) -> str:
    """Stable short key for a person slot (for spec reference only)."""
    keys = [
        "suresh", "lavanya", "ci_raju", "si_traffic",          # 0-3 SHO/SI/CI
        "sp_ravi", "dsp_anitha", "addlsp_ramana",              # 4-6 senior
        "tehsildar", "mro_mallesh",                           # 7-8 civil
        "ramesh",                                              # 9 civilian
        "witness_lakshmi", "accused_mahesh",                  # 10-11 sensitive
    ]
    return keys[index]


def _build_instructions(person_id_by_key: dict[str, str]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for s in INSTRUCTIONS:
        iid = str(uuid.uuid4())
        captured = days_ago(s.captured_days_ago)
        # Nudge the capturedAt to a realistic office-hour time
        captured = captured.replace(hour=10, minute=(s.captured_days_ago * 7) % 60)
        created = captured + timedelta(seconds=30)
        updated = created
        if s.status == "DONE" and s.completed_offset_days is not None:
            completed = captured + timedelta(days=s.completed_offset_days)
        else:
            completed = None
        if s.due_offset_days is None:
            due_at: Optional[str] = None
        elif s.due_offset_days < 0:
            due_at = iso(days_ago(-s.due_offset_days))
        else:
            due_at = iso(days_from_now(s.due_offset_days))
        person_id = person_id_by_key.get(s.person_key) if s.person_key else None
        review_day: Optional[int] = None
        if s.urgency == "worry_with_date" and s.review_offset_days is not None:
            # Epoch-day of the review date (LocalDate.toEpochDay() convention)
            review_day = (NOW + timedelta(days=s.review_offset_days)).date().toordinal() - (datetime(1970, 1, 1).date().toordinal())
        out.append({
            "id": iid,
            "personId": person_id,
            "direction": s.direction,
            "status": s.status,
            "source": s.source,
            "priority": s.priority,
            "title": s.title,
            "rawText": s.raw_text,
            "dueAt": due_at,
            "capturedAt": iso(captured),
            "createdAt": iso(created),
            "updatedAt": iso(updated),
            "isSensitive": s.is_sensitive,
            "syncStatus": "SYNCED" if not s.is_sensitive else "PENDING_INSERT",
            "completedAt": iso(completed) if completed else None,
            "droppedReason": s.dropped_reason,
            "nextActionAt": epoch_millis(NOW + timedelta(days=2)) if s.status == "OPEN" and s.due_offset_days and s.due_offset_days > 0 else None,
            "caseType": s.case_type,
            "urgency": s.urgency,
            "reviewAtEpochDay": review_day,
        })
    return out


def _build_captures(person_id_by_key: dict[str, str],
                    instructions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for c in CAPTURE_SPECS:
        cid = str(uuid.uuid4())
        captured = days_ago(c["captured_days_ago"]).replace(hour=9, minute=37)
        person_id = person_id_by_key.get(c["person_key"]) if c.get("person_key") else None
        idx = c.get("linked_instruction_idx")
        linked_id = instructions[idx]["id"] if idx is not None and 0 <= idx < len(instructions) else None
        out.append({
            "id": cid,
            "mode": c["mode"],
            "rawText": c.get("raw_text"),
            "audioUri": c.get("audioUri"),
            "imageUri": c.get("imageUri"),
            "personId": person_id,
            "processed": linked_id is not None,
            "linkedInstructionId": linked_id,
            "createdAt": iso(captured),
        })
    return out


def _build_tags() -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for t in TAGS:
        tid = str(uuid.uuid4())
        created = days_ago(60)
        last_used = NOW - timedelta(days=2)
        out.append({
            "id": tid,
            "name": t["name"],
            "kind": t["kind"],
            "color": t.get("color"),
            "usageCount": 7,
            "lastUsedAt": iso(last_used),
            "createdAt": iso(created),
            "updatedAt": iso(NOW - timedelta(hours=6)),
        })
    return out


# --- Validation ----------------------------------------------------------

def validate(persons, instructions, captures, tags) -> list[str]:
    issues: list[str] = []
    all_ids = ([p["id"] for p in persons] +
               [i["id"] for i in instructions] +
               [c["id"] for c in captures] +
               [t["id"] for t in tags])
    if len(all_ids) != len(set(all_ids)):
        # Bucket-by-bucket uniqueness still holds in practice; flag if not.
        from collections import Counter
        dupes = [k for k, v in Counter(all_ids).items() if v > 1]
        issues.append(f"duplicate IDs: {dupes}")
    person_ids = {p["id"] for p in persons}
    for i, ins in enumerate(instructions):
        if ins["personId"] is not None and ins["personId"] not in person_ids:
            issues.append(f"instruction[{i}].personId -> unknown person {ins['personId']}")
    for i, cap in enumerate(captures):
        if cap["personId"] is not None and cap["personId"] not in person_ids:
            issues.append(f"capture[{i}].personId -> unknown person {cap['personId']}")
    for i, ins in enumerate(instructions):
        for k in ("capturedAt", "createdAt", "updatedAt"):
            try:
                datetime.fromisoformat(ins[k].replace("Z", "+00:00"))
            except (TypeError, ValueError):
                issues.append(f"instruction[{i}].{k} not parseable: {ins[k]!r}")
        if ins["dueAt"] is not None:
            try:
                datetime.fromisoformat(ins["dueAt"].replace("Z", "+00:00"))
            except (TypeError, ValueError):
                issues.append(f"instruction[{i}].dueAt not parseable: {ins['dueAt']!r}")
    for i, cap in enumerate(captures):
        try:
            datetime.fromisoformat(cap["createdAt"].replace("Z", "+00:00"))
        except (TypeError, ValueError):
            issues.append(f"capture[{i}].createdAt not parseable: {cap['createdAt']!r}")
    return issues


# --- Main ----------------------------------------------------------------

def main() -> int:
    persons, name_to_id = _build_persons()
    instructions = _build_instructions(name_to_id)
    captures = _build_captures(name_to_id, instructions)
    tags = _build_tags()

    # Worries are NOT a separate entity in Baton -- they're just
    # Instruction rows with `urgency` in ("worry", "worry_with_date").
    # The WorryBox section on Today picks them out of the
    # instructions list. So we don't emit a top-level `worries`
    # field anymore; the FixtureLoader reads only `instructions`
    # and counts urgency != "normal" to populate the report.
    worry_count = sum(
        1 for ins in instructions
        if ins["urgency"] in ("worry", "worry_with_date") and ins["status"] == "OPEN"
    )

    fixture = {
        "schemaVersion": 1,
        "generatedAt": iso(NOW),
        "anchorNow": iso(NOW),
        "counts": {
            "persons": len(persons),
            "instructions": len(instructions),
            "captures": len(captures),
            "tags": len(tags),
            "worries": worry_count,
        },
        "persons": persons,
        "instructions": instructions,
        "captures": captures,
        "tags": tags,
    }

    issues = validate(persons, instructions, captures, tags)
    if issues:
        print("VALIDATION FAILED:")
        for it in issues:
            print("  -", it)
        return 1

    out_path = Path(__file__).parent / "v1.6.2-fixture.json"
    out_path.write_text(json.dumps(fixture, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"OK  {out_path}")
    print(f"    persons={len(persons)} instructions={len(instructions)} "
          f"captures={len(captures)} tags={len(tags)} worries={worry_count}")
    print(f"    size={out_path.stat().st_size} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
