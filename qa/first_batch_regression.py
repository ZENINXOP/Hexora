"""Owned native checks for DEX actions, encrypted ZIPs and script controls.
AI-assisted implementation/validation: OpenAI Codex. Run drivers sequentially.
"""
from mumu_ui import *
import regression as r
import shlex
import hashlib
import zipfile
import mumu_ui as ui

_adb = adb
def require_hexora():
    state = _adb('shell','dumpsys','activity','activities')
    resumed = [line for line in state.splitlines() if 'topResumedActivity=' in line or 'mResumedActivity:' in line]
    if not resumed or any(PACKAGE+'/' not in line for line in resumed):
        raise RuntimeError('Hexora lost foreground; no input or screenshots sent to another app')

def guarded_adb(*args, **kwargs):
    if args[:2] == ('shell','input') or args[:2] == ('exec-out','screencap'):
        require_hexora()
    return _adb(*args, **kwargs)

adb = ui.adb = r.adb = guarded_adb
_tree = tree
def guarded_tree():
    for attempt in range(3):
        require_hexora()
        try:
            layout = _tree()
            require_hexora()
            return layout
        except (ET.ParseError,RuntimeError) as error:
            if isinstance(error,RuntimeError) and 'No such file or directory' not in str(error): raise
            if attempt==2: raise
            time.sleep(.3) # Retry only a fresh missing/malformed UIAutomator dump.
tree = ui.tree = r.tree = guarded_tree

REPO=Path(__file__).resolve().parents[1]
SOURCE=REPO/'qa/artifacts/first-batch-20261012/fixtures'
CP=(REPO/'qa/artifacts/first-batch-20261012/test-classpath.txt').read_text()
JAVA=r'C:\Users\saksh\.jdks\jbr-17.0.14\bin\java.exe'
r.ROOT='/sdcard/Download/Hexora-First-Batch-QA-'+time.strftime('%Y%m%d-%H%M%S')

def wait(fn,seconds=25):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:
            value=fn()
            if value is not None and value is not False: return value
        except (AssertionError,RuntimeError): pass
        time.sleep(.3)
    raise AssertionError('Timed out waiting for native UI')

def hide_keyboard():
    if 'mInputShown=true' in adb('shell','dumpsys','input_method'): adb('shell','input','keyevent',4)

def edit_value(value):
    adb('shell','input','keyevent','KEYCODE_MOVE_END')
    adb('shell','input','keycombination','113','29')
    adb('shell','input','text',shlex.quote(value.replace(' ','%s')))

def fields(): return [n for n in tree().iter('node') if n.get('class','').endswith('EditText') and bounds(n)[2]>bounds(n)[0]]
def tap_node(n):
    a,b,c,d=bounds(n); adb('shell','input','tap',(a+c)//2,(b+d)//2)

def dialog_tap(text):
    for _ in range(8):
        try: tap(text=text); return
        except AssertionError:
            scrolls=[n for n in tree().iter('node') if n.get('class','').endswith('ScrollView') and bounds(n)[3]>bounds(n)[1]]
            n=max(scrolls,key=lambda n:(bounds(n)[2]-bounds(n)[0])*(bounds(n)[3]-bounds(n)[1]))
            a,b,c,d=bounds(n); adb('shell','input','swipe',c-15,int(b+(d-b)*.8),c-15,int(b+(d-b)*.2),350)
    raise AssertionError('Dialog action not visible: '+text)

def prepare():
    local=ARTIFACTS/'fixtures'; local.mkdir(exist_ok=True)
    for name in ('classes.dex','import.dex','Imported.smali','aes.zip','zipcrypto.zip'):
        (local/name).write_bytes((SOURCE/name).read_bytes())
    (local/'interactive.sh').write_text('printf "ARGS:%s|%s\\n" "$1" "$2"\necho LIVE_READY\nread reply\necho "INPUT:$reply"\nexit 6\n',newline='\n')
    (local/'stop.sh').write_text('echo $$ > parent.pid\nsleep 600 &\necho $! > child.pid\necho WAIT_READY\nwait\n',newline='\n')
    (local/'flood.sh').write_text('head -c 1000000 /dev/zero | tr "\\000" x\nprintf "\\nEND_FLOOD\\n"\n',newline='\n')
    (local/'timeout.sh').write_text('echo TIME_READY\nsleep 600\n',newline='\n')
    (local/'root.sh').write_text('echo "ROOT_UID:$(id -u)"\nread reply\necho "ROOT_REPLY:$reply"\n',newline='\n')
    (local/'eof.sh').write_text('echo EOF_READY\ncat\necho EOF_DONE\n',newline='\n')
    (local/'Candidate.smali').write_text('.class public Lqa/batch/Candidate;\n.super Ljava/lang/Object;\n',newline='\n')
    (local/'Duplicate.smali').write_text('.class public Lqa/batch/Base;\n.super Ljava/lang/Object;\n',newline='\n')
    with zipfile.ZipFile(local/'cancel.apks','w') as bundle:
        bundle.write(REPO/'samples/share-fixture/build/outputs/apk/debug/share-fixture-debug.apk','base.apk')
    key=local/'cancel-signing.jks'
    command=[str(Path(JAVA).with_name('keytool.exe')),'-genkeypair','-keystore',str(key),'-storetype','JKS',
             '-storepass','qaPassword123','-keypass','qaPassword123','-alias','qa','-keyalg','RSA','-keysize','2048',
             '-dname','CN=Hexora QA','-validity','3650']
    if not key.exists():
        result=subprocess.run(command,capture_output=True,timeout=40); assert result.returncode==0,result.stderr
    adb('push',str(local),r.ROOT)

def console_text(): return '\n'.join(n.get('text','') for n in tree().iter('node'))

def open_script(name,args='',timeout='120',root=False):
    r.reset(); tap(text=name,pane=1)
    if root: tap(text='Run as root')
    widgets=fields(); assert len(widgets)>=2
    tap_node(widgets[0]); edit_value(args); hide_keyboard()
    widgets=fields(); tap_node(widgets[1]); edit_value(timeout); hide_keyboard()
    tap(text='Execute'); wait(lambda:find(text='Script output'))

def interactive():
    open_script('interactive.sh','"hello world" "literal$(id)"','0')
    wait(lambda:'LIVE_READY' in console_text())
    assert 'ARGS:hello world|literal$(id)' in console_text()
    assert 'Exit code:' not in console_text()
    tap_node(fields()[0]); edit_value('REPLY_OK'); hide_keyboard(); tap(text='Send')
    wait(lambda:'Exit code: 6' in console_text())
    assert 'INPUT:REPLY_OK' in console_text(); snapshot('script-live-input'); tap(text='Close')

def script_stop():
    open_script('stop.sh',timeout='0')
    wait(lambda:'WAIT_READY' in console_text())
    child=int(adb('exec-out','cat',r.ROOT+'/child.pid').strip())
    assert any(len(line.split())>1 and line.split()[1]==str(child) for line in adb('shell','ps','-A').splitlines())
    tap(text='Stop'); wait(lambda:'Stopped' in console_text())
    wait(lambda:not any(len(line.split())>1 and line.split()[1]==str(child) for line in adb('shell','ps','-A').splitlines()))
    snapshot('script-stop-child-group'); tap(text='Close')

def script_flood():
    open_script('flood.sh')
    wait(lambda:'Exit code: 0' in console_text())
    text=console_text(); assert 'Earlier output omitted' in text and 'END_FLOOD' in text
    assert len(text)<140000, len(text); snapshot('script-bounded-output'); tap(text='Close')

def script_timeout():
    open_script('timeout.sh',timeout='1')
    wait(lambda:'Command timed out' in console_text()); snapshot('script-configurable-timeout'); tap(text='Close')

def password(value):
    tap_node(fields()[0]); edit_value(value); hide_keyboard(); tap(text='OK')

def encrypted_open(name='aes.zip',wrong=False):
    r.reset(); tap(text=name,pane=1); wait(lambda:find(text='Archive password'))
    if wrong:
        password('wrong'); wait(lambda:'Invalid password' in console_text()); find(text='Archive password')
    password('secret'); wait(lambda:find(text='nested',pane=1)); tap(text='nested',pane=1); tap(text='secret.txt',pane=1)
    wait(lambda:'ENCRYPTED_OK' in find(rid='editor').get('text',''))
    snapshot('encrypted-open-'+name); adb('shell','input','keyevent',4)

def encrypted_cancel():
    r.reset(); tap(text='aes.zip',pane=1); wait(lambda:find(text='Archive password')); tap(text='Cancel')
    assert find(rid='currentFolderPath').get('text')==r.ROOT

def encrypted_extract():
    r.reset(); tap(text='aes.zip',pane=1,long=True); tap(text='Extract ZIP'); wait(lambda:find(text='Archive password'))
    password('secret'); wait(lambda:find(text='aes',pane=1))
    assert adb('exec-out','cat',r.ROOT+'/aes/nested/secret.txt',binary=True)==b'ENCRYPTED_OK\n'
    snapshot('encrypted-extract')

def dex_open():
    r.reset(); tap(text='classes.dex',pane=1); wait(lambda:find(text='qa.batch')); tap(text='qa.batch')
    wait(lambda:find(text='Base'))

def dex_add():
    dex_open(); tap(text='Base',long=True); tap(text='Add')
    tap_node(fields()[0]); edit_value('qa/batch/Added'); hide_keyboard(); tap(text='OK')
    wait(lambda:find(text='qa.batch')); tap(text='qa.batch'); find(text='Added')
    tap(text='Added'); wait(lambda:'Lqa/batch/Added;' in find(rid='editor').get('text',''))
    snapshot('dex-added-class'); adb('shell','input','keyevent',4)

def dex_import():
    tap(text='Base',long=True); tap(text='Import'); tap(rid='file_picker_path'); enter(r.ROOT); tap(text='OK')
    tap(text='Imported.smali'); tap(text='Select')
    wait(lambda:find(text='qa.batch')); tap(text='qa.batch'); find(text='Imported')
    snapshot('dex-imported-smali')

def dex_rename():
    tap(text='Base',long=True); tap(text='Rename')
    tap_node(fields()[0]); edit_value('qa/batch/Renamed'); hide_keyboard(); tap(text='OK')
    wait(lambda:find(text='qa.batch')); tap(text='qa.batch'); find(text='Renamed')
    tap(text='Holder'); wait(lambda:'[[Lqa/batch/Renamed;' in find(rid='editor').get('text',''))
    assert 'literal:Ljava/lang/String; = "Lqa/batch/Base;"' in find(rid='editor').get('text','')
    snapshot('dex-renamed-type-references'); adb('shell','input','keyevent',4)

def dex_diff():
    tap(text='History')
    # The Modified page also contains Recently; select the class in its modified section.
    tap(text='qa.batch'); tap(text='Renamed',long=True); tap(text='Compare the difference')
    wait(lambda:find(text='Compare Text')); snapshot('dex-modified-class-diff')
    assert 'Lqa/batch/' in console_text(); adb('shell','input','keyevent',4)

def dex_save():
    adb('shell','input','keyevent',4); wait(lambda:find(text='Save and Exit')); tap(text='Save and Exit')
    wait(lambda:find(rid='currentFolderPath'))
    result=ARTIFACTS/'saved-classes.dex'; result.write_bytes(adb('exec-out','cat',r.ROOT+'/classes.dex',binary=True))
    output=subprocess.run([JAVA,'-cp',CP,'io.github.abdurazaaqmohammed.domain.editor.DexClassActionsTest','inspect',str(result)],capture_output=True,text=True,timeout=30)
    assert output.returncode==0, output.stderr
    assert all(value in output.stdout for value in ['Lqa/batch/Added;','Lqa/batch/Imported;','Lqa/batch/Renamed;','items:[[Lqa/batch/Renamed;']),output.stdout
    assert '\nLqa/batch/Base;\n' not in '\n'+output.stdout
    assert adb('exec-out','cat',r.ROOT+'/classes.dex.bak',binary=True)==(SOURCE/'classes.dex').read_bytes()
    (ARTIFACTS/'saved-dex-inspection.txt').write_text(output.stdout)

def write_preferences(value):
    command=[ADB,'-s',SERIAL,'shell','run-as',PACKAGE,'sh','-c',
             shlex.quote(f'cat > shared_prefs/{PACKAGE}_preferences.xml')]
    result=subprocess.run(command,input=value,capture_output=True,timeout=20)
    assert result.returncode==0, result.stderr

def signing_cancel():
    previous=adb('exec-out','run-as',PACKAGE,'cat',f'shared_prefs/{PACKAGE}_preferences.xml',binary=True)
    try:
        adb('shell','am','force-stop',PACKAGE)
        settings=ET.fromstring(previous)
        for tag,name,value in [('string','keyPath',r.ROOT+'/cancel-signing.jks'),('boolean','useBiometrics','false'),
                               ('boolean','autosign','true'),('boolean','v4','false')]:
            for existing in list(settings):
                if existing.get('name')==name: settings.remove(existing)
            node=ET.SubElement(settings,tag,{'name':name})
            if tag=='string': node.text=value
            else: node.set('value',value)
        write_preferences(ET.tostring(settings,encoding='utf-8',xml_declaration=True))
        r.reset(); tap(text='cancel.apks',pane=1); tap(text='Merge to single APK'); tap(text='Merge')
        wait(lambda:find(text='Enter password'))
        assert any(n.startswith('.hexora-merge-') for n in adb('shell','ls','-a',r.ROOT).splitlines())
        password('wrong'); find(text='Enter password')
        tap(text='Cancel')
        wait(lambda:not any(n.startswith('.hexora-merge-') for n in adb('shell','ls','-a',r.ROOT).splitlines()))
        assert 'cancel_merged.apk' not in adb('shell','ls',r.ROOT)
        snapshot('signing-cancel-cleans-merged-copy')
    finally:
        adb('shell','am','force-stop',PACKAGE); write_preferences(previous)

def root_console():
    open_script('root.sh',timeout='0',root=True)
    wait(lambda:'ROOT_UID:0' in console_text())
    tap_node(fields()[0]); edit_value('ROOT_OK'); hide_keyboard(); tap(text='Send')
    wait(lambda:'Exit code: 0' in console_text()); assert 'ROOT_REPLY:ROOT_OK' in console_text()
    snapshot('script-root-live-input'); tap(text='Close')

def encrypted_edit():
    r.reset(); tap(text='aes.zip',pane=1); wait(lambda:find(text='Archive password')); password('secret')
    wait(lambda:find(text='nested',pane=1)); tap(text='nested',pane=1); tap(text='secret.txt',pane=1)
    wait(lambda:'ENCRYPTED_OK' in find(rid='editor').get('text',''))
    tap(rid='editor'); adb('shell','input','keyevent','KEYCODE_MOVE_END'); adb('shell','input','text','EDITED')
    tap(rid='btn_save'); wait(lambda:find(rid='btn_save').get('enabled')=='false'); hide_keyboard()
    adb('shell','input','keyevent',4); wait(lambda:find(text='File modified')); tap(text='Yes')
    wait(lambda:find(text='secret.txt',pane=1)); tap(text='secret.txt',pane=1)
    wait(lambda:'EDITED' in find(rid='editor').get('text','')); snapshot('encrypted-edit-reopen')
    assert adb('exec-out','cat',r.ROOT+'/aes.zip.bak',binary=True)==(SOURCE/'aes.zip').read_bytes()
    adb('shell','input','keyevent',4)

def import_picker(names):
    tap(text='Base',long=True); tap(text='Import'); tap(rid='file_picker_path'); enter(r.ROOT); tap(text='OK')
    for name in names: tap(text=name)
    tap(text='Select')

def dark_controls():
    previous=adb('exec-out','run-as',PACKAGE,'cat',f'shared_prefs/{PACKAGE}_preferences.xml',binary=True)
    try:
        r.reset(); r.menu('Preferences'); tap(rid='darkThemeButton')
        open_script('eof.sh',timeout='0'); wait(lambda:'EOF_READY' in console_text())
        tap_node(fields()[0]); edit_value('EOF_INPUT'); hide_keyboard(); tap(text='Send')
        wait(lambda:'EOF_INPUT' in console_text()); tap(text='End input')
        wait(lambda:'Exit code: 0' in console_text()); assert 'EOF_DONE' in console_text()
        snapshot('dark-console-eof'); tap(text='Close')
        r.reset(); tap(text='aes.zip',pane=1); wait(lambda:find(text='Archive password'))
        snapshot('dark-archive-password'); tap(text='Cancel')
        dex_open(); tap(text='Base',long=True); tap(text='Add')
        wait(lambda:find(text='Add class')); snapshot('dark-dex-add'); tap(text='Cancel')
    finally:
        adb('shell','am','force-stop',PACKAGE); write_preferences(previous); r.reset()

def dex_binary_import():
    dex_open(); import_picker(['import.dex'])
    wait(lambda:find(text='qa.batch')); tap(text='qa.batch'); find(text='DexImported')
    adb('shell','input','keyevent',4); wait(lambda:find(text='Save and Exit')); tap(text='Save and Exit')
    wait(lambda:find(rid='currentFolderPath'))
    result=ARTIFACTS/'binary-import-result.dex'; result.write_bytes(adb('exec-out','cat',r.ROOT+'/classes.dex',binary=True))
    output=subprocess.run([JAVA,'-cp',CP,'io.github.abdurazaaqmohammed.domain.editor.DexClassActionsTest','inspect',str(result)],capture_output=True,text=True,timeout=30)
    assert output.returncode==0 and 'Lqa/batch/DexImported;' in output.stdout,output.stderr+output.stdout
    snapshot('dex-binary-import-saved')

def dex_import_collision():
    original=adb('exec-out','cat',r.ROOT+'/classes.dex',binary=True)
    dex_open(); import_picker(['Candidate.smali','Duplicate.smali'])
    wait(lambda:'Class already exists' in console_text()); tap(text='Close')
    find(text='Base'); find(text='Holder')
    assert 'Candidate' not in console_text()
    assert adb('exec-out','cat',r.ROOT+'/classes.dex',binary=True)==original
    snapshot('dex-collision-no-partial-import')

if __name__=='__main__':
    prepare()
    cases=[('script-live-input',interactive),('script-stop-child-group',script_stop),('script-bounded-output',script_flood),
           ('script-timeout',script_timeout),('encrypted-aes-open-wrong-password',lambda:encrypted_open(wrong=True)),
           ('encrypted-zipcrypto-open',lambda:encrypted_open('zipcrypto.zip')),('encrypted-cancel',encrypted_cancel),
           ('encrypted-extract',encrypted_extract),('dex-add',dex_add),('dex-import',dex_import),
           ('dex-rename',dex_rename),('dex-diff',dex_diff),('dex-save-reopen',dex_save)]
    cases.append(('signing-cancel-cleanup',signing_cancel))
    cases.extend([('script-root-input',root_console),('encrypted-edit-save-reopen',encrypted_edit),
                  ('dex-binary-import-saved',dex_binary_import),('dex-import-collision-atomic',dex_import_collision)])
    cases.append(('dark-controls-eof',dark_controls))
    selected=set(sys.argv[1:])
    for name,fn in cases:
        if not selected or name in selected: r.scenario(name,fn)
    sys.exit(1 if any(row['status']=='FAIL' for row in r.RESULTS) else 0)
