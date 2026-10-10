"""Local FTP checks against the app's server; touches only the QA fixture."""
import extended_regression as e
import regression as r
from mumu_ui import *
import ftplib
import io
import sys

def sidebar(label):
    adb("shell","input","swipe",1,800,650,800,250)
    tap(text=label)

def invalid_client():
    r.reset(); sidebar("FTP Client")
    tap(rid="portInput"); enter("99999999999999999"); tap(text="Connect")
    find(rid="portInput")
    assert "MainActivity" in adb("shell","dumpsys","activity","activities")

def server_and_client():
    destination="ftp-destination-"+time.strftime("%H%M%S")
    adb("shell","mkdir","-p",r.ROOT+"/"+destination)
    r.reset(); tap(rid="syncPaneButton"); tap(text=destination,pane=2); sidebar("FTP Server")
    try: tap(text="ALLOW")
    except AssertionError: pass
    tap(rid="portInput"); enter("2121")
    tap(rid="userInput"); enter("hexoraqa")
    tap(rid="passInput"); enter("fixture-only")
    adb("shell","input","keyevent",4)
    tap(text="Start"); time.sleep(1)
    find(text="Stop")
    # Control connection forwarded via ADB; emulator IP used for the passive data socket.
    adb("forward","tcp:32121","tcp:2121")
    ftp=ftplib.FTP(); ftp.connect("127.0.0.1",32121,timeout=10); ftp.login("hexoraqa","fixture-only")
    # EPSV is not port-forwarded. Test control/list via an app-to-app local connection below.
    assert ftp.pwd()=="/"
    ftp.quit()
    r.dismiss(); sidebar("FTP Client")
    tap(rid="ipInput"); enter("127.0.0.1")
    tap(rid="portInput"); enter("2121")
    tap(rid="userInput"); enter("hexoraqa")
    tap(rid="passInput"); enter("fixture-only")
    adb("shell","input","keyevent",4)
    tap(text="Connect"); time.sleep(1)
    r.assert_text("Download",1); tap(text="Download",pane=1)
    tap(text=r.ROOT.rsplit("/",1)[-1],pane=1)
    snapshot("ftp-file-types-final")
    r.context("large.bin","Copy")
    for _ in range(20):
        if r.exists(destination+"/large.bin"): break
        time.sleep(.3)
    import hashlib
    assert hashlib.sha256(r.read("large.bin")).digest()==hashlib.sha256(r.read(destination+"/large.bin")).digest()
    # Move a disposable remote file only after a verified download completes.
    p=ARTIFACTS/"ftp-move.txt"; p.write_bytes(b"move over ftp\n")
    adb("push",str(p),r.ROOT+"/ftp-move.txt")
    tap(desc="Parent directory"); tap(text=r.ROOT.rsplit("/",1)[-1],pane=1)
    r.context("ftp-move.txt","Move")
    for _ in range(20):
        if not r.exists("ftp-move.txt"): break
        time.sleep(.3)
    assert r.read(destination+"/ftp-move.txt")==b"move over ftp\n"
    assert not r.exists("ftp-move.txt")
    sidebar("FTP Server"); tap(text="Stop")
    find(text="Start")

SCENARIOS={"ftp-invalid-port":invalid_client,"ftp-server-client-transfer":server_and_client}
if __name__=="__main__":
    for name in sys.argv[1:] or SCENARIOS: r.scenario(name,SCENARIOS[name])
    (ARTIFACTS/"network-results.json").write_text(json.dumps({"fixture":r.ROOT,"results":r.RESULTS},indent=2))
    (ARTIFACTS/"network-logcat.txt").write_text(adb("logcat","-d"),encoding="utf-8")
    try: adb("forward","--remove","tcp:32121")
    except RuntimeError: pass
    adb("shell","am","force-stop",PACKAGE)
    sys.exit(1 if any(x["status"]=="FAIL" for x in r.RESULTS) else 0)
