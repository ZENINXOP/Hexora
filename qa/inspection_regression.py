"""APK/DEX/ARSC and failure-path checks on disposable emulator files."""
import extended_regression as e
import regression as r
from mumu_ui import *
import hashlib
import sys
import zipfile

def dex_properties():
    r.reset(); tap(text="classes.dex",pane=1); tap(text="DEX properties")
    assert any("Classes: 11" in n.get("text","") for n in tree().iter("node"))

def dex_editor():
    r.reset(); tap(text="classes.dex",pane=1); tap(text="Dex Editor Plus")
    time.sleep(.5)
    assert "DexEditorActivity" in adb("shell","dumpsys","activity","activities")
    root=tree()
    assert any("mpextsample" in n.get("text","").lower() for n in root.iter("node"))

def arsc_editor():
    r.reset(); tap(text="resources.arsc",pane=1); tap(text="ARSC Editor")
    time.sleep(.5)
    assert "ArscEditorActivity" in adb("shell","dumpsys","activity","activities")
    root=tree(); assert any("string" in n.get("text","").lower() for n in root.iter("node"))

def apk_build():
    name="raw-project-"+time.strftime("%H%M%S")
    project=ARTIFACTS/name
    project.mkdir(exist_ok=True)
    with zipfile.ZipFile(ARTIFACTS/"fixtures/sample.apk") as z: z.extractall(project)
    adb("push",str(project),r.ROOT+"/"+name)
    r.reset(); tap(text=name,pane=1); tap(rid="build")
    # Default test key, rather than an owner's custom keystore.
    if find(rid="autosign").get("checked")=="true": tap(rid="autosign")
    tap(text="OK")
    for _ in range(30):
        if r.exists(name+"/"+name+".apk"): break
        time.sleep(.5)
    target=ARTIFACTS/"built-result.apk"; target.write_bytes(r.read(name+"/"+name+".apk"))
    with zipfile.ZipFile(target) as z:
        assert z.read("AndroidManifest.xml").startswith(b"\x03\x00")
        assert z.read("classes.dex").startswith(b"dex\n")
        assert z.read("resources.arsc")

def invalid_manifest_save():
    original = r.read("AndroidManifest.xml")
    r.reset(); tap(text="AndroidManifest.xml",pane=1)
    try: tap(text="Text editor")
    except AssertionError: pass
    tap(rid="editor")
    adb("shell","input","keycombination","113","29")
    adb("shell","input","text","invalidXML")
    tap(rid="btn_save"); time.sleep(.5)
    assert r.read("AndroidManifest.xml") == original
    r.assert_text("Error")

def storage_denied():
    adb("shell","appops","set","--uid",PACKAGE,"MANAGE_EXTERNAL_STORAGE","deny")
    try:
        launch(r.ROOT)
        # Android's All files access settings page can cover the app.
        root=tree()
        assert any("access" in n.get("text","").lower() or "permission" in n.get("text","").lower() for n in root.iter("node"))
        adb("shell","input","keyevent",4)
        assert "MainActivity" in adb("shell","dumpsys","activity","activities")
    finally:
        adb("shell","appops","set","--uid",PACKAGE,"MANAGE_EXTERNAL_STORAGE","allow")
        adb("shell","appops","set",PACKAGE,"MANAGE_EXTERNAL_STORAGE","allow")

def apk_sign():
    name="sign-test-"+time.strftime("%H%M%S")
    rebuilt=ARTIFACTS/"built-result.apk"
    adb("push",str(rebuilt if rebuilt.exists() else ARTIFACTS/"fixtures/sample.apk"),r.ROOT+"/"+name+".apk")
    r.reset(); tap(text=name+".apk",pane=1); tap(text="More"); tap(text="Sign APK")
    tap(text="OK")
    for _ in range(30):
        if r.exists(name+"_signed.apk"): break
        time.sleep(.3)
    result=r.read(name+"_signed.apk")
    (ARTIFACTS/"signed-result.apk").write_bytes(result)
    r.reset(); tap(text=name+"_signed.apk",pane=1); tap(text="More"); tap(text="Signature health")
    root=tree(); report="\n".join(n.get("text","") for n in root.iter("node"))
    assert "V1 (JAR): verified" in report and "V2 (APK Signature Scheme v2): verified" in report
    assert "Zipalign: OK" in report

def archive_copy_cancel():
    target=ARTIFACTS/"cancel-target.zip"
    with zipfile.ZipFile(target,"w") as z: z.writestr("sentinel.txt","unchanged")
    adb("push",str(target),r.ROOT+"/cancel-target.zip")
    r.reset(); tap(rid="syncPaneButton"); tap(text="cancel-target.zip",pane=2)
    tap(text="sample.zip",pane=1); r.context("docs","Copy ->")
    r.dismiss()
    assert hashlib.sha256(r.read("cancel-target.zip")).digest()==hashlib.sha256(target.read_bytes()).digest()
    # Same helper must be available again after Back dismisses the confirmation.
    r.context("top.txt","Copy ->"); tap(text="Add")
    for _ in range(20):
        candidate=r.read("cancel-target.zip")
        if candidate!=target.read_bytes(): break
        time.sleep(.2)
    import io
    with zipfile.ZipFile(io.BytesIO(candidate)) as z: assert z.read("top.txt")==b"archive top\n"

SCENARIOS={"dex-properties":dex_properties,"dex-class-browser":dex_editor,
 "arsc-editor":arsc_editor,"apk-raw-build":apk_build,"invalid-manifest-save":invalid_manifest_save,
 "storage-denied":storage_denied,"archive-copy-cancel":archive_copy_cancel}
SCENARIOS["apk-sign-verify"]=apk_sign
if __name__=="__main__":
    for name in sys.argv[1:] or SCENARIOS: r.scenario(name,SCENARIOS[name])
    (ARTIFACTS/"inspection-results.json").write_text(json.dumps({"fixture":r.ROOT,"results":r.RESULTS},indent=2))
    (ARTIFACTS/"inspection-logcat.txt").write_text(adb("logcat","-d"),encoding="utf-8")
    sys.exit(1 if any(x["status"]=="FAIL" for x in r.RESULTS) else 0)
