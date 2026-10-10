package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.builder.BuilderInstruction;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rewrites exact Android certificate API references, preserving labels, handlers and registers.
 * AI-assisted contribution: OpenAI Codex. */
public final class SignatureCheckRewriter {
    public static final String BASE = "Lapp/hexora/signature/OriginalCertificates;";
    public static final String MODERN = "Lapp/hexora/signature/SigningInfoCompat;";
    private static final String INFO = "Landroid/content/pm/PackageInfo;";
    private static final String SIGNING = "Landroid/content/pm/SigningInfo;";
    private static final String SIGNATURES = "[Landroid/content/pm/Signature;";

    public int legacyReads, modernReads, modernCalls;

    public ClassDef rewrite(ClassDef type) {
        int before = legacyReads + modernReads + modernCalls;
        List<Method> direct = rewriteMethods(type.getDirectMethods());
        List<Method> virtual = rewriteMethods(type.getVirtualMethods());
        if (before == legacyReads + modernReads + modernCalls) return type;
        return new ImmutableClassDef(type.getType(), type.getAccessFlags(), type.getSuperclass(),
                type.getInterfaces(), type.getSourceFile(), type.getAnnotations(),
                type.getStaticFields(), type.getInstanceFields(), direct, virtual);
    }

    private List<Method> rewriteMethods(Iterable<? extends Method> methods) {
        List<Method> result = new ArrayList<>();
        for (Method method : methods) result.add(rewrite(method));
        return result;
    }

    public Method rewrite(Method method) {
        if (method.getImplementation() == null) return method;
        boolean matches = false;
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (replacement(instruction) != null) { matches = true; break; }
        }
        if (!matches) return method;
        MutableMethodImplementation body = new MutableMethodImplementation(method.getImplementation());
        // Keep instruction identities: inserting code can also widen earlier branches.
        List<BuilderInstruction> originals = new ArrayList<>(body.getInstructions());
        for (BuilderInstruction instruction : originals) {
            ImmutableMethodReference target = replacement(instruction);
            if (target == null) continue;
            body.getInstructions(); // Resolve branch/payload adjustments before reading locations.
            int index = instruction.getLocation().getIndex();
            if (instruction.getOpcode() == Opcode.IGET_OBJECT) {
                TwoRegisterInstruction registers = (TwoRegisterInstruction) instruction;
                body.replaceInstruction(index, new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,
                        registers.getRegisterB(), 1, target));
                body.addInstruction(index + 1, new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT,
                        registers.getRegisterA()));
                if (target.getDefiningClass().equals(BASE)) legacyReads++; else modernReads++;
            } else {
                int receiver = instruction instanceof RegisterRangeInstruction
                        ? ((RegisterRangeInstruction) instruction).getStartRegister()
                        : ((FiveRegisterInstruction) instruction).getRegisterC();
                body.replaceInstruction(index, new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,
                        receiver, 1, target));
                modernCalls++;
            }
        }
        return new ImmutableMethod(method.getDefiningClass(), method.getName(), method.getParameters(),
                method.getReturnType(), method.getAccessFlags(), method.getAnnotations(),
                method.getHiddenApiRestrictions(), body);
    }

    private static ImmutableMethodReference replacement(Instruction instruction) {
        if (!(instruction instanceof ReferenceInstruction)) return null;
        Object reference = ((ReferenceInstruction) instruction).getReference();
        if (instruction.getOpcode() == Opcode.IGET_OBJECT && reference instanceof FieldReference) {
            FieldReference field = (FieldReference) reference;
            if (!field.getDefiningClass().equals(INFO)) return null;
            if (field.getName().equals("signatures") && field.getType().equals(SIGNATURES))
                return new ImmutableMethodReference(BASE, "getSignatures", Collections.singletonList(INFO), SIGNATURES);
            if (field.getName().equals("signingInfo") && field.getType().equals(SIGNING))
                return new ImmutableMethodReference(MODERN, "capture", Collections.singletonList(INFO), SIGNING);
        }
        if ((instruction.getOpcode() == Opcode.INVOKE_VIRTUAL
                || instruction.getOpcode() == Opcode.INVOKE_VIRTUAL_RANGE) && reference instanceof MethodReference) {
            MethodReference method = (MethodReference) reference;
            if (!method.getDefiningClass().equals(SIGNING) || !method.getParameterTypes().isEmpty()) return null;
            String name = method.getName();
            boolean array = name.equals("getApkContentsSigners") || name.equals("getSigningCertificateHistory");
            boolean flag = name.equals("hasMultipleSigners") || name.equals("hasPastSigningCertificates");
            if ((array && method.getReturnType().equals(SIGNATURES)) || (flag && method.getReturnType().equals("Z")))
                return new ImmutableMethodReference(MODERN, name, Collections.singletonList(SIGNING), method.getReturnType());
        }
        return null;
    }
}
