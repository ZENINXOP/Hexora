"""Owned URI-provider and native split-merging acceptance. AI-assisted: OpenAI Codex.

Build the opt-in share fixture with -PshareFixtures=true. No Telegram messages
or third-party application data are read or modified by this driver.
"""
from mumu_ui import *
import regression as r
import hashlib
import shlex
import shutil
import zipfile

REPO = Path(__file__).resolve().parents[1]
FIXTURE_PACKAGE = 'app.hexora.sharefixture'
FIXTURE_APK = REPO / 'samples/share-fixture/build/outputs/apk/debug/share-fixture-debug.apk'
r.ROOT = '/sdcard/Download/Hexora-Share-Merge-QA-' + time.strftime('%Y%m%d-%H%M%S')


def private_files():
    listing = adb('shell', 'run-as', PACKAGE, 'find', 'files', '-type', 'f')
    return {p for p in listing.splitlines() if p.startswith('files/Shared files/') and not '/.receiving-' in p}


def private_read(path):
    return adb('exec-out', 'run-as', PACKAGE, 'cat', path, binary=True)


def share(mode, expected_names, expected_bytes=None):
    before = private_files()
    print(adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', FIXTURE_PACKAGE + '/.MainActivity', '--es', 'mode', mode), flush=True)
    time.sleep(.6)
    layout = tree()
    assert not any(n.get('text', '').startswith('Could not locate this file') for n in layout.iter('node'))
    added = private_files() - before
    assert len(added) == len(expected_names), (mode, added)
    assert find(rid='currentFolderPath').get('text').endswith('/Shared files')
    for name in expected_names:
        matching = [p for p in added if p.rsplit('/',1)[-1].startswith(name.rsplit('.',1)[0]) and p.endswith('.' + name.rsplit('.',1)[1])]
        assert len(matching) == 1, (name, matching)
        if expected_bytes is not None: assert private_read(matching[0]) == expected_bytes
        find_visible(text=matching[0].rsplit('/',1)[-1], pane=1)
    snapshot('uri-' + mode)
    return added


def text_share(mode):
    added = share(mode, ['Grant note.txt'], b'URI_GRANT_OK\n')
    path = next(iter(added))
    tap(text=path.rsplit('/',1)[-1], pane=1)
    assert find(rid='editor').get('text') == 'URI_GRANT_OK\n'
    adb('shell', 'input', 'keyevent', 4)


def multi_share():
    added=share('multi',['Grant note.txt','Second note.txt'])
    assert {private_read(path) for path in added}=={b'URI_GRANT_OK\n',b'MULTI_GRANT_OK\n'}


def failed_share(mode):
    before = private_files()
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', FIXTURE_PACKAGE + '/.MainActivity', '--es', 'mode', mode)
    contents = '\n'.join(n.get('text', '') for n in tree().iter('node'))
    assert 'The sharing app did not allow this file to be read' in contents, contents
    assert 'not a local file:' not in contents
    assert private_files() == before
    snapshot('uri-' + mode)
    tap(text='Cancel')


def prepare():
    folder = ARTIFACTS / 'fixtures'
    (folder/'loose').mkdir(parents=True, exist_ok=True)
    split_src = ARTIFACTS / 'split-src'
    (split_src/'res/values-en').mkdir(parents=True, exist_ok=True)
    (split_src/'res/values-en/strings.xml').write_text('<resources><string name="split_probe">SPLIT_RESOURCE</string></resources>')
    manifest = split_src/'AndroidManifest.xml'
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.hexora.sharefixture" split="config.en" android:versionCode="1"><uses-sdk android:minSdkVersion="23" android:targetSdkVersion="28"/><application android:hasCode="false"/></manifest>')
    aapt = r'E:\AndroidStudioSDK\build-tools\36.0.0\aapt2.exe'
    platform = r'E:\AndroidStudioSDK\platforms\android-36\android.jar'
    compiled = split_src/'compiled.zip'
    split = folder/'loose/config.en.apk'
    for command in ([aapt,'compile','--dir',str(split_src/'res'),'-o',str(compiled)],
                    [aapt,'link','-o',str(split),'--manifest',str(manifest),'-I',platform,'--auto-add-overlay','--no-resource-removal',str(compiled)]):
        result = subprocess.run(command, capture_output=True, text=True, timeout=60)
        assert result.returncode == 0, result.stdout + result.stderr
    shutil.copyfile(FIXTURE_APK, folder/'loose/base.apk')
    for name, prefix in [('bundle.XAPK', 'nested/'), ('bundle-apks.apks', ''), ('bundle-apkm.apkm', '')]:
        with zipfile.ZipFile(folder/name, 'w') as archive:
            archive.write(FIXTURE_APK, prefix+'base.apk')
            archive.write(split, prefix+'config.en.apk')
            archive.writestr('manifest.json', '{"package_name":"app.hexora.sharefixture"}')
            archive.writestr('Android/obb/app.hexora.sharefixture/main.1.obb', b'OBB_MUST_REMAIN_SEPARATE')
    with zipfile.ZipFile(folder/'no-base.apks', 'w') as archive: archive.write(split, 'config.en.apk')
    (folder/'unrelated').mkdir(exist_ok=True)
    shutil.copyfile(FIXTURE_APK, folder/'unrelated/base.apk')
    shutil.copyfile(REPO/'app/build/outputs/apk/debug/app-debug.apk', folder/'unrelated/other.apk')
    adb('push', str(folder), r.ROOT)


def wait_view(text, seconds=40):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try: return find_visible(text=text, pane=1)
        except AssertionError:
            contents='\n'.join(n.get('text','') for n in tree().iter('node'))
            if 'java.io.IOException' in contents: raise AssertionError(contents)
            time.sleep(.3)
    raise AssertionError('Missing view: '+text)


def merge_bundle(name='bundle.XAPK', context_menu=False):
    r.reset()
    before=adb('shell','sha256sum',r.ROOT+'/'+name).split()[0]
    tap(text=name,pane=1,long=context_menu)
    tap(text='Merge to single APK')
    # Keep the actual automatic signing path enabled.
    if find(text='Device specs only').get('checked')=='true': tap(text='Device specs only')
    # On this landscape emulator the options scroll; the dialog button remains fixed.
    tap(text='Merge')
    output=name.rsplit('.',1)[0]+'_merged.apk'
    wait_view(output)
    assert adb('shell','sha256sum',r.ROOT+'/'+name).split()[0]==before
    destination=ARTIFACTS/output
    destination.write_bytes(adb('exec-out','cat',r.ROOT+'/'+output,binary=True))
    with zipfile.ZipFile(destination) as archive:
        names=archive.namelist()
        assert 'classes.dex' in names and 'resources.arsc' in names and 'AndroidManifest.xml' in names
        assert not any(n.endswith('.obb') or n.endswith('.apk') for n in names)
    signer=subprocess.run([r'E:\AndroidStudioSDK\build-tools\36.0.0\apksigner.bat','verify','--verbose',str(destination)],capture_output=True,text=True,timeout=30)
    assert signer.returncode==0, signer.stdout+signer.stderr
    snapshot('merge-'+name)
    return destination


def merge_loose():
    r.reset()
    tap(text='loose',pane=1,long=True)
    tap(text='Merge to single APK')
    if find(text='Device specs only').get('checked')=='true': tap(text='Device specs only')
    tap(text='Merge')
    # Loose inputs are staged privately; output defaults to Download/Hexora.
    wait_view('split-files_merged.apk')
    path=find(rid='currentFolderPath').get('text')+'/split-files_merged.apk'
    destination=ARTIFACTS/'loose-merged.apk'
    destination.write_bytes(adb('exec-out','cat',path,binary=True))
    assert destination.stat().st_size>0
    snapshot('merge-loose-folder')


def merge_selected_files():
    r.reset(); tap(text='loose',pane=1)
    a,b,c,d=bounds(find(text='base.apk',pane=1))
    pane_bounds=bounds(find(rid='listViewPane1'))
    y=(b+d)//2
    adb('shell','input','swipe',pane_bounds[0]+100,y,pane_bounds[0]+320,y,350)
    tap(text='config.en.apk',pane=1)
    tap(text='base.apk',pane=1,long=True)
    tap(text='Merge to single APK')
    find(text='base.apk (base)'); find(text='config.en.apk')
    output_dir='/sdcard/Download/Hexora'
    before=set(adb('shell','ls','-1',output_dir).splitlines())
    tap(text='Merge')
    end=time.monotonic()+40
    added=set()
    while time.monotonic()<end:
        added={n for n in set(adb('shell','ls','-1',output_dir).splitlines())-before if n.startswith('split-files_merged') and n.endswith('.apk')}
        if added: break
        time.sleep(.5)
    assert len(added)==1, added
    result=ARTIFACTS/'selection-merged.apk'
    result.write_bytes(adb('exec-out','cat',output_dir+'/'+next(iter(added)),binary=True))
    snapshot('merge-selected-loose-apks')


def unrelated_apps():
    r.reset(); tap(text='unrelated',pane=1,long=True); tap(text='Merge to single APK')
    if find(text='Device specs only').get('checked')=='true': tap(text='Device specs only')
    tap(text='Merge')
    contents='\n'.join(n.get('text','') for n in tree().iter('node'))
    assert 'Select splits of the same app and version.' in contents, contents
    snapshot('merge-unrelated-apps-error')


def merge_chosen_output():
    output_dir=r.ROOT+'/chosen-output'
    adb('shell','mkdir','-p',output_dir)
    r.reset(); tap(text='bundle-apkm.apkm',pane=1); tap(text='Merge to single APK')
    for _ in range(6):
        try:
            tap(text='Choose output folder')
            break
        except AssertionError:
            scrolls=[n for n in tree().iter('node') if n.get('class','').endswith('ScrollView') and len(bounds(n))==4]
            n=max(scrolls,key=lambda n:(bounds(n)[2]-bounds(n)[0])*(bounds(n)[3]-bounds(n)[1]))
            a,b,c,d=bounds(n)
            adb('shell','input','swipe',c-25,int(b+(d-b)*.8),c-25,int(b+(d-b)*.25),400)
    else: raise AssertionError('Output-folder chooser was inaccessible')
    tap(rid='file_picker_path')
    enter(output_dir); tap(text='OK'); tap(text='Select')
    tap(text='Merge')
    wait_view('bundle-apkm_merged.apk')
    assert find(rid='currentFolderPath').get('text')==output_dir
    destination=ARTIFACTS/'chosen-output-merged.apk'
    destination.write_bytes(adb('exec-out','cat',output_dir+'/bundle-apkm_merged.apk',binary=True))
    assert destination.stat().st_size>0
    snapshot('merge-chosen-output-folder')


def invalid_bundle():
    r.reset(); tap(text='no-base.apks',pane=1); tap(text='Merge to single APK'); tap(text='Merge')
    contents='\n'.join(n.get('text','') for n in tree().iter('node'))
    assert 'Select exactly one base APK' in contents, contents
    assert 'no-base_merged.apk' not in adb('shell','ls',r.ROOT)
    snapshot('merge-no-base-error')


def installed_resource(destination):
    # Replace only this disposable fixture; reinstallation of the provider follows.
    if 'package:'+FIXTURE_PACKAGE in adb('shell','pm','list','packages','--user','0',FIXTURE_PACKAGE).splitlines():
        adb('uninstall', FIXTURE_PACKAGE)
    try:
        print(adb('install','-t',str(destination)), flush=True)
        print(adb('shell','am','start','-W','-f','0x10008000','-n',FIXTURE_PACKAGE+'/.MainActivity'), flush=True)
        snapshot('merged-apk-resource-live')
        find(text='RESOURCE: SPLIT_RESOURCE')
        snapshot('merged-apk-installed-resource')
    finally:
        if 'package:'+FIXTURE_PACKAGE in adb('shell','pm','list','packages','--user','0',FIXTURE_PACKAGE).splitlines():
            adb('uninstall',FIXTURE_PACKAGE)
        print(adb('install','-t',str(FIXTURE_APK)), flush=True)


if __name__=='__main__':
    prepare()
    cases=[('uri-view-text',lambda:text_share('view')),('uri-send-text',lambda:text_share('send')),
           ('uri-clip-only',lambda:text_share('clip')),('uri-multiple',multi_share),
           ('uri-telegram-shaped-pdf',lambda:share('pdf',['277449322868.pdf'],b'%PDF-1.4\n% Owned Telegram-shaped grant fixture\n%%EOF\n')),
           ('uri-permission-denied',lambda:failed_share('denied')),('uri-missing-file',lambda:failed_share('missing')),
           ('merge-nested-xapk',lambda:merge_bundle()),('merge-apks-context',lambda:merge_bundle('bundle-apks.apks',True)),
           ('merge-apkm',lambda:merge_bundle('bundle-apkm.apkm')),
           ('merge-loose-folder',merge_loose),('merge-no-base',invalid_bundle),
           ('merge-selected-files',merge_selected_files),('merge-unrelated-apps',unrelated_apps),
           ('merge-chosen-output',merge_chosen_output),
           ('merge-install-split-resource',lambda:installed_resource(ARTIFACTS/'bundle_merged.apk'))]
    selected=set(sys.argv[1:])
    for name,fn in cases:
        if not selected or name in selected: r.scenario(name,fn)
    sys.exit(1 if any(x['status']=='FAIL' for x in r.RESULTS) else 0)
