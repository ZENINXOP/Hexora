package io.github.abdurazaaqmohammed.utils;

import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ValueType;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/** A dedicated drawable avoids density variants, aliases and adaptive XML hiding the replacement. */
public final class ApkIconEditor {
    private ApkIconEditor() { }

    public static void replace(File apk, byte[] png) throws IOException {
        File temporary = File.createTempFile(".hexora-icon-", ".apk", apk.getAbsoluteFile().getParentFile());
        try {
            try (ApkModule module = ApkModule.loadApkFile(apk)) {
                if (!module.hasTableBlock()) throw new IOException("This APK has no resource table.");
                PackageBlock owner = module.getTableBlock().pickOne();
                for (PackageBlock candidate : module.getTableBlock().listPackages())
                    if (module.getPackageName().equals(candidate.getName())) { owner = candidate; break; }
                if (owner == null) throw new IOException("The APK resource package is missing.");
                String name = "hexora_icon_" + java.util.UUID.randomUUID().toString().replace("-", "");
                String path = "res/drawable/" + name + ".png";
                Entry icon = owner.getOrCreate("", "drawable", name);
                icon.setValueAsString(path);
                module.add(new ByteInputSource(png, path));
                AndroidManifestBlock manifest = module.getAndroidManifest();
                manifest.setIconResourceId(icon.getResourceId());
                // Set the correct roundIcon attribute name as well as its framework ID.
                manifest.getOrCreateApplicationElement().getOrCreateAndroidAttribute("roundIcon", 0x0101052c)
                        .setTypeAndData(ValueType.REFERENCE, icon.getResourceId());
                Iterator<ResXmlElement> elements = manifest.getOrCreateApplicationElement().recursive(ResXmlElement.class);
                while (elements.hasNext()) {
                    ResXmlElement element = elements.next();
                    if (!"activity".equals(element.getName()) && !"activity-alias".equals(element.getName())) continue;
                    ResXmlAttribute override = element.searchAttributeByResourceId(0x01010002);
                    if (override != null) override.setTypeAndData(ValueType.REFERENCE, icon.getResourceId());
                }
                module.writeApk(temporary);
            }
            if (Thread.currentThread().isInterrupted()) throw new IOException("Icon edit cancelled.");
            if (!temporary.renameTo(apk)) throw new IOException("Cannot publish the edited APK; the original was retained.");
        } finally { temporary.delete(); }
    }
}
