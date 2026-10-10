"""Capture native type icons in both themes and inside a ZIP."""
import extended_regression as e
import regression as r
from mumu_ui import *
import zipfile

def icons():
    folder=ARTIFACTS/"type-icons"
    folder.mkdir(exist_ok=True)
    names=["01-script.py","02-layout.xml","03-Main.java","04-notes.txt",
           "05-settings.json","06-Main.kt","07-page.html","08-style.css",
           "09-app.js","10-app.ts","11-README.md","12-start.sh",
           "13-query.sql","14-config.yml","15-data.csv","16-code.smali",
           "17-native.cpp","18-font.ttf","19-report.docx","20-data.xlsx"]
    for name in names: (folder/name).write_text("Icon appearance fixture\n")
    adb("push",str(folder),r.ROOT+"/")
    archive=ARTIFACTS/"type-icons.zip"
    with zipfile.ZipFile(archive,"w") as z:
        for name in names: z.write(folder/name,name)
    adb("push",str(archive),r.ROOT+"/type-icons.zip")
    r.reset(); r.menu("Preferences"); tap(rid="lightThemeButton")
    r.reset(); tap(text="type-icons",pane=1); time.sleep(.3); snapshot("file-types-light")
    r.reset(); r.menu("Preferences"); tap(rid="darkThemeButton")
    r.reset(); tap(text="type-icons",pane=1); time.sleep(.3); snapshot("file-types-dark")
    adb("shell","input","swipe",220,1300,220,400,300); snapshot("file-types-more-dark")
    r.reset(); tap(text="type-icons.zip",pane=1); snapshot("file-types-archive")

if __name__=="__main__":
    r.scenario("file-type-icons-light-dark-zip",icons)
    (ARTIFACTS/"icon-results.json").write_text(json.dumps({"fixture":r.ROOT,"results":r.RESULTS},indent=2))
