package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.github.abdurazaaqmohammed.domain.files.FileWorkProgress;
import io.github.abdurazaaqmohammed.domain.files.SafeFileOperations;

/** Persisted system folder grants, including folders exposed by other apps. */
public final class LocalStorage {
    public static final int PICK_FOLDER = 9023;
    private static final String PREFIX = "/@local/";
    private static Context app;
    private static List<Mount> savedMounts;
    private static final ConcurrentHashMap<String, Uri> documents = new ConcurrentHashMap<>();

    public record Mount(String id, String name, String provider, Uri tree) {
        public String path() { return PREFIX + id; }
    }

    private LocalStorage() { }
    public static synchronized void init(Context context) {
        app = context.getApplicationContext();
        savedMounts = null;
    }
    public static boolean isPath(File file) { return file != null && file.getAbsolutePath().startsWith(PREFIX); }

    public static synchronized List<Mount> mounts() {
        if (savedMounts != null) return new ArrayList<>(savedMounts);
        List<Mount> result = new ArrayList<>();
        if (app == null) return result;
        try {
            JSONArray array = new JSONArray(app.getSharedPreferences("local_storage", Context.MODE_PRIVATE).getString("mounts", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                result.add(new Mount(row.getString("id"), row.getString("name"), row.optString("provider"), Uri.parse(row.getString("tree"))));
            }
        } catch (Exception ignored) { }
        savedMounts = new ArrayList<>(result);
        return result;
    }

    private static synchronized void save(List<Mount> mounts) throws IOException {
        try {
            JSONArray array = new JSONArray();
            for (Mount mount : mounts) array.put(new JSONObject().put("id", mount.id()).put("name", mount.name())
                    .put("provider", mount.provider()).put("tree", mount.tree().toString()));
            if (!app.getSharedPreferences("local_storage", Context.MODE_PRIVATE).edit().putString("mounts", array.toString()).commit())
                throw new IOException("Cannot save local storage.");
            savedMounts = new ArrayList<>(mounts);
        } catch (org.json.JSONException e) { throw new IOException(e); }
    }

    public static synchronized Mount add(Intent result) throws IOException {
        Uri tree = result == null ? null : result.getData();
        if (tree == null || !"content".equals(tree.getScheme()) || !DocumentsContract.isTreeUri(tree))
            throw new IOException("Select a folder using the system picker.");
        int flags = result.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) throw new IOException("Folder read permission was not granted.");
        try {
            app.getContentResolver().takePersistableUriPermission(tree, flags);
            Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            String name;
            try (Cursor cursor = app.getContentResolver().query(root, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst() || !DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(1)))
                    throw new IOException("The selected folder is unavailable.");
                name = cursor.getString(0);
            }
            String provider = tree.getAuthority();
            android.content.pm.ProviderInfo info = app.getPackageManager().resolveContentProvider(provider, 0);
            if (info != null) provider = info.loadLabel(app.getPackageManager()).toString();
            if (name == null || name.trim().isEmpty()) name = provider;
            List<Mount> mounts = mounts();
            for (Mount mount : mounts) if (mount.tree().equals(tree)) {
                documents.put(mount.path(), root);
                return mount;
            }
            Mount mount = new Mount(UUID.randomUUID().toString(), name, provider, tree);
            mounts.add(mount);
            save(mounts);
            documents.put(mount.path(), root);
            return mount;
        } catch (SecurityException | IllegalArgumentException e) {
            throw new IOException("The provider did not allow lasting folder access. Select the folder again.", e);
        }
    }

    public static synchronized void remove(String path) throws IOException {
        List<Mount> mounts = mounts();
        mounts.removeIf(mount -> mount.path().equals(path));
        save(mounts);
        documents.keySet().removeIf(key -> key.equals(path) || key.startsWith(path + "/"));
        // Other features may use the same persisted system grant. Removing a sidebar
        // entry must not revoke their access or delete anything in the provider.
    }

    public static Mount mount(File file) {
        if (!isPath(file)) return null;
        String path = file.getAbsolutePath();
        for (Mount mount : mounts()) if (path.equals(mount.path()) || path.startsWith(mount.path() + "/")) return mount;
        return null;
    }

    public static String displayPath(String path) {
        Mount mount = mount(new File(path));
        return mount == null ? path : mount.name() + path.substring(mount.path().length()) + (path.equals(mount.path()) ? "/" : "");
    }

    private static boolean hasGrant(Mount mount, boolean write) {
        for (UriPermission permission : app.getContentResolver().getPersistedUriPermissions())
            if (permission.getUri().equals(mount.tree()) && permission.isReadPermission() && (!write || permission.isWritePermission())) return true;
        return false;
    }

    public static Uri resolve(File file, boolean write) {
        Mount mount = mount(file);
        if (mount == null || !hasGrant(mount, write)) return null;
        String path = file.getAbsolutePath();
        // A canonical boundary check rejects ../ traversal outside the selected root.
        try {
            if (!path.equals(file.getCanonicalPath())) return null;
            Uri current = DocumentsContract.buildDocumentUriUsingTree(mount.tree(), DocumentsContract.getTreeDocumentId(mount.tree()));
            String prefix = mount.path();
            if (path.equals(prefix)) return current;
            for (String name : path.substring(prefix.length() + 1).split("/")) {
                prefix += "/" + name;
                Uri cached = documents.get(prefix);
                if (cached != null) { current = cached; continue; }
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(current, DocumentsContract.getDocumentId(current));
                Uri found = null;
                try (Cursor cursor = app.getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null) while (cursor.moveToNext()) if (name.equals(cursor.getString(1))) {
                        if (found != null) return null; // Ambiguous provider names must never target the wrong document.
                        found = DocumentsContract.buildDocumentUriUsingTree(mount.tree(), cursor.getString(0));
                    }
                }
                if (found == null) return null;
                documents.put(prefix, found);
                current = found;
            }
            return current;
        } catch (Exception e) { return null; }
    }

    public static void remember(File file, Uri uri) { if (isPath(file)) documents.put(file.getAbsolutePath(), uri); }
    public static void invalidate(File file) {
        String path = file.getAbsolutePath();
        documents.keySet().removeIf(key -> key.equals(path) || key.startsWith(path + "/"));
    }

    public static void forgetChildren(File folder) {
        if (isPath(folder)) documents.keySet().removeIf(key -> key.startsWith(folder.getAbsolutePath() + "/"));
    }

    public static File file(File path) throws IOException {
        if (!isPath(path)) return path;
        Uri uri = resolve(path, false);
        if (uri == null) throw new IOException("Local storage is unavailable or permission was revoked. Remove it and add the folder again.");
        return SafAccess.stat(path, uri);
    }

    public static File create(File parent, String name, boolean directory) throws IOException {
        SafeFileOperations.validateName(name);
        Uri parentUri = resolve(parent, true);
        if (parentUri == null) throw new IOException("This storage is read-only or its permission was revoked.");
        File target = new File(parent, name);
        if (resolve(target, false) != null) throw new IOException("Destination already exists: " + name);
        try {
            Uri created = DocumentsContract.createDocument(app.getContentResolver(), parentUri,
                    directory ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream", name);
            if (created == null) throw new IOException("The provider could not create " + name);
            remember(target, created);
            return SafAccess.stat(target, created);
        } catch (Exception e) { throw new IOException("Cannot create " + name, e); }
    }

    /** Copies into or out of a provider; a move verifies the copy before deleting its source. */
    public static void transfer(File source, File folder, boolean move) throws IOException {
        transferTo(source, new File(folder, source.getName()), move);
    }

    public static void transferTo(File source, File destination, boolean move) throws IOException {
        source = file(source);
        File folder = file(destination.getParentFile());
        guardDestination(source, folder);
        SafeFileOperations.validateDestination(source, destination);
        Set<String> names = new HashSet<>();
        File[] children = folder.listFiles();
        if (children == null) throw new IOException("Cannot list the destination folder.");
        for (File child : children) names.add(child.getName());
        String name = destination.getName();
        if (names.contains(name)) throw new IOException("Destination already exists: " + name);
        if (isPath(folder)) destination = copyIntoProvider(source, folder, name, new HashSet<>());
        else if (source.isDirectory()) SafeFileOperations.copyDirectory(source, destination);
        else SafeFileOperations.copyFile(source, destination);
        if (move) {
            SafeFileOperations.verifyCopy(source, destination);
            SafeFileOperations.delete(source);
        }
    }

    private static File physicalPath(File file) {
        if (!isPath(file)) return file;
        Uri uri = resolve(file, false);
        if (uri == null || !"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
        String id = DocumentsContract.getDocumentId(uri);
        int colon = id.indexOf(':');
        if (colon < 0) return null;
        File root = "primary".equals(id.substring(0, colon)) ? android.os.Environment.getExternalStorageDirectory()
                : new File("/storage", id.substring(0, colon));
        return new File(root, id.substring(colon + 1));
    }

    private static void guardDestination(File source, File folder) throws IOException {
        if (!source.isDirectory()) return;
        File physicalSource = physicalPath(source), physicalFolder = physicalPath(folder);
        if (physicalSource != null && physicalFolder != null) {
            String src = physicalSource.getCanonicalPath(), dst = physicalFolder.getCanonicalPath();
            if (src.equals(dst) || dst.startsWith(src + "/")) throw new IOException("A folder cannot be copied or moved into itself.");
        }
        if (!isPath(source) || !isPath(folder)) return;
        Uri src = resolve(source, false), dst = resolve(folder, false);
        if (src == null || dst == null || !src.getAuthority().equals(dst.getAuthority())) return;
        if (DocumentsContract.getDocumentId(src).equals(DocumentsContract.getDocumentId(dst)))
            throw new IOException("A folder cannot be copied or moved into itself.");
        if (!mount(source).id().equals(mount(folder).id())) {
            if (android.os.Build.VERSION.SDK_INT < 24)
                throw new IOException("Use a local folder when transferring between overlapping storage entries.");
            boolean nested;
            try { nested = DocumentsContract.isChildDocument(app.getContentResolver(), src, dst); }
            catch (Exception e) { throw new IOException("This provider cannot check overlapping folders. Transfer through a local folder.", e); }
            if (nested) throw new IOException("A folder cannot be copied or moved into itself.");
        }
    }

    private static File copyIntoProvider(File source, File folder, String name, Set<String> ancestors) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Operation cancelled.");
        File target = create(folder, name, source.isDirectory());
        boolean complete = false;
        try {
            if (source.isDirectory()) {
                if (!ancestors.add(source.getCanonicalPath())) throw new IOException("Directory cycle: " + source);
                try {
                    File[] children = source.listFiles();
                    if (children == null) throw new IOException("Cannot read " + source.getName());
                    for (File child : children) copyIntoProvider(child, target, child.getName(), ancestors);
                } finally { ancestors.remove(source.getCanonicalPath()); }
            } else {
                long copied = 0, expected = source.length(), modified = source.lastModified();
                try (InputStream input = FileWorkProgress.openInput(source); OutputStream output = SafAccess.openOutput(target)) {
                    byte[] buffer = new byte[256 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Operation cancelled.");
                        output.write(buffer, 0, count);
                        copied += count;
                        FileWorkProgress.advance(count);
                    }
                }
                if (copied != expected || source.length() != expected || source.lastModified() != modified)
                    throw new IOException("Source changed while copying: " + source.getName());
            }
            if (Thread.currentThread().isInterrupted()) throw new IOException("Operation cancelled.");
            File published = file(target); // Refresh provider size before verifying a move.
            complete = true;
            FileWorkProgress.advance(1);
            return published;
        } finally {
            if (!complete) {
                // Delete only the document created by this operation; never the source.
                target.delete();
                invalidate(target);
            }
        }
    }
}
