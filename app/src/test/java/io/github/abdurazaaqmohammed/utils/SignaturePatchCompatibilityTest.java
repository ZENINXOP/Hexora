package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Compatibility boundaries, including ordinary apps using native cryptography.
 * AI-assisted contribution: OpenAI Codex. */
public class SignaturePatchCompatibilityTest {
    private static final String APP = "Lsample/Bootstrap;";

    private Method method(String name, String result, int flags, String... parameterTypes) {
        List<ImmutableMethodParameter> parameters = new ArrayList<>();
        for (String parameter : parameterTypes)
            parameters.add(new ImmutableMethodParameter(parameter, Collections.emptySet(), null));
        ImmutableMethodImplementation body = null;
        if ((flags & 0x100) == 0) {
            body = new ImmutableMethodImplementation(parameterTypes.length + 2,
                    result.equals("V") ? Collections.singletonList(new ImmutableInstruction10x(Opcode.RETURN_VOID))
                            : Arrays.asList(new ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
                                    new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)),
                    Collections.emptyList(), Collections.emptyList());
        }
        return new ImmutableMethod(APP, name, parameters, result, flags,
                Collections.emptySet(), Collections.emptySet(), body);
    }

    private ClassDef application(boolean bootstrap, boolean nativeDecoder, boolean loader) {
        List<Method> methods = new ArrayList<>();
        if (bootstrap) methods.add(method("attachBaseContext", "V", 4, "Landroid/content/Context;"));
        methods.add(method("a", "[B", nativeDecoder ? 0x109 : 9, "[B", "[B"));
        if (loader) methods.add(method("b", "Ljava/lang/ClassLoader;", 9, "[B"));
        return new ImmutableClassDef(APP, 1, "Landroid/app/Application;", Collections.emptyList(),
                null, Collections.emptySet(), Collections.emptyList(), methods);
    }

    @Test public void detectsNativePackedBootstrapWithoutDependingOnAppOrMethodNames() {
        assertTrue(SignaturePatchCompatibility.hasNativePackedBootstrap(application(true, true, true), "sample.Bootstrap"));
    }

    @Test public void nativeEncryptionWithoutPayloadLoaderRemainsSupported() {
        assertFalse(SignaturePatchCompatibility.hasNativePackedBootstrap(application(true, true, false), "sample.Bootstrap"));
    }

    @Test public void managedPayloadLoaderDoesNotClaimNativeProtection() {
        assertFalse(SignaturePatchCompatibility.hasNativePackedBootstrap(application(true, false, true), "sample.Bootstrap"));
    }

    @Test public void unrelatedClassCannotBlockTheManifestApplication() {
        assertFalse(SignaturePatchCompatibility.hasNativePackedBootstrap(application(true, true, true), "sample.OtherApp"));
    }

    @Test public void missingCustomApplicationDoesNotBlockOrdinaryApps() {
        assertFalse(SignaturePatchCompatibility.hasNativePackedBootstrap(application(true, true, true), null));
    }

    @Test public void loaderWithoutEarlyApplicationBootstrapDoesNotMatch() {
        assertFalse(SignaturePatchCompatibility.hasNativePackedBootstrap(application(false, true, true), "sample.Bootstrap"));
    }
}
