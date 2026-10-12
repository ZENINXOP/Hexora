package app.hexora.signaturefixture;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import java.security.MessageDigest;

/** Owned acceptance fixture for signature queries. AI-assisted contribution: OpenAI Codex. */
public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        StringBuilder result = new StringBuilder();
        try {
            PackageManager pm = getPackageManager();
            PackageInfo legacy = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            check(result, "legacy", digest(legacy.signatures[0]).equals(BuildConfig.ORIGINAL_CERT));
            PackageInfo modern = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            check(result, "current", digest(modern.signingInfo.getApkContentsSigners()[0]).equals(BuildConfig.ORIGINAL_CERT));
            check(result, "history", digest(modern.signingInfo.getSigningCertificateHistory()[0]).equals(BuildConfig.ORIGINAL_CERT));
            check(result, "multiple", !modern.signingInfo.hasMultipleSigners());
            check(result, "past", !modern.signingInfo.hasPastSigningCertificates());
            check(result, "no-flags", pm.getPackageInfo(getPackageName(), 0).signatures == null);
            PackageInfo platform = pm.getPackageInfo("android", PackageManager.GET_SIGNATURES);
            check(result, "other-package", !digest(platform.signatures[0]).equals(BuildConfig.ORIGINAL_CERT));
            PackageInfo reflected = (PackageInfo) PackageManager.class.getMethod("getPackageInfo", String.class, int.class)
                    .invoke(pm, getPackageName(), PackageManager.GET_SIGNATURES);
            Signature[] signatures = (Signature[]) PackageInfo.class.getField("signatures").get(reflected);
            check(result, "reflection", digest(signatures[0]).equals(BuildConfig.ORIGINAL_CERT));
            if (BuildConfig.NATIVE_FIXTURE) {
                System.loadLibrary("signature_fixture");
                // The host supplies Android's actual installed outer APK path;
                // do not use sourceDir, which a Java hook could already rewrite.
                String outer = getIntent().getStringExtra("installedApkPath");
                long expected = Long.parseLong(getIntent().getStringExtra("originalCrc"));
                check(result, "native-apk-read", nativeApkCrc(outer) == expected);
            }
        } catch (Exception error) {
            result.append("ERROR ").append(error).append('\n');
            Log.e("HexoraSignatureFixture", "Fixture failed", error);
        }
        Log.i("HexoraSignatureFixture", result.toString());
        TextView view = new TextView(this);
        view.setText(result);
        view.setTextSize(18);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        view.setPadding(padding, padding, padding, padding);
        setContentView(view);
    }

    private static native long nativeApkCrc(String path);

    private static void check(StringBuilder result, String name, boolean valid) {
        result.append(valid ? "PASS " : "FAIL ").append(name).append('\n');
    }

    private static String digest(Signature signature) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
        StringBuilder hex = new StringBuilder();
        for (byte value : bytes) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return hex.toString();
    }
}
