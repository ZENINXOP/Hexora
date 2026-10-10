"""Native comparison and editor-preferences checks with deterministic fixtures."""
import extended_regression as e
import regression as r
from mumu_ui import *

def action(label):
    for _ in range(5):
        try: tap(text=label); return
        except AssertionError:
            a,b,c,d=bounds(find(rid="fileMenuList"))
            adb("shell","input","swipe",(a+c)//2,int(b+(d-b)*.8),(a+c)//2,int(b+(d-b)*.3),400)
    raise AssertionError("Menu action not found: "+label)

def text_compare():
    folder=ARTIFACTS/"compare-text-fixture"; folder.mkdir(exist_ok=True)
    (folder/"left").mkdir(exist_ok=True); (folder/"right").mkdir(exist_ok=True)
    (folder/"left/a.txt").write_text("common line\nleft_version\n")
    (folder/"right/b.txt").write_text("common line\nright_version\n")
    adb("push",str(folder),r.ROOT+"/")
    r.reset(); tap(text=folder.name,pane=1); tap(rid="syncPaneButton")
    tap(text="left",pane=1); r.menu("Select all")
    tap(text="right",pane=2); r.menu("Select all")
    tap(text="a.txt",pane=1,long=True); action("Compare Text")
    assert "CompareTextActivity" in adb("shell","dumpsys","activity","activities")
    for _ in range(8):
        report="\n".join(n.get("text","") for n in tree().iter("node"))
        if "left_version" in report and "right_version" in report: return
        time.sleep(.5)
    raise AssertionError("Text comparison did not render both changed lines")

def dex_compare():
    folder=ARTIFACTS/"compare-dex-fixture"; folder.mkdir(exist_ok=True)
    original=(ARTIFACTS/"fixtures/classes.dex").read_bytes()
    (folder/"left").mkdir(exist_ok=True); (folder/"right").mkdir(exist_ok=True)
    (folder/"left/a.dex").write_bytes(original); (folder/"right/b.dex").write_bytes(original)
    adb("push",str(folder),r.ROOT+"/")
    r.reset(); tap(text=folder.name,pane=1); tap(rid="syncPaneButton")
    tap(text="left",pane=1); r.menu("Select all")
    tap(text="right",pane=2); r.menu("Select all")
    tap(text="a.dex",pane=1,long=True); action("Compare DEX")
    tap(text="Compare DEX")
    assert "CompareDexActivity" in adb("shell","dumpsys","activity","activities")
    for _ in range(8):
        try: find(text="No differences found."); return
        except AssertionError: time.sleep(.5)
    raise AssertionError("Identical DEX files did not compare equal")

def editor_settings():
    r.reset(); tap(text="code.java",pane=1); tap(desc="File Options"); tap(text="Preferences")
    assert "EditorSettingsActivity" in adb("shell","dumpsys","activity","activities")
    root=tree(); assert any("Font" in n.get("text","") for n in root.iter("node"))

if __name__=="__main__":
    for name,check in [("text-comparison",text_compare),("dex-comparison",dex_compare),("editor-preferences",editor_settings)]:
        r.scenario(name,check)
    (ARTIFACTS/"comparison-results.json").write_text(json.dumps({"fixture":r.ROOT,"results":r.RESULTS},indent=2))
    sys.exit(1 if any(x["status"]=="FAIL" for x in r.RESULTS) else 0)
