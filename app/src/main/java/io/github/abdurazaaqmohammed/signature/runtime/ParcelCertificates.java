package io.github.abdurazaaqmohammed.signature.runtime;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.Map;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

/** Independent process-local PackageInfo creator hook. Copied only into the explicitly patched APK. */
public final class ParcelCertificates {
    private ParcelCertificates() { }

    public static synchronized void install(String packageName, String certificateHex) {
        try {
            if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/os/Parcel;", "Landroid/content/pm/", "Landroid/app/");
            Parcelable.Creator<PackageInfo> original = PackageInfo.CREATOR;
            if (original instanceof Creator) return;
            Signature signature = new Signature(certificateHex);
            Field creator = PackageInfo.class.getDeclaredField("CREATOR");
            creator.setAccessible(true);
            creator.set(null, new Creator(original, packageName, signature));
            clearMap(Parcel.class, "mCreators");
            clearMap(Parcel.class, "sPairedCreators");
            try {
                Field field = PackageManager.class.getDeclaredField("sPackageInfoCache");
                field.setAccessible(true);
                Object cache = field.get(null);
                if (cache != null) cache.getClass().getMethod("clear").invoke(cache);
            } catch (Throwable ignored) { }
        } catch (Throwable error) {
            // A platform that denies the hook must not break the original Application lifecycle.
            Log.w("HexoraSignature", "Certificate runtime is unavailable on this Android version", error);
        }
    }

    private static void clearMap(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map) synchronized (value) { ((Map<?, ?>) value).clear(); }
        } catch (Throwable ignored) { }
    }

    public static final class Creator implements Parcelable.Creator<PackageInfo> {
        private final Parcelable.Creator<PackageInfo> original;
        private final String packageName;
        private final Signature certificate;

        Creator(Parcelable.Creator<PackageInfo> original, String packageName, Signature certificate) {
            this.original = original;
            this.packageName = packageName;
            this.certificate = certificate;
        }

        @Override public PackageInfo createFromParcel(Parcel parcel) {
            PackageInfo info = original.createFromParcel(parcel);
            if (info == null || !packageName.equals(info.packageName)) return info;
            if (info.signatures != null) info.signatures = new Signature[]{certificate};
            if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
                Signature[] current = info.signingInfo.getApkContentsSigners();
                if (current != null) java.util.Arrays.fill(current, certificate);
                Signature[] history = info.signingInfo.getSigningCertificateHistory();
                if (history != null) java.util.Arrays.fill(history, certificate);
            }
            return info;
        }

        @Override public PackageInfo[] newArray(int size) { return original.newArray(size); }
    }
}
