"""Native comparison checks against the installed MT library text view.
Owned fixtures only. AI-assisted implementation: OpenAI Codex.
"""
import sys
import shutil
import regression as r
from mumu_ui import *

r.ROOT = '/sdcard/Download/Hexora-MT-Text-QA-' + time.strftime('%Y%m%d-%H%M%S')


def open_library(name='21-real-native.so'):
    r.reset()
    tap(text=name, pane=1)
    find(rid='open_with_grid')
    tap(desc='Text editor')
    return tree()


def comparison(mode):
    r.reset()
    r.menu('Preferences')
    tap(rid=mode + 'ThemeButton')
    layout = open_library()
    find(text='21-real-native.so', rid='editor_file_name', root=layout)
    assert 'UTF-8' in find(rid='editor_file_status', root=layout).get('text', '')
    assert 'Preview' in find(rid='editor_file_status', root=layout).get('text', '')
    assert find(rid='btn_save', root=layout).get('enabled') == 'false'
    text = find(rid='editor', root=layout).get('text', '')
    assert 'ELF' in text and '__cxa_finalize' in text, 'Binary padding obscured readable strings'
    assert '\x00' not in text and '7F 45 4C 46' not in text
    assert len(text) <= 65536
    assert not any(n.get('resource-id', '').endswith(':id/document_notice') for n in layout.iter('node'))
    for symbol in ['→', '/', '+', '-', '*', '=', '<', '>', '"', "'", ';', '|']:
        assert find(text=symbol, root=layout).get('enabled') == 'false'
    # The decoded view must wrap to screen width: its first visible logical lines
    # span several visual rows, unlike the previous horizontally clipped view.
    snapshot('mt-style-library-' + mode)
    before = adb('shell', 'sha256sum', r.ROOT + '/21-real-native.so').split()[0]
    tap(rid='editor_file_status')
    find(text='Read-only text preview. Binary control bytes are hidden; undecodable bytes appear as replacement characters. Use Hex editor for the complete file.')
    find(text='Hex editor')
    tap(text='OK')
    a,b,c,d = bounds(find(rid='editor'))
    adb('shell', 'input', 'swipe', (a+c)//2, d-60, (a+c)//2, b+100, 300)
    assert find(rid='btn_save').get('enabled') == 'false'
    assert before == adb('shell', 'sha256sum', r.ROOT + '/21-real-native.so').split()[0]
    snapshot('mt-style-library-scrolled-' + mode)


def symbols_editable():
    open_library('readable.lib')
    assert find(rid='btn_save').get('enabled') == 'false'
    assert find(text='/').get('enabled') == 'true', 'Editable document shortcut stayed disabled after loading'
    tap(text='/')
    assert find(rid='btn_save').get('enabled') == 'true', 'Symbol insertion did not enable Save'
    assert find(rid='editor').get('text').startswith('/LIBRARY'), 'Symbol inserted at the wrong position'
    assert find(rid='editor_file_status').get('text').startswith('1:2'), 'Caret status did not follow symbol insertion'
    tap(rid='btn_undo')
    for _ in range(5):
        if find(rid='btn_save').get('enabled') == 'false': break
        time.sleep(.3)
    assert find(rid='btn_save').get('enabled') == 'false', 'Undo did not restore clean Save'
    snapshot('mt-style-editable-symbols')


def source_light():
    r.reset()
    r.menu('Preferences')
    tap(rid='lightThemeButton')
    r.reset()
    tap(text='syntax.py', pane=1)
    layout = tree()
    assert find(rid='editor').get('text', '').startswith('# Owned syntax fixture')
    assert find(rid='btn_save', root=layout).get('enabled') == 'false'
    assert 'Preview' not in find(rid='editor_file_status', root=layout).get('text', '')
    snapshot('mt-style-source-light')


if __name__ == '__main__':
    fixtures = ARTIFACTS / 'fixtures'
    fixtures.mkdir(parents=True, exist_ok=True)
    source = Path(__file__).resolve().parents[1] / 'app/src/main/assets/signature/lspatch/so/x86_64/liblspatch.so'
    shutil.copyfile(source, fixtures / '21-real-native.so')
    (fixtures / 'readable.lib').write_text('LIBRARY sample\nEXPORTS\n  hello\n', encoding='utf-8')
    (fixtures / 'syntax.py').write_bytes(b'# Owned syntax fixture\ndef hello(name):\n    value = 42\n    return "hello " + name\n')
    adb('push', str(fixtures), r.ROOT)
    cases = [('mt-library-light', lambda: comparison('light')),
             ('mt-library-dark', lambda: comparison('dark')),
             ('mt-symbols-edit-undo-status', symbols_editable),
             ('mt-source-light-contrast', source_light)]
    selected = sys.argv[1:]
    if any(name not in dict(cases) for name in selected): raise SystemExit('Unknown MT view scenario')
    for name, check in cases:
        if not selected or name in selected: r.scenario(name, check)
    (ARTIFACTS / 'mt-text-logcat.txt').write_text(adb('logcat', '-d'), encoding='utf-8')
    sys.exit(1 if any(row['status'] == 'FAIL' for row in r.RESULTS) else 0)
