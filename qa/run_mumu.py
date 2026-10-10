"""Run suites serially so only one UIAutomator driver owns the emulator."""
from pathlib import Path
import json
import subprocess
import sys

HERE=Path(__file__).resolve().parent
ARTIFACTS=HERE/"artifacts"
status=[]
for script,result in (("regression.py","core"),("extended_regression.py","extended"),
                      ("network_regression.py","network"),("inspection_regression.py","inspection")):
    log=ARTIFACTS/("final-"+result+".log")
    with log.open("w",encoding="utf-8") as output:
        completed=subprocess.run([sys.executable,str(HERE/script)],stdout=output,stderr=subprocess.STDOUT)
    source=ARTIFACTS/("results.json" if result=="core" else result+"-results.json")
    contents=json.loads(source.read_text())
    (ARTIFACTS/("final-"+result+"-results.json")).write_text(json.dumps(contents,indent=2))
    status.append({"suite":result,"exit_code":completed.returncode,"results":contents})
    print(result,completed.returncode,flush=True)
(ARTIFACTS/"final-results.json").write_text(json.dumps(status,indent=2))
sys.exit(1 if any(s["exit_code"] for s in status) else 0)
