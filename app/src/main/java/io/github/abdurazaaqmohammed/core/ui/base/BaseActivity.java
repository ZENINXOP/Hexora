package io.github.abdurazaaqmohammed.core.ui.base;

import android.R;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.color.MaterialColors;

import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.core.ui.util.AppFont;

/**
 * Single place for activity-wide UI behaviour.
 * All feature activities should extend this instead of AppCompatActivity
 * so theme switching, dynamic colors and edge-to-edge change in one file.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private int appliedFontEpoch = -1;
    private View insetContent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Theme must be set before super.onCreate so inflation uses it.
        ThemeRegistry.applySaved(this);
        // Font factory must also wrap AppCompat's, so install before super.onCreate.
        AppFont.installFactory(this);
        appliedFontEpoch = AppFont.currentEpoch();
        super.onCreate(savedInstanceState);
        // Own the insets on modern Android, including enforced edge-to-edge.
        // API 19 has no window-inset dispatch, so keep its platform fitting.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), Build.VERSION.SDK_INT < 21);
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        // AppCompat replaces android.R.id.content during setContentView().
        // Install on the final container, not the earlier framework container.
        View content = getWindow().getDecorView().findViewById(R.id.content);
        if (content == null || content == insetContent) return;
        insetContent = content;
        applySystemBarAppearance(content);
        if (Build.VERSION.SDK_INT < 21) return;

        final int left = content.getPaddingLeft();
        final int top = content.getPaddingTop();
        final int right = content.getPaddingRight();
        final int bottom = content.getPaddingBottom();
        final int systemTypes = WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            // Use the maximum bottom inset, rather than adding keyboard and
            // navigation-bar heights. Keep every toolbar and drawer below the
            // status/caption bar, including landscape camera cutouts.
            Insets safe = insets.getInsets(systemTypes | WindowInsetsCompat.Type.ime());
            v.setPadding(left + safe.left, top + safe.top,
                    right + safe.right, bottom + safe.bottom);
            // Descendants are already inside the safe area. Zero only the
            // handled system-bar insets; preserve keyboard visibility for editors.
            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(systemTypes, Insets.NONE)
                    .setInsetsIgnoringVisibility(systemTypes, Insets.NONE)
                    .build();
        });
        ViewCompat.requestApplyInsets(content);
    }

    private void applySystemBarAppearance(View content) {
        int surface = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurface, Color.BLACK);
        boolean lightSurface = MaterialColors.isColorLight(surface);
        // On Android 15+ the status bar is transparent: paint its inset area
        // with the current theme so clock/notification icons stay readable.
        content.setBackgroundColor(surface);
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(lightSurface && Build.VERSION.SDK_INT < 23
                    ? Color.DKGRAY : surface);
            window.setNavigationBarColor(lightSurface && Build.VERSION.SDK_INT < 26
                    ? Color.DKGRAY : surface);
        }
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                window, window.getDecorView());
        controller.setAppearanceLightStatusBars(lightSurface);
        controller.setAppearanceLightNavigationBars(lightSurface);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Pick up a font changed in Settings while this activity was in background.
        if (appliedFontEpoch != AppFont.currentEpoch()) recreate();
    }

    /**
     * Named dpPx (not dp) to avoid clashing with the legacy private dp()
     * helpers still present in migrated activities.
     */
    protected int dpPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
