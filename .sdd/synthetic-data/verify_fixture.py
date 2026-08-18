"""One-off verification of the generated fixture."""
import json
import sys
from collections import Counter
from datetime import datetime, timezone, timedelta

IST = timezone(timedelta(hours=5, minutes=30))

# Force utf-8 so emoji + non-ASCII text print cleanly on Windows cp1252.
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

with open(__file__.replace("verify_fixture.py", "v1.6.2-fixture.json"), encoding='utf-8') as f:
    d = json.load(f)

print('=== Counts ===')
print(json.dumps(d['counts'], indent=2))

print('\n=== Sample person (sensitive+hidden) ===')
hidden = [p for p in d['persons'] if p['vaultMode'] == 'hidden'][0]
print(json.dumps(hidden, indent=2, ensure_ascii=False))

print('\n=== Sample instruction (long rawText) ===')
long_one = [i for i in d['instructions'] if len(i['rawText']) > 200][0]
print(json.dumps(long_one, indent=2, ensure_ascii=False))

print('\n=== Sample capture (PHOTO, no body) ===')
photo = [c for c in d['captures'] if c['mode'] == 'PHOTO'][0]
print(json.dumps(photo, indent=2, ensure_ascii=False))

print('\n=== Free-floating instructions (personId==null) ===')
free = [i for i in d['instructions'] if i['personId'] is None]
pct = 100 * len(free) // len(d['instructions'])
print(f'count={len(free)} ({pct}%)')

print('\n=== Sensitive instructions ===')
sens = [i for i in d['instructions'] if i['isSensitive']]
print(f'count={len(sens)}')
for i, s in enumerate(sens):
    print(f'  [{i}] {s["title"][:60]}')

print('\n=== Status distribution ===')
print(Counter(i['status'] for i in d['instructions']))

print('\n=== capturedAt age buckets (days from now) ===')
now = datetime(2026, 8, 18, 22, 4, 27, tzinfo=IST)
buckets = {'0-14d': 0, '14-30d': 0, '30-60d': 0, '60-90d': 0, '90+d': 0}
for ins in d['instructions']:
    cap = datetime.fromisoformat(ins['capturedAt'])
    age = (now - cap).days
    if age < 14:
        buckets['0-14d'] += 1
    elif age < 30:
        buckets['14-30d'] += 1
    elif age < 60:
        buckets['30-60d'] += 1
    elif age < 90:
        buckets['60-90d'] += 1
    else:
        buckets['90+d'] += 1
print(buckets)

print('\n=== Tier distribution ===')
print(Counter(p['tier'] for p in d['persons']))

print('\n=== Vault modes ===')
print(Counter(p['vaultMode'] for p in d['persons']))

print('\n=== dueAt distribution ===')
past = fut = none_ = 0
for i in d['instructions']:
    if i['dueAt'] is None:
        none_ += 1
    else:
        diff = (datetime.fromisoformat(i['dueAt']) - now).days
        if diff < 0:
            past += 1
        else:
            fut += 1
print(f'past={past} future={fut} null={none_}')

print('\n=== Edge cases present ===')
print('title==body             :', any(i['title'].strip() == i['rawText'].strip() for i in d['instructions']))
print('emoji in title          :', any(any(ord(c) >= 0x1F000 for c in i['title']) for i in d['instructions']))
print('whitespace-heavy title  :', any(i['title'] != i['title'].strip() for i in d['instructions']))
print('very long body (>200 ch):', any(len(i['rawText']) > 200 for i in d['instructions']))
print('short body (<5 ch)      :', any(len(i['rawText']) < 5 for i in d['instructions']))

print('\n=== Worry entries (instructions with urgency != normal) ===')
worries = [i for i in d['instructions'] if i.get('urgency', 'normal') != 'normal']
print(f"count: {len(worries)}")
print(json.dumps([{'id': i['id'], 'urgency': i['urgency'], 'title': i['title'][:40], 'reviewAt': i.get('reviewAtEpochDay')} for i in worries], indent=2, ensure_ascii=False))

print('\n=== Reference integrity ===')
person_ids = {p['id'] for p in d['persons']}
bad_ins = [i for i in d['instructions'] if i['personId'] is not None and i['personId'] not in person_ids]
bad_cap = [c for c in d['captures'] if c['personId'] is not None and c['personId'] not in person_ids]
print(f'unresolved instruction.personId : {len(bad_ins)}')
print(f'unresolved capture.personId     : {len(bad_cap)}')

all_ids = [p['id'] for p in d['persons']] + [i['id'] for i in d['instructions']] + [c['id'] for c in d['captures']] + [t['id'] for t in d['tags']]
print(f'unique IDs                     : {len(set(all_ids))} of {len(all_ids)}')
