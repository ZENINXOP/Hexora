"""Owned arm64 JNI read verification for experimental LSPatch mode.

Build with -PsignatureFixtures=true -PnativeSignatureFixture=true.
Run sequentially after the plain signature driver. Replaces only the owned fixture.
AI-assisted contribution: OpenAI Codex.
"""
import sys
import signature_regression as s
import regression as r
from mumu_ui import *

s.FIXTURE = Path(os.environ.get('HEXORA_SIGNATURE_FIXTURE', str(Path(__file__).resolve().parents[1]
                 / 'samples/signature-fixture/build/outputs/apk/debug/signature-native-fixture-debug.apk')))
r.ROOT = '/sdcard/Download/Hexora-Native-QA-' + time.strftime('%Y%m%d-%H%M%S')


def baseline():
    result = s.launch_fixture(s.FIXTURE)
    assert 'PASS native-apk-read' in result, result
    assert 'FAIL ' not in result, result


def patched(mode, suffix):
    result = s.patch(mode, suffix)
    # Both levels cover common libc open/read calls. This is not a direct-syscall
    # or self-checksumming-packer test of level 3's additional coverage.
    assert 'PASS native-apk-read' in result, result


if __name__ == '__main__':
    if not s.FIXTURE.is_file(): raise SystemExit('Build the owned native fixture first')
    adb('shell', 'mkdir', '-p', r.ROOT)
    adb('push', str(s.FIXTURE), r.ROOT + '/original.apk')
    cases = [
        ('original-native-read', baseline),
        ('advanced-common-native-read-install-launch', lambda: patched('advanced', '_sigkill_advanced')),
        ('experimental-native-read-install-launch', lambda: patched('native', '_sigkill_native')),
    ]
    selected = sys.argv[1:]
    if any(name not in dict(cases) for name in selected): raise SystemExit('Unknown native signature scenario')
    for name, check in cases:
        if not selected or name in selected: r.scenario(name, check)
    (ARTIFACTS / 'signature-native-results.json').write_text(json.dumps({'fixture': r.ROOT, 'results': r.RESULTS}, indent=2))
    (ARTIFACTS / 'signature-native-logcat.txt').write_text(adb('logcat', '-d'), encoding='utf-8')
    sys.exit(1 if any(x['status'] == 'FAIL' for x in r.RESULTS) else 0)
