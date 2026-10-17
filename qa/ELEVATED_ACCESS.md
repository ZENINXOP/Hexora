# Automatic Shizuku and root access

Source changes made without building or compiling, as requested.

- Activity startup/resume discovers an existing Shizuku grant and enables its file backend even when older saved defaults selected non-root mode or disabled file operations.
- A running Shizuku service without permission opens Shizuku's native authorization window. Concurrent file workers share the request and continue after its result; the UI thread never waits for the result.
- Automatic prompting happens once for each binder connection. Denial releases waiting workers, and an explicit later action can retry after a short cooldown. Stopping Shizuku also releases waiters and clears its stale file-service connection.
- File metadata workers wait for the Shizuku user service to bind after a grant. Main-thread metadata checks stay nonblocking.
- Authorized root access enables root file operations automatically. Root checks run off the main thread, time out, and cache results for 15 seconds rather than keeping denials indefinitely. Selecting Root in settings forces a fresh check.
- Explicit file-access switches still apply after initial grant detection in the current session. Silent installation and APK data extraction retain their separate settings.
- Returning from the Shizuku window preserves pending folder loads. The optional folder guide unregisters its retry callback when cancelled, completed, or destroyed, and does not reopen a folder after the user navigates elsewhere.

## Source validation

- Parsed all nine changed Java source files with the Java tree-sitter grammar: no syntax errors.
- Checked the patch with `git diff --check`.
- No Gradle task, Java compilation, APK packaging, or device test was run.

## Device verification still required

1. Already-authorized Shizuku: launch Hexora with old non-root/file-ops defaults, then browse Android/data and rename a test file without changing settings.
2. Running but unauthorized Shizuku: launch Hexora or open a restricted folder, allow the native permission request, and confirm the same pending folder/action completes without another tap.
3. While the prompt is visible, start two folder requests. Confirm one prompt, both requests resume, and neither pane jumps back to the previously displayed folder.
4. Deny permission; confirm no repeated automatic popup, no stuck progress, and normal local files still work. Retry an explicit action after the cooldown.
5. Cancel a pending operation or close its activity while awaiting permission. A later grant must not execute the cancelled action.
6. Stop/restart Shizuku while Hexora stays alive, grant again if needed, then confirm file access reconnects.
7. On a rooted device with an existing grant and old preferences, verify root-only browsing and a test-file rename. Revoke root, allow the cache to expire, then grant again and retry; also check the Root setting forces an immediate recheck.
8. Verify both themes display the native grant flow and settings status correctly.
