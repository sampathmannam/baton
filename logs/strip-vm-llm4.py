p = r'app\src\main\java\com\baton\app\ui\settings\SettingsViewModel.kt'
with open(p, 'r', encoding='utf-8') as f:
    lines = f.readlines()
# Find lines containing "v1.5.4: the on-device LLM model state" (start of LLM block)
# and "fun signOut()" or "fun downloadWhisper() {" to know where it ends
# Let me find all "v1.5.4:" comments that mark the LLM block
print('Searching for LLM block markers:')
i = 0
while i < len(lines):
    line = lines[i]
    if 'v1.5.4: the on-device LLM model state' in line:
        # walk back to /**
        start = i
        for j in range(i-1, -1, -1):
            if lines[j].strip().startswith('/**'):
                start = j
                break
        # find end: look for the next blank line followed by "fun " or close of downloadWhisper
        end = None
        for k in range(i+1, len(lines)):
            if 'fun signOut()' in lines[k] or 'fun downloadLlm' in lines[k]:
                # walk back to the start of "fun downloadWhisper() {"
                # The actual block ends at the } of downloadWhisper. Let's just walk back to "fun downloadLlm"
                # and from there find downloadWhisper's closing }
                # Easier: just find the } that closes downloadWhisper
                # walk forward to find "fun downloadWhisper" then its matching }
                for m in range(k, len(lines)):
                    if 'fun downloadWhisper()' in lines[m]:
                        # find the matching close
                        depth = 0
                        for n in range(m, len(lines)):
                            for ch in lines[n]:
                                if ch == '{': depth += 1
                                elif ch == '}':
                                    depth -= 1
                                    if depth == 0:
                                        end = n + 1
                                        break
                            if end is not None:
                                break
                        break
                break
        print(f'block from line {start+1} to {end} (i={i+1})')
        print('first line:', lines[start].rstrip())
        print('last line:', lines[end-1].rstrip())
        # remove
        del lines[start:end]
        # continue from start
        i = start
    else:
        i += 1
print(f'final line count: {len(lines)}')
with open(p, 'w', encoding='utf-8') as f:
    f.writelines(lines)
print('saved')
