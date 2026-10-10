package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;

/** Detects an unsupported native payload loader before rewriting or signing it.
 * AI-assisted contribution: OpenAI Codex. */
public final class SignaturePatchCompatibility {
    private static final String CONTEXT = "Landroid/content/Context;";
    private static final String CLASS_LOADER = "Ljava/lang/ClassLoader;";
    private static final String BYTES = "[B";

    private SignaturePatchCompatibility() { }

    /**
     * A native byte decoder plus a byte-backed class-loader factory in the manifest
     * Application bootstrap is outside the Java certificate rewriter's coverage.
     * Native libraries or encryption alone are not evidence of a packed bootstrap.
     * This deliberately recognizes a limited pattern, not every possible packer.
     */
    public static boolean hasNativePackedBootstrap(ClassDef type, String applicationClassName) {
        if (applicationClassName == null || !type.getType().equals(
                "L" + applicationClassName.replace('.', '/') + ";")) return false;

        boolean bootstrap = false;
        boolean nativeDecoder = false;
        boolean payloadLoader = false;
        for (Method method : type.getMethods()) {
            if (method.getImplementation() != null) {
                if (method.getName().equals("attachBaseContext") && method.getReturnType().equals("V")
                        && method.getParameterTypes().size() == 1
                        && CONTEXT.contentEquals(method.getParameterTypes().get(0))) bootstrap = true;
                if (method.getReturnType().equals(CLASS_LOADER) && hasByteArrayParameter(method))
                    payloadLoader = true;
            }
            if (AccessFlags.NATIVE.isSet(method.getAccessFlags()) && method.getReturnType().equals(BYTES)
                    && hasByteArrayParameter(method)) nativeDecoder = true;
        }
        return bootstrap && nativeDecoder && payloadLoader;
    }

    private static boolean hasByteArrayParameter(Method method) {
        for (CharSequence parameter : method.getParameterTypes())
            if (BYTES.contentEquals(parameter)) return true;
        return false;
    }
}
