"""Recreate deterministic, disposable QA fixtures using only Python's standard library."""
from pathlib import Path
import gzip
import io
import shutil
import tarfile
import zipfile

ROOT=Path(__file__).resolve().parent/"artifacts"/"fixtures"

def prepare():
    ROOT.mkdir(parents=True,exist_ok=True)
    for name in ("source/first/empty","source/second","destination","empty","many"):
        (ROOT/name).mkdir(parents=True,exist_ok=True)
    files={"source/first/note.txt":b"first branch\n","source/second/note.txt":b"second branch\n",
           "notes.txt":b"Hexora regression fixture\nSearchNeedle\n","empty.txt":b"",
           "code.java":b"public class Example {\n  public static void main(String[] args) {}\n}\n",
           "data.xml":b'<resources><string name="qa">Hexora</string></resources>\n'}
    for name,data in files.items(): (ROOT/name).write_bytes(data)
    (ROOT/"large.bin").write_bytes(bytes(range(256))*65536)
    for number in range(2500): (ROOT/"many"/f"file-{number:04d}.txt").write_text(str(number))
    with zipfile.ZipFile(ROOT/"sample.zip","w",zipfile.ZIP_DEFLATED) as z:
        for name,data in {"docs/alpha.txt":b"archive alpha\n","docs/empty/":b"",
                          "docs/sub/beta.txt":b"archive beta\n","docs-other/keep.txt":b"unrelated sibling\n",
                          "top.txt":b"archive top\n"}.items(): z.writestr(name,data)
    shutil.copyfile(ROOT/"sample.zip",ROOT/"other.zip")
    for name,mode in (("sample.tar","w"),("sample.tar.gz","w:gz")):
        with tarfile.open(ROOT/name,mode) as tar: tar.add(ROOT/"source",arcname="source")
    with gzip.open(ROOT/"notes.txt.gz","wb") as output: output.write(files["notes.txt"])
    apk=Path(__file__).resolve().parents[1]/"samples/plugin-sample/release/plugin-sample-release.apk"
    shutil.copyfile(apk,ROOT/"sample.apk")
    with zipfile.ZipFile(apk) as z:
        for name in ("AndroidManifest.xml","classes.dex","resources.arsc"): (ROOT/name).write_bytes(z.read(name))
    debug=Path(__file__).resolve().parents[1]/"app/build/outputs/apk/debug/app-debug.apk"
    if debug.exists():
        with zipfile.ZipFile(debug) as z: (ROOT/"resources.arsc").write_bytes(z.read("resources.arsc"))
    print(ROOT)

if __name__=="__main__": prepare()
