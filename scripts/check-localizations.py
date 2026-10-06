"""Require complete translations and compatible Android format arguments."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

resources = Path(__file__).resolve().parents[1] / "src/app/src/main/res"
folders = {"en": "values", "ru": "values-ru", "fr": "values-fr", "de": "values-de", "zh-Hans": "values-b+zh+Hans"}
arguments = re.compile(r"%\d+\$[ds]|%%")

def read(folder):
    nodes = ET.parse(resources / folder / "strings.xml").getroot()
    strings = {node.attrib["name"]: node.text or "" for node in nodes}
    assert len(strings) == len(nodes), f"Duplicate keys: {folder}"
    return strings

base = read("values")
for language, folder in folders.items():
    strings = read(folder)
    assert strings.keys() == base.keys(), (language, base.keys() - strings.keys(), strings.keys() - base.keys())
    for key, value in strings.items():
        assert value.strip('" ').strip(), (language, key, "empty translation")
        assert sorted(arguments.findall(value)) == sorted(arguments.findall(base[key])), (language, key, "format arguments differ")
        assert value.count(r"\n") == base[key].count(r"\n"), (language, key, "line breaks differ")
    print(f"{language}: {len(strings)} strings, arguments and line breaks match")

config = ET.parse(resources / "xml/locales_config.xml").getroot()
assert {node.attrib["{http://schemas.android.com/apk/res/android}name"] for node in config} == folders.keys()
