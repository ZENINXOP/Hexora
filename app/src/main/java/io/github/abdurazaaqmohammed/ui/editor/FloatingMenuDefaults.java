package io.github.abdurazaaqmohammed.ui.editor;

import org.json.JSONArray;
import org.json.JSONObject;

/** Shared initial ordering for the menu itself and its preference editor. */
public final class FloatingMenuDefaults {
    private FloatingMenuDefaults() { }
    public static JSONArray create() {
        String[] ids = {"panel_btn_select_all", "panel_btn_copy", "panel_btn_paste", "id_btn", "goto_id_btn",
                "goto_btn", "translate_btn", "panel_btn_cut", "comment_btn", "openLink_btn", "share_btn",
                "panel_btn_long_select", "delete_btn", "customize_btn"};
        String[] names = {"Select All", "Copy", "Paste", "ID", "Goto ID", "Go To", "Translate", "Cut",
                "Toggle comment", "Open link", "Share", "Long Select", "Delete", "Customize"};
        JSONArray result = new JSONArray();
        try {
            for (int index = 0; index < ids.length; index++) result.put(new JSONObject()
                    .put("id", ids[index]).put("title", names[index]).put("disabled", false));
        } catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
        return result;
    }
}
