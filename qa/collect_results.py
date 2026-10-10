"""Summarize latest native outcomes without discarding the original attempts."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

repo=Path(__file__).resolve().parents[1]
artifacts=repo/"qa/artifacts"
sources=["final-core-results.json","final-extended-results.json","final-network-results.json",
         "final-inspection-results.json","surface-first-results.json","surface-second-results.json",
         "final-acceptance-results.json","zipmove24-results.json","last-smoke-results.json",
         "network-results.json","comparison-first-results.json","comparison-results.json",
         "dex-ui-first-pass-results.json","dex-ui-final-attempt-results.json",
         "dex-ui-java-retest-results.json","syntax-first-results.json",
         "syntax-final-attempt-results.json","syntax-tab-isolation-results.json"]
aliases={"properties-correct-size":"properties","rebuilt-apk-sign-verify":"apk-sign-verify",
         "java-highlight-and-save":"java-highlighting","zip-move-out-final":"zip-single-move",
         "dex-followup-java-edit":"java-highlighting","dex-followup-python-open":"python-open-uppercase"}
latest={}; attempts=[]
for filename in sources:
    source=artifacts/filename
    if not source.exists(): raise SystemExit("Missing evidence: "+filename)
    report=json.loads(source.read_text())
    for original in report["results"]:
        result=dict(original, evidence=filename)
        result["scenario"]=aliases.get(result["scenario"],result["scenario"])
        attempts.append(result)
        latest[result["scenario"]]=result
units={}
for module in ["app","sdk"]:
    totals={k:0 for k in ["tests","failures","errors","skipped"]}
    for source in (repo/module/"build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        suite=ET.parse(source).getroot()
        for key in totals: totals[key]+=int(suite.get(key,0))
    units[module]=totals
apk=repo/"qa/dist/Hexora-validated-debug.apk"
output={"device":"MuMu Android 15 / API 35","unit_tests":units,
        "apk_sha256":hashlib.sha256(apk.read_bytes()).hexdigest(),
        "latest_native_results":list(latest.values()),"attempts":attempts}
prior=json.loads((artifacts/"pre-dex-ui-validated-results.json").read_text())
followup=json.loads((artifacts/"dex-ui-results.json").read_text())
output["validation_runs"]=[
    {"scope":"67 native scenarios on previous build", "apk_sha256":prior["apk_sha256"],
     "evidence":"pre-dex-ui-validated-results.json"},
    {"scope":"DEX UI follow-up and generic editor regression", "apk_sha256":followup["apk_sha256"],
     "evidence":"dex-ui-results.json", "scenario_count":len(followup["results"])}]
syntax=json.loads((artifacts/"syntax-results.json").read_text())
output["validation_runs"].append(
    {"scope":"Syntax colors, editing/preferences and Java/DEX regression", "apk_sha256":syntax["apk_sha256"],
     "evidence":"syntax-results.json", "scenario_count":len(syntax["results"])})
(artifacts/"validated-results.json").write_text(json.dumps(output,indent=2))
statuses={}
for result in latest.values(): statuses[result["status"]]=statuses.get(result["status"],0)+1
print(json.dumps({"native":statuses,"units":units,"apk_sha256":output["apk_sha256"]},indent=2))
raise SystemExit(1 if statuses.get("FAIL",0) else 0)
