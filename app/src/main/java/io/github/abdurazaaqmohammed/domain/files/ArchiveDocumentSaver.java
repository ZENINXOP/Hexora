package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.io.IOException;
import java.util.List;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;

/** Publishes all saved editor entries together, retaining the original on failure. */
public final class ArchiveDocumentSaver {
    private ArchiveDocumentSaver() { }

    public record Update(String entry, File file, boolean requireExisting) { }
    public interface Prepare { void run(File staged) throws Exception; }

    public static synchronized File save(File archive, List<Update> updates, Prepare prepare,
            SafeFileOperations.AtomicReplacement replacement) throws Exception {
        if (!archive.isFile() || updates.isEmpty()) throw new IOException("Archive or saved entries are unavailable.");
        long originalSize = archive.length();
        long originalTime = archive.lastModified();
        char[] password = ArchivePasswords.get(archive);
        File staged = SafeFileOperations.unusedFile(new File(archive.getAbsoluteFile().getParentFile(),
                ".hexora-update-" + java.util.UUID.randomUUID() + ".zip"));
        try {
            SafeFileOperations.copyFile(archive, staged);
            try (ZipFile zip = password == null ? new ZipFile(staged) : new ZipFile(staged, password)) {
                if (!zip.isValidZipFile()) throw new IOException("The archive is invalid; original retained.");
                // Validate the whole batch before changing any staged entries.
                for (Update update : updates) {
                    if (update.file() == null || !update.file().isFile()) throw new IOException("Saved entry is unavailable.");
                    String entry = update.entry();
                    if (entry == null || entry.isEmpty() || entry.startsWith("/") || entry.indexOf('\0') >= 0
                            || entry.endsWith("/") || entry.replace('\\', '/').matches(".*(?:^|/)\\.\\.(?:/|$).*")) {
                        throw new IOException("Invalid archive entry: " + entry);
                    }
                    if (update.requireExisting() && zip.getFileHeader(entry) == null) {
                        throw new IOException("Archive entry no longer exists: " + entry);
                    }
                }
                for (Update update : updates) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    ZipParameters parameters = new ZipParameters();
                    parameters.setFileNameInZip(update.entry());
                    net.lingala.zip4j.model.FileHeader previous = zip.getFileHeader(update.entry());
                    if (previous != null && previous.isEncrypted()) {
                        if (password == null) throw new IOException("Unlock the archive before saving encrypted entries.");
                        parameters.setEncryptFiles(true);
                        parameters.setEncryptionMethod(previous.getEncryptionMethod());
                        if (previous.getAesExtraDataRecord() != null) parameters.setAesKeyStrength(previous.getAesExtraDataRecord().getAesKeyStrength());
                    }
                    String name = update.entry().substring(update.entry().lastIndexOf('/') + 1);
                    if (name.equals("AndroidManifest.xml") || name.equals("resources.arsc")) {
                        parameters.setCompressionMethod(CompressionMethod.STORE);
                    }
                    zip.addFile(update.file(), parameters);
                }
            }
            if (prepare != null) prepare.run(staged);
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            if (archive.length() != originalSize || archive.lastModified() != originalTime) {
                throw new IOException("Archive changed while saving; reload it before trying again.");
            }
            File backup = SafeFileOperations.unusedFile(new File(archive.getParentFile(), archive.getName() + ".bak"));
            SafeFileOperations.copyFile(archive, backup);
            replacement.replace(staged, archive);
            if (password != null) ArchivePasswords.remember(archive, password);
            return backup;
        } finally {
            if (staged.exists()) staged.delete();
            if (password != null) java.util.Arrays.fill(password, '\0');
        }
    }
}
