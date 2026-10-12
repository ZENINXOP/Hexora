"""Owned JVM/Smali previews and nested AAR navigation on MuMu. OpenAI Codex."""
import sys
import io
import zipfile
import subprocess
from collections import Counter
from PIL import Image
import mumu_ui as ui
import regression as r
from mumu_ui import *

# Retry only missing fresh UIAutomator XML, retaining the tool fault. App
# assertions are not retried; a malformed/missing app view still fails.
original_tree = ui.tree
def fresh_tree():
    for attempt in range(3):
        try: return original_tree()
        except RuntimeError as error:
            if 'No such file or directory' not in str(error) or attempt == 2: raise
            with (ARTIFACTS/'uiautomator-retries.txt').open('a') as log: log.write(str(error)+'\n')
            time.sleep(.5)
ui.tree = fresh_tree
tree = fresh_tree
r.ROOT = '/sdcard/Download/Hexora-Class-AAR-QA-' + time.strftime('%Y%m%d-%H%M%S')


def wait_for(predicate, seconds=45):
    end = time.monotonic()+seconds
    last = None
    while time.monotonic() < end:
        try:
            value = predicate()
            if value: return value
        except (AssertionError, RuntimeError) as error: last = error
        time.sleep(.5)
    raise AssertionError('Timed out waiting for preview: '+str(last))


def code(): return find(rid='editor').get('text','')
def path(): return find(rid='currentFolderPath').get('text','')
def assert_path(expected):
    wait_for(lambda: path() == expected)
    assert '/cache/' not in path(), path()


def palette(name):
    snapshot(name)
    a,b,c,d = bounds(find(rid='editor'))
    with Image.open(ARTIFACTS/(name+'.png')) as screenshot:
        region=screenshot.convert('RGB').crop((a+(c-a)//3,b+(d-b)//5,c-8,d-8))
        return Counter(region.getdata()).most_common(1)[0][0]


def convert(expected, label):
    before=palette(label+'-before')
    tap(rid='btn_file'); tap(text='View as Java')
    wait_for(lambda: expected in code(), seconds=60)
    assert find(rid='document_notice_text').get('text','').startswith('Java preview')
    after=palette(label+'-java')
    assert before == after, (before,after)
    java=code()
    tap(rid='editor'); adb('shell','input','text','MUSTNOTEDIT')
    assert code() == java
    assert find(rid='btn_save').get('enabled') == 'false'
    assert find(rid='btn_edit').get('enabled') == 'false'


def select_source(name):
    # Read-only editor focus can briefly omit toolbar accessibility nodes.
    # Wait for a real drawer/button and tap its bounds from that same fresh tree.
    def navigation_layout():
        layout=tree()
        if any(n.get('resource-id','').endswith(':id/tabs_recycler_view')
               or n.get('content-desc') == 'Open files' for n in layout.iter('node')):
            return layout
    layout=wait_for(navigation_layout, seconds=10)
    if not any(n.get('resource-id','').endswith(':id/tabs_recycler_view') for n in layout.iter('node')):
        a,b,c,d=bounds(find(desc='Open files',root=layout))
        adb('shell','input','tap',(a+c)//2,(b+d)//2)
    wait_for(lambda: find(rid='tabs_recycler_view'), seconds=10)
    tap(text=name)


def class_java(mode):
    r.reset(); r.menu('Preferences'); tap(rid=mode+'ThemeButton'); r.reset()
    original=r.read('Greeting.class')
    tap(text='Greeting.class',pane=1)
    wait_for(lambda: 'IADD' in code())
    assert find(rid='document_notice_text').get('text','').startswith('JVM bytecode')
    assert 'greet(Ljava/lang/String;)Ljava/lang/String;' in code()
    assert 'ILOAD 0' in code() and 'MAXSTACK = 2' in code()
    assert find(rid='btn_save').get('enabled') == 'false'
    bytecode=code()
    convert('class Greeting', 'class-'+mode)
    assert 'int add(' in code() and ' + ' in code()
    select_source('Greeting.class')
    wait_for(lambda: code() == bytecode)
    assert find(rid='document_notice_text').get('text','').startswith('JVM bytecode')
    assert original == r.read('Greeting.class')
    adb('shell','input','keyevent',4); assert_path(r.ROOT)


def smali_java(archive=False):
    r.reset()
    source='sample.aar' if archive else 'OwnedSmali.smali'
    original=r.read(source)
    if archive:
        tap(text='sample.aar',pane=1); tap(text='sources',pane=1)
    tap(text='OwnedSmali.smali',pane=1)
    wait_for(lambda: '.class public LOwnedSmali;' in code())
    smali=code()
    convert('class OwnedSmali', 'smali-aar' if archive else 'smali-local')
    assert 'int add(' in code()
    select_source('OwnedSmali.smali'); wait_for(lambda: code()==smali)
    adb('shell','input','keyevent',4)
    assert_path(r.ROOT+'/sample.aar!sources/' if archive else r.ROOT)
    assert original == r.read(source)


def aar_class_java():
    r.reset(); original=r.read('sample.aar')
    tap(text='sample.aar',pane=1)
    assert_path(r.ROOT+'/sample.aar!')
    find(text='classes.jar',pane=1)
    tap(text='classes.jar',pane=1)
    assert_path(r.ROOT+'/sample.aar!classes.jar!')
    tap(text='Greeting.class',pane=1)
    wait_for(lambda: 'IADD' in code())
    convert('class Greeting','aar-class')
    adb('shell','input','keyevent',4)
    assert_path(r.ROOT+'/sample.aar!classes.jar!')
    adb('shell','input','keyevent',4); assert_path(r.ROOT+'/sample.aar!')
    adb('shell','input','keyevent',4); assert_path(r.ROOT)
    assert original == r.read('sample.aar')


def archive_parents():
    for control in ('parent-row','up','android-back'):
        r.reset(); tap(text='sample.aar',pane=1); tap(text='libs',pane=1); tap(text='nested.jar',pane=1)
        assert_path(r.ROOT+'/sample.aar!libs/nested.jar!')
        if control=='parent-row': tap(text='..',pane=1)
        elif control=='up': tap(rid='upButton')
        else: adb('shell','input','keyevent',4)
        assert_path(r.ROOT+'/sample.aar!libs/')
        tap(rid='upButton'); assert_path(r.ROOT+'/sample.aar!')
        tap(rid='upButton'); assert_path(r.ROOT)


def recursive_archives():
    r.reset(); original=r.read('outer.zip')
    tap(text='outer.zip',pane=1); tap(text='modules',pane=1); tap(text='sample.aar',pane=1)
    assert_path(r.ROOT+'/outer.zip!modules/sample.aar!')
    tap(text='classes.jar',pane=1)
    assert_path(r.ROOT+'/outer.zip!modules/sample.aar!classes.jar!')
    # Touching the opposite pane and returning must restore the logical header.
    tap(rid='syncPaneButton')
    tap(text='..',pane=2)
    tap(text='Greeting.class',pane=1)
    wait_for(lambda: 'IADD' in code())
    adb('shell','input','keyevent',4)
    assert_path(r.ROOT+'/outer.zip!modules/sample.aar!classes.jar!')
    tap(text='..',pane=1); assert_path(r.ROOT+'/outer.zip!modules/sample.aar!')
    tap(text='..',pane=1); assert_path(r.ROOT+'/outer.zip!modules/')
    adb('shell','input','keyevent',4); assert_path(r.ROOT+'/outer.zip!')
    adb('shell','input','keyevent',4); assert_path(r.ROOT)
    assert original == r.read('outer.zip')


def malformed_classes():
    for name, message in [('broken.class','Invalid JVM class file'),('too-large.class','Class file is too large')]:
        r.reset(); original=r.read(name); tap(text=name,pane=1)
        wait_for(lambda: any(message in n.get('text','') for n in tree().iter('node')))
        snapshot(name+'-error')
        adb('shell','input','keyevent',4); adb('shell','input','keyevent',4)
        assert_path(r.ROOT)
        assert r.read(name) == original


def minimized_java():
    r.reset(); tap(text='Greeting.class',pane=1); wait_for(lambda:'IADD' in code())
    convert('class Greeting','minimized-class')
    tap(rid='btn_minimize'); wait_for(lambda:'MainActivity' in adb('shell','dumpsys','activity','activities'))
    # Restart only Hexora, then reopen a class to verify serialized read-only Java tabs.
    time.sleep(.5)
    r.reset()
    tap(text='Greeting.class',pane=1); wait_for(lambda:'IADD' in code())
    select_source('Greeting.java'); wait_for(lambda:'class Greeting' in code())
    assert find(rid='document_notice_text').get('text','').startswith('Java preview')
    assert find(rid='btn_save').get('enabled') == 'false'
    adb('shell','input','keyevent',4); assert_path(r.ROOT)


def prepare():
    source=ARTIFACTS/'Greeting.java'
    source.write_text('public class Greeting { public static String greet(String name) { return "Hello, "+name; } public static int add(int left,int right) { return left+right; } }')
    subprocess.run([r'C:\Users\saksh\.jdks\jbr-17.0.14\bin\javac.exe','--release','8',str(source)],check=True)
    smali='.class public LOwnedSmali;\n.super Ljava/lang/Object;\n.method public static add(II)I\n    .registers 3\n    add-int v0, p0, p1\n    return v0\n.end method\n'
    (ARTIFACTS/'OwnedSmali.smali').write_text(smali)
    jar=io.BytesIO()
    with zipfile.ZipFile(jar,'w') as z: z.write(ARTIFACTS/'Greeting.class','Greeting.class')
    with zipfile.ZipFile(ARTIFACTS/'sample.aar','w') as z:
        z.writestr('classes.jar',jar.getvalue()); z.writestr('libs/nested.jar',jar.getvalue())
        z.writestr('AndroidManifest.xml','<manifest package="app.hexora.owned.fixture"/>')
        z.writestr('res/values/strings.xml','<resources/>'); z.writestr('sources/OwnedSmali.smali',smali)
    with zipfile.ZipFile(ARTIFACTS/'outer.zip','w') as z: z.write(ARTIFACTS/'sample.aar','modules/sample.aar')
    (ARTIFACTS/'broken.class').write_bytes(b'not JVM bytes')
    (ARTIFACTS/'too-large.class').write_bytes(b'\xCA\xFE\xBA\xBE'+b'\0'*(2*1024*1024))
    adb('shell','mkdir','-p',r.ROOT)
    for name in ['Greeting.class','OwnedSmali.smali','sample.aar','outer.zip','broken.class','too-large.class']:
        adb('push',str(ARTIFACTS/name),r.ROOT+'/'+name)


if __name__=='__main__':
    auto=adb('shell','settings','get','system','accelerometer_rotation')
    rotate=adb('shell','settings','get','system','user_rotation')
    try:
        adb('shell','settings','put','system','accelerometer_rotation',0)
        adb('shell','settings','put','system','user_rotation',0)
        prepare()
        cases=[('class-java-'+mode,lambda m=mode:class_java(m)) for mode in ('light','dark','black')]
        cases += [('smali-java-local',smali_java),('smali-java-aar',lambda:smali_java(True)),('class-java-aar',aar_class_java),('aar-parent-controls',archive_parents),('aar-recursive-parents',recursive_archives),('class-invalid-and-large',malformed_classes),('class-java-minimize',minimized_java)]
        for name,fn in cases:
            if not sys.argv[1:] or name in sys.argv[1:]: r.scenario(name,fn)
        (ARTIFACTS/'class-aar-logcat.txt').write_text(adb('logcat','-d'),encoding='utf-8')
    finally:
        for key,value in [('user_rotation',rotate),('accelerometer_rotation',auto)]:
            if value=='null': adb('shell','settings','delete','system',key)
            else: adb('shell','settings','put','system',key,value)
    sys.exit(1 if any(row['status']=='FAIL' for row in r.RESULTS) else 0)
