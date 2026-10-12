"""Native chooser interactions and PDF/type icon evidence in both themes.

Uses disposable files; run sequentially with the other MuMu drivers.
AI-assisted contribution: OpenAI Codex.
"""
import sys
import zipfile
import regression as r
from mumu_ui import *

r.ROOT = '/sdcard/Download/Hexora-UI-QA-' + time.strftime('%Y%m%d-%H%M%S')


def theme(mode):
    r.reset()
    r.menu('Preferences')
    tap(rid=mode + 'ThemeButton')
    r.reset()


def icons(mode):
    theme(mode)
    find(text='01-report.PDF', pane=1)
    find(text='02-script.py', pane=1)
    snapshot('file-types-' + mode)
    find_visible(text='12-classes.dex', pane=1)
    snapshot('compiled-file-types-' + mode)
    r.reset()
    tap(text='type-icons.zip', pane=1)
    find(text='01-report.PDF', pane=1)
    snapshot('archive-file-types-' + mode)


def chooser():
    r.reset()
    r.context('ambiguous.bin', 'Open with')
    find(desc='Text editor')
    find(desc='Hex editor')
    snapshot('open-with-built-in')
    tap(rid='open_with_search_button')
    tap(rid='open_with_search_input')
    enter('NoSuchTool')
    find(text='No matching open options')
    enter('Text')
    find(desc='Text editor')
    tap(rid='open_with_search_button')
    tap(rid='open_with_info_button')
    details = find(rid='open_with_mime_info').get('text', '')
    assert 'Reported MIME: application/octet-stream' in details, details
    assert 'Real MIME: text/plain' in details, details
    snapshot('open-with-details')
    tap(rid='open_with_type')
    for label in ('Built-in', 'Text', 'Image', 'Video', 'Audio', 'All'):
        find(text=label)
    snapshot('open-with-type-menu')
    tap(text='Text')
    find(rid='open_with_category')
    tap(rid='open_with_type')
    tap(text='Built-in')
    find(desc='Text editor')
    find(desc='Hex editor')
    tap(rid='open_with_close')
    find(rid='listViewPane1')
    r.context('ambiguous.bin', 'Open with')
    tap(desc='Text editor')
    assert 'Chooser fixture' in find(rid='editor').get('text', '')
    assert find(rid='btn_save').get('enabled') == 'false'


if __name__ == '__main__':
    fixture = ARTIFACTS / 'type-fixtures'
    fixture.mkdir(parents=True, exist_ok=True)
    names = ['01-report.PDF', '02-script.py', '03-layout.xml', '04-Main.java',
             '05-notes.txt', '06-settings.json', '07-Main.kt', '08-page.html',
             '09-style.css', '10-code.smali', '11-native.cpp', '12-classes.dex',
             '13-resources.arsc', '14-libnative.so', '15-users.sqlite', '16-Main.class', '17-module.pyc']
    for name in names: (fixture / name).write_text('Type icon fixture\n')
    (fixture / 'ambiguous.bin').write_text('Chooser fixture\n')
    with zipfile.ZipFile(fixture / 'type-icons.zip', 'w') as archive:
        for name in names: archive.write(fixture / name, name)
    adb('push', str(fixture), r.ROOT)
    for mode in ('light', 'dark'): r.scenario('type-icons-' + mode, lambda m=mode: icons(m))
    r.scenario('open-with-search-type-details-open', chooser)
    theme('dark')
    (ARTIFACTS / 'ui-polish-results.json').write_text(json.dumps({'fixture': r.ROOT, 'results': r.RESULTS}, indent=2))
    (ARTIFACTS / 'ui-polish-logcat.txt').write_text(adb('logcat', '-d'), encoding='utf-8')
    sys.exit(1 if any(x['status'] == 'FAIL' for x in r.RESULTS) else 0)
