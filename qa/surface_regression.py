"""Additional native screens and backup/hex editing, using disposable files only."""
import extended_regression as e
import regression as r
from mumu_ui import *
import hashlib
import math
import struct
import sys
import wave
import zipfile
import io

def seed():
    photo=Path(__file__).resolve().parents[1]/"docs/branding/hexora-icon.png"
    adb("push",str(photo),r.ROOT+"/photo.png")
    tone=ARTIFACTS/"tone.wav"
    with wave.open(str(tone),"wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
        w.writeframes(b"".join(struct.pack("<h",int(1000*math.sin(2*math.pi*440*i/16000))) for i in range(8000)))
    adb("push",str(tone),r.ROOT+"/tone.wav")

def open_activity(name):
    r.reset()
    tap(desc="menu")
    labels={"APKExtractorActivity":"Extract APK", "ToolsHubActivity":"Plugins", "WifiManagerActivity":"Wi-Fi Manager"}
    short=name.rsplit(".",1)[-1]
    if short=="StorageManagerActivity":
        tap(text="Internal storage",long=True); tap(text="Manage storage")
    else:
        for _ in range(8):
            try: tap(text=labels[short]); break
            except AssertionError:
                layout=tree(); w=max(bounds(n)[2] for n in layout.iter("node") if len(bounds(n))==4)
                h=max(bounds(n)[3] for n in layout.iter("node") if len(bounds(n))==4)
                adb("shell","input","swipe",int(w*.15),int(h*.75),int(w*.15),int(h*.4),400)
        else: raise AssertionError("Sidebar tool not found: "+labels[short])
    assert name.rsplit(".",1)[-1] in adb("shell","dumpsys","activity","activities")
    tree()

def backup_restore():
    name="restore.bak"; adb("shell","mkdir","-p",r.ROOT+"/"+name)
    p=ARTIFACTS/"README.bak"; p.write_bytes(b"previous version\n")
    current=ARTIFACTS/"README"; current.write_bytes(b"current version\n")
    adb("push",str(p),r.ROOT+"/"+name+"/README.bak")
    adb("push",str(current),r.ROOT+"/"+name+"/README")
    r.reset(); tap(text=name,pane=1); tap(text="README.bak",pane=1)
    assert find(rid="m_et_edittext").get("text")=="README"
    enter("RENAMED-README"); tap(text="Restore")
    assert r.read(name+"/RENAMED-README")==b"previous version\n"
    assert r.read(name+"/README")==b"current version\n"
    adb("push",str(p),r.ROOT+"/"+name+"/README.bak")
    r.reset(); tap(text=name,pane=1); tap(text="README.bak",pane=1); tap(text="Restore")
    assert r.read(name+"/README")==b"previous version\n"
    assert r.read(name+"/README.bak")==b"current version\n"

def hex_edit_save():
    p=ARTIFACTS/"small.bin"; original=bytes(range(16)); p.write_bytes(original)
    adb("push",str(p),r.ROOT+"/small.bin")
    r.reset(); r.context("small.bin","Open with"); tap(text="Hex editor")
    tap(text="A"); tap(text="B"); tap(desc="More"); tap(text="Save")
    assert r.read("small.bin")==b"\xab"+original[1:]
    assert r.read("small.bin.bak")==original

def image_viewer():
    r.reset(); tap(text="photo.png",pane=1)
    assert "ImageViewerActivity" in adb("shell","dumpsys","activity","activities")
    find(rid="pager"); find(rid="btnInfo"); tap(rid="btnInfo")
    r.assert_text("Image Properties")

def image_editor_screen():
    r.reset(); tap(text="photo.png",pane=1); tap(rid="btnEdit")
    assert "ImageEditActivity" in adb("shell","dumpsys","activity","activities")
    tree()

def audio_screen():
    r.reset(); tap(text="tone.wav",pane=1)
    find(desc="Play/Pause"); tap(desc="Open full player")
    assert "MediaPlayerActivity" in adb("shell","dumpsys","activity","activities")
    root=tree(); assert any("tone" in n.get("text","").lower() for n in root.iter("node"))
    find(rid="btnPlayPause"); tap(rid="btnPlayPause")

def checksums_match():
    expected=hashlib.sha256(r.read("notes.txt")).hexdigest()
    r.reset(); r.context("notes.txt","Checksums")
    assert any(expected in n.get("text","").lower() for n in tree().iter("node"))

def arsc_mode(label):
    r.reset(); tap(text="resources.arsc",pane=1); tap(text=label)
    assert "ArscEditorPlusActivity" in adb("shell","dumpsys","activity","activities")
    tree()

def same_archive_copy_move():
    archive=ARTIFACTS/"same-archive.zip"
    with zipfile.ZipFile(archive,"w") as z:
        z.writestr("docs/sub/note.txt","keep contents")
        z.writestr("docs/empty/","")
        z.writestr("target/marker.txt","destination")
        z.writestr("top.txt","move contents")
    adb("push",str(archive),r.ROOT+"/same-archive.zip")
    r.reset(); tap(rid="syncPaneButton")
    tap(text="same-archive.zip",pane=2); tap(text="target",pane=2)
    tap(text="same-archive.zip",pane=1); r.context("docs","Copy ->"); tap(text="Add")
    with zipfile.ZipFile(io.BytesIO(r.read("same-archive.zip"))) as z:
        assert z.read("target/docs/sub/note.txt")==b"keep contents"
        assert z.read("docs/sub/note.txt")==b"keep contents"
        assert "target/docs/empty/" in z.namelist()
    r.context("top.txt","Move ->"); tap(text="Add")
    with zipfile.ZipFile(io.BytesIO(r.read("same-archive.zip"))) as z:
        assert z.read("target/top.txt")==b"move contents"
        assert "top.txt" not in z.namelist()

SCENARIOS={"backup-restore":backup_restore,"hex-edit-save":hex_edit_save,
 "same-archive-copy-move":same_archive_copy_move,
 "image-viewer-properties":image_viewer,"image-editor-screen":image_editor_screen,
 "audio-player-screen":audio_screen,"sha256-matches-file":checksums_match,
 "arsc-plus-screen":lambda:arsc_mode("ARSC Editor Plus"),
 "arsc-translation-screen":lambda:arsc_mode("Translation mode"),
 "arsc-querier-screen":lambda:arsc_mode("Resource querier"),
 "apk-extractor-screen":lambda:open_activity("io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity"),
 "tools-hub-screen":lambda:open_activity("io.github.abdurazaaqmohammed.tools.ToolsHubActivity"),
 "wifi-screen":lambda:open_activity("io.github.abdurazaaqmohammed.tools.WifiManagerActivity"),
 "storage-screen":lambda:open_activity("io.github.abdurazaaqmohammed.tools.StorageManagerActivity")}

if __name__=="__main__":
    seed()
    for name in sys.argv[1:] or SCENARIOS: r.scenario(name,SCENARIOS[name])
    (ARTIFACTS/"surface-results.json").write_text(json.dumps({"fixture":r.ROOT,"results":r.RESULTS},indent=2))
    (ARTIFACTS/"surface-logcat.txt").write_text(adb("logcat","-d"),encoding="utf-8")
    sys.exit(1 if any(x["status"]=="FAIL" for x in r.RESULTS) else 0)
