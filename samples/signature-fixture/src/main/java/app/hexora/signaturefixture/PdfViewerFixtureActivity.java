package app.hexora.signaturefixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Owned MIME handoff observer; verifies the URI grant, without claiming to render PDFs.
 * AI-assisted contribution: OpenAI Codex. Never bundled in Hexora. */
public class PdfViewerFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String result;
        try (InputStream input = getContentResolver().openInputStream(getIntent().getData())) {
            byte[] prefix = new byte[4];
            int count = 0;
            while (count < prefix.length) {
                int n = input.read(prefix, count, prefix.length - count);
                if (n < 0) break;
                count += n;
            }
            result = count == 4 && "%PDF".equals(new String(prefix, StandardCharsets.US_ASCII))
                    && "application/pdf".equals(getIntent().getType())
                    ? "PASS external PDF open/read grant"
                    : count == 4 && prefix[0] == 'P' && prefix[1] == 'K' && prefix[2] == 3 && prefix[3] == 4
                    && "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(getIntent().getType())
                    ? "PASS external DOCX open/read grant" : "FAIL external document MIME or data";
        } catch (Exception error) { result = "FAIL external PDF read: " + error; }
        TextView text = new TextView(this);
        text.setTextSize(20);
        text.setPadding(32, 32, 32, 32);
        text.setText(result);
        setContentView(text);
    }
}
