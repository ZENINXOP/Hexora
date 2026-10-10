package io.github.abdurazaaqmohammed.domain.files;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class FileTypeTest {
    @Test public void requestedTypesHaveDifferentIcons() {
        FileType[] types = {FileType.forPath("main.py"), FileType.forPath("layout.xml"),
                FileType.forPath("Main.java"), FileType.forPath("notes.txt")};
        for (int i = 0; i < types.length; i++) {
            assertNotNull(types[i]);
            for (int j = i + 1; j < types.length; j++) assertNotEquals(types[i].label, types[j].label);
        }
    }
    @Test public void archiveAndWindowsPathsUseFilenameOnly() {
        assertEquals(FileType.PYTHON, FileType.forPath("archive.zip/src/main.PY"));
        assertEquals(FileType.XML, FileType.forPath("C:\\folder.java\\LAYOUT.XML"));
        assertNull(FileType.forPath("/folder.py/no-extension"));
    }
    @Test public void commonDeveloperAndDocumentFormatsAreRecognized() {
        assertEquals(FileType.KOTLIN, FileType.forPath("build.kts"));
        assertEquals(FileType.JSON, FileType.forPath("settings.json"));
        assertEquals(FileType.CONFIG, FileType.forPath(".env"));
        assertEquals(FileType.CONFIG, FileType.forPath("Dockerfile"));
        assertEquals(FileType.WORD, FileType.forPath("report.docx"));
        assertEquals(FileType.SHEET, FileType.forPath("data.xlsx"));
        assertEquals(FileType.FONT, FileType.forPath("font.woff2"));
        assertEquals(FileType.SMALI, FileType.forPath("Main.smali"));
    }
    @Test public void matchingDoesNotDependOnDeviceLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals(FileType.CONFIG, FileType.forPath("SETTINGS.INI"));
        } finally { Locale.setDefault(previous); }
    }
    @Test public void unknownTypesKeepExistingFallback() {
        assertNull(FileType.forPath(null));
        assertNull(FileType.forPath(""));
        assertNull(FileType.forPath("file."));
        assertNull(FileType.forPath("image.png"));
        assertNull(FileType.forPath("archive.zip"));
        assertNull(FileType.forPath("app.apk"));
        assertNull(FileType.forPath("unknown.weird"));
    }
}
