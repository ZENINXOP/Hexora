package io.github.abdurazaaqmohammed.utils;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import io.github.abdurazaaqmohammed.domain.files.FileWorkProgress;

public class ArchiveUtil {

    private static final int BUFFER_SIZE = 8192;

    public static boolean isSupportedArchive(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".rar") || lower.endsWith(".tar")
                || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")
                || lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")
                || lower.endsWith(".tar.xz") || lower.endsWith(".txz")
                || lower.endsWith(".gz") || lower.endsWith(".bz2") || lower.endsWith(".xz");
    }

    public static String[] getSupportedCreateExts() {
        return new String[] { ".zip", ".7z", ".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".gz", ".bz2", ".xz" };
    }

    public static void extract(File archive, File destDir) throws IOException {
        extract(archive, destDir, false);
    }

    public static void extract(File archive, File destDir, boolean preserveTime) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) extractZip(archive, destDir, preserveTime);
        else if (lower.endsWith(".7z")) extract7z(archive, destDir, preserveTime);
        else if (lower.endsWith(".rar")) extractRar(archive, destDir, preserveTime);
        else if (lower.endsWith(".tar")) extractTar(new FileInputStream(archive), destDir, preserveTime);
        else if (lower.endsWith(".tgz")) extractTar(new GzipCompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".tar.gz")) extractTar(new GzipCompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".tbz2")) extractTar(new BZip2CompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".tar.bz2")) extractTar(new BZip2CompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".txz")) extractTar(new XZCompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".tar.xz")) extractTar(new XZCompressorInputStream(new FileInputStream(archive), true), destDir, preserveTime);
        else if (lower.endsWith(".gz")) extractSingleCompressed(new GzipCompressorInputStream(new FileInputStream(archive), true), destDir, archive.getName(), ".gz");
        else if (lower.endsWith(".bz2")) extractSingleCompressed(new BZip2CompressorInputStream(new FileInputStream(archive), true), destDir, archive.getName(), ".bz2");
        else if (lower.endsWith(".xz")) extractSingleCompressed(new XZCompressorInputStream(new FileInputStream(archive), true), destDir, archive.getName(), ".xz");
        else throw new IOException("Unsupported archive format: " + archive.getName());
    }

    public static void extract(File archive, File destDir, boolean preserveTime, ProgressManager progress) throws IOException {
        progress.setText(io.github.abdurazaaqmohammed.MPManager.R.string.progress_counting);
        long total = Math.max(1, extractionUnits(archive));
        progress.setText(io.github.abdurazaaqmohammed.MPManager.R.string.extracting_to_folder, destDir.getName());
        long[] done = {0};
        progress.setProgress(0L, total);
        try (FileWorkProgress.Scope scope = FileWorkProgress.track(units -> {
            done[0] += units;
            progress.setProgress(Math.min(done[0], Math.max(0, total - 1)), total);
        })) {
            extract(archive, destDir, preserveTime);
        }
        progress.setProgress(total, total);
    }

    /** Count actual decoded bytes/entries before extracting; no percentage based on elapsed time. */
    private static long extractionUnits(File archive) throws IOException {
        checkExtractionCancelled();
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        long total = 0;
        if (lower.endsWith(".zip")) {
            try (net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(archive)) {
                for (net.lingala.zip4j.model.FileHeader header : zip.getFileHeaders()) {
                    checkExtractionCancelled();
                    total = Math.addExact(total, header.isDirectory() ? 1 : Math.max(0, header.getUncompressedSize()) + 1);
                }
            }
        } else if (lower.endsWith(".7z")) {
            try (SevenZFile seven = new SevenZFile(archive)) {
                for (SevenZArchiveEntry entry : seven.getEntries()) {
                    checkExtractionCancelled();
                    total = Math.addExact(total, entry.isDirectory() ? 1 : Math.max(0, entry.getSize()) + 1);
                }
            }
        } else if (lower.endsWith(".rar")) {
            try (Archive rar = new Archive(archive)) {
                FileHeader header;
                while ((header = rar.nextFileHeader()) != null) {
                    checkExtractionCancelled();
                    total = Math.addExact(total, header.isDirectory() ? 1 : Math.max(0, header.getFullUnpackSize()) + 1);
                }
            } catch (RarException e) { throw new IOException("Cannot count archive", e); }
        } else {
            boolean tar = lower.endsWith(".tar") || lower.endsWith(".tgz") || lower.endsWith(".tbz2") || lower.endsWith(".txz")
                    || lower.endsWith(".tar.gz") || lower.endsWith(".tar.bz2") || lower.endsWith(".tar.xz");
            try (InputStream decoded = decodedArchiveInput(archive)) {
                if (tar) {
                    try (TarArchiveInputStream input = new TarArchiveInputStream(decoded)) {
                        TarArchiveEntry entry;
                        while ((entry = input.getNextTarEntry()) != null) {
                            checkExtractionCancelled();
                            total = Math.addExact(total, entry.isDirectory() ? 1 : Math.max(0, entry.getSize()) + 1);
                        }
                    }
                } else {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    total = 1;
                    while ((count = decoded.read(buffer)) != -1) {
                        checkExtractionCancelled();
                        total = Math.addExact(total, count);
                    }
                }
            }
        }
        return total;
    }

    private static InputStream decodedArchiveInput(File archive) throws IOException {
        InputStream raw = new BufferedInputStream(new FileInputStream(archive));
        String name = archive.getName().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".gz") || name.endsWith(".tgz")) return new GzipCompressorInputStream(raw, true);
            if (name.endsWith(".bz2") || name.endsWith(".tbz2")) return new BZip2CompressorInputStream(raw, true);
            if (name.endsWith(".xz") || name.endsWith(".txz")) return new XZCompressorInputStream(raw, true);
            return raw;
        } catch (IOException e) { raw.close(); throw e; }
    }

    private static void checkExtractionCancelled() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Extraction cancelled");
    }

    public static void create(File output, List<File> sources) throws IOException {
        checkExtractionCancelled();
        if (output.exists()) throw new IOException("Destination already exists: " + output);
        for (File source : sources) io.github.abdurazaaqmohammed.domain.files.SafeFileOperations.validateDestination(source, output);
        String lower = output.getName().toLowerCase(Locale.ROOT);
        File temporary = File.createTempFile(".hexora-archive-", ".tmp", output.getAbsoluteFile().getParentFile());
        try {
        if (lower.endsWith(".7z")) create7z(temporary, sources);
        else if (lower.endsWith(".tar")) createTar(new FileOutputStream(temporary), sources);
        else if (lower.endsWith(".tgz")) createTar(new GzipCompressorOutputStream(new FileOutputStream(temporary)), sources);
        else if (lower.endsWith(".tar.gz")) createTar(new GzipCompressorOutputStream(new FileOutputStream(temporary)), sources);
        else if (lower.endsWith(".tbz2")) createTar(new BZip2CompressorOutputStream(new FileOutputStream(temporary)), sources);
        else if (lower.endsWith(".tar.bz2")) createTar(new BZip2CompressorOutputStream(new FileOutputStream(temporary)), sources);
        else if (lower.endsWith(".txz")) createTar(new XZCompressorOutputStream(new FileOutputStream(temporary)), sources);
        else if (lower.endsWith(".tar.xz")) createTar(new XZCompressorOutputStream(new FileOutputStream(temporary)), sources);
        else if (lower.endsWith(".gz") && sources.size() == 1) compressSingle(new GzipCompressorOutputStream(new FileOutputStream(temporary)), sources.get(0));
        else if (lower.endsWith(".bz2") && sources.size() == 1) compressSingle(new BZip2CompressorOutputStream(new FileOutputStream(temporary)), sources.get(0));
        else if (lower.endsWith(".xz") && sources.size() == 1) compressSingle(new XZCompressorOutputStream(new FileOutputStream(temporary)), sources.get(0));
        else throw new IOException("Unsupported archive format: " + output.getName());
        checkExtractionCancelled();
        if (output.exists() || !temporary.renameTo(output)) throw new IOException("Cannot finish archive: " + output);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static void extractZip(File archive, File destDir, boolean preserveTime) throws IOException {
        try (net.lingala.zip4j.ZipFile zip = io.github.abdurazaaqmohammed.domain.files.ArchivePasswords.open(archive)) {
            // zip4j handles ZIP64, split archives and rejects paths outside destDir.
            ZipTaskProgress.run(zip, () -> zip.extractAll(destDir.getAbsolutePath()), Math.max(1, extractionUnits(archive)));
            if (!preserveTime) {
                long now = System.currentTimeMillis();
                String root = destDir.getCanonicalPath() + File.separator;
                for (net.lingala.zip4j.model.FileHeader header : zip.getFileHeaders()) {
                    File output = new File(destDir, header.getFileName().replace('\\', '/'));
                    if (output.getCanonicalPath().startsWith(root)) output.setLastModified(now);
                }
            }
        }
    }

    private static void extract7z(File archive, File destDir, boolean preserveTime) throws IOException {
        try (SevenZFile sevenZFile = new SevenZFile(archive)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                checkExtractionCancelled();
                File out = resolveEntry(destDir, entry.getName());
                if (entry.isDirectory()) out.mkdirs();
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        copy(sevenZFile.getInputStream(entry), os);
                    }
                    if (preserveTime && entry.getHasLastModifiedDate()) {
                        //noinspection ResultOfMethodCallIgnored
                        out.setLastModified(entry.getLastModifiedDate().getTime());
                    }
                }
                FileWorkProgress.advance(1);
            }
        }
    }

    private static void extractRar(File archive, File destDir, boolean preserveTime) throws IOException {
        try (Archive rar = new Archive(archive)) {
            FileHeader fh;
            while ((fh = rar.nextFileHeader()) != null) {
                checkExtractionCancelled();
                File out = resolveEntry(destDir, fh.getFileName());
                if (fh.isDirectory()) out.mkdirs();
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new java.io.FilterOutputStream(new BufferedOutputStream(new FileOutputStream(out))) {
                        @Override public void write(int value) throws IOException { this.out.write(value); FileWorkProgress.advance(1); }
                        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                            checkExtractionCancelled(); this.out.write(bytes, offset, length); FileWorkProgress.advance(length);
                        }
                    }) {
                        rar.extractFile(fh, os);
                    }
                    if (preserveTime && fh.getMTime() != null) {
                        //noinspection ResultOfMethodCallIgnored
                        out.setLastModified(fh.getMTime().getTime());
                    }
                }
                FileWorkProgress.advance(1);
            }
        } catch (RarException e) {
            throw new IOException("Failed to extract RAR archive", e);
        }
    }

    private static void extractTar(InputStream tarInput, File destDir, boolean preserveTime) throws IOException {
        List<TarLink> links = new ArrayList<>();
        try (TarArchiveInputStream tais = new TarArchiveInputStream(tarInput)) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                checkExtractionCancelled();
                File out = resolveEntry(destDir, entry.getName());
                if (entry.isSymbolicLink() || entry.isLink()) {
                    TarLink link = new TarLink(out, entry.getLinkName(), entry.isSymbolicLink());
                    resolveLinkTarget(destDir, link); // Reject an escaping target before further writes.
                    links.add(link);
                    continue;
                }
                if (entry.isDirectory()) {
                    if (!out.isDirectory() && !out.mkdirs()) throw new IOException("Cannot create: " + out);
                }
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        copy(tais, os);
                    }
                    if (preserveTime && entry.getLastModifiedDate() != null) {
                        //noinspection ResultOfMethodCallIgnored
                        out.setLastModified(entry.getLastModifiedDate().getTime());
                    }
                    // Preserve runnable toolchain files where the filesystem allows it.
                    out.setExecutable((entry.getMode() & 0111) != 0, false);
                }
                FileWorkProgress.advance(1);
            }
        }
        // Resolve forward references after ordinary entries. No archive-created
        // symlink can redirect writes made while reading the TAR stream.
        extractTarLinks(destDir, links, preserveTime);
    }

    private static final class TarLink {
        final File output;
        final String target;
        final boolean symbolic;
        TarLink(File output, String target, boolean symbolic) {
            this.output = output;
            this.target = target == null ? "" : target.replace('\\', '/');
            this.symbolic = symbolic;
        }
    }

    private static File resolveLinkTarget(File directory, TarLink link) throws IOException {
        if (link.target.isEmpty() || link.target.indexOf('\0') >= 0 || link.target.startsWith("/")
                || link.target.matches("^[A-Za-z]:.*")) throw new IOException("Invalid archive link target: " + link.target);
        // TAR symlinks are relative to their parent; hard links are archive-root relative.
        File target = new File(link.symbolic ? link.output.getParentFile() : directory, link.target).getCanonicalFile();
        requireInsideExtraction(directory, target);
        return target;
    }

    private static void requireInsideExtraction(File directory, File file) throws IOException {
        String root = directory.getCanonicalPath();
        String path = file.getCanonicalPath();
        if (!path.equals(root) && !path.startsWith(root + File.separator))
            throw new IOException("Archive link leaves destination: " + file);
    }

    private static void extractTarLinks(File directory, List<TarLink> pending, boolean preserveTime) throws IOException {
        while (!pending.isEmpty()) {
            boolean advanced = false;
            for (Iterator<TarLink> iterator = pending.iterator(); iterator.hasNext();) {
                checkExtractionCancelled();
                TarLink link = iterator.next();
                requireInsideExtraction(directory, link.output);
                File target = resolveLinkTarget(directory, link);
                if (!target.exists()) continue;
                String outputPath = link.output.getCanonicalPath();
                String targetPath = target.getCanonicalPath();
                if (outputPath.equals(targetPath) || (target.isDirectory() && outputPath.startsWith(targetPath + File.separator)))
                    throw new IOException("Cyclic archive link: " + link.output);
                if (target.isDirectory()) {
                    // A directory copy must include its deferred link children too.
                    boolean waiting = false;
                    for (TarLink other : pending) {
                        if (other.output.getCanonicalPath().startsWith(targetPath + File.separator)) { waiting = true; break; }
                    }
                    if (waiting) continue;
                }
                if (!link.symbolic && !target.isFile()) throw new IOException("Hard link target is not a file: " + link.target);
                if (link.output.exists()) throw new IOException("Archive link destination already exists: " + link.output);
                File parent = link.output.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create: " + parent);
                requireInsideExtraction(directory, parent);
                checkExtractionCancelled();
                boolean created = android.os.Build.VERSION.SDK_INT >= 21 && TarLinksApi21.create(target, link);
                if (!created) copyTarLinkTarget(directory, target, link.output, preserveTime, new HashSet<>());
                iterator.remove();
                advanced = true;
                FileWorkProgress.advance(1);
            }
            if (!advanced) throw new IOException("Archive link target is missing or cyclic: " + pending.get(0).target);
        }
    }

    private static final class TarLinksApi21 {
        static boolean create(File target, TarLink link) throws IOException {
            try {
                if (link.symbolic) android.system.Os.symlink(link.target, link.output.getAbsolutePath());
                else android.system.Os.link(target.getAbsolutePath(), link.output.getAbsolutePath());
                return true;
            } catch (android.system.ErrnoException error) {
                if (error.errno == android.system.OsConstants.EEXIST) throw new IOException("Archive link destination already exists: " + link.output, error);
                // Shared-storage filesystems may prohibit links. Materialize the
                // referenced contents there instead of aborting valid extraction.
                return false;
            }
        }
    }

    private static void copyTarLinkTarget(File directory, File source, File output, boolean preserveTime, Set<String> ancestors) throws IOException {
        checkExtractionCancelled();
        requireInsideExtraction(directory, source);
        requireInsideExtraction(directory, output);
        if (source.isDirectory()) {
            String path = source.getCanonicalPath();
            if (!ancestors.add(path)) throw new IOException("Cyclic archive directory link: " + source);
            try {
                if (!output.mkdir()) throw new IOException("Cannot create: " + output);
                File[] children = source.listFiles();
                if (children == null) throw new IOException("Cannot read link target: " + source);
                for (File child : children) copyTarLinkTarget(directory, child, new File(output, child.getName()), preserveTime, ancestors);
            } finally { ancestors.remove(path); }
        } else if (source.isFile()) {
            try (InputStream input = new BufferedInputStream(new FileInputStream(source));
                 OutputStream destination = new BufferedOutputStream(new FileOutputStream(output))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkExtractionCancelled();
                    destination.write(buffer, 0, count);
                }
            }
            output.setExecutable(source.canExecute(), false);
        } else throw new IOException("Unsupported archive link target: " + source);
        if (preserveTime) output.setLastModified(source.lastModified());
    }

    private static void extractSingleCompressed(InputStream compressedInput, File destDir, String archiveName, String ext) throws IOException {
        try (InputStream is = compressedInput) {
            String base = archiveName.substring(0, archiveName.length() - ext.length());
            File out = new File(destDir, base);
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                copy(is, os);
            }
            FileWorkProgress.advance(1);
        }
    }

    private static void compressSingle(OutputStream compressedOutput, File source) throws IOException {
        try (OutputStream os = new BufferedOutputStream(compressedOutput)) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(source))) {
                copy(is, os);
            }
        }
    }

    private static void create7z(File output, List<File> sources) throws IOException {
        try (SevenZOutputFile sevenZOutput = new SevenZOutputFile(output)) {
            sevenZOutput.setContentCompression(SevenZMethod.LZMA2);
            for (File source : sources) addToSevenZ(sevenZOutput, source, source.isDirectory() ? source.getName() + "/" : source.getName());
        }
    }

    private static void addToSevenZ(SevenZOutputFile sevenZOutput, File file, String entryName) throws IOException {
        SevenZArchiveEntry entry = sevenZOutput.createArchiveEntry(file, entryName);
        sevenZOutput.putArchiveEntry(entry);
        if (file.isDirectory()) {
            sevenZOutput.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) for (File child : children) addToSevenZ(sevenZOutput, child, entryName + child.getName() + (child.isDirectory() ? "/" : ""));
        } else {
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                copySevenZ(is, sevenZOutput);
            }
            sevenZOutput.closeArchiveEntry();
        }
    }

    private static void copySevenZ(InputStream is, SevenZOutputFile os) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) > 0) {
            checkExtractionCancelled();
            os.write(buffer, 0, length);
            FileWorkProgress.advance(length);
        }
    }

    private static void createTar(OutputStream tarOutput, List<File> sources) throws IOException {
        try (TarArchiveOutputStream taos = new TarArchiveOutputStream(tarOutput)) {
            taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            taos.setAddPaxHeadersForNonAsciiNames(true);
            for (File source : sources) addToTar(taos, source, source.isDirectory() ? source.getName() + "/" : source.getName());
        }
    }

    private static void addToTar(TarArchiveOutputStream taos, File file, String entryName) throws IOException {
        checkExtractionCancelled();
        TarArchiveEntry entry = new TarArchiveEntry(file, entryName);
        taos.putArchiveEntry(entry);
        if (file.isDirectory()) {
            taos.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) for (File child : children) addToTar(taos, child, entryName + child.getName() + (child.isDirectory() ? "/" : ""));
        } else {
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                copy(is, taos);
            }
            taos.closeArchiveEntry();
        }
    }

    static File resolveEntry(File directory, String entryName) throws IOException {
        if (entryName == null || entryName.isEmpty()) throw new IOException("Empty archive path.");
        String cleaned = entryName.replace('\\', '/');
        if (cleaned.startsWith("/") || cleaned.matches("^[A-Za-z]:.*")) throw new IOException("Absolute archive path: " + entryName);
        for (String part : cleaned.split("/")) {
            if (part.equals("..")) throw new IOException("Invalid archive path: " + entryName);
        }
        File output = new File(directory, cleaned);
        String root = directory.getCanonicalPath();
        if (!output.getCanonicalPath().equals(root) && !output.getCanonicalPath().startsWith(root + File.separator)) {
            throw new IOException("Archive path leaves destination: " + entryName);
        }
        return output;
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        while ((length = is.read(buffer)) > 0) {
            checkExtractionCancelled();
            os.write(buffer, 0, length);
            FileWorkProgress.advance(length);
        }
    }
}
