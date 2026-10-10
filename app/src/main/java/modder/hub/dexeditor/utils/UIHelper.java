package modder.hub.dexeditor.utils;

import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.MenuItem;
import android.view.Menu;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.drawable.DrawableCompat;

/**
 * UIHelper: Common UI utilities to avoid code duplication across activities and fragments.
 */
public class UIHelper {

    public static void tintMenuIcons(Menu menu, int color) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                icon = DrawableCompat.wrap(icon).mutate();
                DrawableCompat.setTint(icon, color);
                icon.setAlpha(item.isEnabled() ? 255 : 97);
                item.setIcon(icon);
            }
            if (item.hasSubMenu()) tintMenuIcons(item.getSubMenu(), color);
        }
    }

    public static void setMenuItemColor(MenuItem item, int color) {
        SpannableString s = new SpannableString(item.getTitle());
        s.setSpan(new ForegroundColorSpan(color), 0, s.length(), 0);
        item.setTitle(s);
    }
}
