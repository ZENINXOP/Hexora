"""Native deliberate pull-to-refresh checks using owned external file changes.

AI-assisted work: OpenAI Codex. Run sequentially with other UI drivers.
"""
import sys
import regression as r
from mumu_ui import *

r.ROOT = '/sdcard/Download/Hexora-Refresh-QA-' + time.strftime('%Y%m%d-%H%M%S')


def visible(name, pane):
    try:
        find(text=name, pane=pane)
        return True
    except AssertionError:
        return False


def pull(pane, distance_dp):
    a, b, c, d = bounds(find(rid='listViewPane' + str(pane)))
    density_text = adb('shell', 'wm', 'density')
    density = int(re.findall(r'(?:Physical|Override) density: (\d+)', density_text)[-1]) / 160
    start = b + int(220 * density)  # blank list space, away from file rows
    end = start + int(distance_dp * density)
    assert end < d - 20, 'Fixture viewport is too short for the intended pull'
    adb('shell', 'input', 'swipe', (a+c)//2, start, (a+c)//2, end, 350)


def threshold(pane):
    r.reset()
    tap(rid='syncPaneButton')
    for p in (1, 2): find(text='baseline.txt', pane=p)
    marker = 'pending-' + str(pane) + '.txt'
    adb('shell', 'touch', r.ROOT + '/' + marker)
    assert not visible(marker, pane), 'Fixture refreshed automatically before the gesture'
    for distance in (40, 150):
        pull(pane, distance)
        assert not visible(marker, pane), f'{distance}dp drag unexpectedly refreshed pane {pane}'
        assert find(rid='currentFolderPath').get('text') == r.ROOT
    pull(pane, 280)
    find(text=marker, pane=pane)
    assert not visible(marker, 3-pane), 'Pull refreshed the inactive pane'
    snapshot('deliberate-refresh-pane-' + str(pane))


def ordinary_scroll():
    r.reset()
    tap(text='scrolling', pane=1)
    find(text='row-000.txt', pane=1)
    a,b,c,d = bounds(find(rid='listViewPane1'))
    adb('shell', 'input', 'swipe', (a+c)//2, d-120, (a+c)//2, b+130, 500)
    assert not visible('row-000.txt', 1), 'Ordinary list scrolling stopped working'
    adb('shell', 'touch', r.ROOT + '/scrolling/000-pending.txt')
    density = int(re.findall(r'(?:Physical|Override) density: (\d+)', adb('shell', 'wm', 'density'))[-1]) / 160
    start = b + int(180*density)
    adb('shell', 'input', 'swipe', (a+c)//2, start, (a+c)//2, start+int(160*density), 900)
    assert not visible('000-pending.txt', 1), 'Scrolling from the middle refreshed/reset the list'
    assert not visible('row-000.txt', 1), 'Small downward scroll reset the list to the top'
    snapshot('ordinary-scroll-stays-in-list')
    names = [n.get('text') for n in tree().iter('node')
             if re.fullmatch(r'row-\d+.txt', n.get('text','')) and bounds(n)[0] < (c-a)//2]
    tap(text=names[0], pane=1)
    assert 'Owned scrolling fixture' in find(rid='editor').get('text',''), 'Row opening broke after scrolling'


def empty_folder():
    r.reset()
    tap(text='empty', pane=1)
    adb('shell', 'touch', r.ROOT + '/empty/new.txt')
    for distance in (40, 150):
        pull(1, distance)
        assert not visible('new.txt', 1), 'Small pull refreshed the empty folder'
    pull(1, 280)
    find(text='new.txt', pane=1)
    snapshot('empty-folder-deliberate-refresh')


if __name__ == '__main__':
    fixture = ARTIFACTS / 'baseline.txt'
    fixture.write_bytes(b'Owned refresh gesture fixture\n')
    adb('shell', 'mkdir', '-p', r.ROOT)
    adb('push', str(fixture), r.ROOT + '/baseline.txt')
    scrolling = ARTIFACTS / 'scrolling'
    scrolling.mkdir(exist_ok=True)
    for i in range(100): (scrolling / f'row-{i:03d}.txt').write_bytes(b'Owned scrolling fixture\n')
    adb('push', str(scrolling), r.ROOT + '/scrolling')
    adb('shell', 'mkdir', '-p', r.ROOT + '/empty')
    previous = {key: adb('shell', 'settings', 'get', 'system', key)
                for key in ('accelerometer_rotation', 'user_rotation')}
    try:
        adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', 0)
        adb('shell', 'settings', 'put', 'system', 'user_rotation', 0)
        r.scenario('pull-refresh-left-deliberate-only', lambda: threshold(1))
        r.scenario('pull-refresh-right-deliberate-only', lambda: threshold(2))
        r.scenario('pull-refresh-native-scroll-and-row-open', ordinary_scroll)
        r.scenario('pull-refresh-empty-folder-deliberate-only', empty_folder)
    finally:
        for key in ('user_rotation', 'accelerometer_rotation'):
            value = previous[key]
            if value == 'null': adb('shell', 'settings', 'delete', 'system', key)
            else: adb('shell', 'settings', 'put', 'system', key, value)
    (ARTIFACTS / 'refresh-gesture-logcat.txt').write_text(adb('logcat', '-d'), encoding='utf-8')
    sys.exit(1 if any(row['status'] == 'FAIL' for row in r.RESULTS) else 0)
