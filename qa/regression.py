"""Real MuMu interactions with on-disk assertions and screenshots per scenario.

Run from project root: py -3 qa/regression.py [scenario-name ...]
Every run uses a new disposable directory. Failures remain in results.json.
"""
from mumu_ui import *
import hashlib
import sys
import traceback
import zipfile

ROOT = "/sdcard/Download/Hexora-QA-" + time.strftime("%Y%m%d-%H%M%S")
RESULTS = []

def exists(path):
    return adb("shell", "test", "-e", ROOT + "/" + path, ";", "echo", "$?").strip() == "0"

def read(path):
    return adb("exec-out", "cat", ROOT + "/" + path, binary=True)

def assert_text(text, pane=None):
    return find_visible(text=text, pane=pane)

def dismiss():
    adb("shell", "input", "keyevent", 4)
    time.sleep(.25)

def menu(text):
    tap(desc="More"); tap(text=text)

def reset():
    launch(ROOT)
    for _ in range(4):
        try:
            if find(rid="pane1Path").get("text") == ROOT: break
        except AssertionError: pass
        time.sleep(.5)
    else: raise AssertionError("Launch did not display the requested fixture directory")

def pair():
    reset()
    tap(rid="syncPaneButton")
    tap(text="destination",pane=2)

def context(name, action, pane=1):
    tap(text=name,pane=pane,long=True)
    tap(text=action)

def scenario(name, fn):
    start=time.monotonic()
    try:
        fn()
        status="PASS"; detail=""
    except Exception as e:
        status="FAIL"; detail=str(e)
        traceback.print_exc()
    try: snapshot(name)
    except Exception as e: detail += " | Screenshot: " + str(e)
    result={"scenario":name,"status":status,"detail":detail,"seconds":round(time.monotonic()-start,2)}
    RESULTS.append(result)
    (ARTIFACTS/"results.json").write_text(json.dumps({"fixture":ROOT,"results":RESULTS},indent=2))
    print("RESULT",json.dumps(result),flush=True)

def navigation():
    reset()
    assert_text(ROOT)
    tap(text="source",pane=1); assert_text("first",1)
    tap(rid="backButton"); assert_text("source",1)
    tap(rid="forwardButton"); assert_text("first",1)
    tap(rid="upButton"); assert_text("source",1)
    tap(rid="syncPaneButton"); assert_text("source",2)
    tap(text="empty",pane=2); assert_text("Empty folder",2)
    assert find(rid="currentFolderPath").get("text").endswith("/empty")

def folder_copy():
    pair(); context("source","Copy ->")
    assert read("destination/source/first/note.txt").strip()==b"first branch"
    assert read("destination/source/second/note.txt").strip()==b"second branch"
    assert exists("destination/source/first/empty")
    assert not exists("destination/source/note.txt")
    assert_text("source",2)

def large_copy():
    pair(); context("large.bin","Copy ->")
    assert hashlib.sha256(read("large.bin")).digest()==hashlib.sha256(read("destination/large.bin")).digest()

def file_move():
    pair(); context("move-me.txt","Move ->")
    assert not exists("move-me.txt")
    assert read("destination/move-me.txt").strip()==b"move fixture"

def rename():
    reset(); context("rename-me.txt","Rename")
    enter("renamed.txt"); tap(text="OK")
    assert exists("renamed.txt") and not exists("rename-me.txt")
    assert read("renamed.txt").strip()==b"rename fixture"

def rename_collision():
    reset(); context("notes.txt","Rename")
    enter("code.java"); tap(text="OK")
    assert find(rid="m_et_edittext").get("text")=="code.java"
    assert read("notes.txt").startswith(b"Hexora")
    dismiss(); dismiss()

def new_entries():
    reset(); tap(rid="addButton"); tap(rid="m_et_edittext"); enter("created.txt"); tap(text="File")
    assert exists("created.txt")
    tap(rid="addButton"); tap(rid="m_et_edittext"); enter("created-folder"); tap(text="Folder")
    assert exists("created-folder")

def delete_confirm_cancel():
    reset(); context("delete-me.txt","Delete"); tap(text="Cancel")
    assert exists("delete-me.txt")
    context("delete-me.txt","Delete"); tap(text="Yes")
    assert not exists("delete-me.txt")

def sort():
    reset(); menu("Sort"); tap(text="Size"); tap(text="Apply")
    root=tree()
    assert any(n.get("resource-id","").endswith(":id/fileName") for n in root.iter("node"))
    menu("Sort"); tap(text="Name"); tap(text="Apply")

def filter_files():
    reset(); menu("Filter")
    adb("shell","input","text","notes")
    root=tree()
    names=[n.get("text") for n in root.iter("node") if n.get("resource-id","").endswith(":id/fileName") and bounds(n)[0]<450]
    assert "notes.txt" in names and "code.java" not in names
    menu("Filter"); assert_text("code.java",1)

def zip_navigation():
    reset(); tap(text="sample.zip",pane=1)
    assert_text("docs",1); tap(text="docs",pane=1)
    assert_text("alpha.txt",1); tap(text="sub",pane=1); assert_text("beta.txt",1)
    tap(text="..",pane=1); assert_text("alpha.txt",1)
    tap(text="..",pane=1); assert_text("top.txt",1)
    tap(text="..",pane=1); assert_text("sample.zip",1)

def zip_nested_rename():
    reset(); tap(text="rename.zip",pane=1); tap(text="docs",pane=1)
    context("alpha.txt","Rename"); enter("renamed.txt"); tap(text="OK")
    assert_text("renamed.txt",1)
    path=ARTIFACTS/"renamed-result.zip"; path.write_bytes(read("rename.zip"))
    with zipfile.ZipFile(path) as z:
        assert z.read("docs/renamed.txt")==b"archive alpha\n"
        assert z.read("docs-other/keep.txt")==b"unrelated sibling\n"

def zip_move_single():
    pair(); tap(text="move.zip",pane=1); tap(text="docs",pane=1)
    context("alpha.txt","Move ->")
    assert read("destination/alpha.txt")==b"archive alpha\n"
    path=ARTIFACTS/"moved-result.zip"; path.write_bytes(read("move.zip"))
    with zipfile.ZipFile(path) as z: assert "docs/alpha.txt" not in z.namelist()

def extract_zip():
    reset(); context("sample.zip","Extract ZIP")
    time.sleep(.7)
    assert read("sample/docs/sub/beta.txt")==b"archive beta\n"
    assert exists("sample/docs/empty")

def extract_tar():
    reset(); context("sample.tar","Extract")
    assert read("sample_1/source/first/note.txt").strip()==b"first branch" if exists("sample_1") else read("sample/source/first/note.txt").strip()==b"first branch"

def text_editor():
    reset(); tap(text="notes.txt",pane=1)
    root=tree()
    assert any("TextEditorActivity" in line for line in adb("shell","dumpsys","activity","activities").splitlines())
    assert any(n.get("class","").endswith("CodeEditor") for n in root.iter("node"))

def apk_inspect():
    reset(); tap(text="sample.apk",pane=1)
    assert_text("MP Sample Plugin")
    assert_text("View")

def properties():
    reset(); context("notes.txt","Properties")
    assert_text("notes.txt")
    assert_text(str(len(read("notes.txt")))+" B")

def many_files():
    reset(); adb("shell","dumpsys","gfxinfo",PACKAGE,"reset")
    tap(text="many",pane=1)
    assert "2500" in find(rid="folderCount").get("text")
    for _ in range(8): adb("shell","input","swipe",220,1300,220,300,180)
    assert_text("More" ) if False else find(desc="More")
    (ARTIFACTS/"many-files-gfxinfo.txt").write_text(adb("shell","dumpsys","gfxinfo",PACKAGE))
    menu("Sort"); assert_text("Apply")

def settings():
    reset(); menu("Preferences"); find(rid="themeToggleGroup")

SCENARIOS = {
 "navigation":navigation,"nested-folder-copy":folder_copy,"large-file-copy":large_copy,
 "file-move":file_move,"rename":rename,"rename-collision":rename_collision,
 "create-file-folder":new_entries,"delete-confirm-cancel":delete_confirm_cancel,
 "sorting":sort,"filter":filter_files,"zip-navigation":zip_navigation,
 "zip-nested-rename":zip_nested_rename,"zip-single-move":zip_move_single,
 "zip-extraction":extract_zip,"tar-extraction":extract_tar,"text-editor":text_editor,
 "apk-inspect":apk_inspect,"properties":properties,"2500-file-directory":many_files,"settings":settings,
}

if __name__=="__main__":
    from prepare_fixtures import prepare
    prepare()
    adb("push",str(ARTIFACTS/"fixtures"),ROOT)
    adb("shell","mkdir","-p",ROOT+"/destination",ROOT+"/empty",ROOT+"/source/first/empty")
    for name,contents in (("move-me.txt","move fixture"),("rename-me.txt","rename fixture"),("delete-me.txt","delete fixture")):
        f=ARTIFACTS/name; f.write_text(contents); adb("push",str(f),ROOT+"/"+name)
    for name in ("rename.zip","move.zip"):
        adb("push",str(ARTIFACTS/"fixtures/sample.zip"),ROOT+"/"+name)
    adb("logcat","-c")
    for name in sys.argv[1:] or SCENARIOS:
        scenario(name,SCENARIOS[name])
    (ARTIFACTS/"logcat.txt").write_text(adb("logcat","-d"),encoding="utf-8")
    print("FINISHED",ROOT,flush=True)
    sys.exit(1 if any(r["status"]=="FAIL" for r in RESULTS) else 0)
