"""Patch/sign/install/launch an owned signature fixture through Hexora's UI.

Build :samples:signature-fixture:assembleDebug with -PsignatureFixtures=true
after an initial app debug build. Run sequentially with other UI drivers.
Only app.hexora.signaturefixture is replaced; no user APK data is cleared.
AI-assisted contribution: OpenAI Codex.
"""
import hashlib
import sys
import zipfile
import zlib
import regression as r
from mumu_ui import *

FIXTURE_PACKAGE = 'app.hexora.signaturefixture'
FIXTURE = Path(__file__).resolve().parents[1] / 'samples/signature-fixture/build/outputs/apk/debug/signature-fixture-debug.apk'
r.ROOT = '/sdcard/Download/Hexora-Signature-QA-' + time.strftime('%Y%m%d-%H%M%S')


def wait_for(predicate, seconds=30):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = predicate()
            if result is not None and result is not False: return result
        except AssertionError: pass
        time.sleep(.4)
    raise AssertionError('Timed out waiting for signature operation')


def launch_fixture(apk):
    if adb('shell', 'pm', 'path', FIXTURE_PACKAGE): adb('uninstall', FIXTURE_PACKAGE)
    adb('install', str(apk))
    installed = adb('shell', 'pm', 'path', FIXTURE_PACKAGE).splitlines()[0].removeprefix('package:')
    adb('shell', 'am', 'start', '-W', '-n', FIXTURE_PACKAGE + '/.MainActivity',
        '--es', 'installedApkPath', installed,
        '--es', 'originalCrc', str(zlib.crc32(FIXTURE.read_bytes())))
    result = '\n'.join(n.get('text', '') for n in tree().iter('node'))
    assert 'ERROR ' not in result, result
    return result


def baseline():
    result = launch_fixture(FIXTURE)
    for check in ('legacy', 'current', 'history', 'multiple', 'past', 'no-flags', 'other-package', 'reflection'):
        assert 'PASS ' + check in result, result


def begin_patch(name, mode):
    r.reset()
    tap(text=name, pane=1)
    tap(text='More')
    tap(text='Kill signature verification')
    tap(rid='signature_mode_' + mode)
    tap(text='Patch and sign')


def patch(mode, suffix):
    begin_patch('original.apk', mode)
    wait_for(lambda: find(text='Patched APK saved'))
    find(text='Install')
    remote = r.ROOT + '/original' + suffix + '.apk'
    local = ARTIFACTS / ('fixture-' + mode + '.apk')
    adb('pull', remote, str(local))
    original_on_device = adb('exec-out', 'cat', r.ROOT + '/original.apk', binary=True)
    assert original_on_device == FIXTURE.read_bytes(), 'Original APK was altered'
    with zipfile.ZipFile(local) as z:
        assert 'assets/hexora/signature-patch.properties' in z.namelist()
        if mode == 'parcel':
            assert 'assets/lspatch/origin.apk' not in z.namelist()
            assert 'engine=parcel' in z.read('assets/hexora/signature-patch.properties').decode()
            assert 'assets/hexora/parcel/HiddenApiBypass-LICENSE.txt' in z.namelist()
    snapshot('patch-' + mode + '-saved')
    result = launch_fixture(local)
    for check in ('legacy', 'current', 'history', 'multiple', 'past', 'no-flags', 'other-package'):
        assert 'PASS ' + check in result, result
    # Static Java rewriting does not cover reflective field access.
    assert ('FAIL ' if mode == 'java' else 'PASS ') + 'reflection' in result, result
    return result


def repeated_patch_guard():
    begin_patch('original_sigkill.apk', 'java')
    wait_for(lambda: any('already' in n.get('text', '').lower() for n in tree().iter('node')))
    assert 'original_sigkill_sigkill.apk' not in adb('shell', 'ls', r.ROOT)


if __name__ == '__main__':
    if not FIXTURE.is_file(): raise SystemExit('Build the owned signature fixture first')
    adb('shell', 'mkdir', '-p', r.ROOT)
    adb('push', str(FIXTURE), r.ROOT + '/original.apk')
    for name, check in [
        ('original-signature-fixture', baseline),
        ('java-patch-install-launch', lambda: patch('java', '_sigkill')),
        ('parcel-patch-install-launch', lambda: patch('parcel', '_sigkill_parcel')),
        ('repeated-patch-no-output', repeated_patch_guard),
    ]: r.scenario(name, check)
    (ARTIFACTS / 'signature-results.json').write_text(json.dumps({'fixture': r.ROOT, 'original_sha256': hashlib.sha256(FIXTURE.read_bytes()).hexdigest(), 'results': r.RESULTS}, indent=2))
    (ARTIFACTS / 'signature-logcat.txt').write_text(adb('logcat', '-d'), encoding='utf-8')
    sys.exit(1 if any(x['status'] == 'FAIL' for x in r.RESULTS) else 0)
