"""Read-only dependency checks; never builds, packages, installs or runs APK code.

AI-assisted contribution: OpenAI Codex.
Run from the repository root: py -3 -B qa/verify_lspatch_assets.py
"""

import hashlib
import json
from pathlib import Path
import re
import struct
import unittest
import zlib

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/signature/lspatch"
PINNED_RELEASE = "d238fdc414d121b7fa454d8b4ccf420df3a8c97d563761861ff92bd9c5da2165"


class LspatchAssetsTest(unittest.TestCase):
    def setUp(self):
        self.provenance = json.loads((ASSETS / "provenance.json").read_text(encoding="utf-8"))

    def test_release_and_source_are_pinned(self):
        self.assertEqual(self.provenance["release_sha256"], PINNED_RELEASE)
        self.assertEqual(self.provenance["commit"], "0dc50f42503711b14f5e2bb217c2bdd6321ce5be")
        self.assertEqual(self.provenance["version"], "v1.2")
        downloaded = ROOT / "qa/artifacts/lspatch-research/lspatch-v1.2-487-release.jar"
        if downloaded.is_file():
            self.assertEqual(hashlib.sha256(downloaded.read_bytes()).hexdigest(), PINNED_RELEASE)

    def test_payload_hashes_match_provenance_and_production_allowlist(self):
        source = (ROOT / "app/src/main/java/io/github/abdurazaaqmohammed/utils/LspatchSignaturePatcher.java").read_text(encoding="utf-8")
        production = re.findall(r'"([0-9a-f]{64})"', source)
        self.assertEqual(len(production), 6)
        for asset in self.provenance["assets"]:
            data = (ASSETS / asset["path"]).read_bytes()
            self.assertEqual(len(data), asset["size"])
            self.assertEqual(hashlib.sha256(data).hexdigest(), asset["sha256"])
            self.assertIn(asset["sha256"], production)

    def test_dex_headers_and_internal_checksums(self):
        for name in ("metaloader.dex", "loader.dex"):
            data = (ASSETS / name).read_bytes()
            self.assertRegex(data[:8], rb"dex\n0[0-9]{2}\x00")
            self.assertEqual(struct.unpack_from("<I", data, 32)[0], len(data))
            self.assertEqual(data[12:32], hashlib.sha1(data[32:]).digest())
            self.assertEqual(struct.unpack_from("<I", data, 8)[0], zlib.adler32(data[12:]) & 0xffffffff)
        self.assertIn(b"Lorg/lsposed/lspatch/metaloader/LSPAppComponentFactoryStub;", (ASSETS / "metaloader.dex").read_bytes())
        self.assertIn(b"Lorg/lsposed/lspatch/loader/LSPApplication;", (ASSETS / "loader.dex").read_bytes())

    def test_native_abis_and_16k_elf_segments(self):
        expected = {"arm64-v8a": 183, "armeabi-v7a": 40, "x86": 3, "x86_64": 62}
        for abi, machine in expected.items():
            data = (ASSETS / "so" / abi / "liblspatch.so").read_bytes()
            self.assertEqual(data[:4], b"\x7fELF")
            self.assertEqual(data[5], 1)  # Little endian.
            self.assertEqual(struct.unpack_from("<H", data, 18)[0], machine)
            is64 = data[4] == 2
            offset = struct.unpack_from("<Q" if is64 else "<I", data, 32 if is64 else 28)[0]
            size, count = struct.unpack_from("<HH", data, 54 if is64 else 42)
            segments = 0
            for i in range(count):
                pos = offset + size * i
                if struct.unpack_from("<I", data, pos)[0] == 1:
                    alignment = struct.unpack_from("<Q" if is64 else "<I", data, pos + (48 if is64 else 28))[0]
                    self.assertGreaterEqual(alignment, 16384)
                    segments += 1
            self.assertGreater(segments, 0)

    def test_upstream_notices_are_present(self):
        self.assertIn("GNU GENERAL PUBLIC LICENSE", (ASSETS / "UPSTREAM-LICENSE.txt").read_text(encoding="utf-8"))
        self.assertIn("JingMatrix", (ASSETS / "NOTICE.txt").read_text(encoding="utf-8"))
        for name in ("BUNDLED-APACHE-LICENSE.txt", "BUNDLED-META-INF-LICENSE.txt"):
            self.assertGreater((ASSETS / name).stat().st_size, 100)


if __name__ == "__main__":
    unittest.main(verbosity=2)
