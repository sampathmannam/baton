#!/usr/bin/env python3
"""Count tests from JUnit XML files (debug version)."""
import re
from pathlib import Path
import sys
root = Path(sys.argv[1] if len(sys.argv) > 1 else 'app/build/test-results/testDebugUnitTest')
total = failed = errors = skipped = 0
xmls = list(root.rglob('*.xml'))
for p in xmls:
    t = p.read_text()
    # The XML attribute order varies; extract each independently
    m_tests = re.search(r'tests="(\d+)"', t)
    m_fail = re.search(r'failures="(\d+)"', t)
    m_err = re.search(r'errors="(\d+)"', t)
    m_skip = re.search(r'skipped="(\d+)"', t)
    if m_tests:
        total += int(m_tests.group(1))
        failed += int(m_fail.group(1)) if m_fail else 0
        errors += int(m_err.group(1)) if m_err else 0
        skipped += int(m_skip.group(1)) if m_skip else 0
passed = total - failed - errors - skipped
print(f'xmls={len(xmls)}  total={total}  passed={passed}  failed={failed}  errors={errors}  skipped={skipped}')
