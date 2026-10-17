"""Source-only checks; never invokes Gradle, javac, packaging, or a device.

Requires tree_sitter and tree_sitter_java. AI-assisted contribution: OpenAI Codex.
These checks do not replace compilation or Android runtime profiling.
"""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from tree_sitter import Language, Parser
import tree_sitter_java

ROOT = Path(__file__).resolve().parents[1]
parser = Parser(Language(tree_sitter_java.language()))


def git_paths(*args):
    output = subprocess.check_output(["git", *args, "-z"], cwd=ROOT, stderr=subprocess.PIPE)
    return {ROOT / name.decode("utf-8") for name in output.split(b"\0") if name}


def main():
    paths = git_paths("diff", "--name-only") | git_paths("ls-files", "--others", "--exclude-standard")
    java_count = xml_count = 0
    failures = []
    for path in sorted(paths):
        if not path.is_file():
            continue
        if path.suffix == ".java":
            java_count += 1
            tree = parser.parse(path.read_bytes())
            if tree.root_node.has_error:
                stack = [tree.root_node]
                while stack:
                    node = stack.pop()
                    if node.type == "ERROR" or node.is_missing:
                        failures.append(f"{path.relative_to(ROOT)}:{node.start_point.row + 1}: {node.type}")
                    stack.extend(reversed(node.children))
        elif path.suffix == ".xml":
            xml_count += 1
            try:
                ET.parse(path)
            except ET.ParseError as error:
                failures.append(f"{path.relative_to(ROOT)}: {error}")
    if failures:
        raise AssertionError("\n".join(failures))
    print(f"PASS: syntax parsed {java_count} Java and {xml_count} XML changed/new files.")

    # Catch the specific regression where a delegated single-thread executor is
    # cast to ThreadPoolExecutor for purge(), which would crash at runtime.
    for filename in ["player/ImageViewerActivity.java", "player/ImageEditActivity.java"]:
        source = (ROOT / "app/src/main/java/io/github/abdurazaaqmohammed" / filename).read_text(encoding="utf-8")
        assert "Executors.newSingleThreadExecutor()" not in source, filename
    print("PASS: purge-capable image executors use concrete thread pools.")
    print("Source checks only; no build, compilation, APK, or runtime test performed.")


if __name__ == "__main__":
    main()
