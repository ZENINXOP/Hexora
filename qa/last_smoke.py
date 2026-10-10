"""Checks of final APK changes using new transfer destinations."""
import surface_regression as s
import network_regression as n
import regression as r
from mumu_ui import *
import io
import zipfile

def regular_to_zip_move():
    name="move-into-archive.txt"; content=b"verified archive move\n"
    source=ARTIFACTS/name; source.write_bytes(content)
    adb("push",str(source),r.ROOT+"/"+name)
    archive=ARTIFACTS/"move-target.zip"
    with zipfile.ZipFile(archive,"w") as z: z.writestr("original.txt","keep")
    adb("push",str(archive),r.ROOT+"/move-target.zip")
    r.reset(); tap(rid="syncPaneButton"); tap(text="move-target.zip",pane=2)
    r.context(name,"Move ->"); tap(text="Add")
    for _ in range(20):
        if not r.exists(name): break
        time.sleep(.2)
    assert not r.exists(name)
    with zipfile.ZipFile(io.BytesIO(r.read("move-target.zip"))) as z:
        assert z.read(name)==content and z.read("original.txt")==b"keep"
    r.assert_text(name,2)

def zip_move_out():
    archive=ARTIFACTS/"move-out-final.zip"
    with zipfile.ZipFile(archive,"w") as z: z.writestr("docs/final-move.txt","move out safely")
    adb("push",str(archive),r.ROOT+"/move-out-final.zip")
    r.pair(); tap(text="move-out-final.zip",pane=1); tap(text="docs",pane=1)
    r.context("final-move.txt","Move ->")
    assert r.read("destination/final-move.txt")==b"move out safely"
    with zipfile.ZipFile(io.BytesIO(r.read("move-out-final.zip"))) as z:
        assert "docs/final-move.txt" not in z.namelist()

if __name__=="__main__":
    adb("logcat","-c")
    for name,check in [("audio-player-screen",s.audio_screen),
                       ("same-archive-copy-move",s.same_archive_copy_move),
                       ("regular-to-zip-move",regular_to_zip_move),
                       ("zip-move-out-final",zip_move_out),
                       ("ftp-server-client-transfer",n.server_and_client)]:
        r.scenario(name,check)
    (ARTIFACTS/"last-smoke-results.json").write_text(json.dumps({"fixture":r.ROOT,"results":r.RESULTS},indent=2))
    (ARTIFACTS/"last-smoke-logcat.txt").write_text(adb("logcat","-d"),encoding="utf-8")
    try: adb("forward","--remove","tcp:32121")
    except RuntimeError: pass
    adb("shell","am","force-stop",PACKAGE)
    sys.exit(1 if any(x["status"]=="FAIL" for x in r.RESULTS) else 0)
