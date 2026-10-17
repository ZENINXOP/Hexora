package io.github.abdurazaaqmohammed.plugins.packs;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class PackCatalogReconciliationTest {
    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private PackDescriptor pack(String id, int version, String hash, String entry) {
        return new PackDescriptor(id, id, "", "", version, "1." + version,
                "https://example.invalid/pack-" + id + ".apk", hash, entry, Collections.emptyList());
    }

    @Test public void hostUpdateCorrectsStaleVersionForIdenticalPinnedApk() {
        PackDescriptor cached = pack("device", 2, HASH_A, "DevicePack");
        PackDescriptor bundled = pack("device", 1, HASH_A, "DevicePack");
        List<PackDescriptor> result = PackCatalogReconciliation.withBundledCorrections(
                Collections.singletonList(cached), Collections.singletonList(bundled));
        assertSame(bundled, result.get(0));
        assertEquals(1, result.get(0).version);
        assertEquals(2, cached.version); // The cached source object is not mutated.
    }

    @Test public void newerRemoteApkWithDifferentChecksumIsNeverDowngraded() {
        PackDescriptor remote = pack("network", 3, HASH_B, "NetworkPack");
        PackDescriptor bundled = pack("network", 1, HASH_A, "NetworkPack");
        assertSame(remote, PackCatalogReconciliation.withBundledCorrections(
                Collections.singletonList(remote), Collections.singletonList(bundled)).get(0));
    }

    @Test public void appUpdateUsesItsNewerBundledPackInsteadOfOldCachedRelease() {
        PackDescriptor cached = pack("device", 1, HASH_A, "DevicePack");
        PackDescriptor bundled = pack("device", 2, HASH_B, "DevicePack");
        assertSame(bundled, PackCatalogReconciliation.withBundledCorrections(
                Collections.singletonList(cached), Collections.singletonList(bundled)).get(0));
    }

    @Test public void unpinnedOrInvalidChecksumsDoNotInheritBundledTrust() {
        for (String hash : Arrays.asList("", "invalid", null)) {
            PackDescriptor cached = pack("math", 5, hash, "MathPack");
            PackDescriptor bundled = pack("math", 5, hash, "MathPack");
            assertSame(cached, PackCatalogReconciliation.withBundledCorrections(
                    Collections.singletonList(cached), Collections.singletonList(bundled)).get(0));
        }
    }

    @Test public void sameChecksumCannotSubstituteAnotherPackOrEntryClass() {
        PackDescriptor cached = pack("device", 2, HASH_A, "DevicePack");
        for (PackDescriptor other : Arrays.asList(pack("network", 1, HASH_A, "DevicePack"),
                pack("device", 1, HASH_A, "OtherEntry")))
            assertSame(cached, PackCatalogReconciliation.withBundledCorrections(
                    Collections.singletonList(cached), Collections.singletonList(other)).get(0));
    }

    @Test public void checksumHexIsCaseInsensitiveAndUnknownRemotePacksAreRetained() {
        PackDescriptor corrected = pack("device", 1, HASH_A, "DevicePack");
        PackDescriptor cached = pack("device", 2, HASH_A.toUpperCase(java.util.Locale.ROOT), "DevicePack");
        PackDescriptor unknown = pack("new-pack", 7, HASH_B, "NewPack");
        List<PackDescriptor> result = PackCatalogReconciliation.withBundledCorrections(
                Arrays.asList(cached, unknown), Collections.singletonList(corrected));
        assertSame(corrected, result.get(0));
        assertSame(unknown, result.get(1));
    }
}
