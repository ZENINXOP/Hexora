"""Native syntax-color checks, live editing, preferences and document isolation."""
import regression as r
from mumu_ui import *
from PIL import Image
from collections import Counter

ROOT="/sdcard/Download/Hexora-Syntax-QA-"+time.strftime("%Y%m%d-%H%M%S")
r.ROOT=ROOT
SAMPLES={
 "sample.PY": '# Python fixture\ndef greet(name):\n    count = 42\n    message = "Hello, नमस्ते"\n    if count > 0:\n        print(message) # comment\n    return message\n',
 "layout.xml": '<?xml version="1.0"?>\n<!-- XML fixture -->\n<LinearLayout android:orientation="vertical">\n    <TextView android:text="Hello" />\n</LinearLayout>\n',
 "settings.json": '{\n  "name": "Hexora",\n  "count": 42,\n  "enabled": true\n}\n',
 "app.js": '// JavaScript fixture\nconst count = 42;\nfunction greet() {\n    return "Hello";\n}\n',
 "main.kt": '// Kotlin fixture\nfun greet(): String {\n    val count = 42\n    return "Hello"\n}\n',
 "query.sql": "-- SQL fixture\nSELECT name, 42 FROM records\nWHERE name = 'Hello';\n",
 "style.css": '/* CSS fixture */\nbody {\n    font-family: "sans-serif";\n    margin: 42px;\n}\n',
 "config.yml": '# YAML fixture\nname: "Hexora"\ncount: 42\nenabled: true\n',
 "page.html": '<!-- HTML fixture -->\n<html lang="en">\n  <body class="main">Hello</body>\n</html>\n',
 "plain.txt": 'if count == 42: print("Hello") # plain text\n',
 "Main.java": '// Java fixture\npublic class Main {\n    String message = "Hello";\n    int count = 42;\n}\n'
}
PALETTES={
 "dark":{"string":(168,218,181),"number":(242,193,141),"property":(138,180,248),"type":(196,171,238)},
 "light":{"string":(40,116,66),"number":(146,83,22),"property":(36,87,166),"type":(112,71,163)}
}
COLOR_RESULTS=[]

def prepare():
    folder=ARTIFACTS/"syntax-fixtures"; folder.mkdir(exist_ok=True)
    for name,value in SAMPLES.items():
        sub=folder/name.replace('.','-');sub.mkdir(exist_ok=True)
        (sub/name).write_text(value,encoding="utf-8",newline="\n")
    adb("shell","mkdir","-p",ROOT)
    adb("push",str(folder)+"/.",ROOT)


def code(): return find(rid="editor").get("text","")

def open_file(name):
    folder=ROOT+"/"+name.replace('.','-')
    launch(folder)
    tap(text=name,pane=1)
    for _ in range(8):
        try:
            if code()==SAMPLES[name]: return
        except AssertionError: pass
        time.sleep(.3)
    raise AssertionError("Wrong document or text mismatch: "+name)


def colors(name,mode="dark"):
    node=find(rid="editor");a,b,c,d=bounds(node)
    target=ARTIFACTS/(name+".png")
    target.write_bytes(adb("exec-out","screencap","-p",binary=True))
    with Image.open(target).convert("RGB") as pic:
        counts=Counter(pic.getpixel((x,y)) for y in range(b,min(d,pic.height)) for x in range(a+60,min(c,pic.width)))
    values={key:counts[color] for key,color in PALETTES[mode].items()}
    COLOR_RESULTS.append({"capture":name,"mode":mode,"pixels":values})
    (ARTIFACTS/"syntax-color-counts.json").write_text(json.dumps(COLOR_RESULTS,indent=2))
    return values


def highlighted(name,kinds):
    open_file(name)
    values=colors("syntax-"+name.replace('.','-'))
    for key in kinds: assert values[key]>5,(name,key,values)


def syntax_menu(label):
    tap(desc="File Options");tap(text="Syntax")
    # Android positions the checked language at the top, so earlier choices
    # such as Automatic/Plain Text may require scrolling up rather than down.
    for upward in [True, False]:
      for _ in range(5):
        try:tap(text=label);return
        except AssertionError:
            a,b,c,d=bounds(find(rid="select_dialog_listview"))
            start,end=(.3,.8) if upward else (.8,.3)
            adb("shell","input","swipe",(a+c)//2,int(b+(d-b)*start),(a+c)//2,int(b+(d-b)*end),350)
    raise AssertionError("Syntax choice missing: "+label)


def plain_and_manual():
    open_file("plain.txt")
    assert colors("syntax-plain")["string"]==0
    syntax_menu("Python")
    assert colors("syntax-manual-python")["string"]>5
    assert code()==SAMPLES["plain.txt"]
    syntax_menu("Plain Text")
    assert colors("syntax-manual-plain")["string"]==0
    syntax_menu("Automatic (Plain Text)")
    assert colors("syntax-automatic-plain")["string"]==0


def settings():
    tap(desc="File Options");tap(text="Preferences")


def toggle_highlighting():
    open_file("sample.PY")
    settings();tap(text="Syntax Highlighting");adb("shell","input","keyevent",4)
    assert colors("syntax-disabled")["string"]==0
    settings();tap(text="Syntax Highlighting");adb("shell","input","keyevent",4)
    assert colors("syntax-enabled")["string"]>5


def light_theme():
    open_file("sample.PY");settings();tap(text="Theme");tap(text="GitHub")
    adb("shell","input","keyevent",4)
    try:
        values=colors("syntax-python-light","light")
        assert values["string"]>5 and values["number"]>5,values
        assert code()==SAMPLES["sample.PY"]
    finally:
        settings();tap(text="Theme");tap(text="Dracula");adb("shell","input","keyevent",4)


def edit_undo_save():
    name="sample.PY";open_file(name);original=code()
    path=ROOT+"/"+name.replace('.','-')+"/"+name
    original_bytes=adb("exec-out","cat",path,binary=True)
    tap(rid="editor");adb("shell","input","keycombination","113","122")
    adb("shell","input","text", "'\"\"\"'")
    assert code().startswith('"""')
    after=colors("syntax-live-triple-string")
    assert after["string"]>100 and after["number"]==0,after
    tap(rid="btn_undo");assert code()==original
    assert colors("syntax-live-undo")["number"]>5
    tap(rid="editor");adb("shell","input","keycombination","113","122")
    adb("shell","input","keyevent","KEYCODE_MOVE_END")
    adb("shell","input","text","%sSYNTAX_SAVE")
    tap(rid="btn_save")
    assert b"SYNTAX_SAVE" in adb("exec-out","cat",path,binary=True)
    assert adb("exec-out","cat",path+".bak",binary=True)==original_bytes
    # Restore the disposable fixture for repeatability.
    local=ARTIFACTS/"syntax-fixtures"/name.replace('.','-')/name
    adb("push",str(local),path)


def tab_switch():
    # Persisted editor sessions can contain older fixtures with identical names.
    # A fresh basename isolates this check, while two paths with that same name
    # verify that a language override belongs to a document rather than its title.
    name="override-"+time.strftime("%H%M%S")+".txt"
    SAMPLES[name]=SAMPLES["plain.txt"]
    local=ARTIFACTS/name;local.write_text(SAMPLES[name],encoding="utf-8",newline="\n")
    folder=ROOT+"/"+name.replace('.','-')
    adb("shell","mkdir","-p",folder,folder+"-other")
    adb("push",str(local),folder+"/"+name)
    adb("push",str(local),folder+"-other/"+name)
    open_file(name);syntax_menu("Python")
    component=PACKAGE+"/io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity"
    adb("shell","am","start","-W","-n",component,"--es","path",folder+"-other/"+name)
    assert code()==SAMPLES[name]
    assert colors("syntax-same-name-other-tab")["string"]==0
    path=ROOT+"/settings-json/settings.json"
    adb("shell","am","start","-W","-n",component,"--es","path",path)
    assert code()==SAMPLES["settings.json"]
    assert colors("syntax-second-tab-json")["property"]>5
    tap(desc="Open files")
    for _ in range(10):
        try:tap(text=name);break
        except AssertionError:
            a,b,c,d=bounds(find(rid="tabs_recycler_view"))
            adb("shell","input","swipe",(a+c)//2,int(b+(d-b)*.8),(a+c)//2,int(b+(d-b)*.3),350)
    else:raise AssertionError("Current fixture tab missing")
    assert code()==SAMPLES[name]
    assert colors("syntax-first-tab-restored")["string"]>5


def java_regression():
    open_file("Main.java")
    node=find(rid="editor");a,b,c,d=bounds(node)
    target=ARTIFACTS/"syntax-java-regression.png"
    target.write_bytes(adb("exec-out","screencap","-p",binary=True))
    with Image.open(target).convert("RGB") as pic:
        colorful=sum(1 for y in range(b,min(d,pic.height)) for x in range(a+60,min(c,pic.width))
                     if max(pic.getpixel((x,y)))-min(pic.getpixel((x,y)))>40)
    assert colorful>50


def dex_regression():
    original_root=r.ROOT
    import dex_ui_regression as dex
    try:
        r.ROOT=json.loads((ARTIFACTS/"dex-ui-results.json").read_text())["fixture"]
        dex.open_class()
        assert colors("syntax-dex-regression")["string"]>5
        tap(desc="File Options");tap(text="Syntax");find(text="Smali")
        tap(text="Cancel")
        assert ".class public Lcom/example/mpextsample/EditorActivity;" in code()
    finally:r.ROOT=original_root


if __name__=="__main__":
    prepare();adb("logcat","-c")
    cases=[("syntax-python",lambda:highlighted("sample.PY",["string","number"])),
           ("syntax-xml",lambda:highlighted("layout.xml",["type","property","string"])),
           ("syntax-json",lambda:highlighted("settings.json",["property","string","number"]))]
    cases += [("syntax-"+name,lambda n=name,k=keys:highlighted(n,k)) for name,keys in [
        ("app.js",["string","number"]),("main.kt",["string","number"]),("query.sql",["string","number"]),
        ("style.css",["property","string","number"]),("config.yml",["property","string","number"]),
        ("page.html",["type","property","string"])]]
    cases += [("syntax-plain-manual-auto",plain_and_manual),("syntax-disable-enable",toggle_highlighting),
              ("syntax-light-editor-theme",light_theme),("syntax-edit-undo-save",edit_undo_save),
              ("syntax-tab-switch-override",tab_switch),("syntax-java-regression",java_regression),
              ("syntax-dex-regression",dex_regression)]
    for name,check in cases:r.scenario(name,check)
    (ARTIFACTS/"syntax-results.json").write_text(json.dumps({"fixture":ROOT,"results":r.RESULTS},indent=2))
    (ARTIFACTS/"syntax-logcat.txt").write_text(adb("logcat","-d"),encoding="utf-8")
    sys.exit(1 if any(x["status"]=="FAIL" for x in r.RESULTS) else 0)
