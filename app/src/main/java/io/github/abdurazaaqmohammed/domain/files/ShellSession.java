package io.github.abdurazaaqmohammed.domain.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Streaming shell session. No API-26-only Process methods are needed. */
public final class ShellSession {
    public interface Factory {
        Process start(String marker) throws Exception;
        void killGroup(long pid) throws Exception;
        default void cleanup() { }
    }
    private final Factory factory;
    private final long timeoutSeconds;
    public final BoundedOutput stdout=new BoundedOutput(65536), stderr=new BoundedOutput(65536);
    private final AtomicBoolean started=new AtomicBoolean(), stopping=new AtomicBoolean();
    private volatile Process process;
    private volatile long group;
    public volatile boolean finished, timedOut;
    public volatile int exitCode=-1;
    public ShellSession(Factory factory,long timeoutSeconds) { this.factory=factory; this.timeoutSeconds=timeoutSeconds; }
    public void start() {
        if (!started.compareAndSet(false,true)) return;
        new Thread(() -> {
            try {
                String marker="HEXORA_"+java.util.UUID.randomUUID()+":";
                process=factory.start(marker);
                Thread out=drain(process.getInputStream(),stdout,null), err=drain(process.getErrorStream(),stderr,marker);
                long end=timeoutSeconds==0 ? Long.MAX_VALUE : System.nanoTime()+timeoutSeconds*1_000_000_000L;
                while (true) {
                    try { exitCode=process.exitValue(); break; }
                    catch (IllegalArgumentException running) { }
                    if (System.nanoTime()>=end) { timedOut=true; stop(); }
                    if (stopping.get()) { stopProcess(); break; }
                    Thread.sleep(40);
                }
                if (stopping.get()) stopProcess();
                try { process.getOutputStream().close(); } catch (Exception ignored) { }
                out.join(1500); err.join(1500);
            } catch (Exception e) { stderr.append(String.valueOf(e.getMessage())); }
            finally { factory.cleanup(); finished=true; }
        },"hexora-script-session").start();
    }
    private Thread drain(InputStream input,BoundedOutput output,String marker) {
        Thread thread=new Thread(() -> {
            try (InputStreamReader reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
                if (marker!=null) {
                    StringBuilder first=new StringBuilder(); int c;
                    while (first.length()<256 && (c=reader.read())!=-1 && c!='\n') first.append((char)c);
                    if (first.toString().startsWith(marker)) {
                        try { group=Long.parseLong(first.substring(marker.length()).trim()); } catch (NumberFormatException ignored) { }
                    } else output.append(first+"\n");
                }
                char[] chars=new char[4096]; int count;
                while ((count=reader.read(chars))!=-1) output.append(new String(chars,0,count));
            } catch (IOException ignored) { }
        },"hexora-script-output"); thread.start(); return thread;
    }
    public synchronized void send(String value) throws IOException {
        if (process==null || finished || stopping.get()) throw new IOException("Script is not accepting input.");
        OutputStream stream=process.getOutputStream(); stream.write((value+"\n").getBytes(StandardCharsets.UTF_8)); stream.flush();
    }
    public synchronized void endInput() throws IOException {
        if (process!=null) process.getOutputStream().close();
    }
    public void stop() { stopping.set(true); }
    public boolean wasStopped() { return stopping.get() && !timedOut; }
    private void stopProcess() {
        // The bootstrap emits its process-group id before running user code.
        for (int i=0;group==0 && i<25;i++) try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        if (group>0) try { factory.killGroup(group); } catch (Exception ignored) { }
        if (process!=null) process.destroy();
    }
}
