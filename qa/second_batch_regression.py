"""Owned clipboard, archive-search and DEX-package native checks. OpenAI Codex."""
from first_batch_regression import *

r.ROOT='/sdcard/Download/Hexora-Second-Batch-QA-'+time.strftime('%Y%m%d-%H%M%S')

def menu(text):
    tap(desc='More')
    for _ in range(8):
        try: tap(text=text); return
        except AssertionError:
            layout=tree()
            scrolls=[n for n in layout.iter('node') if n.get('scrollable')=='true']
            if not scrolls: raise
            a,b,c,d=bounds(scrolls[-1]); adb('shell','input','swipe',(a+c)//2,int(b+(d-b)*.8),(a+c)//2,int(b+(d-b)*.25),350)
    raise AssertionError('Menu item unavailable: '+text)
r.menu=menu

def menu_enabled(text):
    layout=tree(); node=find(text=text,root=layout)
    parents={child:parent for parent in layout.iter() for child in parent}
    while node is not None:
        if node.get('enabled')=='false': return False
        node=parents.get(node)
    return True

def prepare_second():
    local=ARTIFACTS/'fixtures'; local.mkdir(exist_ok=True)
    for folder in ['source/folder/empty','destination','cut-source','cut-destination','self/child','same','missing']:
        (local/folder).mkdir(parents=True,exist_ok=True)
    for name,content in {'source/note.txt':'CLIPBOARD_COPY\n','source/folder/nested.txt':'NESTED_OK\n',
                         'destination/note.txt':'ORIGINAL_DEST\n','cut-source/a.txt':'CUT_A\n','cut-source/b.txt':'CUT_B\n',
                         'self/keep.txt':'SELF_RETAINED\n','same/note.txt':'SAME_DIR\n','missing/gone.txt':'MISSING\n'}.items():
        (local/name).write_text(content,newline='\n')
    for main,args in [('io.github.abdurazaaqmohammed.domain.editor.DexClassActionsTest',['generate',str(local)]),
                      ('io.github.abdurazaaqmohammed.domain.files.ArchiveSearchTest',[str(local)]),
                      ('io.github.abdurazaaqmohammed.domain.files.ArchivePasswordTest',[str(local)])]:
        result=subprocess.run([JAVA,'-cp',CP,main,*args],capture_output=True,timeout=40)
        assert result.returncode==0,result.stderr
    adb('push',str(local),r.ROOT)
    # adb push does not materialize empty fixture directories on this emulator.
    adb('shell','mkdir','-p',r.ROOT+'/cut-destination',r.ROOT+'/self/child',r.ROOT+'/source/folder/empty')

def goto(path):
    launch(r.ROOT+('/'+path if path else ''))
    wait(lambda:find(rid='currentFolderPath').get('text')==r.ROOT+('/'+path if path else ''))

def capture(name,cut=False):
    tap(text=name,pane=1,long=True); dialog_tap('Cut' if cut else 'Copy to clipboard')

def paste(): r.menu('Paste')
def read(path): return adb('exec-out','cat',r.ROOT+'/'+path,binary=True)
def exists(path): return adb('shell','sh','-c',shlex.quote('test -e '+shlex.quote(r.ROOT+'/'+path)+' && echo yes || echo no'))=='yes'

def clipboard_copy():
    goto('source'); capture('note.txt')
    tap(rid='upButton'); tap(text='destination',pane=1); paste()
    wait(lambda:find(text='note_1.txt',pane=1))
    assert read('destination/note.txt')==b'ORIGINAL_DEST\n'
    assert read('destination/note_1.txt')==read('source/note.txt')==b'CLIPBOARD_COPY\n'
    goto('source'); capture('folder'); tap(rid='upButton'); tap(text='destination',pane=1); paste()
    wait(lambda:exists('destination/folder/nested.txt'))
    assert read('destination/folder/nested.txt')==b'NESTED_OK\n' and exists('destination/folder/empty')
    snapshot('clipboard-copy-preserves-collision-and-tree')

def clipboard_cut():
    goto('cut-source'); r.menu('Select all'); capture('a.txt',True)
    tap(rid='upButton'); tap(text='cut-destination',pane=1); paste()
    wait(lambda:find(text='a.txt',pane=1)); find(text='b.txt',pane=1)
    assert read('cut-destination/a.txt')==b'CUT_A\n' and read('cut-destination/b.txt')==b'CUT_B\n'
    assert not exists('cut-source/a.txt') and not exists('cut-source/b.txt')
    assert find(rid='currentFolderPath').get('text')==r.ROOT+'/cut-destination'
    find(text='Download',pane=2) # Paste must not navigate the other pane.
    tap(desc='More'); assert not menu_enabled('Paste'); adb('shell','input','keyevent',4)
    snapshot('clipboard-cut-multiselect-consumed')

def clipboard_same():
    goto('same'); capture('note.txt'); paste(); wait(lambda:find(rid='m_et_edittext'))
    tap_node(fields()[0]); edit_value('another.txt'); hide_keyboard(); tap(text='OK')
    wait(lambda:find(text='another.txt',pane=1)); assert read('same/another.txt')==read('same/note.txt')

def clipboard_self():
    goto(''); capture('self',True); tap(text='self',pane=1); tap(text='child',pane=1); paste()
    wait(lambda:'cannot be copied or moved into itself' in console_text()); tap(text='Close')
    assert read('self/keep.txt')==b'SELF_RETAINED\n' and not exists('self/child/self')
    tap(desc='More'); assert menu_enabled('Paste'); adb('shell','input','keyevent',4)

def clipboard_missing():
    goto('missing'); capture('gone.txt',True)
    adb('shell','rm',r.ROOT+'/missing/gone.txt'); tap(rid='upButton'); tap(text='destination',pane=1); paste()
    wait(lambda:'Clipboard source no longer exists' in console_text()); tap(text='Close')
    assert not exists('destination/gone.txt')

def clipboard_clear():
    goto('source'); capture('note.txt'); r.menu('Clear file clipboard')
    tap(desc='More'); assert not menu_enabled('Paste'); adb('shell','input','keyevent',4)

def archive_open(name='search.zip'):
    goto(''); tap(text=name,pane=1)
    if name=='aes.zip': wait(lambda:find(text='Archive password')); password('secret')
    wait(lambda:find(text='nested',pane=1))

def find_archive(query,regex=False):
    r.menu('Find in files'); tap_node(fields()[0]); edit_value(query); hide_keyboard()
    if regex: tap(text='Use regex')
    tap(text='Search')

def search_names():
    archive_open(); r.menu('Search'); tap(rid='searchQuery'); edit_value('Note'); hide_keyboard()
    tap(rid='searchSubfolders'); tap(text='Search')
    wait(lambda:find(text='nested/Note.txt')); snapshot('archive-name-results')
    tap(text='nested/Note.txt'); wait(lambda:'ARCHIVE_NEEDLE' in find(rid='editor').get('text',''))
    adb('shell','input','keyevent',4)
    # Implicit directory results must open the actual archive directory.
    r.menu('Search'); tap(rid='searchQuery'); edit_value('nested'); hide_keyboard(); tap(text='Search')
    wait(lambda:find(text='nested/')); tap(text='nested/'); wait(lambda:find(text='Note.txt',pane=1))

def content_search_edit():
    archive_open(); tap(text='nested',pane=1); before=read('search.zip')
    find_archive('ARCHIVE_NEEDLE')
    node=wait(lambda:next((n for n in tree().iter('node') if n.get('text','').startswith('nested/Note.txt :1')),None))
    snapshot('archive-content-results'); tap_node(node)
    wait(lambda:'ARCHIVE_NEEDLE' in find(rid='editor').get('text',''))
    tap(rid='editor'); adb('shell','input','keyevent','KEYCODE_MOVE_END'); adb('shell','input','text','SEARCH_EDIT')
    tap(rid='btn_save'); wait(lambda:find(rid='btn_save').get('enabled')=='false'); hide_keyboard()
    adb('shell','input','keyevent',4); wait(lambda:find(text='File modified')); tap(text='Yes')
    wait(lambda:find(text='Note.txt',pane=1))
    assert read('search.zip.bak')==before
    data=zipfile.ZipFile(__import__('io').BytesIO(read('search.zip'))).read('nested/Note.txt')
    assert b'SEARCH_EDIT' in data

def encrypted_search():
    archive_open('aes.zip'); find_archive('ENCRYPTED_OK')
    node=wait(lambda:next((n for n in tree().iter('node') if n.get('text','').startswith('nested/secret.txt :1')),None))
    tap_node(node); wait(lambda:'ENCRYPTED_OK' in find(rid='editor').get('text','')); snapshot('encrypted-archive-search')

def invalid_regex():
    archive_open(); find_archive('[',True)
    wait(lambda:find(text='nested',pane=1)); assert 'ArchiveSearch' not in console_text()
    find_archive('^ROOT_TEXT$',True)
    wait(lambda:find(text='root.txt :1\nROOT_TEXT')); tap(text='Cancel')

def package_rename():
    goto(''); tap(text='classes.dex',pane=1); wait(lambda:find(text='qa.batch'))
    tap(text='qa.batch',long=True); tap(text='Rename'); wait(lambda:find(text='Rename package'))
    tap_node(fields()[0]); edit_value('qa.changed'); hide_keyboard(); tap(text='OK')
    wait(lambda:find(text='qa.changed')); tap(text='qa.changed'); tap(text='Holder')
    wait(lambda:'[[Lqa/changed/Base;' in find(rid='editor').get('text',''))
    assert '"Lqa/batch/Base;"' in find(rid='editor').get('text','')
    snapshot('dex-package-renamed-references'); adb('shell','input','keyevent',4)
    tap(text='History'); tap(text='qa.changed'); tap(text='Base',long=True); tap(text='Compare the difference')
    wait(lambda:find(text='Compare Text'))
    wait(lambda:'Lqa/batch/Base;' in console_text() and 'Lqa/changed/Base;' in console_text())
    snapshot('dex-package-original-diff'); adb('shell','input','keyevent',4)
    adb('shell','input','keyevent',4); wait(lambda:find(text='Save and Exit')); tap(text='Save and Exit')
    wait(lambda:find(rid='currentFolderPath'))
    result=ARTIFACTS/'package-result.dex'; result.write_bytes(read('classes.dex'))
    inspected=subprocess.run([JAVA,'-cp',CP,'io.github.abdurazaaqmohammed.domain.editor.DexClassActionsTest','inspect',str(result)],capture_output=True,text=True,timeout=30)
    assert inspected.returncode==0 and 'Lqa/changed/Base;' in inspected.stdout and 'items:[[Lqa/changed/Base;' in inspected.stdout,inspected.stdout+inspected.stderr
    assert 'Lqa/batch/Holder;' not in inspected.stdout
    assert read('classes.dex.bak')==(ARTIFACTS/'fixtures/classes.dex').read_bytes()
    (ARTIFACTS/'package-inspection.txt').write_text(inspected.stdout)

def dark_search_clipboard():
    previous=adb('exec-out','run-as',PACKAGE,'cat',f'shared_prefs/{PACKAGE}_preferences.xml',binary=True)
    try:
        goto(''); r.menu('Preferences'); tap(rid='darkThemeButton')
        archive_open(); find_archive('needle')
        wait(lambda:any('nested/Note.txt :' in n.get('text','') for n in tree().iter('node')))
        snapshot('dark-archive-search-results'); tap(text='Cancel')
        goto('source'); tap(text='note.txt',pane=1,long=True); snapshot('dark-clipboard-menu'); tap(text='Copy to clipboard')
    finally:
        adb('shell','am','force-stop',PACKAGE); write_preferences(previous); goto('')

if __name__=='__main__':
    prepare_second()
    cases=[('clipboard-copy-collision-folder',clipboard_copy),('clipboard-cut-multiple',clipboard_cut),
           ('clipboard-same-folder-copy',clipboard_same),('clipboard-self-destination',clipboard_self),
           ('clipboard-missing-source',clipboard_missing),('clipboard-clear',clipboard_clear),
           ('archive-name-search-open',search_names),('archive-content-search-edit',content_search_edit),
           ('encrypted-archive-search',encrypted_search),('archive-regex-validation',invalid_regex),
           ('dex-package-rename-diff-save',package_rename),('dark-search-clipboard',dark_search_clipboard)]
    selected=set(sys.argv[1:])
    for name,fn in cases:
        if not selected or name in selected: r.scenario(name,fn)
    sys.exit(1 if any(row['status']=='FAIL' for row in r.RESULTS) else 0)
