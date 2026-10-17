package io.github.abdurazaaqmohammed.plugins.packs;

import java.util.ArrayList;
import java.util.List;

/** Prefer newer bundled releases and correct metadata for the exact same pinned APK.
 * AI-assisted contribution: OpenAI Codex. */
final class PackCatalogReconciliation {
    private PackCatalogReconciliation() { }

    static List<PackDescriptor> withBundledCorrections(List<PackDescriptor> cached, List<PackDescriptor> bundled) {
        List<PackDescriptor> result = new ArrayList<>();
        for (PackDescriptor pack : cached) {
            PackDescriptor corrected = pack;
            for (PackDescriptor shipped : bundled) {
                // A host update can ship a newer trusted pack, or correct the
                // metadata of identical bytes. Keep genuinely newer remote packs.
                if (pack.id.equals(shipped.id) && pack.entryClass.equals(shipped.entryClass)
                        && pack.hasChecksum() && shipped.hasChecksum()
                        && pack.sha256.trim().matches("(?i)[0-9a-f]{64}")
                        && shipped.sha256.trim().matches("(?i)[0-9a-f]{64}")
                        && (shipped.version > pack.version
                        || pack.sha256.trim().equalsIgnoreCase(shipped.sha256.trim()))) {
                    corrected = shipped;
                    break;
                }
            }
            result.add(corrected);
        }
        return result;
    }
}
