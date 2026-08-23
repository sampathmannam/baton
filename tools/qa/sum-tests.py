import re
import os
import sys

dir_path = sys.argv[1]
total_tests = 0
total_failures = 0
total_skipped = 0
total_errors = 0
per_class = []
for f in sorted(os.listdir(dir_path)):
    if not f.endswith('.xml'):
        continue
    with open(os.path.join(dir_path, f), 'r', encoding='utf-8') as fp:
        text = fp.read()
    m = re.search(r'<testsuite\s+name="([^"]+)"\s+tests="(\d+)"\s+skipped="(\d+)"\s+failures="(\d+)"\s+errors="(\d+)"', text)
    if m:
        name, tests, skipped, failures, errors = m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5))
        total_tests += tests
        total_failures += failures
        total_errors += errors
        total_skipped += skipped
        per_class.append((name, tests, skipped, failures, errors))
print(f'tests={total_tests} failures={total_failures} errors={total_errors} skipped={total_skipped}')
for name, tests, skipped, failures, errors in per_class:
    short = name.rsplit('.', 1)[-1]
    print(f'  {short}: tests={tests} skipped={skipped} failures={failures} errors={errors}')
