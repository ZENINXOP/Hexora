"""Native large-document, clean Save and minimize/back acceptance checks.

Uses disposable fixtures; run after the core driver, never concurrently.
AI-assisted contribution: OpenAI Codex.
"""
import hashlib
import sys
import regression as r
from mumu_ui import *

r.ROOT = '/sdcard/Download/Hexora-Editor-QA-' + time.strftime('%Y%m%d-%H%M%S')


def wait_for(predicate, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = predicate()
            if result is not None and result is not False: return
        except AssertionError: pass
        time.sleep(.3)
    raise AssertionError('Timed out waiting for editor state')


def open_editor(name, force_text=False):
    r.reset()
    started = time.monotonic()
    library = name.lower().endswith(('.lib', '.so', '.dll', '.a', '.o'))
    if library:
        tap(text=name, pane=1)
        find(rid='open_with_grid')
        tap(desc='Text editor')
    elif force_text:
        r.context(name, 'Open with')
        tap(desc='Text editor')
    else: tap(text=name, pane=1)
    wait_for(lambda: find(rid='editor'))
    return round(time.monotonic() - started, 2)


def save_enabled():
    return find(rid='btn_save').get('enabled') == 'true'


def insert_marker():
    tap(rid='editor')
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    adb('shell', 'input', 'text', 'QAEDIT')
    wait_for(save_enabled)
    # MuMu can use a hardware keyboard. Back would exit the editor in that case.
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', 4)


def dirty_undo_save():
    open_editor('small.txt')
    assert not save_enabled(), 'Clean Save must be disabled'
    insert_marker()
    tap(rid='btn_undo')
    wait_for(lambda: not save_enabled())
    insert_marker()
    tap(rid='btn_save')
    wait_for(lambda: not save_enabled())
    assert b'QAEDIT' in r.read('small.txt')
    assert r.read('small.txt.bak') == b'original small text\n'


def large_edit():
    seconds = open_editor('large.txt')
    assert seconds < 15, 'Large document open exceeded 15 seconds'
    find(text='Large file mode · Syntax colors and word wrap are paused for smoother editing.')
    assert not save_enabled()
    insert_marker()
    tap(rid='btn_save')
    wait_for(lambda: not save_enabled())
    assert b'QAEDIT' in r.read('large.txt')
    print('Large-document open seconds:', seconds, flush=True)


def protected_preview(name, banner, force_text=False):
    before = adb('shell', 'sha256sum', r.ROOT + '/' + name).split()[0]
    seconds = open_editor(name, force_text)
    assert seconds < 15, 'Preview open exceeded 15 seconds'
    if force_text:
        assert 'Preview' in find(rid='editor_file_status').get('text', '')
    else: find(text=banner)
    assert not save_enabled()
    if force_text:
        shown = find(rid='editor').get('text', '')
        assert 'ELF' in shown, 'Missing decoded library text'
        assert '00000000  7F' not in shown and '7F 45 4C 46' not in shown, 'Text viewer showed hex rows'
        assert len(shown) <= 65536, 'Binary text preview was not bounded'
    tap(rid='editor')
    adb('shell', 'input', 'text', 'MUSTNOTSAVE')
    assert not save_enabled()
    after = adb('shell', 'sha256sum', r.ROOT + '/' + name).split()[0]
    assert before == after, 'Read-only preview altered its original'
    snapshot('protected-preview-' + name)
    print(name, 'preview-open seconds:', seconds, flush=True)


def text_library(name, encoding='utf-8'):
    original = r.read(name)
    seconds = open_editor(name)
    assert seconds < 15
    find(text=original.decode(encoding).lstrip('\ufeff'), rid='editor')
    assert not save_enabled()
    insert_marker()
    tap(rid='btn_undo')
    wait_for(lambda: not save_enabled())
    insert_marker()
    tap(rid='btn_save')
    wait_for(lambda: not save_enabled())
    saved = r.read(name)
    assert 'QAEDIT' in saved.decode(encoding)
    assert '你好' in saved.decode(encoding), 'Library Unicode changed during Save'
    assert r.read(name + '.bak') == original
    if encoding != 'utf-8': assert saved[:2] == original[:2], 'Library BOM changed during Save'
    snapshot('text-library-' + name)


def minimize_back():
    open_editor('small.txt')
    tap(rid='btn_minimize')
    find(rid='listViewPane1')
    # Opening another file must retain a deliberately minimized tab.
    tap(text='other.txt', pane=1)
    wait_for(lambda: find(rid='editor'))
    tap(desc='Open files')
    find(text='small.txt')
    find(text='other.txt')
    adb('shell', 'input', 'keyevent', 4)  # drawer
    wait_for(lambda: not any(n.get('resource-id', '').endswith(':id/action_minimize')
                             for n in tree().iter('node')))
    adb('shell', 'input', 'keyevent', 4)  # close editor session
    wait_for(lambda: find(rid='listViewPane1'))
    tap(text='third.txt', pane=1)
    wait_for(lambda: find(rid='editor'))
    tap(desc='Open files')
    find(text='third.txt')
    layout = tree()
    assert not any(n.get('text') in ('small.txt', 'other.txt') for n in layout.iter('node'))


if __name__ == '__main__':
    fixture = ARTIFACTS / 'editor-fixtures'
    fixture.mkdir(parents=True, exist_ok=True)
    for name in ('small.txt', 'other.txt', 'third.txt'):
        (fixture / name).write_bytes(b'original small text\n')
    (fixture / 'large.txt').write_bytes((b'Line with enough text to exercise scrolling and background loading.\n') * 45000)
    (fixture / 'long-line.txt').write_bytes(b'A' * 100000)
    (fixture / 'binary.lib').write_bytes(b'\x7fELF\0' + bytes(range(256)) * 1024)
    (fixture / 'binary.so').write_bytes(b'\x7fELF\0' + bytes(range(256)) * 1024)
    for name, encoding in [('readable.lib', 'utf-8'), ('linker.so', 'utf-8'),
                           ('unicode-le.LIB', 'utf-16-le'), ('unicode-be.lib', 'utf-16-be')]:
        bom = b'\xff\xfe' if encoding == 'utf-16-le' else b'\xfe\xff' if encoding == 'utf-16-be' else b''
        (fixture / name).write_bytes(bom + 'LIBRARY sample\nEXPORTS\n  hello 你好\n'.encode(encoding))
    for name in ['large-binary.lib', 'large-binary.so']:
        with (fixture / name).open('wb') as output:
            output.write(b'\x7fELF\0')
            chunk = bytes(range(256)) * 4096
            for _ in range(32): output.write(chunk)
    with (fixture / 'huge.txt').open('wb') as output:
        chunk = b'Huge read-only preview fixture\n' * 32768
        for _ in range(104): output.write(chunk)
    adb('push', str(fixture), r.ROOT)
    cases = [
        ('clean-save-undo-save', dirty_undo_save),
        ('large-document-edit-save', large_edit),
        ('huge-file-protected-preview', lambda: protected_preview('huge.txt', 'Read-only preview · This file is too large or has very long lines. Only the beginning is shown; saving is disabled.')),
        ('long-line-protected-preview', lambda: protected_preview('long-line.txt', 'Read-only preview · This file is too large or has very long lines. Only the beginning is shown; saving is disabled.')),
        ('binary-lib-protected-preview', lambda: protected_preview('binary.lib', 'Binary file · Read-only text preview. Some bytes cannot be shown as text. Use Hex editor for the complete file.', True)),
        ('binary-so-protected-preview', lambda: protected_preview('binary.so', 'Binary file · Read-only text preview. Some bytes cannot be shown as text. Use Hex editor for the complete file.', True)),
        ('large-binary-lib-protected-preview', lambda: protected_preview('large-binary.lib', 'Binary file · Read-only text preview. Some bytes cannot be shown as text. Use Hex editor for the complete file.', True)),
        ('large-binary-so-protected-preview', lambda: protected_preview('large-binary.so', 'Binary file · Read-only text preview. Some bytes cannot be shown as text. Use Hex editor for the complete file.', True)),
        ('text-lib-chooser-open-save', lambda: text_library('readable.lib')),
        ('text-so-chooser-open-save', lambda: text_library('linker.so')),
        ('utf16le-lib-chooser-open-save', lambda: text_library('unicode-le.LIB', 'utf-16-le')),
        ('utf16be-lib-chooser-open-save', lambda: text_library('unicode-be.lib', 'utf-16-be')),
        ('minimize-retains-back-clears-tabs', minimize_back),
    ]
    selected = sys.argv[1:]
    if any(name not in dict(cases) for name in selected): raise SystemExit('Unknown editor scenario')
    for name, check in cases:
        if not selected or name in selected: r.scenario(name, check)
    (ARTIFACTS / 'editor-performance-results.json').write_text(json.dumps({'fixture': r.ROOT, 'results': r.RESULTS}, indent=2))
    (ARTIFACTS / 'editor-performance-logcat.txt').write_text(adb('logcat', '-d'), encoding='utf-8')
    sys.exit(1 if any(x['status'] == 'FAIL' for x in r.RESULTS) else 0)
