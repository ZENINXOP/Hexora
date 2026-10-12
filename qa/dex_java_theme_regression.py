"""Owned DEX Smali-to-Java appearance and read-only acceptance checks.

Run sequentially with other native UI drivers. AI-assisted work: OpenAI Codex.
"""
import sys
import zipfile
from collections import Counter
from PIL import Image
import dex_session_regression as dex
import regression as r
from mumu_ui import *

r.ROOT = '/sdcard/Download/Hexora-DEX-Java-QA-' + time.strftime('%Y%m%d-%H%M%S')


def palette(name):
    layout = tree()
    a, b, c, d = bounds(find(rid='editor', root=layout))
    with Image.open(ARTIFACTS / (name + '.png')) as screenshot:
        rgb = screenshot.convert('RGB')
        body = rgb.crop((a + (c-a)//3, b + (d-b)//5, c-8, d-8))
        gutter = rgb.crop((a+1, b+8, a+5, d-8))
        return {label: Counter(region.getdata()).most_common(1)[0][0]
                for label, region in [('background', body), ('gutter', gutter)]}


def conversion(mode):
    r.reset()
    r.menu('Preferences')
    tap(rid=mode + 'ThemeButton')
    original = dex.open_class()
    before_hash = adb('shell', 'sha256sum', r.ROOT + '/classes.dex').split()[0]
    before_name = 'smali-before-java-' + mode
    snapshot(before_name)
    before = palette(before_name)
    tap(rid='btn_file')
    tap(text='Smali to Java')
    dex.wait_for(lambda: 'class EditorActivity' in dex.code(), seconds=60)
    find(text='Java preview · read-only')
    after_name = 'converted-java-' + mode
    snapshot(after_name)
    after = palette(after_name)
    assert before == after, f'DEX theme changed on conversion: {before} -> {after}'
    java = dex.code()
    tap(rid='editor')
    adb('shell', 'input', 'text', 'MUSTNOTEDIT')
    assert dex.code() == java, 'Converted preview became editable'
    assert not any(n.get('resource-id', '').endswith(':id/btn_save') for n in tree().iter('node'))
    if mode == 'black':
        auto = adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation')
        rotation = adb('shell', 'settings', 'get', 'system', 'user_rotation')
        try:
            adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', 0)
            adb('shell', 'settings', 'put', 'system', 'user_rotation', 1)
            dex.wait_for(lambda: dex.code() == java)
            snapshot('converted-java-black-landscape')
            assert palette('converted-java-black-landscape') == before
        finally:
            for key, value in [('user_rotation', rotation), ('accelerometer_rotation', auto)]:
                if value == 'null': adb('shell', 'settings', 'delete', 'system', key)
                else: adb('shell', 'settings', 'put', 'system', key, value)
    # Wide layouts/rotation can already expose the tab drawer. Open it only
    # when the tab list is absent instead of expecting a hidden toolbar button.
    layout = tree()
    if not any(n.get('resource-id', '').endswith(':id/tabs_recycler_view') for n in layout.iter('node')):
        tap(desc='Open navigation drawer')
    tap(text='EditorActivity')
    dex.wait_for(lambda: dex.code() == original)
    snapshot('smali-after-java-' + mode)
    assert palette('smali-after-java-' + mode) == before
    assert before_hash == adb('shell', 'sha256sum', r.ROOT + '/classes.dex').split()[0]
    (ARTIFACTS / ('palette-' + mode + '.json')).write_text(json.dumps({'smali': before, 'java': after}, indent=2))


if __name__ == '__main__':
    sample = Path(__file__).resolve().parents[1] / 'samples/plugin-sample/release/plugin-sample-release.apk'
    with zipfile.ZipFile(sample) as apk: (ARTIFACTS / 'classes.dex').write_bytes(apk.read('classes.dex'))
    adb('shell', 'mkdir', '-p', r.ROOT)
    adb('push', str(ARTIFACTS / 'classes.dex'), r.ROOT + '/classes.dex')
    selected = sys.argv[1:]
    if any(mode not in ('light', 'dark', 'black') for mode in selected): raise SystemExit('Unknown theme')
    for mode in selected or ('light', 'dark', 'black'):
        r.scenario('smali-to-java-theme-' + mode, lambda m=mode: conversion(m))
    (ARTIFACTS / 'dex-java-theme-logcat.txt').write_text(adb('logcat', '-d'), encoding='utf-8')
    sys.exit(1 if any(row['status'] == 'FAIL' for row in r.RESULTS) else 0)
