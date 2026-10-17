package io.github.abdurazaaqmohammed.domain.files;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import io.github.abdurazaaqmohammed.domain.editor.EditorDocumentReader;

/** Searches one archive without extracting it or changing browser navigation. */
public final class ArchiveSearch {
    private ArchiveSearch() { }
    public record Result<T>(List<T> hits, boolean limited) { }
    public record Hit(ZipEntryInfo entry,int line,String snippet) { }
    private static final long TOTAL_CONTENT_LIMIT=64L*1024*1024;
    private static String folder(String path) {
        return path==null || path.isEmpty() ? "" : path.replace('\\','/').replaceAll("/+$","")+"/";
    }
    private static boolean safe(String path) {
        return !path.isEmpty() && !path.startsWith("/") && path.indexOf('\0')<0
                && !Arrays.asList(path.split("/")).contains("..");
    }
    private static Pattern matcher(String query,boolean matchCase,boolean regex) {
        return regex ? SearchPattern.compile(query, matchCase ? 0 : Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE)
                : Pattern.compile(Pattern.quote(query), matchCase ? 0 : Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    }
    private static void interrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Search cancelled");
    }
    private static final class Budget {
        long remaining=TOTAL_CONTENT_LIMIT;
        boolean limited;
    }
    private static String text(ZipFile zip,FileHeader header,Budget budget) throws IOException {
        if (header.getUncompressedSize()>=FileSearch.MAX_CONTENT_BYTES || header.getUncompressedSize()<0) {
            budget.limited=true; return null;
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try (InputStream input=zip.getInputStream(header)) {
            byte[] buffer=new byte[8192]; int count;
            while ((count=input.read(buffer))!=-1) {
                interrupted(); budget.remaining-=count;
                if (budget.remaining<0 || (long)bytes.size()+count>=FileSearch.MAX_CONTENT_BYTES) {
                    budget.limited=true; return null;
                }
                bytes.write(buffer,0,count);
            }
        }
        byte[] raw=bytes.toByteArray();
        EditorDocumentReader.Result document=EditorDocumentReader.read(new ByteArrayInputStream(raw),raw.length);
        if (document.mode==EditorDocumentReader.Mode.PREVIEW) budget.limited=true;
        if (document.isReadOnly() || document.text.indexOf('\0')>=0) return null;
        return document.text;
    }
    public static Result<ZipEntryInfo> names(File archive,String scope,String query,boolean recursive,
            boolean matchCase,boolean regex,String content,long minSize,long maxSize) throws IOException {
        Pattern names=matcher(query,matchCase,regex), inside=matcher(content==null ? "" : content,matchCase,false);
        String prefix=folder(scope); List<ZipEntryInfo> hits=new ArrayList<>(); Budget budget=new Budget();
        try (ZipFile zip=ArchivePasswords.open(archive)) {
            Map<String,ZipEntryInfo> entries=new LinkedHashMap<>(); Map<String,FileHeader> headers=new HashMap<>();
            for (FileHeader header:zip.getFileHeaders()) {
                interrupted(); String path=header.getFileName().replace('\\','/');
                if (!safe(path)) continue;
                headers.put(path,header); entries.put(path,new ZipEntryInfo(header,archive,""));
                for (int slash=path.indexOf('/');slash>=0;slash=path.indexOf('/',slash+1)) {
                    String directory=path.substring(0,slash+1);
                    if (!entries.containsKey(directory)) entries.put(directory,new ZipEntryInfo(
                            path.substring(path.lastIndexOf('/',slash-1)+1,slash),directory,true,0,0,archive));
                }
            }
            for (ZipEntryInfo entry:entries.values()) {
                if (budget.remaining<=0) { budget.limited=true; break; }
                interrupted(); String path=entry.getFullPath();
                if (!path.startsWith(prefix) || path.equals(prefix)) continue;
                String relative=path.substring(prefix.length()).replaceAll("/+$","");
                if (!recursive && relative.indexOf('/')>=0 || !SearchPattern.find(names, entry.getName())) continue;
                if (!entry.isDirectory() && (minSize>=0 && entry.getSize()<minSize || maxSize>=0 && entry.getSize()>maxSize)) continue;
                if (content!=null && !content.isEmpty()) {
                    if (entry.isDirectory()) continue;
                    String text=text(zip,headers.get(path),budget);
                    if (text==null || !SearchPattern.find(inside, text)) continue;
                }
                hits.add(entry);
                if (hits.size()>=FileSearch.MAX_HITS) { budget.limited=true; break; }
                if (budget.remaining<=0) { budget.limited=true; break; }
            }
        }
        return new Result<>(hits,budget.limited);
    }
    public static Result<Hit> contents(File archive,String scope,String query,boolean matchCase,boolean regex) throws IOException {
        Pattern pattern=matcher(query,matchCase,regex); String prefix=folder(scope);
        List<Hit> hits=new ArrayList<>(); Budget budget=new Budget();
        try (ZipFile zip=ArchivePasswords.open(archive)) {
            outer: for (FileHeader header:zip.getFileHeaders()) {
                interrupted(); String path=header.getFileName().replace('\\','/');
                if (!safe(path) || !path.startsWith(prefix) || header.isDirectory()) continue;
                String text=text(zip,header,budget);
                if (budget.remaining<=0) { budget.limited=true; break; }
                if (text==null) continue;
                try (BufferedReader reader=new BufferedReader(new StringReader(text))) {
                    int number=0; String line;
                    while ((line=reader.readLine())!=null) {
                        interrupted(); number++;
                        if (number>FileSearch.MAX_LINES_PER_FILE) { budget.limited=true; break; }
                        if (SearchPattern.find(pattern, line)) {
                            String snippet=line.trim();
                            if (snippet.length()>FileSearch.SNIPPET_LEN) snippet=snippet.substring(0,FileSearch.SNIPPET_LEN)+"…";
                            hits.add(new Hit(new ZipEntryInfo(header,archive,""),number,snippet));
                            if (hits.size()>=FileSearch.MAX_HITS) { budget.limited=true; break outer; }
                        }
                    }
                }
            }
        }
        return new Result<>(hits,budget.limited);
    }
}
