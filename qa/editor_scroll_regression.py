"""Owned file-list scroll retention through editor lifecycle on MuMu. OpenAI Codex."""
import sys
import zipfile
import regression as r
from mumu_ui import *

r.ROOT = '/sdcard/Download/Hexora-Scroll-QA-' + time.strftime('%Y%m%d-%H%M%S')


def wait_for(fn, seconds=25):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            value = fn()
            if value is not None and value is not False: return value
        except AssertionError: pass
        time.sleep(.4)
    raise AssertionError('Timed out waiting for editor/browser state')


def rows(pane, layout=None):
    layout = layout if layout is not None else tree()
    left, top, right, bottom = bounds(find(rid='listViewPane' + str(pane), root=layout))
    return [(n.get('text'), bounds(n)) for n in layout.iter('node')
            if n.get('resource-id', '').endswith(':id/fileName')
            and left <= bounds(n)[0] < right and bounds(n)[1] >= top
            and bounds(n)[3] <= bottom and n.get('text') != '..']


def scroll(pane, count):
    a,b,c,d = bounds(find(rid='listViewPane' + str(pane)))
    for _ in range(count):
        adb('shell', 'input', 'swipe', (a+c)//2, d-130, (a+c)//2, b+130, 500)
    time.sleep(.7)
    visible = rows(pane)
    assert len(visible) >= 4 and not visible[0][0].startswith('row-000'), visible


def positions():
    layout = tree()
    return {pane: rows(pane, layout) for pane in (1,2)}


def assert_positions(before, label):
    wait_for(lambda: find(rid='listViewPane1'))
    time.sleep(.8)  # allow both asynchronous refreshes and layout to finish
    after = positions()
    (ARTIFACTS/(label+'-positions.json')).write_text(
        json.dumps({'before':before, 'after':after}, indent=2), encoding='utf-8')
    for pane in (1,2):
        # Use an interior row so the assertion includes pixel offset and is
        # independent of accessibility clipping at the viewport edges.
        name, original = before[pane][2]
        matches = [b for text,b in after[pane] if text == name]
        assert matches, f'Pane {pane} lost visible anchor {name}: {after[pane]}'
        assert max(abs(a-b) for a,b in zip(original,matches[0])) <= 2, (
            f'Pane {pane} moved {name}: {original} -> {matches[0]}')


def prepare(archive=False, libraries=False):
    r.reset()
    tap(text='rows.zip' if archive else 'libraries' if libraries else 'rows', pane=1)
    if archive: tap(text='docs', pane=1)
    tap(rid='syncPaneButton')
    if archive:
        # Sync intentionally shows the archive's containing directory. Browse
        # the second pane into the same entry directory through real row taps.
        tap(text='rows.zip',pane=2)
        tap(text='docs',pane=2)
    wait_for(lambda: len(rows(2)) >= 4)
    scroll(1, 2)
    scroll(2, 3)
    return positions()


def open_visible(pane, before, library=False):
    name = before[pane][4][0]
    tap(text=name, pane=pane)
    if library: tap(desc='Text editor')
    wait_for(lambda: 'Owned scroll fixture' in find(rid='editor').get('text',''))
    return name


def close_editor(archive_save=False):
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', 4)
    adb('shell', 'input', 'keyevent', 4)
    wait_for(lambda: find(text='Yes') if archive_save else find(rid='listViewPane1'))


def back(pane=1, archive=False):
    before = prepare(archive)
    open_visible(pane, before)
    close_editor()
    assert_positions(before, f'back-pane-{pane}-archive-{archive}')
    expected = r.ROOT+'/rows.zip!docs/' if archive else r.ROOT+'/rows'
    assert find(rid='currentFolderPath').get('text') == expected


def minimize_with_insert():
    before = prepare()
    name = open_visible(2, before)
    adb('shell', 'touch', r.ROOT+'/rows/000-added.txt')
    tap(rid='btn_minimize')
    assert_positions(before, 'minimize-with-insert')
    # Verify the listing really refreshed, rather than retaining stale rows:
    # normal navigation away/back reveals the new row and resets to the top.
    tap(rid='upButton')
    tap(text='rows', pane=2)
    find(text='000-added.txt', pane=2)
    tap(text=name, pane=2)
    wait_for(lambda: find(rid='editor'))
    close_editor()


def library_back():
    before=prepare(libraries=True)
    open_visible(1,before,library=True)
    assert find(rid='btn_save').get('enabled') == 'false'
    close_editor()
    assert_positions(before,'binary-library-text-back')


def save(archive=False):
    before = prepare(archive)
    name = open_visible(1, before)
    tap(rid='editor')
    adb('shell','input','keyevent','KEYCODE_MOVE_END')
    adb('shell','input','text','SCROLLSAVE')
    wait_for(lambda: find(rid='btn_save').get('enabled') == 'true')
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell','input','keyevent',4)
    tap(rid='btn_save')
    wait_for(lambda: find(rid='btn_save').get('enabled') == 'false')
    close_editor(archive)
    if archive:
        wait_for(lambda: find(text='Yes'))
        # A plain ZIP is saved directly; APK signing is not applicable.
        assert not any(n.get('resource-id','').endswith(':id/autosign') for n in tree().iter('node'))
        tap(text='Yes')
        wait_for(lambda: find(rid='listViewPane1'))
    assert_positions(before, 'archive-save' if archive else 'local-save')
    if archive:
        result = ARTIFACTS/'saved-rows.zip'
        result.write_bytes(adb('exec-out','cat',r.ROOT+'/rows.zip',binary=True))
        with zipfile.ZipFile(result) as z:
            assert b'SCROLLSAVE' in z.read('docs/'+name)
    else:
        assert b'SCROLLSAVE' in adb('exec-out','cat',r.ROOT+'/rows/'+name,binary=True)


if __name__ == '__main__':
    local = ARTIFACTS/'fixtures'
    (local/'rows').mkdir(parents=True, exist_ok=True)
    for i in range(160):
        (local/'rows'/f'row-{i:03d}.txt').write_bytes(b'Owned scroll fixture\n')
    (local/'libraries').mkdir(exist_ok=True)
    for i in range(160):
        (local/'libraries'/f'row-{i:03d}.lib').write_bytes(
            b'\x7fELF\0Owned scroll fixture\0'+bytes(range(256))*4)
    with zipfile.ZipFile(local/'rows.zip','w') as z:
        for p in (local/'rows').iterdir(): z.write(p,'docs/'+p.name)
    adb('push',str(local),r.ROOT)
    previous={key:adb('shell','settings','get','system',key)
              for key in ('accelerometer_rotation','user_rotation')}
    cases=[('editor-back-left-scroll',lambda:back(1)),
           ('editor-back-right-scroll',lambda:back(2)),
           ('editor-minimize-scroll-with-new-row',minimize_with_insert),
           ('editor-local-save-scroll',save),
           ('editor-archive-back-scroll',lambda:back(1,True)),
           ('editor-archive-save-scroll',lambda:save(True)),
           ('editor-library-text-back-scroll',library_back)]
    selected=set(sys.argv[1:])
    try:
        adb('shell','settings','put','system','accelerometer_rotation',0)
        adb('shell','settings','put','system','user_rotation',0)
        for name,fn in cases:
            if not selected or name in selected: r.scenario(name,fn)
    finally:
        for key in ('user_rotation','accelerometer_rotation'):
            value=previous[key]
            if value == 'null': adb('shell','settings','delete','system',key)
            else: adb('shell','settings','put','system',key,value)
    (ARTIFACTS/'editor-scroll-logcat.txt').write_text(adb('logcat','-d'),encoding='utf-8')
    sys.exit(1 if any(row['status']=='FAIL' for row in r.RESULTS) else 0)
