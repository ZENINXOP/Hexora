package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pure file-listing sort logic extracted from MainActivity.
 * Sort mode comes from the caller (see data.prefs.ListPrefs).
 */
public final class FileSorting {

    public static final int SORT_NAME = 0;
    public static final int SORT_SIZE = 1;
    public static final int SORT_DATE = 2;
    public static final int SORT_TYPE = 3;

    private static final int APK_PRIORITY_DIR = 0;
    private static final int APK_PRIORITY_MANIFEST = 1;
    private static final int APK_PRIORITY_CLASSES = 2;
    private static final int APK_PRIORITY_ARSC = 3;
    private static final int APK_PRIORITY_OTHER = 4;

    private FileSorting() {
    }

    public static String getExt(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1);
    }

    public static void sortFiles(File[] files, int sortBy, boolean reverse) {
        if (files == null) return;
        // Snapshot once: File methods may hit disk/Binder, and changing metadata
        // during TimSort can also violate the comparator contract.
        FileSortKey[] keys = new FileSortKey[files.length];
        for (int i = 0; i < files.length; i++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            keys[i] = new FileSortKey(files[i], sortBy);
        }
        Arrays.sort(keys, (f1, f2) -> {
            if (f1.directory != f2.directory) return f1.directory ? -1 : 1;
            int result;
            switch (sortBy) {
                case SORT_SIZE:
                    result = Long.compare(f1.value, f2.value);
                    break;
                case SORT_DATE:
                    result = Long.compare(f1.value, f2.value);
                    break;
                case SORT_TYPE:
                    result = f1.extension.compareToIgnoreCase(f2.extension);
                    if (result == 0) result = f1.name.compareToIgnoreCase(f2.name);
                    break;
                default:
                    result = f1.name.compareToIgnoreCase(f2.name);
                    break;
            }
            return reverse ? -result : result;
        });
        for (int i = 0; i < files.length; i++) files[i] = keys[i].file;
    }

    private static final class FileSortKey {
        final File file;
        final String name, extension;
        final boolean directory;
        final long value;

        FileSortKey(File file, int sortBy) {
            this.file = file;
            name = file.getName();
            extension = sortBy == SORT_TYPE ? getExt(name) : "";
            directory = file.isDirectory();
            value = sortBy == SORT_SIZE ? file.length() : sortBy == SORT_DATE ? file.lastModified() : 0;
        }
    }

    private static final java.util.regex.Pattern CLASSES_DEX = java.util.regex.Pattern.compile("classes\\d*\\.dex");

    public static void sortZipEntries(List<ZipEntryInfo> entries, String folderPath, int sortBy, boolean reverse) {
        if (entries == null) return;
        boolean isApk = isZipFolderApk(folderPath);
        Collections.sort(entries, (e1, e2) -> {
            if (e1 == e2) return 0;
            if (e1.getName().equals("..") && e2.getName().equals("..")) return 0;
            if (e1.getName().equals("..")) return -1;
            if (e2.getName().equals("..")) return 1;
            if (isApk) {
                int p1 = apkEntryPriority(e1);
                int p2 = apkEntryPriority(e2);
                if (p1 != p2) return Integer.compare(p1, p2);
                if (p1 == APK_PRIORITY_CLASSES)
                    return Integer.compare(classesDexIndex(e1.getName()), classesDexIndex(e2.getName()));
                return compareZipEntries(e1, e2, sortBy);
            }
            int result = compareZipEntries(e1, e2, sortBy);
            if (e1.isDirectory() && !e2.isDirectory()) return -1;
            if (!e1.isDirectory() && e2.isDirectory()) return 1;
            return reverse ? -result : result;
        });
    }

    public static boolean isZipFolderApk(String folderPath) {
        if (folderPath == null) return false;
        int bang = folderPath.indexOf('!');
        String zipPath = bang == -1 ? folderPath : folderPath.substring(0, bang);
        return zipPath.toLowerCase(java.util.Locale.ROOT).endsWith(".apk");
    }

    private static int apkEntryPriority(ZipEntryInfo entry) {
        if (entry.isDirectory()) return APK_PRIORITY_DIR;
        String name = entry.getName();
        if (name.equalsIgnoreCase("AndroidManifest.xml")) return APK_PRIORITY_MANIFEST;
        if (CLASSES_DEX.matcher(name).matches()) return APK_PRIORITY_CLASSES;
        if (name.equals("resources.arsc")) return APK_PRIORITY_ARSC;
        return APK_PRIORITY_OTHER;
    }

    private static int classesDexIndex(String name) {
        String num = name.substring("classes".length(), name.length() - ".dex".length());
        if (num.isEmpty()) return 1;
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static int compareZipEntries(ZipEntryInfo e1, ZipEntryInfo e2, int sortBy) {
        switch (sortBy) {
            case SORT_SIZE:
                return Long.compare(e1.getSize(), e2.getSize());
            case SORT_DATE:
                return Long.compare(e1.getLastModified(), e2.getLastModified());
            case SORT_TYPE: {
                int ext = getExt(e1.getName()).compareToIgnoreCase(getExt(e2.getName()));
                return ext == 0 ? e1.getName().compareToIgnoreCase(e2.getName()) : ext;
            }
            default:
                return e1.getName().compareToIgnoreCase(e2.getName());
        }
    }
}
