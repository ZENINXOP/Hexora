package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.lingala.zip4j.ZipFile;

/** Passwords belong to this process/session, never preferences or log files. */
public final class ArchivePasswords {
    private ArchivePasswords() { }
    private static final Map<String, Entry> passwords = new LinkedHashMap<>(8, .75f, true);
    private static String key(File file) {
        // Android aliases /sdcard and /storage/emulated/0 to the same storage.
        try { return file.getCanonicalPath(); }
        catch (java.io.IOException e) { return file.getAbsolutePath(); }
    }
    private static final class Entry {
        final long length, modified;
        final char[] password;
        Entry(File file, char[] value) { length=file.length(); modified=file.lastModified(); password=value.clone(); }
    }
    public static synchronized void remember(File file, char[] password) {
        forget(file);
        passwords.put(key(file), new Entry(file, password));
        if (passwords.size()>8) {
            String oldest=passwords.keySet().iterator().next();
            Arrays.fill(passwords.remove(oldest).password, '\0');
        }
    }
    public static synchronized char[] get(File file) {
        Entry entry=passwords.get(key(file));
        if (entry==null) return null;
        if (entry.length!=file.length() || entry.modified!=file.lastModified()) { forget(file); return null; }
        return entry.password.clone();
    }
    public static synchronized void forget(File file) {
        Entry old=passwords.remove(key(file));
        if (old!=null) Arrays.fill(old.password, '\0');
    }
    public static ZipFile open(File file) {
        char[] password=get(file);
        return password==null ? new ZipFile(file) : new ZipFile(file, password);
    }
}
