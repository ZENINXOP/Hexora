package io.github.abdurazaaqmohammed.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives explicit notification actions without opening an activity. */
public final class ProgressCancelReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ProgressManager.cancelFromNotification(context, intent);
    }
}
