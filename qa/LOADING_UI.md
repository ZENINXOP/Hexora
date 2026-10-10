# Loading UI — 2026-10-10

Source-only update, implemented and reviewed with OpenAI Codex. No builds, compilation, packaging, installation or native runtime testing were run.

- Replaced the shared progress dialog's rainbow GIF with a native Material linear indicator, using one theme accent and a neutral track. Removed the unused GIF.
- Added consistent spacing, bounded multiline status text, a footer Hide action and a localized percentage when the task supplies a positive total.
- Material animates progress changes and transitions from unknown to known progress. Frequent status/progress callbacks share one pending UI update, at a 50 ms interval, rather than posting every callback. Dismiss cancels pending rendering. Background notifications now reflect determinate progress after a total becomes available.
- DEX linear and circular loading layouts explicitly use the same single theme accent.

Java syntax parsing, three XML parses, single-color bindings, obsolete-resource checks and `git diff --check` passed. Read-only inspection confirms Material 1.12.0's `setProgressCompat` API. [Check record](artifacts/loading/source-checks.json). Existing APK SHA-256 remains `649146dfa9cc5ba894e37d8ef43b74bdc8fdbad8007bbe0bc1bc04efa71f6ad2`.

Pending native checks after an authorized build: unknown/known progress and their transition; rapid copy/log updates; long status text and large fonts; light/dark/black themes; animation scale disabled; Hide notifications; dismissal during queued updates; task completion before the dialog appears.
