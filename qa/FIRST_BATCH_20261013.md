# Recommended feature batch — 2026-10-13

AI-assisted implementation and validation: OpenAI Codex. Changes remain local.

This batch implements the four groups approved by the user: DEX actions,
password-protected ZIP access, script controls, and merge-signing cancellation
cleanup. The previous Telegram URI and split-APK merge fixes remain included.
The app keeps target SDK 28 as requested; compile SDK is 36.

## Changes

- DEX tree: Add class, Import `.smali`/`.dex`, and Rename class. Imports validate
  the whole batch before adding classes. Rename updates typed DEX references,
  including arrays; it does not change manifest names or reflection strings.
  Modified classes can open an original/current Smali comparison. Dirty tabs are
  committed before these actions. DEX serialization re-interns references with
  DexPool and publishes through a temporary file, retaining a unique backup.
- ZIPs: password prompts for AES and ZipCrypto, wrong-password retry, cancellation,
  browsing and extraction. Edited encrypted entries retain their encryption and
  produce an unchanged original backup. Passwords stay in a bounded process-only
  cache and are invalidated if the archive changes. Canonical path lookup keeps
  `/sdcard` and `/storage/emulated/0` aliases consistent when saving.
- Scripts: literal quoted arguments, live bounded stdout/stderr, line input,
  End input, Stop, and configurable timeout (0 means unlimited). Stop terminates
  the launched process group. This is a pipe console, not a terminal emulator.
  App, root and existing Shizuku backends use the same session controls.
- Cancelling merge signing removes that job's hidden staged APK and staged V4
  signature. Invalid signing passwords leave the prompt available for retry.

## Validation

The final offline debug build and 181 JVM unit tests pass (zero failures/errors).
Tests cover reference rewriting and serialized DEX round trips, encryption and
backup retention, password-path aliases, literal arguments, streaming output,
bounded output, cancellation and timeout.

Native validation uses the user's authorized MuMu (`emulator-5554`, Android 15,
API 35) and owned disposable fixtures. The vivo is disconnected. Results and
screenshots are under `qa/artifacts/first-batch-20261012/` (the task's start date).
The DEX chain passed on the DexPool build; after the sole subsequent app change
to canonical password keys, encrypted extract/edit and binary-import checks were
run on the final APK. Other unchanged flows passed in the preceding focused run.

All 19 native scenarios passed across focused runs:

| Group | Checks |
| --- | --- |
| Scripts | Literal arguments/live line input/exit status; Stop kills child group; bounded million-character output; timeout; root UID 0 with input |
| Encrypted ZIPs | AES wrong-password retry/open; ZipCrypto open; Cancel; extract exact bytes; edit/save/reopen and original backup |
| DEX | Add; Smali import; typed rename; modified comparison; saved DEX inspection/backup; binary DEX import/save; atomic rejection of duplicate imports |
| Merge signing | Wrong-password retry then Cancel removes the staged merge without publishing output |
| Dark controls | Console input/EOF/exit; password and Add class dialogs, visually inspected |

Machine-readable acceptance and APK verification are recorded in
`qa/artifacts/first-batch-20261012/acceptance.json` and `apksigner-final.txt`.
Captured `androidruntime-final.txt` is empty. Signing and theme preferences were
restored after the tests. The saved device ZIP was also inspected to confirm
its edited entry still has the encrypted flag and AES compression marker.

Earlier failed logs are retained. They found the uninitialized DEX version,
builder reference serialization failure and ZIP password path-alias bug, all
fixed and retested. Unrelated foreground interruptions invalidated several
earlier checks; the driver now checks Hexora's foreground package before sending
input or capturing UI evidence. The comparison-title mismatch and the collision
check comparing against bytes from before the preceding binary import were
driver fixes; both checks were rerun successfully.

## Artifact and limits

APK: `qa/dist/Hexora.0.1.2-first-batch.apk` (debug signed, version 0.1.2/code 13).
SHA-256: `1f776b906c4defd403dbdc1044e23cbe5e607ff3dd1f2b5fa7720143a525734b`.
APK signature verification passes v1/v2; the installed MuMu APK has the same hash.

Encrypted RAR/7z support, creating encrypted archives, directory/package renames,
and the remainder of the earlier audit are outside this approved batch. Shizuku
execution and biometric cancellation need the corresponding service/hardware
and were not tested here. No Shizuku installation or phone permission changes
were made. DEX class renames can require manual manifest/reflection updates.
