"""Connected-device script/folder checks. AI-assisted contribution: OpenAI Codex.

Set HEXORA_SERIAL explicitly for a physical phone. Uses owned fixtures; the
reported third-party file is opened read-only and its bytes are never printed.
"""
from mumu_ui import *
import hashlib
import zipfile
import regression as r

r.ROOT = '/sdcard/Download/Hexora-Shell-QA-' + time.strftime('%Y%m%d-%H%M%S')
REPORTED = '/storage/emulated/0/Android/data/com.pubg.imobile/files/login-identifier.txt'


def prepare():
    fixture = ARTIFACTS / 'fixtures'
    fixture.mkdir(exist_ok=True)
    (fixture / 'folder.with.dots' / 'nested').mkdir(parents=True, exist_ok=True)
    (fixture / 'empty').mkdir(exist_ok=True)
    (fixture / 'sibling.txt').write_text('RELATIVE_OK\n', encoding='utf-8')
    script = '#!/system/bin/sh\nprintf "SCRIPT_OK\\n"\ncat sibling.txt\npwd\nprintf "ERROR_OK\\n" >&2\nexit 7\n'
    (fixture / 'run script.sh').write_text(script, encoding='utf-8', newline='\n')
    (fixture / 'UPPER.SH').write_text('echo UPPER_OK\n', encoding='utf-8', newline='\n')
    with zipfile.ZipFile(fixture / 'scripts.zip', 'w') as archive:
        archive.writestr('inside.sh', 'echo ARCHIVE_OK\n')
    adb('push', str(fixture), r.ROOT)
    # ADB push omits empty directories.
    adb('shell', 'mkdir', '-p', r.ROOT + '/folder.with.dots/nested', r.ROOT + '/empty')


def shell_local():
    r.reset()
    original = adb('exec-out', 'cat', r.ROOT + '/run script.sh', binary=True)
    tap(text='run script.sh', pane=1)
    find(text='Run as app')
    tap(text='Execute')
    output = find_visible(text='Script output')
    layout = tree()
    contents = '\n'.join(n.get('text', '') for n in layout.iter('node'))
    assert all(x in contents for x in ['Exit code: 7', 'SCRIPT_OK', 'RELATIVE_OK', 'ERROR_OK', r.ROOT.replace('/sdcard', '/storage/emulated/0')]), contents
    assert adb('exec-out', 'cat', r.ROOT + '/run script.sh', binary=True) == original
    snapshot('shell-local-output')
    tap(text='OK')


def shell_edit():
    r.reset()
    tap(text='UPPER.SH', pane=1)
    tap(text='Edit')
    assert 'UPPER_OK' in find(rid='editor').get('text', '')
    adb('shell', 'input', 'keyevent', 4)


def shell_archive():
    r.reset()
    tap(text='scripts.zip', pane=1)
    tap(text='inside.sh', pane=1)
    tap(text='Execute')
    contents = '\n'.join(n.get('text', '') for n in tree().iter('node'))
    assert 'Exit code: 0' in contents and 'ARCHIVE_OK' in contents, contents
    snapshot('shell-archive-output')
    tap(text='OK')


def folders():
    r.reset()
    tap(text='folder.with.dots', pane=1)
    tap(text='nested', pane=1)
    find(text='Empty folder', pane=1)
    tap(rid='upButton')
    find(text='nested', pane=1)
    tap(rid='upButton')
    tap(text='empty', pane=1)
    find(text='Empty folder', pane=1)
    snapshot('folders-empty-and-dotted')


def shell_archive_manual_save():
    r.reset()
    tap(text='scripts.zip', pane=1)
    tap(text='inside.sh', pane=1, long=True)
    tap(text='Open with')
    tap(desc='Script executor')
    tap(text='Edit')
    tap(rid='editor')
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    adb('shell', 'input', 'text', 'echo%sSAVED_SCRIPT')
    tap(rid='btn_save')
    assert find(rid='btn_save').get('enabled') == 'false'
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', 4)
    adb('shell', 'input', 'keyevent', 4)
    find(text='File modified')
    tap(text='Yes')
    result = ARTIFACTS / 'edited-scripts.zip'
    result.write_bytes(adb('exec-out', 'cat', r.ROOT + '/scripts.zip', binary=True))
    with zipfile.ZipFile(result) as archive:
        assert b'echo SAVED_SCRIPT' in archive.read('inside.sh')
    snapshot('shell-archive-manual-save')


def reported_read():
    before = adb('exec-out', 'cat', REPORTED, binary=True)
    # Grant through Android's actual picker if this folder has not been granted.
    launch(REPORTED.rsplit('/', 1)[0])
    layout = tree()
    if any(n.get('text') == 'Folder access' for n in layout.iter('node')):
        tap(text='Grant folder access')
        button = find(text='USE THIS FOLDER')
        assert button.get('enabled') == 'true', 'Android did not permit this folder grant'
        tap(text='USE THIS FOLDER')
        tap(text='ALLOW')
    tap(text='login-identifier.txt', pane=1)
    # Inspect bytes privately; do not include the login identifier in logs/screenshots.
    try:
        editor_text = find(rid='editor').get('text', '')
        assert editor_text == before.decode('utf-8'), 'Editor contents differ from original bytes'
    finally:
        adb('shell', 'input', 'keyevent', 4)
    assert adb('exec-out', 'cat', REPORTED, binary=True) == before
    (ARTIFACTS / 'reported-read.json').write_text(json.dumps({
        'path': REPORTED, 'bytes': len(before), 'unchanged': True,
        'sha256': hashlib.sha256(before).hexdigest(), 'editor_matches': True}, indent=2))


if __name__ == '__main__':
    prepare()
    scenarios = [('shell-local-output', shell_local), ('shell-edit-uppercase', shell_edit),
                 ('shell-archive-output', shell_archive), ('folders', folders),
                 ('shell-archive-manual-save', shell_archive_manual_save),
                 ('reported-read', reported_read)]
    selected = sys.argv[1:]
    for name, fn in scenarios:
        if not selected or name in selected: r.scenario(name, fn)
    sys.exit(1 if any(x['status'] == 'FAIL' for x in r.RESULTS) else 0)
