"""Read-only catalog/APK release audit; never builds or runs an APK.

Usage: py qa/check_pack_releases.py --download
Uses the installed SDK's aapt dump badging (or --aapt PATH). Downloaded APKs
and the report stay under the selected --apk-dir, defaulting to qa/artifacts.
AI-assisted contribution: OpenAI Codex.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def default_aapt():
    properties = (ROOT / 'local.properties').read_text(encoding='utf-8')
    sdk = re.search(r'^sdk\.dir=(.+)$', properties, re.M).group(1).strip()
    sdk = sdk.replace('\\:', ':').replace('\\\\', '\\')
    candidates = list((Path(sdk) / 'build-tools').glob('*/aapt.exe'))
    candidates += list((Path(sdk) / 'build-tools').glob('*/aapt'))
    if not candidates:
        raise RuntimeError('Pass --aapt with the installed Android SDK aapt path')
    return sorted(candidates)[-1]


def inspect(pack, folder, aapt, download):
    apk = folder / ('pack-' + pack['id'] + '.apk')
    if not apk.is_file() and download:
        with urllib.request.urlopen(pack['apkUrl'], timeout=30) as response:
            data = response.read(64 * 1024 * 1024 + 1)
        if len(data) > 64 * 1024 * 1024:
            raise AssertionError('Pack download exceeds audit size limit')
        apk.write_bytes(data)
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    assert digest == pack['sha256'].lower(), 'Downloaded bytes differ from the pinned checksum'
    output = subprocess.run([str(aapt), 'dump', 'badging', str(apk)],
                            capture_output=True, text=True, check=True).stdout
    package = next(line for line in output.splitlines() if line.startswith('package:'))
    manifest_version = int(re.search(r"versionCode='(\d+)'", package).group(1))
    manifest_name = re.search(r"versionName='([^']*)'", package).group(1)
    assert manifest_version == pack['version'], (manifest_version, pack['version'])
    assert manifest_name == pack['versionName'], (manifest_name, pack['versionName'])
    module = ROOT / 'packs' / ('pack-' + pack['id'])
    gradle = (module / 'build.gradle').read_text(encoding='utf-8')
    assert int(re.search(r'\bversionCode\s+(\d+)', gradle).group(1)) == manifest_version
    assert re.search(r"\bversionName\s+'([^']+)'", gradle).group(1) == manifest_name
    assert re.search(r'buildFeatures\s*\{[^}]*buildConfig\s+true', gradle, re.S)
    entry = module / 'src/main/java' / Path(*pack['entryClass'].split('.')).with_suffix('.java')
    source = entry.read_text(encoding='utf-8')
    assert re.search(r'int version\(\)\s*\{\s*return BuildConfig.VERSION_CODE;', source)
    return {'id': pack['id'], 'status': 'PASS', 'versionCode': manifest_version,
            'versionName': manifest_name, 'sha256': digest, 'url': pack['apkUrl']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--aapt', type=Path)
    parser.add_argument('--apk-dir', type=Path, default=ROOT / 'qa/artifacts/pack-version-review-20261016')
    parser.add_argument('--download', action='store_true', help='Fetch missing catalog APKs for offline metadata inspection')
    args = parser.parse_args()
    args.apk_dir.mkdir(parents=True, exist_ok=True)
    aapt = args.aapt or default_aapt()
    catalog = json.loads((ROOT / 'app/src/main/assets/packs.json').read_text(encoding='utf-8'))
    results = []
    for pack in catalog['packs']:
        try:
            result = inspect(pack, args.apk_dir, aapt, args.download)
        except Exception as error:
            result = {'id': pack['id'], 'status': 'FAIL', 'error': str(error)}
        results.append(result)
        print(json.dumps(result))
    (args.apk_dir / 'release-audit.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    return int(any(result['status'] != 'PASS' for result in results))


if __name__ == '__main__':
    raise SystemExit(main())
