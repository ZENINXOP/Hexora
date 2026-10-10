"""Retest corrected assertions and the final build's changed behavior."""
import extended_regression as e
import inspection_regression as i
import surface_regression as s
import icon_regression as icons
import regression as r
from mumu_ui import *

def java_edit():
    name="editable-JAVA.java"
    original=b'public class Example {\n  int value = 42; // syntax colors\n}\n'
    source=ARTIFACTS/name; source.write_bytes(original)
    adb("push",str(source),r.ROOT+"/"+name)
    r.reset(); tap(text=name,pane=1); find(rid="editor")
    time.sleep(.6); snapshot("java-syntax-final")
    tap(rid="editor"); adb("shell","input","keyevent","KEYCODE_MOVE_END")
    adb("shell","input","text","%s//saved")
    tap(rid="btn_save")
    assert b"//saved" in r.read(name)
    assert r.read(name+".bak")==original

def python_open():
    source=ARTIFACTS/"script.PY"; source.write_text('print("Hexora")\n')
    adb("push",str(source),r.ROOT+"/script.PY")
    r.reset(); tap(text="script.PY",pane=1); find(rid="editor")

if __name__=="__main__":
    for name,check in [("properties-correct-size",r.properties),
                       ("same-archive-copy-move",s.same_archive_copy_move),
                       ("java-highlight-and-save",java_edit),
                       ("python-open-uppercase",python_open),
                       ("rebuilt-apk-sign-verify",i.apk_sign),
                       ("file-type-icons-light-dark-zip",icons.icons)]:
        r.scenario(name,check)
    (ARTIFACTS/"final-acceptance-results.json").write_text(json.dumps({"fixture":r.ROOT,"results":r.RESULTS},indent=2))
    (ARTIFACTS/"final-acceptance-logcat.txt").write_text(adb("logcat","-d"),encoding="utf-8")
    sys.exit(1 if any(x["status"]=="FAIL" for x in r.RESULTS) else 0)
