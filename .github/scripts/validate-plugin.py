#!/usr/bin/env python3
import json, sys, pathlib

# Simple validation of plugin.json files inside a plugin directory.
# Expect fields: id, name, version, apiVersion, entryClass, author.
# Also ensure apiVersion <= 1 (the host supports version 1).

def error(msg):
    print(f'Error: {msg}', file=sys.stderr)
    sys.exit(1)

if len(sys.argv) != 2:
    error('Usage: validate-plugin.py <plugin-dir>')

plugin_dir = pathlib.Path(sys.argv[1])
plugin_json_path = plugin_dir / 'app' / 'src' / 'main' / 'assets' / 'plugin.json'
if not plugin_json_path.is_file():
    # Some projects keep the asset directly under app/src/main/assets.
    plugin_json_path = plugin_dir / 'app' / 'src' / 'main' / 'assets' / 'plugin.json'
    if not plugin_json_path.is_file():
        error(f'plugin.json not found in {plugin_dir}')

try:
    data = json.load(plugin_json_path.open())
except Exception as e:
    error(f'Failed to parse JSON: {e}')

required = ['id', 'name', 'version', 'apiVersion', 'entryClass', 'author']
for key in required:
    if key not in data:
        error(f"Missing required field '{key}' in {plugin_json_path}")

if not isinstance(data['apiVersion'], int):
    error('apiVersion must be an integer')
if data['apiVersion'] > 1:
    error(f"apiVersion {data['apiVersion']} is newer than host supports (1)")

# Additional sanity checks (optional)
if not data['id'].startswith('com.'):
    print(f"Warning: id '{data['id']}' does not follow the typical Java package style", file=sys.stderr)

print('Validation passed')
