package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableExceptionHandler;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;
import com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class SignatureCheckRewriterTest {
    private static final String INFO = "Landroid/content/pm/PackageInfo;";
    private static final String SIGNING = "Landroid/content/pm/SigningInfo;";
    private static final String SIGNATURES = "[Landroid/content/pm/Signature;";

    private Method method(int registers, String parameter, String result, List<? extends Instruction> code,
                          List<ImmutableTryBlock> handlers) {
        return new ImmutableMethod("Lsample/Checks;", "check", Collections.singletonList(
                new ImmutableMethodParameter(parameter, Collections.emptySet(), "info")), result, 9,
                Collections.emptySet(), Collections.emptySet(), new ImmutableMethodImplementation(registers,
                (Iterable<? extends Instruction>) code, handlers, Collections.emptyList()));
    }
    private ImmutableInstruction22c legacy(int dest, int source) {
        return new ImmutableInstruction22c(Opcode.IGET_OBJECT, dest, source,
                new ImmutableFieldReference(INFO, "signatures", SIGNATURES));
    }
    private List<Instruction> code(Method method) {
        List<Instruction> code = new ArrayList<>();
        for (Instruction instruction : method.getImplementation().getInstructions()) code.add(instruction);
        return code;
    }

    @Test public void legacyReadPreservesRegistersAndParameterMetadata() {
        Method input = method(2, INFO, SIGNATURES, Arrays.asList(legacy(0, 1),
                new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)), Collections.emptyList());
        SignatureCheckRewriter rewriter = new SignatureCheckRewriter();
        Method output = rewriter.rewrite(input);
        List<Instruction> code = code(output);
        assertEquals(Opcode.INVOKE_STATIC_RANGE, code.get(0).getOpcode());
        assertEquals(1, ((RegisterRangeInstruction) code.get(0)).getStartRegister());
        assertEquals(Opcode.MOVE_RESULT_OBJECT, code.get(1).getOpcode());
        assertEquals(0, ((OneRegisterInstruction) code.get(1)).getRegisterA());
        assertEquals(input.getParameters(), output.getParameters());
        assertEquals(2, output.getImplementation().getRegisterCount());
        assertEquals(1, rewriter.legacyReads);
    }

    @Test public void destinationMayAliasPackageInfoReceiver() {
        Method output = new SignatureCheckRewriter().rewrite(method(1, INFO, SIGNATURES,
                Arrays.asList(legacy(0, 0), new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)), Collections.emptyList()));
        assertEquals(0, ((RegisterRangeInstruction) code(output).get(0)).getStartRegister());
        assertEquals(0, ((OneRegisterInstruction) code(output).get(1)).getRegisterA());
    }

    @Test public void branchesSkipBothInstructionsOfExpandedFieldRead() {
        Method input = method(2, INFO, "V", Arrays.asList(new ImmutableInstruction21t(Opcode.IF_EQZ, 1, 4),
                legacy(0, 1), new ImmutableInstruction10x(Opcode.RETURN_VOID)), Collections.emptyList());
        Method output = new SignatureCheckRewriter().rewrite(input);
        assertEquals(6, ((OffsetInstruction) code(output).get(0)).getCodeOffset());
        assertEquals(Opcode.RETURN_VOID, code(output).get(3).getOpcode());
    }

    @Test public void exceptionRangesAndHandlersFollowExpandedRead() {
        ImmutableTryBlock block = new ImmutableTryBlock(0, 2,
                Collections.singletonList(new ImmutableExceptionHandler(null, 3)));
        Method input = method(2, INFO, SIGNATURES, Arrays.asList(legacy(0, 1),
                new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0),
                new ImmutableInstruction11x(Opcode.MOVE_EXCEPTION, 0),
                new ImmutableInstruction11x(Opcode.THROW, 0)), Collections.singletonList(block));
        Method output = new SignatureCheckRewriter().rewrite(input);
        assertEquals(4, output.getImplementation().getTryBlocks().get(0).getCodeUnitCount());
        assertEquals(5, output.getImplementation().getTryBlocks().get(0).getExceptionHandlers().get(0).getHandlerCodeAddress());
    }

    @Test public void modernRangeCallKeepsHighReceiverRegister() {
        ImmutableMethodReference call = new ImmutableMethodReference(SIGNING, "getApkContentsSigners", Collections.emptyList(), SIGNATURES);
        Method input = method(301, SIGNING, SIGNATURES, Arrays.asList(
                new ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 300, 1, call),
                new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
                new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)), Collections.emptyList());
        SignatureCheckRewriter rewriter = new SignatureCheckRewriter();
        Method output = rewriter.rewrite(input);
        assertEquals(300, ((RegisterRangeInstruction) code(output).get(0)).getStartRegister());
        MethodReference target = (MethodReference) ((ReferenceInstruction) code(output).get(0)).getReference();
        assertEquals(SignatureCheckRewriter.MODERN, target.getDefiningClass());
        assertEquals(Collections.singletonList(SIGNING), target.getParameterTypes());
        assertEquals(1, rewriter.modernCalls);
    }

    @Test public void modernFieldCaptureIsSeparateFromLegacyHelper() {
        Method input = method(2, INFO, SIGNING, Arrays.asList(new ImmutableInstruction22c(Opcode.IGET_OBJECT, 0, 1,
                new ImmutableFieldReference(INFO, "signingInfo", SIGNING)),
                new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)), Collections.emptyList());
        SignatureCheckRewriter rewriter = new SignatureCheckRewriter();
        Method output = rewriter.rewrite(input);
        MethodReference target = (MethodReference) ((ReferenceInstruction) code(output).get(0)).getReference();
        assertEquals("capture", target.getName());
        assertEquals(SignatureCheckRewriter.MODERN, target.getDefiningClass());
        assertEquals(1, rewriter.modernReads);
        assertEquals(0, rewriter.legacyReads);
    }

    @Test public void sameFieldNameOnAnotherClassIsUntouched() {
        Method input = method(2, "Lsample/Other;", SIGNATURES, Arrays.asList(new ImmutableInstruction22c(Opcode.IGET_OBJECT, 0, 1,
                new ImmutableFieldReference("Lsample/Other;", "signatures", SIGNATURES)),
                new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)), Collections.emptyList());
        assertSame(input, new SignatureCheckRewriter().rewrite(input));
    }

    @Test public void modernBooleanFlagsRetainPrimitiveMoveResult() {
        for (String name : Arrays.asList("hasMultipleSigners", "hasPastSigningCertificates")) {
            Method input = method(1, SIGNING, "Z", Arrays.asList(new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,
                    1, 0, 0, 0, 0, 0, new ImmutableMethodReference(SIGNING, name, Collections.emptyList(), "Z")),
                    new ImmutableInstruction11x(Opcode.MOVE_RESULT, 0), new ImmutableInstruction11x(Opcode.RETURN, 0)), Collections.emptyList());
            List<Instruction> output = code(new SignatureCheckRewriter().rewrite(input));
            assertEquals(Opcode.INVOKE_STATIC_RANGE, output.get(0).getOpcode());
            assertEquals(Opcode.MOVE_RESULT, output.get(1).getOpcode());
        }
    }
}
