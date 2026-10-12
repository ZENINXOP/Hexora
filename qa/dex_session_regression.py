"""Current DEX editor Back, Save, loading and theme acceptance checks.

Uses a disposable copy of the repository's owned sample DEX. Run sequentially.
AI-assisted contribution: OpenAI Codex.
"""
import sys
import zipfile
import regression as r
from mumu_ui import *

r.ROOT = '/sdcard/Download/Hexora-DEX-QA-' + time.strftime('%Y%m%d-%H%M%S')


def wait_for(predicate, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = predicate()
            if result is not None and result is not False: return result
        except AssertionError: pass
        time.sleep(.3)
    raise AssertionError('Timed out waiting for DEX editor state')


def code():
    return find(rid='editor').get('text', '')


def save_enabled():
    return find(rid='btn_save').get('enabled') == 'true'


def open_class():
    r.reset()
    tap(text='classes.dex', pane=1)
    tap(text='com.example.mpextsample')
    tap(text='EditorActivity')
    wait_for(lambda: 'Lcom/example/mpextsample/EditorActivity;' in code())
    assert not save_enabled(), 'Clean class Save must be disabled'
    return code()


def edit_comment(marker):
    tap(rid='editor')
    adb('shell', 'input', 'keycombination', '113', '122')
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    adb('shell', 'input', 'text', marker)
    wait_for(save_enabled)
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', 4)


def themed(mode):
    r.reset()
    r.menu('Preferences')
    tap(rid=mode + 'ThemeButton')
    open_class()
    assert not any(n.get('resource-id', '').endswith(':id/loading_progress') for n in tree().iter('node'))


def dirty_cancel_undo_close():
    original = open_class()
    edit_comment('_QA_CANCEL')
    adb('shell', 'input', 'keyevent', 4)
    tap(text='Cancel')
    assert '_QA_CANCEL' in code()
    tap(rid='btn_undo')
    wait_for(lambda: not save_enabled())
    assert code() == original
    adb('shell', 'input', 'keyevent', 4)
    find(text='EditorActivity')
    tap(text='FileActionActivity')
    wait_for(lambda: 'Lcom/example/mpextsample/FileActionActivity;' in code())
    tap(desc='Open navigation drawer')
    assert not any(n.get('text') == 'EditorActivity' for n in tree().iter('node'))


def save_restore():
    original = open_class()
    edit_comment('_QA_SAVE')
    assert '# classes.dex_QA_SAVE' in code()
    tap(rid='btn_save')
    wait_for(lambda: not save_enabled())
    assert '_QA_SAVE' in code()
    tap(rid='btn_undo')
    wait_for(save_enabled)
    assert code() == original
    tap(rid='btn_save')
    wait_for(lambda: not save_enabled())


def rapid_open_back():
    open_class()
    adb('shell', 'input', 'keyevent', 4)
    a, b, c, d = bounds(find(text='EditorActivity'))
    adb('shell', 'dumpsys', 'gfxinfo', PACKAGE, 'reset')
    for _ in range(8):
        adb('shell', 'input', 'tap', (a+c)//2, (b+d)//2)
        adb('shell', 'input', 'keyevent', 4)
    (ARTIFACTS / 'dex-opening-gfxinfo.txt').write_text(adb('shell', 'dumpsys', 'gfxinfo', PACKAGE, 'framestats'))
    find(text='EditorActivity')
    tap(text='EditorActivity')
    wait_for(lambda: 'Lcom/example/mpextsample/EditorActivity;' in code())


def rotation():
    original = open_class()
    auto = adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation')
    previous = adb('shell', 'settings', 'get', 'system', 'user_rotation')
    try:
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', 0)
        adb('shell', 'settings', 'put', 'system', 'user_rotation', 1)
        wait_for(lambda: code() == original)
        snapshot('dex-landscape')
    finally:
        for key, value in [('user_rotation', previous), ('accelerometer_rotation', auto)]:
            if value == 'null': adb('shell', 'settings', 'delete', 'system', key)
            else: adb('shell', 'settings', 'put', 'system', key, value)
    wait_for(lambda: code() == original)


if __name__ == '__main__':
    sample = Path(__file__).resolve().parents[1] / 'samples/plugin-sample/release/plugin-sample-release.apk'
    with zipfile.ZipFile(sample) as apk: (ARTIFACTS / 'classes.dex').write_bytes(apk.read('classes.dex'))
    adb('shell', 'mkdir', '-p', r.ROOT)
    adb('push', str(ARTIFACTS / 'classes.dex'), r.ROOT + '/classes.dex')
    cases = [('dex-theme-' + m, lambda mode=m: themed(mode)) for m in ('light', 'dark', 'black')]
    cases += [('dex-dirty-cancel-undo-close', dirty_cancel_undo_close), ('dex-save-restore', save_restore),
              ('dex-rapid-open-back', rapid_open_back), ('dex-rotation', rotation)]
    selected = sys.argv[1:]
    if any(name not in dict(cases) for name in selected): raise SystemExit('Unknown DEX scenario')
    for name, check in cases:
        if not selected or name in selected: r.scenario(name, check)
    r.reset()
    r.menu('Preferences')
    tap(rid='darkThemeButton')
    r.reset()
    (ARTIFACTS / 'dex-session-results.json').write_text(json.dumps({'fixture': r.ROOT, 'results': r.RESULTS}, indent=2))
    (ARTIFACTS / 'dex-session-logcat.txt').write_text(adb('logcat', '-d'), encoding='utf-8')
    sys.exit(1 if any(x['status'] == 'FAIL' for x in r.RESULTS) else 0)
