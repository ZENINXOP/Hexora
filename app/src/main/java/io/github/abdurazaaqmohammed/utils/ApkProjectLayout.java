package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Select an encoder from the actual manifest format, not the presence of a res folder.
 * AI-assisted contribution: OpenAI Codex. */
public enum ApkProjectLayout {
    FLAT, RAW, XML, JSON;

    public static ApkProjectLayout detect(File directory) {
        if (new File(directory, "AndroidManifest.xml.json").isFile()) return JSON;
        if (new File(directory, "AndroidManifest.xml.bin").isFile()) return RAW;
        File manifest = new File(directory, "AndroidManifest.xml");
        if (!manifest.isFile()) return null;
        try (FileInputStream input = new FileInputStream(manifest)) {
            boolean binary = input.read() == 3 && input.read() == 0
                    && input.read() == 8 && input.read() == 0;
            if (!binary) return XML;
            // Decoder projects store payloads under root/ and dex/. Directly
            // extracted APKs keep their original APK paths, including res/.
            return new File(directory, "root").isDirectory() || new File(directory, "dex").isDirectory()
                    || new File(directory, "smali").isDirectory() ? RAW : FLAT;
        } catch (IOException error) {
            return null;
        }
    }
}
