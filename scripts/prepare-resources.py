"""Bundle the pinned MaaYYs project's original resources for Android (no Windows host)."""
import argparse
import hashlib
import json
import pathlib
import urllib.request
import zipfile

COMMIT = '1bdea0d3f1030d3cc7872ecefdde43e731420cbe'
ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=pathlib.Path, help='Local checkout of the pinned MaaYYs revision')
args = parser.parse_args()
source = args.source
if source is None:
    cache = ROOT / 'build/upstream'
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / (COMMIT + '.zip')
    if not archive.exists():
        urllib.request.urlretrieve('https://codeload.github.com/TanyaShue/MaaYYs/zip/' + COMMIT, archive)
    with zipfile.ZipFile(archive) as z:
        z.extractall(cache)
    source = cache / ('MaaYYs-' + COMMIT)
assets = ROOT / 'app/src/main/assets'
assets.mkdir(parents=True, exist_ok=True)
paths = [source / 'interface.json', source / 'LICENSE']
for folder in ['tasks', 'preset', 'resource_pack', 'assets']:
    paths += sorted(p for p in (source / folder).rglob('*') if p.is_file())
bundle = assets / 'maayys-resources.zip'
with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for path in paths:
        name = path.relative_to(source).as_posix()
        info = zipfile.ZipInfo(name, date_time=(2026, 9, 15, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(info, path.read_bytes())
project = json.loads((source / 'interface.json').read_text(encoding='utf-8-sig'))
manifest = dict(version=project['version'] + '-android-' + COMMIT[:8], project_version=project['version'],
                upstream='https://github.com/TanyaShue/MaaYYs', commit=COMMIT,
                size=bundle.stat().st_size, sha256=hashlib.sha256(bundle.read_bytes()).hexdigest(), files=len(paths))
(assets / 'maayys-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(json.dumps(manifest))
