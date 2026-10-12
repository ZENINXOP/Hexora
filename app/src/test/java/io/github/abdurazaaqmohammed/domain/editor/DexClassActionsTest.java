package io.github.abdurazaaqmohammed.domain.editor;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.LinkedHashMap;
import java.util.Map;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;
import com.android.tools.smali.smali.SmaliOptions;
import com.android.tools.smali.smali2.Smali;

public class DexClassActionsTest {
    public static ClassDef definition(String code) throws Exception { return Smali.assemble(code,new SmaliOptions(),35); }
    public static String holder() {
        return ".class public Lqa/batch/Holder;\n.super Ljava/lang/Object;\n"
                +".field public item:Lqa/batch/Base;\n.field public items:[[Lqa/batch/Base;\n"
                +".field public static final literal:Ljava/lang/String; = \"Lqa/batch/Base;\"\n"
                +".method public echo(Lqa/batch/Base;)Lqa/batch/Base;\n.locals 0\nreturn-object p1\n.end method\n";
    }
    @Test public void templateAssemblesAndNamesValidate() throws Exception {
        assertEquals("qa/batch/New",DexClassActions.name("Lqa/batch/New;"));
        assertEquals("qa/batch/New",DexClassActions.name("qa.batch.New"));
        assertEquals("Lqa/batch/New;",definition(DexClassActions.template("qa.batch.New")).getType());
        assertThrows(java.io.IOException.class,()->DexClassActions.name("../escape"));
    }
    @Test public void renameUpdatesTypedReferencesButNotReflectionStrings() throws Exception {
        Map<String,ClassDef> before=new LinkedHashMap<>();
        before.put("qa/batch/Base",definition(DexClassActions.template("qa/batch/Base")));
        before.put("qa/batch/Holder",definition(holder()));
        Map<String,ClassDef> after=DexClassActions.rename(before,"qa/batch/Base","qa/batch/Renamed");
        assertTrue(before.containsKey("qa/batch/Base"));
        assertFalse(after.containsKey("qa/batch/Base"));
        assertEquals("Lqa/batch/Renamed;",after.get("qa/batch/Renamed").getType());
        for (Field field:after.get("qa/batch/Holder").getFields()) {
            if (field.getName().equals("item")) assertEquals("Lqa/batch/Renamed;",field.getType());
            if (field.getName().equals("items")) assertEquals("[[Lqa/batch/Renamed;",field.getType());
            if (field.getName().equals("literal")) assertEquals("Lqa/batch/Base;",((StringEncodedValue)field.getInitialValue()).getValue());
        }
        for (Method method:after.get("qa/batch/Holder").getMethods()) {
            assertEquals("Lqa/batch/Renamed;",method.getReturnType());
            assertEquals("Lqa/batch/Renamed;",method.getParameterTypes().get(0));
        }
    }
    @Test public void duplicateRenameLeavesInputUntouched() throws Exception {
        Map<String,ClassDef> before=new LinkedHashMap<>();
        before.put("qa/batch/Base",definition(DexClassActions.template("qa/batch/Base")));
        before.put("qa/batch/Other",definition(DexClassActions.template("qa/batch/Other")));
        assertThrows(java.io.IOException.class,()->DexClassActions.rename(before,"qa/batch/Base","qa/batch/Other"));
        assertEquals(2,before.size());
    }
    @Test public void packageRenameIncludesNestedClassesAndExternalTypedReferences() throws Exception {
        Map<String,ClassDef> before=new LinkedHashMap<>();
        for (String name:java.util.List.of("qa/batch/Base","qa/batch/deep/Nested","qa/batchExtra/Keep"))
            before.put(name,definition(DexClassActions.template(name)));
        before.put("outside/Holder",definition(holder().replace("Lqa/batch/Holder;","Loutside/Holder;")));
        var names=DexClassActions.packageNames(before,"qa.batch","qa.renamed");
        var after=DexClassActions.renameTypes(before,names);
        assertTrue(after.containsKey("qa/renamed/Base")); assertTrue(after.containsKey("qa/renamed/deep/Nested"));
        assertTrue(after.containsKey("qa/batchExtra/Keep")); assertEquals(2,names.size());
        for (Field field:after.get("outside/Holder").getFields()) if (field.getName().equals("items"))
            assertEquals("[[Lqa/renamed/Base;",field.getType());
        before.put("qa/renamed/Base",definition(DexClassActions.template("qa/renamed/Base")));
        assertThrows(java.io.IOException.class,()->DexClassActions.renameTypes(before,names));
        assertTrue(before.containsKey("qa/batch/Base"));
    }
    @Test public void rewrittenAndAddedClassesSerializeWithTheirOwnReferenceIndexes() throws Exception {
        Map<String,ClassDef> before=new LinkedHashMap<>();
        before.put("qa/batch/Base",definition(DexClassActions.template("qa/batch/Base")));
        before.put("qa/batch/Holder",definition(holder()));
        before.put("qa/batch/Added",definition(DexClassActions.template("qa/batch/Added")));
        Map<String,ClassDef> after=DexClassActions.rename(before,"qa/batch/Base","qa/batch/Renamed");
        com.android.tools.smali.dexlib2.writer.pool.DexPool pool=new com.android.tools.smali.dexlib2.writer.pool.DexPool(com.android.tools.smali.dexlib2.Opcodes.forDexVersion(35));
        for (ClassDef definition:after.values()) pool.internClass(definition);
        com.android.tools.smali.dexlib2.writer.io.MemoryDataStore bytes=new com.android.tools.smali.dexlib2.writer.io.MemoryDataStore();
        pool.writeTo(bytes);
        com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile reopened=new com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile(
                com.android.tools.smali.dexlib2.Opcodes.forDexVersion(35),java.util.Arrays.copyOf(bytes.getBuffer(),bytes.getSize()));
        assertEquals(3,reopened.getClasses().size());
        for (ClassDef definition:reopened.getClasses()) {
            assertNotEquals("Lqa/batch/Base;",definition.getType());
            for (Method method:definition.getMethods()) if (method.getImplementation()!=null)
                for (com.android.tools.smali.dexlib2.iface.instruction.Instruction instruction:method.getImplementation().getInstructions())
                    if (instruction instanceof com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction reference) assertNotNull(reference.getReference().toString());
        }
    }
    /** Creates/inspects owned native QA files using the same assembler and DEX parser. */
    public static void main(String[] args) throws Exception {
        java.io.File directory=new java.io.File(args[1]); directory.mkdirs();
        if (args[0].equals("generate")) {
            com.android.tools.smali.dexlib2.writer.pool.DexPool pool=new com.android.tools.smali.dexlib2.writer.pool.DexPool(com.android.tools.smali.dexlib2.Opcodes.forDexVersion(35));
            pool.internClass(definition(DexClassActions.template("qa/batch/Base"))); pool.internClass(definition(holder()));
            pool.writeTo(new com.android.tools.smali.dexlib2.writer.io.FileDataStore(new java.io.File(directory,"classes.dex")));
            java.nio.file.Files.write(new java.io.File(directory,"Imported.smali").toPath(),DexClassActions.template("qa/batch/Imported").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            com.android.tools.smali.dexlib2.writer.pool.DexPool imported=new com.android.tools.smali.dexlib2.writer.pool.DexPool(com.android.tools.smali.dexlib2.Opcodes.forDexVersion(35));
            imported.internClass(definition(DexClassActions.template("qa/batch/DexImported")));
            imported.writeTo(new com.android.tools.smali.dexlib2.writer.io.FileDataStore(new java.io.File(directory,"import.dex")));
        } else {
            com.android.tools.smali.dexlib2.iface.DexFile dex=com.android.tools.smali.dexlib2.DexFileFactory.loadDexFile(new java.io.File(args[1]),com.android.tools.smali.dexlib2.Opcodes.forDexVersion(35));
            for (ClassDef definition:dex.getClasses()) {
                System.out.println(definition.getType());
                for (Field field:definition.getFields()) System.out.println(field.getName()+":"+field.getType());
            }
        }
    }
}
