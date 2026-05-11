#!/usr/bin/env python3
"""
Generates index.json for the plugin marketplace.

Usage:
  generate-index.py --apks-dir <dir> --plugins-dir <dir> --base-url <url>

For each APK found in --apks-dir:
  - Reads plugin.json from inside the APK ZIP.
  - Computes SHA-256 of the APK.
  - Locates the plugin's source directory in --plugins-dir by matching the plugin ID.
  - Derives the author/collection/screen path components.
  - Includes URLs to README.md files at each level if they exist in --plugins-dir.
"""

import argparse, hashlib, json, pathlib, sys, zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--apks-dir', required=True)
parser.add_argument('--plugins-dir', required=True)
parser.add_argument('--base-url', required=True)
args = parser.parse_args()

apks_dir = pathlib.Path(args.apks_dir)
plugins_dir = pathlib.Path(args.plugins_dir)
base_url = args.base_url.rstrip('/')

if not apks_dir.is_dir():
    print(f'Error: {apks_dir} is not a directory', file=sys.stderr)
    sys.exit(1)

# Build a map from plugin ID -> source plugin directory by scanning all plugin.json files.
id_to_source = {}
for plugin_json_path in plugins_dir.glob('*/*/*/app/src/main/assets/plugin.json'):
    try:
        data = json.loads(plugin_json_path.read_text())
        plugin_id = data.get('id')
        if plugin_id:
            # The plugin root is 4 levels up from plugin.json (app/src/main/assets/).
            id_to_source[plugin_id] = plugin_json_path.parents[4]
    except Exception as e:
        print(f'Warning: could not read {plugin_json_path}: {e}', file=sys.stderr)

index = []

for apk_path in sorted(apks_dir.glob('*.apk')):
    # Extract plugin.json from the APK.
    try:
        with zipfile.ZipFile(apk_path, 'r') as z:
            with z.open('assets/plugin.json') as f:
                plugin_json = json.load(f)
    except Exception as e:
        print(f'Warning: could not read plugin.json from {apk_path.name}: {e}', file=sys.stderr)
        continue

    # Compute SHA-256.
    sha256 = hashlib.sha256()
    with apk_path.open('rb') as f:
        while chunk := f.read(8192):
            sha256.update(chunk)
    digest = sha256.hexdigest()

    plugin_id = plugin_json.get('id')
    source_dir = id_to_source.get(plugin_id)

    # Derive author/collection/screen from the source directory path.
    # Structure: plugins_dir/<author>/<collection>/<screen>
    readme_urls = {}
    if source_dir:
        parts = source_dir.relative_to(plugins_dir).parts  # (author, collection, screen)
        if len(parts) == 3:
            author, collection, screen = parts
            levels = [
                ('authorReadmeUrl',     plugins_dir / author),
                ('collectionReadmeUrl', plugins_dir / author / collection),
                ('screenReadmeUrl',     plugins_dir / author / collection / screen),
            ]
            url_paths = [
                f'readmes/{author}/README.md',
                f'readmes/{author}/{collection}/README.md',
                f'readmes/{author}/{collection}/{screen}/README.md',
            ]
            for (key, src_dir), url_path in zip(levels, url_paths):
                if (src_dir / 'README.md').exists():
                    readme_urls[key] = f'{base_url}/{url_path}'

    collection = parts[1] if source_dir and len(parts) == 3 else ''
    entry = {
        'id':         plugin_id,
        'name':       plugin_json.get('name'),
        'version':    plugin_json.get('version'),
        'author':     plugin_json.get('author'),
        'collection': collection,
        'apkUrl':     f'{base_url}/apks/{apk_path.name}',
        'sha256':     digest,
        **readme_urls,
    }
    index.append(entry)

print(json.dumps(index, indent=2))
