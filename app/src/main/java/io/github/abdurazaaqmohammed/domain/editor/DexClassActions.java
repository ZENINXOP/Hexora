package io.github.abdurazaaqmohammed.domain.editor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.rewriter.DexRewriter;
import com.android.tools.smali.dexlib2.rewriter.Rewriter;
import com.android.tools.smali.dexlib2.rewriter.RewriterModule;
import com.android.tools.smali.dexlib2.rewriter.Rewriters;
import com.android.tools.smali.dexlib2.rewriter.TypeRewriter;

/** Changes typed DEX references; reflection strings and APK manifest names remain explicit. */
public final class DexClassActions {
    private DexClassActions() { }
    public static String name(String input) throws IOException {
        String value=input==null ? "" : input.trim();
        if (value.startsWith("L") && value.endsWith(";")) value=value.substring(1,value.length()-1);
        value=value.replace('.','/');
        if (!value.matches("[A-Za-z_$][A-Za-z0-9_$]*(/[A-Za-z_$][A-Za-z0-9_$]*)*")) throw new IOException("Enter a valid class name.");
        return value;
    }
    public static String template(String name) throws IOException {
        String type="L"+name(name)+";";
        return ".class public "+type+"\n.super Ljava/lang/Object;\n\n.method public constructor <init>()V\n"
                +"    .locals 0\n    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n    return-void\n.end method\n";
    }
    public static Map<String,ClassDef> rename(Map<String,ClassDef> source, String oldName, String newName) throws IOException {
        String destination=name(newName);
        if (!source.containsKey(oldName)) throw new IOException("Class no longer exists.");
        if (source.containsKey(destination)) throw new IOException("A class with that name already exists.");
        return renameTypes(source, java.util.Collections.singletonMap(oldName,destination));
    }
    public static Map<String,String> packageNames(Map<String,ClassDef> source,String oldPackage,String newPackage) throws IOException {
        String prefix=name(oldPackage)+"/", destination=name(newPackage)+"/";
        if (prefix.equals(destination)) throw new IOException("Choose a different package name.");
        Map<String,String> names=new LinkedHashMap<>();
        for (String type:source.keySet()) if (type.startsWith(prefix)) names.put(type,destination+type.substring(prefix.length()));
        if (names.isEmpty()) throw new IOException("Package no longer exists.");
        return names;
    }
    public static Map<String,ClassDef> renameTypes(Map<String,ClassDef> source,Map<String,String> names) throws IOException {
        Map<String,String> descriptors=new LinkedHashMap<>();
        java.util.Set<String> destinations=new java.util.HashSet<>();
        for (Map.Entry<String,String> entry:names.entrySet()) {
            String destination=name(entry.getValue());
            if (!source.containsKey(entry.getKey())) throw new IOException("Class no longer exists.");
            if (!destinations.add(destination) || source.containsKey(destination) && !names.containsKey(destination))
                throw new IOException("A class with that name already exists: "+destination);
            descriptors.put("L"+entry.getKey()+";","L"+destination+";");
        }
        DexRewriter rewriter=new DexRewriter(new RewriterModule() {
            @Override public Rewriter<String> getTypeRewriter(Rewriters rewriters) {
                return new TypeRewriter() {
                    @Override protected String rewriteUnwrappedType(String value) { return descriptors.containsKey(value) ? descriptors.get(value) : value; }
                };
            }
        });
        Map<String,ClassDef> result=new LinkedHashMap<>();
        for (ClassDef definition:source.values()) {
            ClassDef rewritten=rewriter.getClassDefRewriter().rewrite(definition);
            String type=rewritten.getType();
            result.put(type.substring(1,type.length()-1),rewritten);
        }
        return result;
    }
}
