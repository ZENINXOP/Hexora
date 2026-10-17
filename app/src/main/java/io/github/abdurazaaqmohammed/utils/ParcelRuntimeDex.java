package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.rewriter.DexRewriter;
import com.android.tools.smali.dexlib2.rewriter.Rewriter;
import com.android.tools.smali.dexlib2.rewriter.RewriterModule;
import com.android.tools.smali.dexlib2.rewriter.Rewriters;
import java.io.IOException;
import java.util.Collections;

/** Keeps injected runtime code independent of Hexora's core-library desugaring. */
final class ParcelRuntimeDex {
    private ParcelRuntimeDex() { }

    static MethodReference portableReference(MethodReference method) {
        if ((method.getDefiningClass().equals("Lj$/util/List$-CC;") || method.getDefiningClass().equals("Ljava/util/List;"))
                && method.getName().equals("of") && method.getParameterTypes().isEmpty()
                && method.getReturnType().equals("Ljava/util/List;"))
            return new ImmutableMethodReference("Ljava/util/Collections;", "emptyList", Collections.emptyList(), "Ljava/util/List;");
        return method;
    }

    static ClassDef portable(ClassDef type) throws IOException {
        DexRewriter rewriter = new DexRewriter(new RewriterModule() {
            @Override public Rewriter<MethodReference> getMethodReferenceRewriter(Rewriters rewriters) {
                return ParcelRuntimeDex::portableReference;
            }
        });
        ClassDef result = rewriter.getClassDefRewriter().rewrite(type);
        for (Method method : result.getMethods()) if (method.getImplementation() != null)
            for (var instruction : method.getImplementation().getInstructions())
                if (instruction instanceof ReferenceInstruction
                        && ((ReferenceInstruction) instruction).getReference().toString().contains("Lj$/"))
                    throw new IOException("The certificate runtime has an unsupported desugared dependency. No output was saved.");
        return result;
    }
}
