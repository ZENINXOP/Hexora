package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.*;

public class FileSearchTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File file(String name,String text) throws Exception {
        File file = new File(tmp.getRoot(),name);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(),text.getBytes(StandardCharsets.UTF_8));
        return file;
    }
    private List<File> search(String name, boolean recurse, boolean matchCase, boolean regex, String content, long min, long max) {
        return FileSearch.searchByName(tmp.getRoot(),name,recurse,matchCase,regex,content,min,max,f->null);
    }
    @Test public void recursiveAndFlatScopes() throws Exception {
        file("top.txt","top"); file("nested/deep.txt","deep");
        assertEquals(1,search(".txt",false,false,false,"",-1,-1).size());
        assertEquals(2,search(".txt",true,false,false,"",-1,-1).size());
    }
    @Test public void caseInsensitiveSearchIgnoresDeviceLocale() throws Exception {
        file("INDEX.txt","text"); Locale original=Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr","TR"));
            assertEquals(1,search("index",false,false,false,"",-1,-1).size());
        } finally { Locale.setDefault(original); }
    }
    @Test public void contentSearchExcludesDirectoryMatches() throws Exception {
        file("notes/notes.txt","needle"); file("other.txt","different");
        assertEquals(1,search("notes",true,false,false,"needle",-1,-1).size());
    }
    @Test public void sizeFiltersAndRegex() throws Exception {
        file("a.txt","123"); file("b.txt","12345678"); file("c.bin","123");
        assertEquals(1,search("\\.txt$",false,false,true,"",4,10).size());
        assertTrue(search("[",false,false,true,"",-1,-1).isEmpty());
    }
    @Test public void contentHitsHaveCorrectLineNumbersAndIgnoreBinary() throws Exception {
        file("notes.txt","first\nNeedle\nlast"); file("binary.bin","\0Needle");
        List<ContentHit> hits=FileSearch.findInFiles(tmp.getRoot(),"needle",false,false,null);
        assertEquals(1,hits.size()); assertEquals(2,hits.get(0).line());
    }
    @Test public void interruptedSearchStops() throws Exception {
        file("notes.txt","needle"); Thread.currentThread().interrupt();
        try { assertTrue(search("notes",true,false,false,"",-1,-1).isEmpty()); }
        finally { Thread.interrupted(); }
    }
    @Test public void missingDirectoryAndNullListerFailGracefully() {
        assertTrue(FileSearch.searchByName(new File(tmp.getRoot(),"missing"),"x",true,false,false,"",-1,-1,null).isEmpty());
    }
}
