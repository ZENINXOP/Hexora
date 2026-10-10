"""Additional UI coverage. Run after regression.py; uses its disposable fixture."""
import regression as r
from mumu_ui import *
import sys
import zipfile

saved=json.loads((ARTIFACTS/"results.json").read_text())
r.ROOT=saved["fixture"]
r.RESULTS=[]

def search():
    r.reset(); r.menu("Search"); tap(rid="searchQuery"); enter("notes.txt"); tap(text="Search")
    r.assert_text("notes.txt",1)
    assert "Search Results" in find(rid="currentFolderPath").get("text")

def content_search():
    r.reset(); r.menu("Find in files")
    node=next(n for n in tree().iter("node") if n.get("class","").endswith("EditText"))
    a,b,c,d=bounds(node); adb("shell","input","tap",(a+c)//2,(b+d)//2)
    enter("SearchNeedle"); tap(text="Search")
    r.assert_text("notes.txt :2")
    r.assert_text("SearchNeedle")

def editor_save():
    original = r.read("notes.txt")
    r.reset(); tap(text="notes.txt",pane=1); tap(rid="editor")
    adb("shell","input","keyevent","KEYCODE_MOVE_END")
    adb("shell","input","text","-saved-by-qa")
    tap(rid="btn_save")
    for _ in range(10):
        if b"saved-by-qa" in r.read("notes.txt"): break
        time.sleep(.2)
    assert b"saved-by-qa" in r.read("notes.txt")
    assert r.exists("notes.txt.bak")
    assert r.read("notes.txt.bak") == original
    tap(rid="btn_undo"); tap(rid="btn_redo")

def code_editor():
    r.reset(); tap(text="code.java",pane=1); find(rid="editor")

def xml_editor():
    r.reset(); tap(text="data.xml",pane=1)
    # XML offers an opening menu; select text if needed.
    try: tap(text="Text editor")
    except AssertionError: pass
    find(rid="editor")

def zip_folder_copy():
    r.pair(); tap(text="sample.zip",pane=1); r.context("docs","Copy ->")
    assert r.read("destination/docs/sub/beta.txt")==b"archive beta\n"
    assert r.exists("destination/docs/empty")

def zip_to_zip_folder():
    # A distinct destination catches a no-op that otherwise looks like a successful copy.
    target=ARTIFACTS/"empty-target.zip"
    with zipfile.ZipFile(target,"w") as z: z.writestr("sentinel.txt","destination")
    adb("push",str(target),r.ROOT+"/other.zip")
    r.reset(); tap(rid="syncPaneButton"); tap(text="other.zip",pane=2)
    tap(text="sample.zip",pane=1); r.context("docs","Copy ->"); tap(text="Add")
    time.sleep(.5)
    target=ARTIFACTS/"zip-to-zip-result.zip"; target.write_bytes(r.read("other.zip"))
    with zipfile.ZipFile(target) as z:
        assert z.read("docs/sub/beta.txt")==b"archive beta\n"
        assert "docs/empty/" in z.namelist()
        assert z.read("sentinel.txt")==b"destination"

def zip_folder_rename():
    r.reset(); tap(text="rename.zip",pane=1); r.context("docs","Rename")
    enter("renamed-docs"); tap(text="OK"); r.assert_text("renamed-docs",1)
    target=ARTIFACTS/"zip-folder-rename-result.zip"; target.write_bytes(r.read("rename.zip"))
    with zipfile.ZipFile(target) as z:
        assert "renamed-docs/sub/beta.txt" in z.namelist()
        assert "docs-other/keep.txt" in z.namelist()

def compress_7z():
    r.reset(); r.context("source","Compress")
    tap(desc="Show dropdown menu"); tap(text=".7z")
    tap(rid="filename_compress_edittext"); enter("created.7z")
    tap(text="Compress")
    for _ in range(20):
        if r.exists("created.7z"): break
        time.sleep(.3)
    assert r.exists("created.7z")
    r.reset(); r.context("created.7z","Extract")
    for _ in range(20):
        if r.exists("created/source/second/note.txt"): break
        time.sleep(.3)
    assert r.read("created/source/second/note.txt").strip()==b"second branch"

def compress_zip():
    r.reset(); r.context("source","Compress")
    tap(rid="filename_compress_edittext"); enter("created.zip"); tap(text="Compress")
    time.sleep(.5)
    target=ARTIFACTS/"created-result.zip"; target.write_bytes(r.read("created.zip"))
    with zipfile.ZipFile(target) as z:
        assert z.read("source/first/note.txt").strip()==b"first branch"
        assert "source/first/empty/" in z.namelist()

def bookmark():
    r.reset(); r.menu("Add to bookmarks")
    adb("shell","input","swipe",450,1550,450,1100,250)
    r.assert_text("Bookmarks"); r.assert_text("History")
    r.assert_text(r.ROOT.rsplit("/",1)[-1])

def themes():
    r.reset(); r.menu("Preferences"); tap(rid="lightThemeButton"); time.sleep(.6)
    snapshot("settings-light")
    find(rid="darkThemeButton"); tap(rid="darkThemeButton"); time.sleep(.6)
    snapshot("settings-dark")
    find(rid="blackThemeButton"); tap(rid="blackThemeButton"); time.sleep(.6)
    snapshot("settings-black")
    tap(rid="darkThemeButton")

def apk_view():
    r.reset(); tap(text="sample.apk",pane=1); tap(text="View")
    r.assert_text("AndroidManifest.xml",1); r.assert_text("classes.dex",1)
    tap(text="AndroidManifest.xml",pane=1)
    try: tap(text="Text editor")
    except AssertionError: pass
    find(rid="editor")

def dex_inspect():
    r.reset(); tap(text="classes.dex",pane=1)
    # The DEX action dialog is a real feature screen.
    root=tree(); assert any("Dex" in n.get("text","") or "DEX" in n.get("text","") for n in root.iter("node"))

def hex_editor():
    r.reset(); r.context("large.bin","Open with"); tap(text="Hex editor")
    assert "HexEditorActivity" in adb("shell","dumpsys","activity","activities")

def checksum():
    r.reset(); r.context("notes.txt","Checksums")
    root=tree(); assert any("SHA" in n.get("text","") for n in root.iter("node"))

def multi_selection():
    r.pair(); tap(text="source",pane=1); r.menu("Select all")
    find(desc="Invert Selection"); tap(desc="Invert Selection")
    tap(desc="Select all")
    r.context("first","Copy ->")
    assert r.read("destination/first/note.txt").strip()==b"first branch"
    assert r.read("destination/second/note.txt").strip()==b"second branch"

SCENARIOS={"search":search,"content-search":content_search,"editor-save":editor_save,
 "java-highlighting":code_editor,"xml-editor":xml_editor,"zip-folder-copy":zip_folder_copy,
 "zip-to-zip-folder":zip_to_zip_folder,"zip-folder-rename":zip_folder_rename,
 "7z-create-extract":compress_7z,"zip-create":compress_zip,"bookmarks-history":bookmark,
 "light-dark-black-themes":themes,"apk-manifest-view":apk_view,"dex-inspection":dex_inspect,
 "hex-editor":hex_editor,"checksums":checksum,"multi-selection":multi_selection}

if __name__=="__main__":
    for name in sys.argv[1:] or SCENARIOS:
        r.scenario(name,SCENARIOS[name])
    (ARTIFACTS/"extended-results.json").write_text(json.dumps({"fixture":r.ROOT,"results":r.RESULTS},indent=2))
    (ARTIFACTS/"extended-logcat.txt").write_text(adb("logcat","-d"),encoding="utf-8")
    sys.exit(1 if any(x["status"]=="FAIL" for x in r.RESULTS) else 0)
