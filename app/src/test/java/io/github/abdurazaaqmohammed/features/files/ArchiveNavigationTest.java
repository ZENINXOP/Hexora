package io.github.abdurazaaqmohammed.features.files;

import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class ArchiveNavigationTest {
    @Test public void nestedJarReturnsToContainingAarFolder() {
        ArchiveNavigation navigation = new ArchiveNavigation();
        File aar = new File("owned/fixture.aar").getAbsoluteFile(), staged = new File("cache/staged.jar");
        navigation.register(aar, aar, null, null);
        navigation.register(staged, staged, aar, "libs/nested.jar");
        assertEquals(new NavigationHistoryEntry(aar, true, "libs/"), navigation.parent(staged));
        assertEquals(aar.getPath() + "!libs/nested.jar", navigation.displayPath(staged));
        assertEquals(aar.getParentFile(), navigation.parent(aar).file());
    }
    @Test public void recursiveArchiveParentsSurviveRestorationAndDoNotPointToCache() {
        ArchiveNavigation navigation = new ArchiveNavigation();
        File original = new File("owned/outer.zip").getAbsoluteFile();
        File aar = new File("cache/aar"), jar = new File("cache/jar");
        navigation.register(aar, aar, original, "android/sample.aar");
        navigation.register(jar, jar, aar, "classes.jar");
        ArchiveNavigation restored = new ArchiveNavigation(); restored.restore(navigation.snapshot());
        assertEquals(new NavigationHistoryEntry(aar, true, ""), restored.parent(jar));
        assertEquals(new NavigationHistoryEntry(original, true, "android/"), restored.parent(aar));
        assertEquals(original.getPath() + "!android/sample.aar!classes.jar", restored.displayPath(jar));
    }
    @Test public void rootStagedLocalArchiveReturnsToOriginalDirectory() {
        ArchiveNavigation navigation = new ArchiveNavigation();
        File original = new File("protected/module.aar").getAbsoluteFile(), staged = new File("cache/root-staged");
        navigation.register(staged, original, null, null);
        assertEquals(original.getParentFile(), navigation.parent(staged).file());
        assertFalse(navigation.parent(staged).isZip());
        assertEquals(original.getPath(), navigation.displayPath(staged));
    }
}
