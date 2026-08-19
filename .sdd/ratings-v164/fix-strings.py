import sys

path = 'app/src/main/res/values/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

old = '    <string name="settings_dev_load_fixture">Load test data</string>\n    <string name="settings_dev_loading">'
new = '    <string name="settings_dev_load_fixture">Load test data</string>\n    <string name="settings_dev_clear_reload">Clear &amp; reload</string>\n    <string name="settings_dev_loading">'

if old in content:
    content = content.replace(old, new, 1)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Replaced')
else:
    print('Not found, dumping 500 chars around "settings_dev_load_fixture":')
    idx = content.find('settings_dev_load_fixture')
    print(repr(content[idx-50:idx+500]))
    sys.exit(1)
