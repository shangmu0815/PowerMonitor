package com.cci.powermonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.SystemProperties;

public class PowerMonitorBootReceiver extends BroadcastReceiver {
    private final static String TAG = "PowerMonitorBootReceiver";
    private final static String POWER_MONITOR_BOOT_COMPLETED = "com.cci.powermonitor.boot_completed";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "action :" + action);
        Log.i(TAG, "get persist.powermonitor.enable:" + SystemProperties.get("persist.powermonitor.enable", "0"));
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                && "1".equals(SystemProperties.get("persist.powermonitor.enable", "0"))) {
            Intent i = new Intent(context, PowerMonitorService.class);
            i.setAction(POWER_MONITOR_BOOT_COMPLETED);
            context.startService(i);
            Log.i(TAG, "PowerMonitorBootReceiver startService");
        }
    }
}
