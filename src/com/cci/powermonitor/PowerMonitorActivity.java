package com.cci.powermonitor;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class PowerMonitorActivity extends Activity {
    private final static String TAG = "PowerMonitorActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        finish();
    }
}
