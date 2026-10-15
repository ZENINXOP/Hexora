package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Uses only system-issued folder grants; never treats an ungranted path as accessible. */
public final class SafAccess {
    private static final String AUTHORITY = "com.android.externalstorage.documents";
    private static Context application;
    private SafAccess() { }

    public static void init(Context context) {
        application = context.getApplicationContext();
        LocalStorage.init(context);
    }

    private static String documentId(File file) {
        if (file == null) return null;
        String path;
        try { path = new java.net.URI(null, null, file.getAbsolutePath().replace('\\', '/'), null).normalize().getPath(); }
        catch (java.net.URISyntaxException e) { return null; }
        String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
        for (String root : new String[]{primary, "/sdcard", "/storage/self/primary"}) {
            if (path.equals(root)) return "primary:";
            if (path.startsWith(root + "/")) return "primary:" + path.substring(root.length() + 1);
        }
        if (path.startsWith("/storage/")) {
            int slash = path.indexOf('/', 9);
            if (slash > 9) {
                String volume = path.substring(9, slash);
                if (!"emulated".equals(volume) && !"self".equals(volume)) return volume + ':' + path.substring(slash + 1);
            }
        }
        return null;
    }

    public static boolean isRestrictedFolder(File file) {
        String id = documentId(file);
        if (id == null) return false;
        String relative = id.substring(id.indexOf(':') + 1);
        return relative.equals("Android/data") || relative.startsWith("Android/data/")
                || relative.equals("Android/obb") || relative.startsWith("Android/obb/");
    }

    private static boolean covers(String tree, String document) {
        return tree.equals(document) || document.startsWith(tree + (tree.endsWith(":") ? "" : "/"));
    }

    public static Uri resolve(File file, boolean write) {
        if (LocalStorage.isPath(file)) return LocalStorage.resolve(file, write);
        String id = documentId(file);
        if (application == null || id == null) return null;
        Uri best = null;
        int bestLength = -1;
        for (UriPermission permission : application.getContentResolver().getPersistedUriPermissions()) {
            Uri tree = permission.getUri();
            if (!permission.isReadPermission() || (write && !permission.isWritePermission())
                    || !AUTHORITY.equals(tree.getAuthority())) continue;
            try {
                String root = DocumentsContract.getTreeDocumentId(tree);
                if (covers(root, id) && root.length() > bestLength) {
                    best = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    bestLength = root.length();
                }
            } catch (IllegalArgumentException ignored) { }
        }
        return best;
    }

    public static Intent folderPicker(File folder) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        String id = documentId(folder);
        if (Build.VERSION.SDK_INT >= 26 && id != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(AUTHORITY, id));
        }
        return intent;
    }

    public static void retainGrant(Context context, Intent result, File requested) throws IOException {
        Uri tree = result == null ? null : result.getData();
        String id = documentId(requested);
        if (tree == null || id == null || !AUTHORITY.equals(tree.getAuthority())) throw new IOException("Select the requested storage folder.");
        String granted;
        try { granted = DocumentsContract.getTreeDocumentId(tree); }
        catch (IllegalArgumentException e) { throw new IOException("Select a storage folder.", e); }
        if (!covers(granted, id)) throw new IOException("The selected folder does not contain " + requested.getName() + '.');
        int flags = result.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) throw new IOException("Folder read permission was not granted.");
        try { context.getContentResolver().takePersistableUriPermission(tree, flags); }
        catch (SecurityException e) { throw new IOException("The system did not allow a lasting folder permission.", e); }
    }

    private static final String[] COLUMNS = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS};

    public static File stat(File path, Uri uri) throws IOException {
        try (Cursor cursor = application.getContentResolver().query(uri, COLUMNS, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("The storage document is unavailable.");
            return new GrantedFile(path.getAbsolutePath(), uri,
                    DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2)),
                    cursor.isNull(3) ? 0 : cursor.getLong(3), cursor.isNull(4) ? 0 : cursor.getLong(4),
                    cursor.isNull(5) ? 0 : cursor.getInt(5), cursor.getString(1));
        } catch (SecurityException e) { throw new IOException("Folder permission was revoked.", e); }
    }

    public static File[] tryList(File folder) {
        Uri document = resolve(folder, false);
        if (document == null) return null;
        try {
            LocalStorage.forgetChildren(folder);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(document, DocumentsContract.getDocumentId(document));
            try (Cursor cursor = application.getContentResolver().query(children, COLUMNS, null, null, null)) {
                if (cursor == null) return null;
                List<File> files = new ArrayList<>();
                java.util.Set<String> names = new java.util.HashSet<>();
                while (cursor.moveToNext()) {
                    String name = cursor.getString(1);
                    if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")) continue;
                    if (LocalStorage.isPath(folder) && !names.add(name)) throw new IOException("The provider returned duplicate file names.");
                    Uri uri = DocumentsContract.buildDocumentUriUsingTree(document, cursor.getString(0));
                    LocalStorage.remember(new File(folder, name), uri);
                    files.add(new GrantedFile(new File(folder, name).getAbsolutePath(), uri,
                            DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2)),
                            cursor.isNull(3) ? 0 : cursor.getLong(3), cursor.isNull(4) ? 0 : cursor.getLong(4),
                            cursor.isNull(5) ? 0 : cursor.getInt(5), name));
                }
                return files.toArray(new File[0]);
            }
        } catch (Exception ignored) { return null; }
    }

    public static InputStream openInput(File file) throws IOException {
        Uri uri = file instanceof GrantedFile ? ((GrantedFile) file).uri : resolve(file, false);
        if (uri == null) throw new IOException("No system folder permission for " + file);
        try {
            InputStream stream = application.getContentResolver().openInputStream(uri);
            if (stream == null) throw new IOException("Cannot read " + file.getName());
            return stream;
        } catch (SecurityException e) { throw new IOException("Folder permission was revoked.", e); }
    }

    public static OutputStream openOutput(File file) throws IOException {
        Uri uri = resolve(file, true);
        if (uri == null) throw new IOException("No folder write permission for " + file);
        try {
            OutputStream stream = application.getContentResolver().openOutputStream(uri, "rwt");
            if (stream == null) throw new IOException("Cannot write " + file.getName());
            return stream;
        } catch (SecurityException e) { throw new IOException("Folder write permission was revoked.", e); }
    }

    public static File stage(Context context, File source) throws IOException {
        if (LocalStorage.isPath(source)) {
            long available = context.getCacheDir().getUsableSpace();
            try (InputStream input = openInput(source)) {
                return io.github.abdurazaaqmohammed.domain.files.SharedFileCopy.copy(input,
                        SharedFileCache.directory(context), source.getName(),
                        available > 0 ? Math.max(0, available - 16L * 1024 * 1024) : Long.MAX_VALUE);
            }
        }
        File directory = new File(context.getCacheDir(), "folder_staging/" + java.util.UUID.randomUUID());
        if (!directory.mkdirs()) throw new IOException("Cannot prepare file viewer");
        File destination = new File(directory, source.getName());
        boolean complete = false;
        try (InputStream input = openInput(source); OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                output.write(buffer, 0, count);
            }
            complete = true;
            return destination;
        } finally {
            if (!complete) { destination.delete(); directory.delete(); }
        }
    }

    public static final class GrantedFile extends File implements io.github.abdurazaaqmohammed.domain.files.FileWorkProgress.ReadableFile {
        private final Uri uri;
        private final boolean directory;
        private final long size, modified;
        private final int flags;
        private final String displayName;
        private GrantedFile(String path, Uri uri, boolean directory, long size, long modified, int flags, String displayName) {
            super(path);
            this.uri = uri;
            this.directory = directory;
            this.size = size;
            this.modified = modified;
            this.flags = flags;
            this.displayName = displayName;
        }
        @Override public String getName() { return displayName == null ? super.getName() : displayName; }
        @Override public File getParentFile() {
            LocalStorage.Mount mount = LocalStorage.mount(this);
            return mount != null && mount.path().equals(getAbsolutePath()) ? this : super.getParentFile();
        }
        @Override public boolean exists() { return true; }
        @Override public InputStream openInput() throws IOException { return SafAccess.openInput(this); }
        @Override public boolean isDirectory() { return directory; }
        @Override public boolean isFile() { return !directory; }
        @Override public boolean isHidden() { return getName().startsWith("."); }
        @Override public boolean canRead() { return resolve(this, false) != null; }
        @Override public boolean canWrite() {
            return resolve(this, true) != null && (flags & (directory ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                    : DocumentsContract.Document.FLAG_SUPPORTS_WRITE)) != 0;
        }
        @Override public long length() { return size; }
        @Override public long lastModified() { return modified; }
        @Override public boolean delete() {
            LocalStorage.Mount mount = LocalStorage.mount(this);
            if (mount != null && mount.path().equals(getAbsolutePath())) return false;
            if ((flags & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) == 0 || resolve(this, true) == null) return false;
            try {
                boolean deleted = DocumentsContract.deleteDocument(application.getContentResolver(), uri);
                if (deleted) LocalStorage.invalidate(this);
                return deleted;
            }
            catch (Exception ignored) { return false; }
        }
        @Override public boolean renameTo(File destination) {
            if (!LocalStorage.isPath(this)) return super.renameTo(destination);
            if ((flags & DocumentsContract.Document.FLAG_SUPPORTS_RENAME) == 0 || resolve(this, true) == null
                    || getParentFile().equals(this) || !getParentFile().equals(destination.getParentFile())) return false;
            try {
                io.github.abdurazaaqmohammed.domain.files.SafeFileOperations.validateName(destination.getName());
                if (LocalStorage.resolve(destination, false) != null) return false;
                Uri renamed = DocumentsContract.renameDocument(application.getContentResolver(), uri, destination.getName());
                if (renamed == null) return false;
                LocalStorage.invalidate(this);
                LocalStorage.remember(destination, renamed);
                return true;
            } catch (Exception ignored) { return false; }
        }
        @Override public File[] listFiles() { return directory ? tryList(this) : null; }
        @Override public File[] listFiles(FileFilter filter) {
            File[] children = listFiles();
            if (children == null || filter == null) return children;
            return java.util.Arrays.stream(children).filter(filter::accept).toArray(File[]::new);
        }
        @Override public File[] listFiles(FilenameFilter filter) {
            File[] children = listFiles();
            if (children == null || filter == null) return children;
            return java.util.Arrays.stream(children).filter(file -> filter.accept(this, file.getName())).toArray(File[]::new);
        }
        @Override public File getAbsoluteFile() { return this; }
    }
}
