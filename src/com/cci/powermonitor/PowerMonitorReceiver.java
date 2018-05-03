package com.cci.powermonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
//import android.os.SystemProperties;
import android.util.Log;

public class PowerMonitorReceiver extends BroadcastReceiver {
    private final static String TAG = "PowerMonitorReceiver";
    private final static String ACTION_SECRET_CODE = "android.provider.Telephony.SECRET_CODE";

    @Override
    public void onReceive(Context context, Intent intent){
        if(intent.getAction().equals(ACTION_SECRET_CODE)){
            Log.i(TAG,"SECRET_CODE 76937");
            Intent i = new Intent();
            i.setClass(context, PowerMonitorEnabler.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }
}
