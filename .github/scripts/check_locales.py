"""Every UI locale must have the keys of en.json, with the same {{placeholders}}, and readable text."""
import json
import pathlib
import re
import sys

LOCALES = pathlib.Path(__file__).resolve().parents[2] / 'frontend' / 'src' / 'i18n' / 'locales'
PLACEHOLDER = re.compile(r'\{\{\w+\}\}')

en = json.loads((LOCALES / 'en.json').read_text(encoding='utf-8'))
problems = []
for path in sorted(LOCALES.glob('*.json')):
    data = json.loads(path.read_text(encoding='utf-8'))
    for key in sorted(set(en) - set(data)):
        problems.append(f'{path.name}: missing {key}')
    for key in sorted(set(data) - set(en)):
        problems.append(f'{path.name}: {key} is not in en.json')
    for key, text in data.items():
        if key in en and sorted(PLACEHOLDER.findall(text)) != sorted(PLACEHOLDER.findall(en[key])):
            problems.append(f'{path.name}: {key} has other placeholders than en.json')
        if '�' in text:
            problems.append(f'{path.name}: {key} has a broken character')
print('\n'.join(problems) or f'{len(list(LOCALES.glob("*.json")))} locales, {len(en)} keys each: OK')
sys.exit(1 if problems else 0)
