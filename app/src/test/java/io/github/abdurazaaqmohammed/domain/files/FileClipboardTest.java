package io.github.abdurazaaqmohammed.domain.files;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;
import java.util.List;

public class FileClipboardTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void nestedSelectionsAndAliasesTransferOnlyOnce() throws Exception {
        File folder=temp.newFolder("folder"), child=new File(folder,"note.txt"); assertTrue(child.createNewFile());
        FileClipboard clipboard=new FileClipboard();
        clipboard.set(List.of(child,folder,new File(folder,".")),false);
        assertEquals(List.of(folder),clipboard.snapshot().files());
        assertThrows(UnsupportedOperationException.class,()->clipboard.snapshot().files().clear());
    }
    @Test public void partiallyCompletedCutsRetainUnfinishedItemsAndRespectNewSelections() throws Exception {
        File first=temp.newFile(), second=temp.newFile(); FileClipboard clipboard=new FileClipboard();
        clipboard.set(List.of(first,second),true); var job=clipboard.snapshot();
        clipboard.completed(job,List.of(first)); assertEquals(List.of(second),clipboard.snapshot().files());
        clipboard.set(List.of(first),false); clipboard.completed(job,List.of(second));
        assertEquals(List.of(first),clipboard.snapshot().files());
        clipboard.completed(clipboard.snapshot(),List.of(first)); assertFalse(clipboard.snapshot().files().isEmpty());
        clipboard.clear(); assertTrue(clipboard.snapshot().files().isEmpty());
    }
}
