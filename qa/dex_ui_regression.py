"""Native regression for DEX class opening, tab isolation and theme consistency."""
import extended_regression as e
import inspection_regression as inspection
import regression as r
from mumu_ui import *


def code():
    return find(rid="editor").get("text", "")


def open_class(name="EditorActivity"):
    inspection.dex_editor()
    tap(text="com.example.mpextsample")
    tap(text=name)
    for _ in range(12):
        try:
            value=code()
            if ".class" in value and name+";" in value: return value
        except AssertionError: pass
        time.sleep(.25)
    raise AssertionError("Smali class did not load: "+name)


def themed_class(mode):
    r.reset(); r.menu("Preferences"); tap(rid=mode+"ThemeButton")
    time.sleep(.5)
    open_class()
    try:
        find(rid="loading_progress")
        raise AssertionError("Loading indicator remained after code loaded")
    except AssertionError as ex:
        if "remained" in str(ex): raise
    snapshot("dex-code-"+mode)


def multiple_classes():
    original=open_class()
    adb("shell","input","keyevent",4)
    tap(text="FileActionActivity")
    assert "Lcom/example/mpextsample/FileActionActivity;" in code()
    tap(desc="Open navigation drawer")
    tap(text="EditorActivity")
    assert code()==original
    tap(desc="Open navigation drawer")
    tap(text="FileActionActivity")
    assert "Lcom/example/mpextsample/FileActionActivity;" in code()


def edit_and_return():
    original=open_class()
    tap(rid="editor")
    adb("shell","input","keycombination","113","122")  # Ctrl+Home
    adb("shell","input","text","%sHEXORA_DEX_UI_CHECK%s")
    modified=code()
    assert "HEXORA_DEX_UI_CHECK" in modified
    # MuMu may use a hardware keyboard, so dismiss IME only when it is present.
    for _ in range(2):
        adb("shell","input","keyevent",4)
        try:
            find(text="FileActionActivity")
            break
        except AssertionError: pass
    tap(text="FileActionActivity")
    assert "HEXORA_DEX_UI_CHECK" not in code()
    tap(desc="Open navigation drawer"); tap(text="*EditorActivity")
    assert code()==modified
    tap(rid="btn_undo")
    assert code()==original
    tap(desc="Open navigation drawer")
    find(text="EditorActivity")
    try:
        find(text="*EditorActivity")
        raise AssertionError("Undo left an unchanged class marked as modified")
    except AssertionError as ex:
        if "Undo left" in str(ex): raise
    adb("shell","input","keyevent",4)


def rapid_open_back():
    open_class()
    # Resolve once, then stress opening and Back without slow UI dumps between them.
    adb("shell","input","keyevent",4)
    a,b,c,d=bounds(find(text="EditorActivity"))
    adb("shell","dumpsys","gfxinfo",PACKAGE,"reset")
    for _ in range(8):
        adb("shell","input","tap",(a+c)//2,(b+d)//2)
        adb("shell","input","keyevent",4)
    (ARTIFACTS/"dex-opening-gfxinfo.txt").write_text(adb("shell","dumpsys","gfxinfo",PACKAGE,"framestats"),encoding="utf-8")
    find(text="com.example.mpextsample")
    tap(text="EditorActivity")
    assert "Lcom/example/mpextsample/EditorActivity;" in code()


def save_smali():
    original=open_class()
    tap(rid="editor")
    adb("shell","input","keycombination","113","122")
    adb("shell","input","keyevent","KEYCODE_MOVE_END")
    adb("shell","input","text","_HEXORA_DEX_SAVE_CHECK")
    assert "# classes.dex_HEXORA_DEX_SAVE_CHECK" in code()
    tap(rid="btn_save")
    time.sleep(1)
    tap(desc="Open navigation drawer")
    find(text="EditorActivity")
    tap(text="EditorActivity")
    assert "# classes.dex_HEXORA_DEX_SAVE_CHECK" in code()
    # Undo/Save restores the fixture's class content before leaving the session.
    tap(rid="btn_undo")
    assert code()==original
    tap(rid="btn_save")
    time.sleep(.5)


def rotation():
    original=open_class()
    adb("shell","settings","put","system","accelerometer_rotation",0)
    try:
        adb("shell","settings","put","system","user_rotation",1)
        time.sleep(1)
        assert code()==original
        snapshot("dex-code-landscape")
    finally:
        adb("shell","settings","put","system","user_rotation",0)
    time.sleep(.5)
    assert code()==original


def java_edit():
    import final_acceptance as acceptance
    original_root=r.ROOT
    isolated=original_root+"/dex-ui-text-"+time.strftime("%H%M%S")
    adb("shell","mkdir","-p",isolated)
    try:
        r.ROOT=isolated
        acceptance.java_edit()
    finally:
        r.ROOT=original_root


if __name__=="__main__":
    adb("logcat","-c")
    cases=[("dex-theme-"+mode,lambda m=mode:themed_class(m)) for mode in ["light","dark","black"]]
    cases += [("dex-multiple-classes",multiple_classes),("dex-edit-switch-undo",edit_and_return),
              ("dex-rapid-open-back",rapid_open_back),("dex-code-rotation",rotation),
              ("dex-smali-save",save_smali)]
    import final_acceptance as acceptance
    cases += [("dex-followup-java-edit",java_edit),("dex-followup-python-open",acceptance.python_open)]
    for name,check in cases:r.scenario(name,check)
    r.reset(); r.menu("Preferences"); tap(rid="darkThemeButton"); r.reset()
    (ARTIFACTS/"dex-ui-results.json").write_text(json.dumps({"fixture":r.ROOT,"results":r.RESULTS},indent=2))
    (ARTIFACTS/"dex-ui-logcat.txt").write_text(adb("logcat","-d"),encoding="utf-8")
    sys.exit(1 if any(x["status"]=="FAIL" for x in r.RESULTS) else 0)
