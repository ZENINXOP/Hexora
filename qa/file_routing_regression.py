"""Direct local/archive opening and single-header pane touch acceptance on MuMu.

Uses owned disposable fixtures. AI-assisted contribution: OpenAI Codex.
"""
import sys
import zipfile
import shutil
import regression as r
from mumu_ui import *

r.ROOT = '/sdcard/Download/Hexora-Routing-QA-' + time.strftime('%Y%m%d-%H%M%S')


def current_path():
    return find(rid='currentFolderPath').get('text')


def only_main_header():
    ids = [n.get('resource-id', '') for n in tree().iter('node')]
    assert not any(x.endswith((':id/pane1Path', ':id/pane2Path')) for x in ids)
    assert sum(x.endswith(':id/currentFolderPath') for x in ids) == 1


def pane_touch():
    r.reset()
    tap(rid='syncPaneButton')
    tap(text='right', pane=2)
    assert current_path() == r.ROOT + '/right'
    tap(text='left-empty', pane=1)
    assert current_path() == r.ROOT + '/left-empty'
    only_main_header()
    for pane, path in [(2, r.ROOT + '/right'), (1, r.ROOT + '/left-empty'),
                       (2, r.ROOT + '/right'), (1, r.ROOT + '/left-empty')]:
        a, b, c, d = bounds(find(rid='listViewPane' + str(pane)))
        x, y = (a+c)//2, d-45
        adb('shell', 'input', 'motionevent', 'DOWN', x, y)
        try: assert current_path() == path, 'Header did not switch on touch down'
        finally: adb('shell', 'input', 'motionevent', 'UP', x, y)
    snapshot('single-header-pane-touch')


def automatic(name, activity, archive=False, expected='Routing fixture'):
    r.reset()
    if archive:
        tap(text='formats.zip', pane=1)
        tap(text='nested', pane=1)
    tap(text=name, pane=1)
    if name.lower().endswith(('.lib', '.so', '.dll', '.a', '.o')):
        find(rid='open_with_grid')
        tap(desc='Text editor' if activity == 'TextEditorActivity' else 'Hex editor')
    for _ in range(5):
        info = adb('shell', 'dumpsys', 'activity', 'activities')
        if activity in info and ('mResumedActivity' in info or 'topResumedActivity' in info): break
        time.sleep(.5)
    # Check the foreground activity, rather than a stale activity elsewhere in its task.
    foreground = '\n'.join(line for line in info.splitlines() if 'ResumedActivity' in line)
    assert activity in foreground, foreground
    layout = tree()
    assert not any(n.get('resource-id', '').endswith(':id/open_with_grid') for n in layout.iter('node'))
    if activity == 'TextEditorActivity': assert expected in find(rid='editor', root=layout).get('text', '')
    if archive and activity == 'TextEditorActivity':
        tap(rid='editor')
        adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
        adb('shell', 'input', 'text', 'QAARCHIVE')
        tap(rid='btn_save')
        assert find(rid='btn_save').get('enabled') == 'false'
        if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
            adb('shell', 'input', 'keyevent', 4)
        adb('shell', 'input', 'keyevent', 4)
        # The app asks before replacing the entry; that is a save confirmation, not a viewer menu.
        find(text='File modified')
        tap(text='Cancel')


def font_preview():
    r.reset()
    tap(text='13-font.ttf', pane=1)
    find(text='Font preview: 13-font.ttf')


def real_library_text(archive=False):
    r.reset()
    original = adb('shell', 'sha256sum', r.ROOT + ('/formats.zip' if archive else '/21-real-native.so')).split()[0]
    if archive:
        tap(text='formats.zip', pane=1)
        tap(text='nested', pane=1)
    tap(text='21-real-native.so', pane=1)
    find(rid='open_with_grid')
    tap(desc='Text editor')
    layout = tree()
    assert find(rid='btn_save', root=layout).get('enabled') == 'false'
    shown = find(rid='editor', root=layout).get('text', '')
    assert 'ELF' in shown and len(shown) <= 65536
    assert '7F 45 4C 46' not in shown and '00000000  7F' not in shown
    assert 'Preview' in find(rid='editor_file_status', root=layout).get('text', '')
    find(text='21-real-native.so', root=layout)
    tap(rid='editor')
    adb('shell', 'input', 'text', 'MUSTNOTSAVE')
    assert find(rid='btn_save').get('enabled') == 'false'
    assert original == adb('shell', 'sha256sum', r.ROOT + ('/formats.zip' if archive else '/21-real-native.so')).split()[0]


def compiled_xml_limit():
    r.reset()
    before = adb('shell', 'sha256sum', r.ROOT + '/17-large.XML').split()[0]
    tap(text='17-large.XML', pane=1)
    find(text='Compiled XML is too large to decode safely in the text editor')
    snapshot('compiled-xml-safe-size-error')
    tap(text='Close')
    assert find(rid='btn_save').get('enabled') == 'false'
    assert adb('shell', 'sha256sum', r.ROOT + '/17-large.XML').split()[0] == before


def external_document(name='14-report.PDF', label='PASS external PDF open/read grant'):
    # Replace only the same owned disposable package used by the signature suite.
    package = 'app.hexora.signaturefixture'
    # pm path returns nonzero for an absent package; absence is normal after cleanup.
    if 'package:' + package in adb('shell', 'pm', 'list', 'packages', package).splitlines():
        adb('uninstall', package)
    fixture = Path(__file__).resolve().parents[1] / 'samples/signature-fixture/build/outputs/apk/debug/signature-fixture-debug.apk'
    adb('install', str(fixture))
    try:
        r.reset()
        tap(text=name, pane=1)
        find(text=label)
        snapshot('external-document-' + name)
    finally:
        # The observer must not remain a PDF handler for later manual emulator use.
        adb('uninstall', package)


def archive_header_touch():
    r.reset()
    tap(rid='syncPaneButton')
    tap(text='formats.zip', pane=2)
    tap(text='nested', pane=2)
    assert current_path() == r.ROOT + '/formats.zip!nested/'
    only_main_header()
    a, b, c, d = bounds(find(rid='listViewPane1'))
    adb('shell', 'input', 'swipe', (a+c)//2, d-70, (a+c)//2, d-180, 200)
    assert current_path() == r.ROOT


if __name__ == '__main__':
    fixture = ARTIFACTS / 'routing-fixtures'
    fixture.mkdir(parents=True, exist_ok=True)
    (fixture / 'right').mkdir(exist_ok=True)
    (fixture / 'left-empty').mkdir(exist_ok=True)
    (fixture / 'right' / 'right.txt').write_text('Routing fixture right\n')
    for name in ('00-NOTES.TXT', '01-Main.java', '02-script.sh', '03-config.json', '04-README', '05-unknown.zzz'):
        (fixture / name).write_text('Routing fixture\n')
    for name in ('06-libNative.SO', '07-code.CLASS', '08-cache.PYC', '09-users.sqlite', '10-data.bin'):
        (fixture / name).write_bytes(b'\x7fELF\0' + bytes(range(256))*4)
    sample = Path(__file__).resolve().parents[1] / 'samples/plugin-sample/release/plugin-sample-release.apk'
    with zipfile.ZipFile(sample) as apk:
        (fixture / '11-classes.DEX').write_bytes(apk.read('classes.dex'))
        (fixture / '12-resources.ARSC').write_bytes(apk.read('resources.arsc'))
        compiled_xml = apk.read('AndroidManifest.xml')
        (fixture / '15-Manifest.XML').write_bytes(compiled_xml)
        (fixture / '16-renamed.axmlblob').write_bytes(compiled_xml)
        (fixture / '17-large.XML').write_bytes(compiled_xml + b'\0' * (9 * 1024 * 1024))
    shutil.copyfile(r'C:\Windows\Fonts\arial.ttf', fixture / '13-font.ttf')
    (fixture / '14-report.PDF').write_bytes(b'%PDF-1.4\n% Owned MIME handoff fixture; renderer coverage is separate.\n')
    (fixture / '19-readable.LIB').write_text('Routing fixture\nLIBRARY sample\nEXPORTS\n  hello\n', encoding='utf-8')
    (fixture / '20-linker.SO').write_bytes('Routing fixture\nGROUP ( libsample.so )\n'.encode('utf-16'))
    native_library = Path(__file__).resolve().parents[1] / 'app/src/main/assets/signature/lspatch/so/x86_64/liblspatch.so'
    shutil.copyfile(native_library, fixture / '21-real-native.so')
    with zipfile.ZipFile(fixture / '18-report.DOCX', 'w') as docx:
        docx.writestr('[Content_Types].xml', '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
        docx.writestr('word/document.xml', '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Owned handoff fixture</w:t></w:r></w:p></w:body></w:document>')
    with zipfile.ZipFile(fixture / 'formats.zip', 'w') as archive:
        for file in fixture.iterdir():
            if file.is_file() and file.name != 'formats.zip': archive.write(file, 'nested/' + file.name)
    # Nested entries exercise preservation of their full archive path.
    adb('push', str(fixture), r.ROOT)
    adb('shell', 'mkdir', '-p', r.ROOT + '/left-empty')
    cases = [('single-header-switches-on-touch-down', pane_touch)]
    for name in ('00-NOTES.TXT', '01-Main.java', '02-script.sh', '03-config.json', '04-README', '05-unknown.zzz'):
        cases.append(('auto-text-' + name, lambda n=name: automatic(n, 'TextEditorActivity')))
    # JVM classes have their own bytecode/Java checks in class_aar_regression.py.
    for name in ('06-libNative.SO', '08-cache.PYC', '09-users.sqlite', '10-data.bin'):
        cases.append((('library-chooser-hex-' if name.endswith('.SO') else 'auto-binary-') + name,
                      lambda n=name: automatic(n, 'HexEditorActivity')))
    cases += [('auto-dex-uppercase', lambda: automatic('11-classes.DEX', 'DexEditorActivity')),
              ('auto-arsc-uppercase', lambda: automatic('12-resources.ARSC', 'ArscEditorPlusActivity')),
              ('auto-font-preview', font_preview), ('archive-single-header-touch', archive_header_touch),
              ('external-pdf-direct-read-grant', external_document),
              ('external-docx-direct-read-grant', lambda: external_document('18-report.DOCX', 'PASS external DOCX open/read grant')),
              ('compiled-xml-direct-worker', lambda: automatic('15-Manifest.XML', 'TextEditorActivity', expected='<manifest')),
              ('compiled-xml-renamed-magic', lambda: automatic('16-renamed.axmlblob', 'TextEditorActivity', expected='<manifest')),
              ('compiled-xml-size-guard', compiled_xml_limit),
              ('archive-text-direct-save-confirm', lambda: automatic('00-NOTES.TXT', 'TextEditorActivity', True)),
              ('archive-lib-chooser-hex', lambda: automatic('06-libNative.SO', 'HexEditorActivity', True)),
              ('archive-text-lib-chooser-save-confirm', lambda: automatic('19-readable.LIB', 'TextEditorActivity', True)),
              ('archive-text-so-chooser-save-confirm', lambda: automatic('20-linker.SO', 'TextEditorActivity', True)),
              ('real-native-so-chooser-hex', lambda: automatic('21-real-native.so', 'HexEditorActivity')),
              ('real-native-so-text-preview', real_library_text),
              ('archive-real-native-so-text-preview', lambda: real_library_text(True)),
              ('archive-dex-direct', lambda: automatic('11-classes.DEX', 'DexEditorActivity', True)),
              ('archive-arsc-direct', lambda: automatic('12-resources.ARSC', 'ArscEditorPlusActivity', True))]
    selected = sys.argv[1:]
    for name, check in cases:
        if not selected or name in selected: r.scenario(name, check)
    (ARTIFACTS / 'file-routing-results.json').write_text(json.dumps({'fixture': r.ROOT, 'results': r.RESULTS}, indent=2))
    (ARTIFACTS / 'file-routing-logcat.txt').write_text(adb('logcat', '-d'), encoding='utf-8')
    sys.exit(1 if any(x['status'] == 'FAIL' for x in r.RESULTS) else 0)
