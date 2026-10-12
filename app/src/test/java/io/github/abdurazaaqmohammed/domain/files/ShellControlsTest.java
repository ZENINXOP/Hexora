package io.github.abdurazaaqmohammed.domain.files;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import java.io.File;

public class ShellControlsTest {
    @Test public void argumentsStayLiteralAndQuotesPreserveSpaces() throws Exception {
        assertEquals(List.of("one","two words","","a b","$(id)"),ShellWords.parse("one 'two words' \"\" a\\ b $(id)"));
        assertEquals("'a'\\''b'",ShellWords.quote("a'b"));
        assertThrows(java.io.IOException.class,()->ShellWords.parse("'open"));
    }
    @Test public void hugeSingleLineKeepsOnlyABoundedTail() {
        BoundedOutput output=new BoundedOutput(128);
        output.append("x".repeat(1000000)); output.append("END");
        assertTrue(output.length()<=128); assertTrue(output.snapshot().endsWith("END"));
        assertTrue(output.snapshot().startsWith("[Earlier output omitted]"));
    }
    private ShellSession session(String mode,long timeout) {
        return new ShellSession(new ShellSession.Factory() {
            Process child;
            @Override public Process start(String marker) throws Exception {
                String java=new File(System.getProperty("java.home"),"bin/"+(System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")).getAbsolutePath();
                String cp=new File(Child.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
                child=new ProcessBuilder(java,"-cp",cp,Child.class.getName(),marker,mode).start(); return child;
            }
            @Override public void killGroup(long pid) { child.destroy(); }
        },timeout);
    }
    private void await(java.util.function.BooleanSupplier predicate) throws Exception {
        long deadline=System.currentTimeMillis()+10000;
        while (!predicate.getAsBoolean() && System.currentTimeMillis()<deadline) Thread.sleep(20);
        assertTrue(predicate.getAsBoolean());
    }
    @Test public void liveInputAndExitCodeAreRetained() throws Exception {
        ShellSession session=session("input",0); session.start();
        await(()->session.stdout.snapshot().contains("READY"));
        assertFalse(session.finished); session.send("hello world");
        await(()->session.finished);
        assertTrue(session.stdout.snapshot().contains("ECHO:hello world")); assertEquals(7,session.exitCode);
    }
    @Test public void stopAndTimeoutReleaseTheProcess() throws Exception {
        ShellSession stopped=session("wait",0); stopped.start(); await(()->stopped.stdout.snapshot().contains("READY"));
        stopped.stop(); await(()->stopped.finished); assertTrue(stopped.wasStopped());
        ShellSession timed=session("wait",1); timed.start(); await(()->timed.finished); assertTrue(timed.timedOut);
    }
    public static class Child {
        public static void main(String[] args) throws Exception {
            System.err.println(args[0]+"12345"); System.err.flush();
            System.out.println("READY"); System.out.flush();
            if (args[1].equals("input")) {
                String line=new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
                System.out.println("ECHO:"+line); System.exit(7);
            } else Thread.sleep(60000);
        }
    }
}
