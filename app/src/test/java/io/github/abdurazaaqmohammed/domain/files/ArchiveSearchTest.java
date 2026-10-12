package io.github.abdurazaaqmohammed.domain.files;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;

public class ArchiveSearchTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private File archive(boolean encrypted) throws Exception {
        File archive=new File(temp.getRoot(),java.util.UUID.randomUUID()+".zip");
        try (ZipFile zip=encrypted ? new ZipFile(archive,"secret".toCharArray()) : new ZipFile(archive)) {
            add(zip,"top.txt","TOP\n",encrypted);
            add(zip,"nested/Note.txt","needle here\nSecond NEEDLE\n",encrypted);
            add(zip,"nested/deep/utf16.txt", "\ufeffUnicode café needle\n".getBytes(StandardCharsets.UTF_16LE),encrypted);
            add(zip,"binary.dat",new byte[]{0,1,2,3},encrypted);
        }
        if (encrypted) ArchivePasswords.remember(archive,"secret".toCharArray());
        return archive;
    }
    private static void add(ZipFile zip,String name,String text,boolean encrypted) throws Exception { add(zip,name,text.getBytes(StandardCharsets.UTF_8),encrypted); }
    private static void add(ZipFile zip,String name,byte[] text,boolean encrypted) throws Exception {
        ZipParameters parameters=new ZipParameters(); parameters.setFileNameInZip(name);
        if (encrypted) { parameters.setEncryptFiles(true); parameters.setEncryptionMethod(EncryptionMethod.AES); }
        zip.addStream(new ByteArrayInputStream(text),parameters);
    }
    @Test public void scopedNamesIncludeImplicitDirectoriesAndFilters() throws Exception {
        File zip=archive(false);
        var directories=ArchiveSearch.names(zip,"","nested",false,false,false,"",-1,-1);
        assertEquals("nested/",directories.hits().get(0).getFullPath());
        assertEquals(1,ArchiveSearch.names(zip,"nested",".*txt$",false,false,true,"needle",1,1000).hits().size());
        assertEquals(2,ArchiveSearch.names(zip,"nested/",".txt",true,false,false,"",-1,-1).hits().size());
        assertTrue(ArchiveSearch.names(zip,"","Note",true,true,false,"",1000,-1).hits().isEmpty());
    }
    @Test public void encryptedContentUsesPasswordAndDecodesUtf16() throws Exception {
        File zip=archive(true); byte[] original=Files.readAllBytes(zip.toPath());
        var result=ArchiveSearch.contents(zip,"nested/","needle",false,false);
        assertEquals(3,result.hits().size()); assertFalse(result.limited());
        assertTrue(result.hits().stream().anyMatch(hit->hit.snippet().contains("café")));
        assertEquals(1,ArchiveSearch.contents(zip,"nested/","^Second NEEDLE$",true,true).hits().size());
        assertArrayEquals(original,Files.readAllBytes(zip.toPath()));
    }
    @Test public void outputAndEntrySizeAreBoundedAndInterruptionIsHonored() throws Exception {
        File zip=archive(false);
        try (ZipFile writer=new ZipFile(zip)) {
            add(writer,"many.txt","hit\n".repeat(1000),false);
            add(writer,"large.txt",new byte[(int)FileSearch.MAX_CONTENT_BYTES],false);
        }
        var result=ArchiveSearch.contents(zip,"","hit",false,false);
        assertEquals(FileSearch.MAX_HITS,result.hits().size()); assertTrue(result.limited());
        assertTrue(ArchiveSearch.names(zip,"","large",true,false,false,"x",-1,-1).limited());
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class,()->ArchiveSearch.contents(zip,"","hit",false,false)); }
        finally { Thread.interrupted(); }
        assertThrows(java.util.regex.PatternSyntaxException.class,()->ArchiveSearch.contents(zip,"","[",false,true));
    }
    public static void main(String[] args) throws Exception {
        File directory=new File(args[0]); directory.mkdirs();
        try (ZipFile zip=new ZipFile(new File(directory,"search.zip"))) {
            add(zip,"root.txt","ROOT_TEXT\n",false);
            add(zip,"nested/Note.txt","ARCHIVE_NEEDLE\nSecond needle line\n",false);
            add(zip,"nested/deep/unicode.txt","café ARCHIVE_NEEDLE\n",false);
        }
    }
}
