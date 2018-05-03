package com.cci.powermonitor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

public class PowerMonitorEnabler extends Activity implements CompoundButton.OnCheckedChangeListener{

    private final static String TAG = "PowerMonitorEnabler";
    private final static String POWER_MONITOR_START_RECORD = "com.cci.powermonitor.start_record";
    Switch mSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_powermonitorenabler);
        mSwitch = (Switch) findViewById(R.id.powermonitor_switch);
        if (mSwitch != null) {
            mSwitch.setOnCheckedChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        if (SystemProperties.get("persist.powermonitor.enable", "0").equals("1")) {
            Log.i(TAG, "onResume persist.powermonitor.enable 1 Switch.setChecked true");
            mSwitch.setChecked(true);
        } else {
            Log.i(TAG, "onResume persist.powermonitor.enable 0 Switch.setChecked false");
            mSwitch.setChecked(false);
        }
    }
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.i(TAG, "onCheckedChanged isChecked:"+isChecked+" mSwitch.isChecked():"+mSwitch.isChecked());
        Log.i(TAG, "onCheckedChanged SystemProperties.get(persist.powermonitor.enable, 0):"+SystemProperties.get("persist.powermonitor.enable", "0"));
        if (isChecked) {
            if ("0".equals(SystemProperties.get("persist.powermonitor.enable", "0"))) {
                Log.i(TAG, "Set persist.powermonitor.enable to 1");
                SystemProperties.set("persist.powermonitor.enable", "1");
                Intent i = new Intent(this, PowerMonitorService.class);
                i.setAction(POWER_MONITOR_START_RECORD);
                this.startService(i);
            }            
        } else {
            if ("1".equals(SystemProperties.get("persist.powermonitor.enable", "0"))) {
                Log.i(TAG, "Set persist.powermonitor.enable to 0");
                SystemProperties.set("persist.powermonitor.enable", "0");
                Intent i = new Intent(this, PowerMonitorService.class);
                this.stopService(i);
            }
        }
    }
}
