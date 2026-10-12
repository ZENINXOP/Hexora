package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/** Publishes a complete shared copy without trusting provider names or replacing existing files. */
public final class SharedFileCopy {
    private SharedFileCopy() { }

    public static String safeName(String name, String extension) {
        if (name == null) name = "";
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) name = "Shared file";
        if (!name.contains(".") && extension != null && extension.matches("[a-zA-Z0-9]{1,16}")) name += "." + extension;
        if (name.length() > 160) {
            int dot = name.lastIndexOf('.');
            String suffix = dot > 0 && name.length() - dot < 18 ? name.substring(dot) : "";
            name = name.substring(0, 160 - suffix.length()) + suffix;
        }
        return name;
    }

    public static File copy(InputStream input, File directory, String name, long maxBytes) throws IOException {
        if (input == null) throw new IOException("The sharing app did not provide readable file data.");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) throw new IOException("Cannot create shared-files folder.");
        name = safeName(name, null);
        File target = null;
        File temporary = null;
        boolean published = false;
        try {
            temporary = File.createTempFile(".receiving-", ".tmp", directory);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] bytes = new byte[65536];
                long total = 0;
                int count;
                while ((count = input.read(bytes)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Receiving cancelled.");
                    if (count > maxBytes - total) throw new IOException("Not enough free space to receive this file.");
                    output.write(bytes, 0, count);
                    total += count;
                }
                output.getFD().sync();
            }
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Receiving cancelled.");
            synchronized (SharedFileCopy.class) {
                target = reserve(directory, name);
                // Windows cannot rename over a reservation. Keep name allocation and
                // publication together so concurrent receivers cannot reuse the name.
                if (!target.delete() || !temporary.renameTo(target)) throw new IOException("Cannot finish receiving the file.");
            }
            published = true;
            return target;
        } finally {
            if (temporary != null) temporary.delete();
            if (!published && target != null) target.delete();
        }
    }

    private static File reserve(File directory, String name) throws IOException {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        for (int i = 0; i < 10000; i++) {
            File target = new File(directory, i == 0 ? name : base + " (" + i + ")" + suffix);
            if (target.createNewFile()) return target;
        }
        throw new IOException("Too many files with the same name.");
    }
}
