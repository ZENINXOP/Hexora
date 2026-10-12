"""Owned MuMu regression fixtures for filter, reload and archive-save bugs."""
import sys
import zipfile
import regression as r
from mumu_ui import *

r.ROOT = '/sdcard/Download/Hexora-Bugs-QA-' + time.strftime('%Y%m%d-%H%M%S')


def wait_for(fn, seconds=25):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            value = fn()
            if value is not None and value is not False: return value
        except AssertionError: pass
        time.sleep(.4)
    raise AssertionError('Timed out waiting for expected UI state')


def hide_keyboard():
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', 4)


def names(pane):
    layout = tree()
    a,b,c,d = bounds(find(rid='listViewPane'+str(pane),root=layout))
    return [n.get('text') for n in layout.iter('node')
            if n.get('resource-id','').endswith(':id/fileName')
            and a <= bounds(n)[0] < c and b <= bounds(n)[1] < d
            and n.get('text') != '..']


def select_pane(pane):
    a,b,c,d = bounds(find(rid='listViewPane'+str(pane)))
    adb('shell','input','tap',(a+c)//2,d-50)
    time.sleep(.4)


def menu(text):
    # A fresh archive bind can temporarily omit the toolbar from accessibility.
    node=wait_for(lambda:find(desc='More',actionable=True))
    a,b,c,d=bounds(node); adb('shell','input','tap',(a+c)//2,(b+d)//2)
    node=wait_for(lambda:find(text=text,actionable=True))
    a,b,c,d=bounds(node); adb('shell','input','tap',(a+c)//2,(b+d)//2)
    time.sleep(.35)


def filter_by(value):
    menu('Filter')
    layout=tree()
    fields=[n for n in layout.iter('node') if n.get('class')=='android.widget.EditText']
    assert len(fields)==1, 'Expected one active filter input'
    a,b,c,d=bounds(fields[0]); adb('shell','input','tap',(a+c)//2,(b+d)//2)
    enter(value); hide_keyboard(); time.sleep(.5)


def query():
    fields=[n for n in tree().iter('node') if n.get('class')=='android.widget.EditText']
    assert len(fields)==1, 'Filter input missing'
    return fields[0].get('text')


def close_editor():
    hide_keyboard(); adb('shell','input','keyevent',4)


def editor_text():
    return find(rid='editor').get('text','')


def add_marker(marker):
    tap(rid='editor'); adb('shell','input','keyevent','KEYCODE_MOVE_END')
    adb('shell','input','text',marker); hide_keyboard()
    wait_for(lambda: marker in editor_text())
    assert find(rid='btn_save').get('enabled')=='true'


def save():
    tap(rid='btn_save'); wait_for(lambda: find(rid='btn_save').get('enabled')=='false')


def filtered_editor_return():
    r.reset(); tap(text='filters',pane=1); tap(rid='syncPaneButton')
    filter_by('keep')
    assert names(1)==['keep-first.txt','keep-second.txt']
    tap(text='keep-first.txt',pane=1); wait_for(lambda: find(rid='editor'))
    close_editor(); wait_for(lambda: find(rid='listViewPane1'))
    assert query()=='keep'
    assert names(1)==['keep-first.txt','keep-second.txt'], names(1)
    assert 'other.txt' in names(2)


def independent_pane_filters():
    r.reset(); tap(text='filters',pane=1); tap(rid='syncPaneButton')
    filter_by('keep'); select_pane(2); filter_by('other')
    assert names(1)==['keep-first.txt','keep-second.txt'], names(1)
    assert names(2)==['other.txt'], names(2)
    select_pane(1); assert query()=='keep', query()
    menu('Filter'); assert 'other.txt' in names(1)
    select_pane(2); assert query()=='other'
    assert names(2)==['other.txt']


def archive_filter_refresh():
    r.reset(); tap(text='sort-a.zip',pane=1); tap(text='docs',pane=1)
    filter_by('b.txt'); menu('Refresh'); time.sleep(.7)
    assert names(1)==['b.txt'], names(1)
    find(text='..',pane=1); assert query()=='b.txt'


def archive_folder_sort():
    r.reset(); tap(text='sort-a.zip',pane=1); tap(text='docs',pane=1)
    menu('Sort'); tap(text='Size'); tap(text='Only for this folder'); tap(text='Apply')
    time.sleep(.8); assert names(1)==['b.txt','c.txt','a.txt'], names(1)
    tap(rid='upButton'); tap(text='docs',pane=1)
    assert names(1)==['b.txt','c.txt','a.txt'], names(1)
    tap(rid='upButton'); tap(rid='upButton')
    tap(text='sort-b.zip',pane=1); tap(text='docs',pane=1)
    assert names(1)==['a.txt','b.txt','c.txt'], names(1)


def reload_confirmation():
    r.reset(); tap(text='note.txt',pane=1); wait_for(lambda: find(rid='editor'))
    original=editor_text(); add_marker('UNSAVEDRELOAD')
    tap(rid='btn_file'); tap(text='File'); tap(text='Reload file')
    find(text='Reloading will discard unsaved changes in this file.')
    tap(text='Cancel'); assert 'UNSAVEDRELOAD' in editor_text()
    assert r.read('note.txt')==b'Owned reload fixture\n'
    tap(rid='btn_file'); tap(text='File'); tap(text='Reload file'); tap(text='Reload file')
    wait_for(lambda: editor_text()==original)
    assert find(rid='btn_save').get('enabled')=='false'
    close_editor()


def archive_bytes(name):
    target=ARTIFACTS/(name.replace('/','-'))
    target.write_bytes(r.read(name)); return target


def confirm_zip_save():
    wait_for(lambda: find(text='Yes'))
    layout=tree()
    assert not any(n.get('resource-id','').endswith(':id/autosign') for n in layout.iter('node')), 'Ordinary ZIP must not offer APK signing'
    snapshot('archive-batch-confirmation')
    tap(text='Yes'); wait_for(lambda: find(rid='listViewPane1'))
    time.sleep(1)


def archive_batch_save(rotate=False):
    name='rotate.zip' if rotate else 'batch.zip'
    r.reset(); tap(text=name,pane=1); tap(text='docs',pane=1)
    original=r.read(name)
    tap(text='first.txt',pane=1); wait_for(lambda: find(rid='editor'))
    add_marker('FIRSTSAVED'); save(); tap(rid='btn_minimize')
    wait_for(lambda: find(rid='listViewPane1'))
    tap(text='second.txt',pane=1); wait_for(lambda:'second owned fixture' in editor_text())
    add_marker('SECONDSAVED'); save()
    if rotate:
        adb('shell','settings','put','system','user_rotation',1); time.sleep(1)
        wait_for(lambda:'SECONDSAVED' in editor_text())
        adb('shell','settings','put','system','user_rotation',0); time.sleep(1)
        wait_for(lambda:'SECONDSAVED' in editor_text())
    close_editor(); confirm_zip_save()
    with zipfile.ZipFile(archive_bytes(name)) as z:
        assert b'FIRSTSAVED' in z.read('docs/first.txt'), 'First saved tab was lost'
        assert b'SECONDSAVED' in z.read('docs/second.txt'), 'Second saved tab was lost'
        assert z.read('keep.txt')==b'unchanged sibling'
    assert r.read(name+'.bak')==original, 'Backup must contain the original archive'


def save_and_exit_batch():
    r.reset(); tap(text='exit.zip',pane=1); tap(text='docs',pane=1)
    tap(text='first.txt',pane=1); wait_for(lambda: find(rid='editor'))
    add_marker('FIRSTEXIT'); tap(rid='btn_minimize')
    wait_for(lambda: find(rid='listViewPane1'))
    tap(text='second.txt',pane=1); wait_for(lambda:'second owned fixture' in editor_text())
    add_marker('SECONDEXIT'); close_editor(); tap(text='Save and Exit')
    confirm_zip_save()
    with zipfile.ZipFile(archive_bytes('exit.zip')) as z:
        assert b'FIRSTEXIT' in z.read('docs/first.txt')
        assert b'SECONDEXIT' in z.read('docs/second.txt')


def saved_archive_then_local(restart=False):
    name='restart.zip' if restart else 'local-resume.zip'
    r.reset(); tap(text=name,pane=1); tap(text='docs',pane=1)
    tap(text='first.txt',pane=1); wait_for(lambda: find(rid='editor'))
    add_marker('ARCHIVERESUME'); save(); tap(rid='btn_minimize')
    wait_for(lambda: find(rid='listViewPane1'))
    if restart:
        r.reset()
    else:
        tap(rid='upButton'); tap(rid='upButton')
    tap(text='note.txt',pane=1); wait_for(lambda:'Owned reload fixture' in editor_text())
    close_editor(); confirm_zip_save()
    with zipfile.ZipFile(archive_bytes(name)) as z:
        assert b'ARCHIVERESUME' in z.read('docs/first.txt')
        assert z.read('docs/second.txt')==b'second owned fixture\n'


def missing_original_archive():
    r.reset(); tap(text='missing.zip',pane=1); tap(text='docs',pane=1)
    tap(text='first.txt',pane=1); wait_for(lambda: find(rid='editor'))
    add_marker('MISSINGORIGINAL'); save()
    other=r.read('sort-b.zip')
    adb('shell','mv',r.ROOT+'/missing.zip',r.ROOT+'/missing-original.zip')
    close_editor(); wait_for(lambda: find(text='Error'))
    snapshot('missing-original-error')
    assert r.read('sort-b.zip')==other
    with zipfile.ZipFile(archive_bytes('missing-original.zip')) as z:
        assert z.read('docs/first.txt')==b'first owned fixture\n'
    tap(text='Close')


def uppercase_apk_autosign(v4=False):
    name='owned-v4.APK' if v4 else 'owned.APK'
    r.reset(); original=r.read(name)
    tap(text=name,pane=1); tap(text='View'); tap(text='qa-docs',pane=1)
    tap(text='owned.txt',pane=1); wait_for(lambda: find(rid='editor'))
    add_marker('OWNEDAPKSAVE'); save(); close_editor()
    wait_for(lambda: find(rid='autosign'))
    was_checked=find(rid='autosign').get('checked')=='true'
    if not was_checked: tap(rid='autosign')
    if v4:
        tap(rid='sign_settings'); current_v4=find(rid='v4').get('checked')=='true'
        # A failed earlier QA attempt can leave its temporary toggle enabled.
        previous_v4 = os.environ['HEXORA_RESTORE_V4']=='1' if 'HEXORA_RESTORE_V4' in os.environ else current_v4
        (ARTIFACTS/'v4-preference.json').write_text(json.dumps({'current':current_v4,'restore':previous_v4}),encoding='utf-8')
        if not current_v4: tap(rid='v4')
        tap(text='OK')
    tap(text='Yes'); wait_for(lambda: find(rid='listViewPane1')); time.sleep(2)
    with zipfile.ZipFile(archive_bytes(name)) as z:
        assert b'OWNEDAPKSAVE' in z.read('qa-docs/owned.txt')
        assert z.read('classes.dex')[:4]==b'dex\n'
    assert r.read(name+'.bak')==original
    if v4:
        signature=ARTIFACTS/'owned-v4.idsig'; signature.write_bytes(r.read('owned-v4.idsig'))
        assert signature.stat().st_size>0
        import subprocess
        verification=subprocess.run([r'E:\AndroidStudioSDK\build-tools\36.0.0\apksigner.bat','verify','--verbose',
                '--v4-signature-file',str(signature),str(ARTIFACTS/name)],capture_output=True,text=True,timeout=45)
        (ARTIFACTS/'owned-v4-signature-verification.txt').write_text(verification.stdout+verification.stderr,encoding='utf-8')
        assert verification.returncode==0 and 'v4): true' in verification.stdout, verification.stdout+verification.stderr
    if not was_checked or v4:
        tap(text='owned.txt',pane=1); wait_for(lambda: find(rid='editor'))
        add_marker('RESTOREPREF'); save(); close_editor(); wait_for(lambda: find(rid='autosign'))
        if not was_checked: tap(rid='autosign')
        if v4:
            tap(rid='sign_settings')
            if (find(rid='v4').get('checked')=='true') != previous_v4: tap(rid='v4')
            tap(text='OK')
        tap(text='Cancel')


if __name__=='__main__':
    local=ARTIFACTS/'fixtures'; (local/'filters').mkdir(parents=True,exist_ok=True)
    for name in ('keep-first.txt','keep-second.txt','other.txt'):
        (local/'filters'/name).write_bytes(b'Owned filter fixture\n')
    (local/'note.txt').write_bytes(b'Owned reload fixture\n')
    for name in ('batch.zip','rotate.zip','exit.zip','local-resume.zip','restart.zip','missing.zip'):
        with zipfile.ZipFile(local/name,'w') as z:
            z.writestr('docs/first.txt','first owned fixture\n')
            z.writestr('docs/second.txt','second owned fixture\n')
            z.writestr('keep.txt','unchanged sibling')
    import shutil
    shutil.copyfile(Path(__file__).resolve().parents[1]/'samples/plugin-sample/release/plugin-sample-release.apk',local/'owned.APK')
    with zipfile.ZipFile(local/'owned.APK','a') as z: z.writestr('qa-docs/owned.txt','Owned APK fixture\n')
    shutil.copyfile(local/'owned.APK',local/'owned-v4.APK')
    for name in ('sort-a.zip','sort-b.zip'):
        with zipfile.ZipFile(local/name,'w') as z:
            for entry,size in [('a',100),('b',1),('c',50)]: z.writestr('docs/'+entry+'.txt','x'*size)
    adb('push',str(local),r.ROOT)
    selected=set(sys.argv[1:])
    cases=[('filter-editor-return',filtered_editor_return),('filter-independent-panes',independent_pane_filters),
           ('archive-filter-refresh',archive_filter_refresh),('archive-folder-sort-isolation',archive_folder_sort),
           ('reload-protects-unsaved-buffer',reload_confirmation),('archive-all-saved-tabs',archive_batch_save),
           ('archive-all-saved-tabs-rotation',lambda:archive_batch_save(True)),('archive-save-and-exit-all-tabs',save_and_exit_batch),
           ('archive-save-resume-through-local-file',saved_archive_then_local),
           ('archive-save-resume-after-process-restart',lambda:saved_archive_then_local(True)),
           ('archive-missing-source-retains-original',missing_original_archive),('uppercase-apk-save-autosign',uppercase_apk_autosign),
           ('uppercase-apk-save-v4-sidecar',lambda:uppercase_apk_autosign(True))]
    for name,fn in cases:
        if not selected or name in selected: r.scenario(name,fn)
    (ARTIFACTS/'bug-sweep-logcat.txt').write_text(adb('logcat','-d'),encoding='utf-8')
    sys.exit(1 if any(row['status']=='FAIL' for row in r.RESULTS) else 0)
