package io.github.abdurazaaqmohammed.domain.files;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;

public class ArchivePasswordTest {
    public static void main(String[] args) throws Exception {
        File directory=new File(args[0]); directory.mkdirs();
        File plain=new File(directory,"plain.txt"); Files.write(plain.toPath(),"ENCRYPTED_OK\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (EncryptionMethod method:List.of(EncryptionMethod.AES,EncryptionMethod.ZIP_STANDARD)) {
            File archive=new File(directory,method==EncryptionMethod.AES ? "aes.zip" : "zipcrypto.zip");
            if (archive.exists()) archive.delete();
            ZipParameters parameters=new ZipParameters(); parameters.setFileNameInZip("nested/secret.txt");
            parameters.setEncryptFiles(true); parameters.setEncryptionMethod(method);
            try (ZipFile zip=new ZipFile(archive,"secret".toCharArray())) { zip.addFile(plain,parameters); }
        }
    }
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    private File encrypted(EncryptionMethod method) throws Exception {
        File source=folder.newFile(); Files.write(source.toPath(),new byte[]{1,2,3,4});
        File archive=new File(folder.getRoot(),java.util.UUID.randomUUID()+".zip");
        ZipParameters parameters=new ZipParameters(); parameters.setFileNameInZip("note.txt");
        parameters.setEncryptFiles(true); parameters.setEncryptionMethod(method);
        try (ZipFile zip=new ZipFile(archive,"secret".toCharArray())) { zip.addFile(source,parameters); }
        return archive;
    }
    @Test public void aesAndZipCryptoReadWithSessionPassword() throws Exception {
        for (EncryptionMethod method:List.of(EncryptionMethod.AES,EncryptionMethod.ZIP_STANDARD)) {
            File archive=encrypted(method); ArchivePasswords.remember(archive,"secret".toCharArray());
            try (ZipFile zip=ArchivePasswords.open(archive); InputStream input=zip.getInputStream(zip.getFileHeader("note.txt"))) {
                assertArrayEquals(new byte[]{1,2,3,4},input.readAllBytes());
            }
        }
    }
    @Test public void changedArchiveForgetsPasswordAndReturnsDefensiveCopies() throws Exception {
        File archive=encrypted(EncryptionMethod.AES); char[] password="secret".toCharArray();
        ArchivePasswords.remember(archive,password); password[0]='X';
        assertEquals('s',ArchivePasswords.get(archive)[0]);
        Files.write(archive.toPath(),new byte[]{0}); assertNull(ArchivePasswords.get(archive));
    }
    @Test public void archivePathAliasesShareAndForgetTheSamePassword() throws Exception {
        File archive=encrypted(EncryptionMethod.AES);
        File alias=new File(archive.getParentFile(),"."+File.separator+archive.getName());
        ArchivePasswords.remember(alias,"secret".toCharArray());
        assertArrayEquals("secret".toCharArray(),ArchivePasswords.get(archive));
        ArchivePasswords.forget(archive);
        assertNull(ArchivePasswords.get(alias));
    }
    @Test public void editingEncryptedEntryKeepsEncryptionAndOriginalBackup() throws Exception {
        File archive=encrypted(EncryptionMethod.AES); byte[] original=Files.readAllBytes(archive.toPath());
        ArchivePasswords.remember(archive,"secret".toCharArray());
        File edit=folder.newFile(); Files.write(edit.toPath(),new byte[]{8,9});
        File backup=ArchiveDocumentSaver.save(archive,List.of(new ArchiveDocumentSaver.Update("note.txt",edit,true)),null,
                (temporary,destination)->Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING));
        assertArrayEquals(original,Files.readAllBytes(backup.toPath()));
        try (ZipFile zip=ArchivePasswords.open(archive)) {
            assertTrue(zip.getFileHeader("note.txt").isEncrypted());
            try (InputStream input=zip.getInputStream(zip.getFileHeader("note.txt"))) { assertArrayEquals(new byte[]{8,9},input.readAllBytes()); }
        }
    }
}
