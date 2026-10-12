package io.github.abdurazaaqmohammed.domain.files;

/** Keeps a bounded tail even when a process writes one enormous line. */
public final class BoundedOutput {
    private final int limit;
    private final StringBuilder text=new StringBuilder();
    private boolean truncated;
    public BoundedOutput(int limit) { if (limit<1) throw new IllegalArgumentException(); this.limit=limit; }
    public synchronized void append(String value) {
        if (value.length()>=limit) { text.setLength(0); text.append(value,value.length()-limit,value.length()); truncated=true; }
        else { text.append(value); if (text.length()>limit) { text.delete(0,text.length()-limit); truncated=true; } }
    }
    public synchronized String snapshot() { return (truncated ? "[Earlier output omitted]\n" : "")+text; }
    public synchronized int length() { return text.length(); }
}
