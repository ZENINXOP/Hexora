package app.hexora.sharefixture;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        TextView result = new TextView(this);
        result.setText("RESOURCE: " + getString(R.string.split_probe));
        view.addView(result);
        setContentView(view);
        String mode = getIntent().getStringExtra("mode");
        if (mode != null) {
            try { share(mode); } catch (Exception e) { result.setText("FIXTURE FAILED: " + e); }
        }
    }
    private Uri uri(String name) {
        return new Uri.Builder().scheme("content").authority("app.hexora.sharefixture.provider")
                .appendPath("media").appendPath("Android").appendPath("data").appendPath("org.telegram.messenger")
                .appendPath("files").appendPath("Telegram").appendPath("Telegram Files").appendPath(name).build();
    }
    private void write(String name, String content) throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(getFilesDir(), name))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
    private void share(String mode) throws Exception {
        write("Grant note.txt", "URI_GRANT_OK\n");
        write("Second note.txt", "MULTI_GRANT_OK\n");
        write("277449322868.pdf", "%PDF-1.4\n% Owned Telegram-shaped grant fixture\n%%EOF\n");
        Uri first = uri(mode.equals("pdf") ? "277449322868.pdf" : mode.equals("missing") ? "missing.txt" : "Grant note.txt");
        Intent outgoing = new Intent(mode.equals("multi") ? Intent.ACTION_SEND_MULTIPLE
                : mode.equals("view") || mode.equals("pdf") ? Intent.ACTION_VIEW : Intent.ACTION_SEND);
        outgoing.setClassName("com.hexora.zenin", "io.github.abdurazaaqmohammed.player.LocateActivity");
        outgoing.setType(mode.equals("pdf") ? "application/pdf" : "text/plain");
        ClipData clip = ClipData.newRawUri("attachment", first);
        if (mode.equals("multi")) {
            ArrayList<Uri> uris = new ArrayList<>(); uris.add(first); uris.add(uri("Second note.txt"));
            outgoing.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            clip.addItem(new ClipData.Item(uris.get(1)));
        } else if (Intent.ACTION_VIEW.equals(outgoing.getAction())) outgoing.setDataAndType(first, outgoing.getType());
        else if (!mode.equals("clip")) outgoing.putExtra(Intent.EXTRA_STREAM, first);
        outgoing.setClipData(clip);
        if (!mode.equals("denied")) outgoing.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(outgoing);
        finish();
    }
}
