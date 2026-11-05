"""Every UI locale must have the keys of en.json, with the same {{placeholders}}, and readable text.

A key may also have plural forms (key_one, key_few, ...) when its text has {{count}}: i18next picks the form
of the number and falls back to the key itself, so a form is optional, but it must exist in the language.
"""
import json
import pathlib
import re
import sys

LOCALES = pathlib.Path(__file__).resolve().parents[2] / 'frontend' / 'src' / 'i18n' / 'locales'
PLACEHOLDER = re.compile(r'\{\{\w+\}\}')
PLURAL_KEY = re.compile(r'^(.+)_(zero|one|two|few|many|other)$')
# Cardinal plural categories of each language (Intl.PluralRules(lang).resolvedOptions().pluralCategories)
PLURAL_CATEGORIES = {
    'ar': {'zero', 'one', 'two', 'few', 'many', 'other'},
    'cs': {'one', 'few', 'many', 'other'}, 'pl': {'one', 'few', 'many', 'other'},
    'ru': {'one', 'few', 'many', 'other'}, 'uk': {'one', 'few', 'many', 'other'},
    'ro': {'one', 'few', 'other'}, 'he': {'one', 'two', 'other'},
    'es': {'one', 'many', 'other'}, 'fr': {'one', 'many', 'other'},
    'it': {'one', 'many', 'other'}, 'pt': {'one', 'many', 'other'},
    'id': {'other'}, 'ja': {'other'}, 'ko': {'other'}, 'th': {'other'}, 'vi': {'other'}, 'zh': {'other'},
}
DEFAULT_CATEGORIES = {'one', 'other'}


def base_key(key, keys):
    """The key a plural form belongs to, or None when the key is not a plural form."""
    m = PLURAL_KEY.match(key)
    return m.group(1) if m and m.group(1) in keys else None


en = json.loads((LOCALES / 'en.json').read_text(encoding='utf-8'))
en_keys = {k for k in en if base_key(k, en) is None}
problems = []
for path in sorted(LOCALES.glob('*.json')):
    lang = path.stem
    data = json.loads(path.read_text(encoding='utf-8'))
    for key in sorted(en_keys - set(data)):
        problems.append(f'{path.name}: missing {key}')
    for key, text in data.items():
        base = key if key in en_keys else base_key(key, en_keys)
        if base is None:
            problems.append(f'{path.name}: {key} is not in en.json')
            continue
        if base != key:
            category = PLURAL_KEY.match(key).group(2)
            if '{{count}}' not in en[base]:
                problems.append(f'{path.name}: {key} is a plural form of a text without {{{{count}}}}')
            if category not in PLURAL_CATEGORIES.get(lang, DEFAULT_CATEGORIES):
                problems.append(f'{path.name}: {key}: the language has no plural form "{category}"')
        if sorted(PLACEHOLDER.findall(text)) != sorted(PLACEHOLDER.findall(en[base])):
            problems.append(f'{path.name}: {key} has other placeholders than en.json')
        if '�' in text:
            problems.append(f'{path.name}: {key} has a broken character')
print('\n'.join(problems) or f'{len(list(LOCALES.glob("*.json")))} locales, {len(en_keys)} keys each: OK')
sys.exit(1 if problems else 0)
