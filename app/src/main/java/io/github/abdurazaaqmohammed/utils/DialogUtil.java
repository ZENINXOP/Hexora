package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DialogUtil {
    private final Activity context;

    public DialogUtil(Activity c) {
        this.context = c;
    }

    public MaterialAlertDialogBuilder getDialogBuilder() {
        return new MaterialAlertDialogBuilder(context);
    }

    public void styleAlertDialog(AlertDialog ad) {
        context.runOnUiThread(ad::show);
    }

    /** Persistent choices remain accessible on devices that dismiss transient popups. */
    public static void enableChoices(android.widget.AutoCompleteTextView input, String title,
                                     String[] choices, java.util.function.IntConsumer selected) {
        android.view.View.OnClickListener open = v -> {
            if (!input.isEnabled()) return;
            input.dismissDropDown();
            int checked = java.util.Arrays.asList(choices).indexOf(input.getText().toString());
            new MaterialAlertDialogBuilder(input.getContext()).setTitle(title)
                    .setSingleChoiceItems(choices, checked, (dialog, which) -> {
                        input.setText(choices[which], false);
                        selected.accept(which);
                        dialog.dismiss();
                    }).setNegativeButton(android.R.string.cancel, null).show();
        };
        input.setOnClickListener(open);
        input.setOnTouchListener((view, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) view.performClick();
            return true;
        });
        android.view.ViewParent parent = input.getParent();
        while (parent instanceof android.view.View) {
            if (parent instanceof com.google.android.material.textfield.TextInputLayout layout) {
                layout.setEndIconOnClickListener(open);
                break;
            }
            parent = parent.getParent();
        }
    }
}
