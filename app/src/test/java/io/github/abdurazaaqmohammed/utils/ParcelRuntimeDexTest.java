package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParcelRuntimeDexTest {
    @Test public void desugaredEmptyListNeedsNoHostJdkRuntime() {
        MethodReference original = new ImmutableMethodReference("Lj$/util/List$-CC;", "of", Collections.emptyList(), "Ljava/util/List;");
        MethodReference result = ParcelRuntimeDex.portableReference(original);
        assertEquals("Ljava/util/Collections;", result.getDefiningClass());
        assertEquals("emptyList", result.getName());
    }

    @Test public void listFactoryWithArgumentsIsNotChanged() {
        MethodReference original = new ImmutableMethodReference("Lj$/util/List$-CC;", "of", Collections.singletonList("Ljava/lang/Object;"), "Ljava/util/List;");
        assertSame(original, ParcelRuntimeDex.portableReference(original));
    }

    @Test public void resolvesAllManifestApplicationNameForms() throws Exception {
        assertEquals("android.app.Application", ParcelSignaturePatcher.applicationName("sample.app", null));
        assertEquals("sample.app.App", ParcelSignaturePatcher.applicationName("sample.app", ".App"));
        assertEquals("sample.app.App", ParcelSignaturePatcher.applicationName("sample.app", "App"));
        assertEquals("other.App", ParcelSignaturePatcher.applicationName("sample.app", "other.App"));
    }
}
