package com.cci.powermonitor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.io.BufferedReader;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import android.os.UEventObserver;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import com.android.internal.util.AsyncChannel;
import android.os.SystemProperties;
import com.android.internal.os.ProcessCpuTracker;

public class PowerMonitorService extends Service {
    private static final boolean DEBUG = false;
    private final static String TAG = "PowerMonitorService";
    private PowerMonitorReceiver mPMReceiver;
    private static final String BATTERYSTATS_SOURCE_PATH = "/data/powermonitorbatterystats/";
    private static final String REPORT_SOURCE_PATH = "/storage/sdcard0/powermonitor/";
    private static final String BATTERY_CAPACITY = "/sys/class/power_supply/battery/capacity";
    private boolean mIsMtkPlatform = false;
    private static final String CURRENT_DRAIN_FORM_CHARGER_IC = "/sys/class/power_supply/battery/current_now";
    private static final String CURRENT_DRAIN_FORM_CHARGER_IC_FOR_MTK= "/sys/class/power_supply/battery/BatteryAverageCurrent";
    //private static final String CURRENT_DRAIN_FORM_CHARGER_IC_FOR_MTK= "/sys/devices/platform/battery_meter/FG_suspend_current";
    private static final String LOAD_AVERAGE = "/proc/loadavg";
    private static final String LCD_BACKLIGHT = "/sys/class/leds/lcd-backlight/brightness";
    private static final String BATTERY_VOLTAGE_INFO_FOR_MTK = "/sys/class/power_supply/battery/batt_vol";
    private static final String FG_CURRENT_FOR_MTK = "/sys/devices/platform/battery_meter/FG_Current";
    //private static final String FG_CURRENTCONSUMPTION_FOR_MTK = "/sys/devices/platform/battery/FG_CurrentConsumption";
    private static final String FG_CURRENTCONSUMPTION_FOR_MTK = "/sys/devices/platform/battery/FG_Battery_CurrentConsumption";
    //cpu info
    private static final String CPU_ONLINE_CORES_INFO = "/sys/devices/system/cpu/online";//0,0-1,0-2,0-3
    private static final String CPU0_FREQUENCY_INFO = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq";
    private static final String CPU1_FREQUENCY_INFO = "/sys/devices/system/cpu/cpu1/cpufreq/cpuinfo_cur_freq";
    private static final String CPU2_FREQUENCY_INFO = "/sys/devices/system/cpu/cpu2/cpufreq/cpuinfo_cur_freq";
    private static final String CPU3_FREQUENCY_INFO = "/sys/devices/system/cpu/cpu3/cpufreq/cpuinfo_cur_freq";

    private final static String mOutFileName = "Output.txt";
    private final static String POWER_MONITOR_BOOT_COMPLETED = "com.cci.powermonitor.boot_completed";
    private final static String POWER_MONITOR_ACTION_POLLING = "com.cci.powermonitor.action_polling";
    private final static String POWER_MONITOR_START_RECORD = "com.cci.powermonitor.start_record";
    private final static long INIT_CHECK_TIME = 10 * 60 * 1000;//Ex:test 10 minutes
    // Report type
    private final static int BOOT_COMPLETED = -2;
    private final static int START_RECORD = -1;
    private final static int POLLING_ACTION = 0;
    private final static int SCREEN_ON = 1;
    private final static int SCREEN_OFF = 2;
    private final static int BATTERY_CHANGED = 3;
    private final static int BATTERY_PLUGGED = 4;
    private final static int PREPARE_SUSPEND = 5;
    private final static int RESUME = 6;
    private final static int WIFI_STATE_CHANGED = 7;
    private final static int BT_STATE_CHANGED = 8;
    private final static int GPS_STATE_CHANGED = 9;
    private final static int OUTGOING_CALL_STARTED = 10;
    private final static int OUTGOING_CALL_ENDED = 11;
    private final static int INCOMING_CALL_STARTED = 12;
    private final static int INCOMING_CALL_ENDED = 13;
    private final static int WIFI_AP_STATE_CHANGED = 14;

    private int mPrevBatteryLevel = 0;
    private int mNowBatteryLevel = 0;
    private int mPrevPowerSourceType = -1;
    private int mNowPowerSourceType = 0;
    private boolean mIsScreenOn = false;
    Thread mPrintThread;
    Context mContext;

    public static final String TOP_10_SYSTEM_LOAD_CMD = "top -m 10 -n 1";
    public static final String LOAD_SUSPEND_RESUME_INSERT_MODULE_CMD = "insmod /system/lib/modules/cci_powermonitor.ko\n";

    private int mPrevWifiType = -1;
    private int mNowWifiType = 0;
    private WifiManager mWifiManager;
    private LocationManager mLocationManager;
    private static BluetoothAdapter mBluetoothAdapter ;
    private final static String ACTION_PROVIDERS_CHANGED = "android.location.PROVIDERS_CHANGED";
    private final static String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";
    private int mPrevWifiState = WifiManager.WIFI_STATE_DISABLED;
    private int mNowWifiState = WifiManager.WIFI_STATE_UNKNOWN;
    private int mPrevBtState = BluetoothAdapter.STATE_OFF;
    private int mNowBtState = BluetoothAdapter.STATE_OFF;
    private final static int GPS_STATE_DISABLED = 0;
    private final static int GPS_STATE_ENABLED = 1;
    private int mPrevGpsState = GPS_STATE_DISABLED;
    private int mNowGpsState = GPS_STATE_DISABLED;
    private int mPrevWifiAPState = WifiManager.WIFI_AP_STATE_DISABLED;
    private int mNowWifiAPState = WifiManager.WIFI_AP_STATE_DISABLED;

    private int gsmSignalStrength = 0;
    public ConnectivityManager connMgr;
    private boolean mIsBootCompleted = false;
    String interfaceName;
    String mobileConnInterfaceName = "rmnet0";
    String mobileConnInterfaceNameForMtk = "ccmni0";
    String mobileConnInterfaceNameForQctMsm8909 = "rmnet_data0";
    String wifiConnInterfaceName = "wlan0";
    long mobileConnTxBytes = 0;
    long mobileConnRxBytes = 0;
    long wifiConnTxBytes = 0;
    long wifiConnRxBytes = 0;
    AsyncChannel mWifiChannel;

    boolean isIncomingCallStart = false;
    boolean isOutgoingCallStart = false;
    boolean isIncomingCallRinging = false;

    private static final String ACTION_DUMP_KLOGCAT = "com.android.cci.ACTION_DUMP_KLOGCAT";
    private static final String ACTION_TRANSFOR = "com.compalcomm.transferreport.transfer";
    private static final String DATA_CRASH_PATH = "/data/crash/";

    private String mKlogDest;
    private String mKlogName;
    private String mBatterystatsName;

    private boolean mIsAbnormalPowerConsumption;
    private long mPrevNowMillis = 0;
    private long mCurrentNowMillis = 0;
    public static final int MSG_GET = 1;
    private float mFGCurrent = 0;
    private float mBatteryAverageCurrent = 0;
    private int mNewBatteryLevel = 0;
    private int mOldBatteryLevel = 0;
    private int mNewBatteryVoltage = 0;
    private int mOldBatteryVoltage = 0;

    private int mBatteryLevelDropCounter = 0;
    private int mDumpCounter = 0;

    private int mTestSysConsumptionValuePeriod = 30 * 60;//30 min (1800s)

    int mGetRawDataPeriod = 10 * 60;//10min

    int mTestSysConsumptionValueNum = mTestSysConsumptionValuePeriod / mGetRawDataPeriod;//180
    private int[] mTestSysConsumptionValueArray = new int[mTestSysConsumptionValueNum];
    int mTestSysConsumptionValueCount = 0;
    int mTestSysConsumptionValue = 0;
    private boolean mIsTestSysConsumptionCurrentFirstRun = true;

    int mTestBatteryVoltagePeriod1 = 15 * 60;//15 min (900s)
    int mTestBatteryVoltagePeriod1Num = mTestBatteryVoltagePeriod1 / mGetRawDataPeriod;//90
    int mTestBatteryVoltagePeriod2 = 20 * 60;//20 min (1200s)
    int mTestBatteryVoltagePeriod2Num = mTestBatteryVoltagePeriod1 / mGetRawDataPeriod;//120
    int mTestBatteryVoltagePeriod = mTestBatteryVoltagePeriod1 + mTestBatteryVoltagePeriod2;//35 min
    int mTestBatteryVoltageNum = mTestBatteryVoltagePeriod / mGetRawDataPeriod;//210 //

    int mBatteryVoltage = 0;
    private int[] mTestBatteryVoltageArray = new int[mTestBatteryVoltageNum];
    int mTestBatteryVoltageValueCount = 0;
    int mTestBatteryVoltageDropCounter = 0;
    private boolean mIsTestBatteryVoltageFirstRun = true;
    private float mFGCurrentmA = 0;

    private float mFGCurrentmAInfo = 0;
    private float SysConsumptionValueInfo = 0;

    ProcessCpuTracker CpuTracker;
    //
    int waitCounter = 0;
    private boolean waitFlag = false;
    int SysConsumptionValueCounter = 0;
    int mSysConsumptionValueOverCounter = 0;

    int klogNum = 0;
    int batteryVoltageDropKlogNum = 0;
    long mDataPartionThresholdSize = 50;//50MB
    long mStorageEmulatedThresholdSize = 50; //50MB
    public static Integer[] mBatterystatsThreadShold =
        { 95, 90, 85, 80, 75, 70, 65, 60, 55, 50,
            45, 40, 35, 30, 25, 20, 15, 10, 5, 0 };
    public static HashSet<Integer> mBatterystatsThreadSholdHS = new HashSet<Integer>(Arrays.asList(mBatterystatsThreadShold));

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_GET:
                /*
                if(readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK) != null){
                    mBatteryVoltage= Integer.parseInt(readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK));
                }

                if (readFileOneLine(FG_CURRENT_FOR_MTK) != null) {
                    mFGCurrent = Float.valueOf(readFileOneLine(FG_CURRENT_FOR_MTK));
                    Log.e(TAG, "handleMessage mFGCurrent:" + mFGCurrent+"----");
                }
                if (readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC_FOR_MTK) != null) {
                    mBatteryAverageCurrent = Float.valueOf(readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC_FOR_MTK));
                }else{
                    mBatteryAverageCurrent = 0.0f;//if no suspend current
                }
                Log.i(TAG,"handleMessage mBatteryAverageCurrent:" + mBatteryAverageCurrent);

                Log.e(TAG,"handleMessage mFGCurrent:" + mFGCurrent);
                mFGCurrentmA =  mFGCurrent / 10;
                Log.e(TAG,"handleMessage mFGCurrentmA:" + mFGCurrentmA);

                mTestSysConsumptionValue = Math.round((mBatteryAverageCurrent + mFGCurrentmA));
                Log.e(TAG,"handleMessage mTestSysConsumptionValue:" + mTestSysConsumptionValue);
                */

                // test case 1: battery level drop and drop 3%
                // continually,dumpKlogcat
                /*
                if (mNewBatteryLevel - mOldBatteryLevel >= 0) {
                    chargingOrConstantState();
                } else if (mNewBatteryLevel - mOldBatteryLevel < 0) {
                    dischargingState();
                }
                */
                /*
                mNewBatteryVoltage = Integer.parseInt(readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK));
                if (DEBUG)Log.i(TAG,"handleMessage mNewBatteryVoltage:" + mNewBatteryVoltage+" mOldBatteryVoltage:"+mOldBatteryVoltage);
                if (mOldBatteryVoltage - mNewBatteryVoltage > 300) {
                    if (DEBUG)Log.i(TAG,"handleMessage batteryVoltageDropKlogNum:" + batteryVoltageDropKlogNum);
                    if (batteryVoltageDropKlogNum < 50) {
                        //dumpKlogcat("batteryVoltageDrop");
                        dumpKlogcatToPowermonitorFolder("batteryVoltageDrop");
                        batteryVoltageDropKlogNum++;
                    }
                }
                mOldBatteryVoltage = mNewBatteryVoltage;
                */
                mNewBatteryLevel = Integer.parseInt(readFileOneLine(BATTERY_CAPACITY));
                if (DEBUG)Log.i(TAG,"handleMessage mNewBatteryLevel:" + mNewBatteryLevel+" mOldBatteryLevel:"+mOldBatteryLevel);
                if (mNewBatteryLevel - mOldBatteryLevel < 0) {
                    Log.i(TAG,"handleMessage klogNum:" + klogNum);
                    if (klogNum < 50) {
                        //dumpKlogcat("batteryLevelDrop");
                        dumpKlogcatToPowermonitorFolder("batteryLevelDrop");
                        klogNum++;
                    }
                }
                mOldBatteryLevel = mNewBatteryLevel;

                //test case 2:
                /*
                Log.e(TAG, "handleMessage waitFlag:" + waitFlag+"----");
                Log.e(TAG, "handleMessage SysConsumptionValueCounter:" + SysConsumptionValueCounter+"----");
                if (waitFlag) {
                    waitCounter++;
                    // wait 10 min
                    if (waitCounter == 60) {
                        waitFlag = false;
                        SysConsumptionValueCounter = 0;
                    }
                }
                if (!waitFlag) {
                    // wait30sec to test the mTestSysConsumptionValue
                    if (SysConsumptionValueCounter == 3) {
                        Log.e(TAG, "handleMessage SysConsumptionValueCounter == 3-mTestSysConsumptionValue:"+mTestSysConsumptionValue);
                        if (mTestSysConsumptionValue < 600) {
                            SysConsumptionValuePassState();
                        } else if (mTestSysConsumptionValue >= 600) {
                            SysConsumptionValueNotPassState();
                        }
                    }
                }
                SysConsumptionValueCounter++;
                */
                //test case 2: testSysConsumptionCurrent
                /*
                testSysConsumptionCurrent();
                */

                //test case 3:testBatteryVoltage
                //testBatteryVoltage();
                mHandler.sendEmptyMessageDelayed(MSG_GET, mGetRawDataPeriod * 1000);//GetRawDataPeriod every 10min
                break;
            }
            super.handleMessage(msg);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        if ("".equals(SystemProperties.get("ro.mediatek.platform", ""))) {
            mIsMtkPlatform = false;
        } else {
            mIsMtkPlatform = true;
        }
        Log.i(TAG,"onCreate mIsMtkPlatform : " + mIsMtkPlatform);
        mPMReceiver = new PowerMonitorReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(ACTION_PROVIDERS_CHANGED);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        intentFilter.addAction(ACTION_PHONE_STATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_SHUTDOWN);
        intentFilter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        registerReceiver(mPMReceiver, intentFilter);

        // get the first Battery level
        mPrevBatteryLevel = Integer.parseInt(readFileOneLine(BATTERY_CAPACITY));
        mIsScreenOn = true;
        mContext = this;

        // execute load suspend resume insert module command
        //doExeCmd(LOAD_SUSPEND_RESUME_INSERT_MODULE_CMD);

        //set the initial polling period in the setting file
        try {
            String setPollingPeriodPath = getAppPath() + "/pollingPeriod.txt";
            String initCheckTime = String.valueOf(INIT_CHECK_TIME);
            Log.i(TAG, "set polling period path:" + setPollingPeriodPath + " initCheckTime:" + initCheckTime);
            File setPollingPeriodFile = new File(setPollingPeriodPath);
            if (!setPollingPeriodFile.exists()) {
                Log.i(TAG, "initial file");
                FileOutputStream fos = new FileOutputStream(setPollingPeriodFile, true);
                fos.write(initCheckTime.getBytes());
                fos.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // PowerMonitorObserver path
        //Log.i(TAG, "mPowerMonitorObserver start");
        //Not used in Eagle,Calla project
        //mPowerMonitorObserver.startObserving("DEVPATH=/devices/platform/cci_pm");

        mWifiManager = (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
        if (mWifiManager != null) {
            if (mWifiManager.isWifiEnabled()) {
                mPrevWifiState = WifiManager.WIFI_STATE_ENABLED;
            } else {
                mPrevWifiState = WifiManager.WIFI_STATE_DISABLED;
            }
            if (DEBUG)Log.i(TAG, "onCreate mPrevWifiState:" + mPrevWifiState);
            if(mWifiManager.isWifiApEnabled()) {
                mPrevWifiAPState = WifiManager.WIFI_AP_STATE_ENABLED;
            } else {
                mPrevWifiAPState = WifiManager.WIFI_AP_STATE_DISABLED;
            }
            if (DEBUG)Log.e(TAG, "onCreate mPrevWifiAPState:" + mPrevWifiAPState);
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
            if(mBluetoothAdapter.isEnabled()){
                mPrevBtState = BluetoothAdapter.STATE_ON;
            } else {
                mPrevBtState = BluetoothAdapter.STATE_OFF;
            }
            if (DEBUG)Log.i(TAG, "onCreate mPrevBtState:" + mPrevBtState);
        }

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (mLocationManager != null) {
            if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mPrevGpsState = GPS_STATE_ENABLED;
            } else {
                mPrevGpsState = GPS_STATE_DISABLED;
            }
            if (DEBUG)Log.i(TAG, "onCreate mPrevGpsState:" + mPrevGpsState);
        }

        TelephonyManager telMgr = (TelephonyManager)mContext.getSystemService(TELEPHONY_SERVICE);
        //start the signal strength listener and
        //listen for changes to the direction of data traffic on the data connection
        if (telMgr != null) {
            telMgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
                                               PhoneStateListener.LISTEN_DATA_ACTIVITY);
        }
        // create wifi Handler to trace wifi data transmission bytes
        createWifiHandler();
        connMgr = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (mIsMtkPlatform) {
            // test for ESTA Abnormal Power Consumption
            mHandler.sendEmptyMessage(MSG_GET);
        }
    }

    protected void createWifiHandler()
    {
        // wifi
        mWifiManager = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
        Handler mWifiHandler = new WifiHandler();
        mWifiChannel = new AsyncChannel();
        Messenger wifiMessenger = mWifiManager.getWifiServiceMessenger();
        if (wifiMessenger != null) {
            mWifiChannel.connect(mContext, mWifiHandler, wifiMessenger);
        }
    }
    class WifiHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            //Log.i(TAG, "WifiHandler handleMessage msg.what :" + msg.what + " interfaceName :" + interfaceName);
            switch (msg.what) {
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    if (msg.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                        mWifiChannel.sendMessage(Message.obtain(this,AsyncChannel.CMD_CHANNEL_FULL_CONNECTION));
                    } else {
                        Log.i(TAG, "Failed to connect to wifi");
                    }
                break;
                case WifiManager.DATA_ACTIVITY_NOTIFICATION:
                if(interfaceName != null && !interfaceName.equals(""))
                {
                    wifiConnTxBytes = TrafficStats.getTxBytes(interfaceName);
                    wifiConnRxBytes = TrafficStats.getRxBytes(interfaceName);
                }
                /*
                Log.i(TAG, "WifiHandler wifiConnTxBytes:" + wifiConnTxBytes
                        + " wifiConnRxBytes:" + wifiConnRxBytes);
                */
                break;
            }
        }
    }

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener(){
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
            gsmSignalStrength = signalStrength.getGsmSignalStrength();
        }

        @Override
        public void onDataActivity(int direction) {
            super.onDataActivity(direction);
            if (DEBUG)Log.i(TAG, "onDataActivity interfaceName:" + interfaceName);
            if (interfaceName != null && !interfaceName.equals(""))
            {
                if (mIsMtkPlatform) {
                    if (interfaceName.equals(mobileConnInterfaceNameForMtk)) {
                        mobileConnTxBytes = TrafficStats.getTxBytes(interfaceName);
                        mobileConnRxBytes = TrafficStats.getRxBytes(interfaceName);
                    } else if (interfaceName.equals(wifiConnInterfaceName)) {
                        wifiConnTxBytes = TrafficStats.getTxBytes(interfaceName);
                        wifiConnRxBytes = TrafficStats.getRxBytes(interfaceName);
                    }
                } else {//QCT platform
                    if (interfaceName.equals(mobileConnInterfaceName)) {
                        mobileConnTxBytes = TrafficStats.getTxBytes(interfaceName);
                        mobileConnRxBytes = TrafficStats.getRxBytes(interfaceName);
                    }
                    else if (interfaceName.equals(mobileConnInterfaceNameForQctMsm8909)) {
                        mobileConnTxBytes = TrafficStats.getTxBytes(interfaceName);
                        mobileConnRxBytes = TrafficStats.getRxBytes(interfaceName);
                    }
                    else if (interfaceName.equals(wifiConnInterfaceName)) {
                        wifiConnTxBytes = TrafficStats.getTxBytes(interfaceName);
                        wifiConnRxBytes = TrafficStats.getRxBytes(interfaceName);
                    }
                }
            }
            /*
            Log.i(TAG, "onDataActivity mobileConnTxBytes:" + mobileConnTxBytes
                    + " mobileConnRxBytes:" + mobileConnRxBytes
                    + " wifiConnTxBytes:" + wifiConnTxBytes
                    + " wifiConnRxBytes:" + wifiConnRxBytes);
            */
        }
    };

    //Not used in Eagle,Calla project
    /*
    private UEventObserver mPowerMonitorObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {

            String state = event.get("EVENT", "");
            Log.i(TAG, "mPowerMonitorObserver event.get(EVENT,):" + state
                    + " mPrevPowerSourceType:" + mPrevPowerSourceType
                    + " mPrevWifiState:" + mPrevWifiState
                    + " mPrevBtState:" + mPrevBtState
                    + " mPrevGpsState:" + mPrevGpsState);
            // "EVENT=0" means prepare to suspend
            // "EVENT=1" means resume.
            if (state.equals("0")) {
                // print log when prepare to suspend
                printReport(PREPARE_SUSPEND, String.valueOf(mPrevPowerSourceType),
                        String.valueOf(mPrevWifiState),
                        String.valueOf(mPrevBtState),
                        String.valueOf(mPrevGpsState));
            }
            else if (state.equals("1")) {
                // print log when resume
                printReport(RESUME, String.valueOf(mPrevPowerSourceType),
                        String.valueOf(mPrevWifiState),
                        String.valueOf(mPrevBtState),
                        String.valueOf(mPrevGpsState));
            }
            else {
                if (!state.equals("")) {
                    // print log when resume
                    // (print wakeUp Info: EVENT=1;WAKELOCK=xxx)
                    String[] resumeAndWakeUpInfo = state.split(";|=", 3);
                    String wakeUpInfo = resumeAndWakeUpInfo[resumeAndWakeUpInfo.length - 1];
                    Log.i(TAG, "wakeUpInfo:" + wakeUpInfo);
                    printReport(RESUME, String.valueOf(mPrevPowerSourceType),
                            String.valueOf(mPrevWifiState),
                            String.valueOf(mPrevBtState),
                            String.valueOf(mPrevGpsState), wakeUpInfo);
                }
            }
        }
    };
    */

    public String getAppPath() {
        PackageManager packageManager = getPackageManager();
        String strLocationPackageName = getPackageName();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(strLocationPackageName, 0);
            strLocationPackageName = packageInfo.applicationInfo.dataDir;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return strLocationPackageName;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand-get persist.powermonitor.enable:" + SystemProperties.get("persist.powermonitor.enable", "0"));
        /*
        if ("0".equals(SystemProperties.get("persist.powermonitor.enable", "0"))) {
            return START_NOT_STICKY ;
        }
        */
        if ("".equals(SystemProperties.get("ro.mediatek.platform", ""))) {
            mIsMtkPlatform = false;
        } else {
            mIsMtkPlatform = true;
        }
        Log.i(TAG,"onStartCommand mIsMtkPlatform : " + mIsMtkPlatform);
        if (intent != null) {
            if (DEBUG)Log.i(TAG,"onStartCommand intent.getAction():" + intent.getAction()
                            + " mPrevPowerSourceType:" + mPrevPowerSourceType
                            + " mPrevWifiState:" + mPrevWifiState
                            + " mPrevBtState:" + mPrevBtState
                            + " mPrevGpsState:" + mPrevGpsState);
            if (intent.getAction().equals(POWER_MONITOR_START_RECORD)) {
                String currentConsumption = "";
                String mCurrnetDrainFormChargerIc = "";
                String mFgCurrentmAInfo="";
                String mSysConsumptionValueInfo="";
                String mBatteryVoltageInfo = "";
                // print log when start record
                if (mIsMtkPlatform) {
                   currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                   /*
                   if (currentConsumption.contains(" ")){
                       String[] currentConsumptionParts = currentConsumption.split(" ");
                     if (currentConsumptionParts.length == 3) {
                         mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                         mFgCurrentmAInfo = currentConsumptionParts[1];
                         mSysConsumptionValueInfo = currentConsumptionParts[2];
                     }
                   }
                   */
                   mSysConsumptionValueInfo = currentConsumption;
                   mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                } else {
                    mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                }
                if (mIsMtkPlatform && DEBUG)Log.i(TAG, "start record-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                printReport(START_RECORD, String.valueOf(mPrevPowerSourceType),
                        String.valueOf(mPrevWifiState),
                        String.valueOf(mPrevBtState),
                        String.valueOf(mPrevGpsState),
                        mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                        String.valueOf(mPrevWifiAPState));
                // start polling
                //startPolling(this);
            } else if (intent.getAction().equals(POWER_MONITOR_ACTION_POLLING)) {
                String currentConsumption = "";
                String mCurrnetDrainFormChargerIc = "";
                String mFgCurrentmAInfo="";
                String mSysConsumptionValueInfo="";
                String mBatteryVoltageInfo = "";
                // print log when polling action
                if (mIsMtkPlatform) {
                    currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                    /*
                    if (currentConsumption.contains(" ")){
                        String[] currentConsumptionParts = currentConsumption.split(" ");
                      if (currentConsumptionParts.length == 3) {
                          mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                          mFgCurrentmAInfo = currentConsumptionParts[1];
                          mSysConsumptionValueInfo = currentConsumptionParts[2];
                      }
                    }
                    */
                    mSysConsumptionValueInfo = currentConsumption;
                    mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                } else {
                    mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                }
                if (mIsMtkPlatform && DEBUG)Log.i(TAG, "polling action-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                printReport(POLLING_ACTION, String.valueOf(mPrevPowerSourceType),
                        String.valueOf(mPrevWifiState),
                        String.valueOf(mPrevBtState),
                        String.valueOf(mPrevGpsState),
                        mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                        String.valueOf(mPrevWifiAPState));
                // start polling
                //startPolling(this);
            }
            else if (intent.getAction().equals(POWER_MONITOR_BOOT_COMPLETED)) {
                String currentConsumption = "";
                String mCurrnetDrainFormChargerIc = "";
                String mFgCurrentmAInfo="";
                String mSysConsumptionValueInfo="";
                String mBatteryVoltageInfo = "";
                // print log when boot completed
                if (mIsMtkPlatform) {
                   currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                   /*
                   if (currentConsumption.contains(" ")){
                       String[] currentConsumptionParts = currentConsumption.split(" ");
                     if (currentConsumptionParts.length == 3) {
                         mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                         mFgCurrentmAInfo = currentConsumptionParts[1];
                         mSysConsumptionValueInfo = currentConsumptionParts[2];
                     }
                   }
                   */
                   mSysConsumptionValueInfo = currentConsumption;
                   mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                } else {
                    mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                }
              if (mIsMtkPlatform && DEBUG)Log.i(TAG, "boot completed-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
              printReport(BOOT_COMPLETED, String.valueOf(mPrevPowerSourceType),
                        String.valueOf(mPrevWifiState),
                        String.valueOf(mPrevBtState),
                        String.valueOf(mPrevGpsState),
                        mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                        String.valueOf(mPrevWifiAPState));
                // start polling
                //startPolling(this);
            }
        }
        //return START_NOT_STICKY;
        return START_STICKY;//if this service's process is killed while it is started
    }

    public class PowerMonitorReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String callState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (DEBUG)Log.i(TAG, "PowerMonitorReceiver action:" + action + " mIsScreenOn:" + mIsScreenOn +" mPrevPowerSourceType:"+mPrevPowerSourceType);
            if (DEBUG)Log.i(TAG, "PowerMonitorReceiver mPrevWifiState:" + mPrevWifiState +" mPrevBtState:"+mPrevBtState +" mPrevGpsState:"+mPrevGpsState);
            if (DEBUG)Log.i(TAG, "PowerMonitorReceiver callState:" + callState);
            if (action.equals(Intent.ACTION_SCREEN_ON) && (!mIsScreenOn)) {
                String currentConsumption = "";
                String mCurrnetDrainFormChargerIc = "";
                String mFgCurrentmAInfo="";
                String mSysConsumptionValueInfo="";
                String mBatteryVoltageInfo = "";
                mIsScreenOn = true;
                // print log when screen on
                if (mIsMtkPlatform) {
                    currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                    /*
                    if (currentConsumption.contains(" ")){
                        String[] currentConsumptionParts = currentConsumption.split(" ");
                      if (currentConsumptionParts.length == 3) {
                          mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                          mFgCurrentmAInfo = currentConsumptionParts[1];
                          mSysConsumptionValueInfo = currentConsumptionParts[2];
                      }
                    }
                    */
                    mSysConsumptionValueInfo = currentConsumption;
                    mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                } else {
                    mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                }
                if (mIsMtkPlatform && DEBUG)Log.i(TAG, "screen on-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                printReport(SCREEN_ON, String.valueOf(mPrevPowerSourceType),
                        String.valueOf(mPrevWifiState),
                        String.valueOf(mPrevBtState),
                        String.valueOf(mPrevGpsState),
                        mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                        String.valueOf(mPrevWifiAPState));
                // start polling
                //startPolling(context);
            }
            else if (action.equals(Intent.ACTION_SCREEN_OFF) && (mIsScreenOn)) {
                String currentConsumption = "";
                String mCurrnetDrainFormChargerIc = "";
                String mFgCurrentmAInfo="";
                String mSysConsumptionValueInfo="";
                String mBatteryVoltageInfo = "";
                mIsScreenOn = false;
                // print log when screen off
                if (mIsMtkPlatform) {
                    currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                    /*
                    if (currentConsumption.contains(" ")){
                        String[] currentConsumptionParts = currentConsumption.split(" ");
                      if (currentConsumptionParts.length == 3) {
                          mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                          mFgCurrentmAInfo = currentConsumptionParts[1];
                          mSysConsumptionValueInfo = currentConsumptionParts[2];
                      }
                    }
                    */
                    mSysConsumptionValueInfo = currentConsumption;
                    mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                } else {
                    mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                }
                if (mIsMtkPlatform && DEBUG)Log.i(TAG, "screen off-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                printReport(SCREEN_OFF, String.valueOf(mPrevPowerSourceType),
                        String.valueOf(mPrevWifiState),
                        String.valueOf(mPrevBtState),
                        String.valueOf(mPrevGpsState),
                        mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                        String.valueOf(mPrevWifiAPState));
                // cancel polling
                //cancelPolling(context);
            }
            else if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                /*
                long nowMillis = System.currentTimeMillis();
                String currentDateTime = getCurrentTime(nowMillis);
                Log.i(TAG, "PowerMonitorReceiver ACTION_BATTERY_CHANGED-currentDateTime:"+currentDateTime);
                if (mIsMtkPlatform) {
                    String fgCurrentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                    Log.i(TAG, "PowerMonitorReceiver ACTION_BATTERY_CHANGED-fgCurrentConsumption:"+fgCurrentConsumption);
                }
                */
                //get battery plugged state
                mNowPowerSourceType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                if (mPrevPowerSourceType == -1) {
                    mPrevPowerSourceType = mNowPowerSourceType;
                    return;
                }
                if (DEBUG)Log.i(TAG, "mPrevPowerSourceType :" + mPrevPowerSourceType + " mNowPowerSourceType :" + mNowPowerSourceType);
                if (mPrevPowerSourceType != mNowPowerSourceType) {
                    //print log if the Power Source Type state is changed
                    String currentConsumption = "";
                    String mCurrnetDrainFormChargerIc = "";
                    String mFgCurrentmAInfo="";
                    String mSysConsumptionValueInfo="";
                    String mBatteryVoltageInfo = "";
                    if (mIsMtkPlatform) {
                        currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                        /*
                        if (currentConsumption.contains(" ")){
                            String[] currentConsumptionParts = currentConsumption.split(" ");
                          if (currentConsumptionParts.length == 3) {
                              mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                              mFgCurrentmAInfo = currentConsumptionParts[1];
                              mSysConsumptionValueInfo = currentConsumptionParts[2];
                          }
                        }
                        */
                        mSysConsumptionValueInfo = currentConsumption;
                        mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                    } else {
                        mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                    }
                    if (mIsMtkPlatform && DEBUG)Log.i(TAG, "battery plugged-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                    printReport(BATTERY_PLUGGED, String.valueOf(mNowPowerSourceType),
                            String.valueOf(mPrevWifiState),
                            String.valueOf(mPrevBtState),
                            String.valueOf(mPrevGpsState),
                            mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                            String.valueOf(mPrevWifiAPState));
                }
                // set now powerSourceType to Prev PowerSourceType
                mPrevPowerSourceType = mNowPowerSourceType;

                mNowBatteryLevel = intent.getIntExtra("level", 0);
                if (DEBUG)Log.i(TAG, "mNowBatteryLevel:" + mNowBatteryLevel + " mPrevBatteryLevel:" + mPrevBatteryLevel);
                //dumpsys batterystats when BatteryLevel in 95%,90%,85%....5%.0% and only dumpsys one time,and can overwrite it
                if (mBatterystatsThreadSholdHS.contains(Integer.valueOf(mNowBatteryLevel))) {
                    if (mNowBatteryLevel != mPrevBatteryLevel) {
                        dumpInfoToPowermonitorFolder("dumpsys batterystats",mNowBatteryLevel, "batterystats");
                    }
                }
                if (mPrevBatteryLevel - mNowBatteryLevel >= 1) {
                    // print log when battery drop over 1%
                    String currentConsumption = "";
                    String mCurrnetDrainFormChargerIc = "";
                    String mFgCurrentmAInfo="";
                    String mSysConsumptionValueInfo="";
                    String mBatteryVoltageInfo = "";
                    if (mIsMtkPlatform) {
                        currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                        /*
                        if (currentConsumption.contains(" ")){
                            String[] currentConsumptionParts = currentConsumption.split(" ");
                          if (currentConsumptionParts.length == 3) {
                              mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                              mFgCurrentmAInfo = currentConsumptionParts[1];
                              mSysConsumptionValueInfo = currentConsumptionParts[2];
                          }
                        }
                        */
                        mSysConsumptionValueInfo = currentConsumption;
                        mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                    } else {
                        mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                    }
                    if (mIsMtkPlatform && DEBUG)Log.i(TAG, "battery drop-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                    printReport(BATTERY_CHANGED, String.valueOf(mPrevPowerSourceType),
                            String.valueOf(mPrevWifiState),
                            String.valueOf(mPrevBtState),
                            String.valueOf(mPrevGpsState),
                            mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                            String.valueOf(mPrevWifiAPState));
                }
                // set now BatteryLevel to PrevBatteryLevel
                mPrevBatteryLevel = mNowBatteryLevel;
            }
            else if (action.equals(ACTION_PROVIDERS_CHANGED)) {
                mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                if (mLocationManager != null) {
                    mNowGpsState = (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ? GPS_STATE_ENABLED
                            : GPS_STATE_DISABLED);
                    if (DEBUG)Log.i(TAG, "PowerMonitorReceiver mNowGpsState:"+mNowGpsState +" mPrevGpsState:"+mPrevGpsState);
                    if (mNowGpsState != mPrevGpsState) {
                        String currentConsumption = "";
                        String mCurrnetDrainFormChargerIc = "";
                        String mFgCurrentmAInfo="";
                        String mSysConsumptionValueInfo="";
                        String mBatteryVoltageInfo = "";
                        if (mIsMtkPlatform) {
                            currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                            /*
                            if (currentConsumption.contains(" ")){
                                String[] currentConsumptionParts = currentConsumption.split(" ");
                              if (currentConsumptionParts.length == 3) {
                                  mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                                  mFgCurrentmAInfo = currentConsumptionParts[1];
                                  mSysConsumptionValueInfo = currentConsumptionParts[2];
                              }
                            }
                            */
                            mSysConsumptionValueInfo = currentConsumption;
                            mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                        } else {
                            mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                        }
                        if (mIsMtkPlatform && DEBUG)Log.i(TAG, "GPS_STATE-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                        printReport(GPS_STATE_CHANGED, String.valueOf(mPrevPowerSourceType),
                                String.valueOf(mPrevWifiState),
                                String.valueOf(mPrevBtState),
                                String.valueOf(mNowGpsState),
                                mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                                String.valueOf(mPrevWifiAPState));
                    }
                    mPrevGpsState = mNowGpsState;
                }
            }
            else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                mNowWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (DEBUG)Log.i(TAG, "PowerMonitorReceiver mNowWifiState:" + mNowWifiState + " mPrevWifiState:" + mPrevWifiState);
                if (((mNowWifiState == WifiManager.WIFI_STATE_ENABLED) && (mNowWifiState != mPrevWifiState))
                        || ((mNowWifiState == WifiManager.WIFI_STATE_DISABLED) && (mNowWifiState != mPrevWifiState))) {
                    String currentConsumption = "";
                    String mCurrnetDrainFormChargerIc = "";
                    String mFgCurrentmAInfo="";
                    String mSysConsumptionValueInfo="";
                    String mBatteryVoltageInfo = "";
                    if (mIsMtkPlatform) {
                        currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                        /*
                        if (currentConsumption.contains(" ")){
                            String[] currentConsumptionParts = currentConsumption.split(" ");
                          if (currentConsumptionParts.length == 3) {
                              mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                              mFgCurrentmAInfo = currentConsumptionParts[1];
                              mSysConsumptionValueInfo = currentConsumptionParts[2];
                          }
                        }
                        */
                        mSysConsumptionValueInfo = currentConsumption;
                        mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                    } else {
                        mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                    }
                    if (mIsMtkPlatform && DEBUG)Log.i(TAG, "WifiState-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                    printReport(WIFI_STATE_CHANGED, String.valueOf(mPrevPowerSourceType),
                            String.valueOf(mNowWifiState),
                            String.valueOf(mPrevBtState),
                            String.valueOf(mPrevGpsState),
                            mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                            String.valueOf(mPrevWifiAPState));
                }
                mPrevWifiState = mNowWifiState;
            }
            else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                String currentConsumption = "";
                String mCurrnetDrainFormChargerIc = "";
                String mFgCurrentmAInfo="";
                String mSysConsumptionValueInfo="";
                String mBatteryVoltageInfo = "";
                mNowBtState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (DEBUG)Log.i(TAG, "PowerMonitorReceiver mNowBtState:" + mNowBtState + " mPrevBtState:" + mPrevBtState);
                if (((mNowBtState == BluetoothAdapter.STATE_ON) && (mNowBtState != mPrevBtState))
                        || ((mNowBtState == BluetoothAdapter.STATE_OFF) && (mNowBtState != mPrevBtState))) {
                    if (mIsMtkPlatform) {
                        currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                        /*
                        if (currentConsumption.contains(" ")){
                            String[] currentConsumptionParts = currentConsumption.split(" ");
                          if (currentConsumptionParts.length == 3) {
                              mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                              mFgCurrentmAInfo = currentConsumptionParts[1];
                              mSysConsumptionValueInfo = currentConsumptionParts[2];
                          }
                        }
                        */
                        mSysConsumptionValueInfo = currentConsumption;
                        mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                    } else {
                        mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                    }
                    if (mIsMtkPlatform && DEBUG)Log.i(TAG, "BtState-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                    printReport(BT_STATE_CHANGED, String.valueOf(mPrevPowerSourceType),
                            String.valueOf(mPrevWifiState),
                            String.valueOf(mNowBtState),
                            String.valueOf(mPrevGpsState),
                            mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                            String.valueOf(mPrevWifiAPState));
                }
                mPrevBtState = mNowBtState;
            }
            else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo nwInfo;
                NetworkInfo[] nwInfoArray;
                LinkProperties linkProperties;
                Boolean noDataConnection = true;
                nwInfoArray = connMgr.getAllNetworkInfo();
                if (nwInfoArray != null) {
                    for (int i = 0; i < nwInfoArray.length; i++) {
                        nwInfo = nwInfoArray[i];
                        if (nwInfo.getState() == NetworkInfo.State.CONNECTED) {
                            linkProperties = connMgr.getLinkProperties(nwInfo.getType());
                            if (linkProperties != null) {
                                interfaceName = linkProperties.getInterfaceName();
                                noDataConnection = false;
                            }
                        }
                    }
                }
                if (noDataConnection) {
                    interfaceName = "";
                }
                if (DEBUG)Log.i(TAG,"action.equals(ConnectivityManager.CONNECTIVITY_ACTION) interfaceName:" + interfaceName);
            }
            else if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
                String currentConsumption = "";
                String mCurrnetDrainFormChargerIc = "";
                String mFgCurrentmAInfo="";
                String mSysConsumptionValueInfo="";
                String mBatteryVoltageInfo = "";
                // This code will execute when the phone has an outgoing call
                isOutgoingCallStart = true;
                if (DEBUG)Log.i(TAG, "phone has an outgoing call");
                // print log when the phone has an outgoing call
                if (mIsMtkPlatform) {
                    currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                    /*
                    if (currentConsumption.contains(" ")){
                        String[] currentConsumptionParts = currentConsumption.split(" ");
                      if (currentConsumptionParts.length == 3) {
                          mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                          mFgCurrentmAInfo = currentConsumptionParts[1];
                          mSysConsumptionValueInfo = currentConsumptionParts[2];
                      }
                    }
                    */
                    mSysConsumptionValueInfo = currentConsumption;
                    mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                } else {
                    mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                }
                if (mIsMtkPlatform && DEBUG)Log.i(TAG, "outgoing call-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                printReport(OUTGOING_CALL_STARTED, String.valueOf(mPrevPowerSourceType),
                        String.valueOf(mPrevWifiState),
                        String.valueOf(mPrevBtState),
                        String.valueOf(mPrevGpsState),
                        mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                        String.valueOf(mPrevWifiAPState));
            }
            else if (callState != null && isOutgoingCallStart
                    && callState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                String currentConsumption = "";
                String mCurrnetDrainFormChargerIc = "";
                String mFgCurrentmAInfo="";
                String mSysConsumptionValueInfo="";
                String mBatteryVoltageInfo = "";
                //This code will execute when the outgoing call is disconnected
                isOutgoingCallStart = false;
                if (DEBUG)Log.i(TAG, "outgoing call is disconnected");
                // print log when the outgoing call is disconnected
                if (mIsMtkPlatform) {
                    currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                    /*
                    if (currentConsumption.contains(" ")){
                        String[] currentConsumptionParts = currentConsumption.split(" ");
                      if (currentConsumptionParts.length == 3) {
                          mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                          mFgCurrentmAInfo = currentConsumptionParts[1];
                          mSysConsumptionValueInfo = currentConsumptionParts[2];
                      }
                    }
                    */
                    mSysConsumptionValueInfo = currentConsumption;
                    mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                } else {
                    mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                }
                if (mIsMtkPlatform && DEBUG)Log.i(TAG, "outgoing call disconnected-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                printReport(OUTGOING_CALL_ENDED, String.valueOf(mPrevPowerSourceType),
                         String.valueOf(mPrevWifiState),
                         String.valueOf(mPrevBtState),
                         String.valueOf(mPrevGpsState),
                         mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                         String.valueOf(mPrevWifiAPState));
            }
            else if (callState != null
                    && callState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                //This code will execute when the phone has an incoming call
                isIncomingCallRinging = true;
                if (DEBUG)Log.i(TAG, "phone has an incoming call ringing");
            }
            else if (callState != null && isIncomingCallRinging
                    && callState.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                String currentConsumption = "";
                String mCurrnetDrainFormChargerIc = "";
                String mFgCurrentmAInfo="";
                String mSysConsumptionValueInfo="";
                String mBatteryVoltageInfo = "";
                // This code will execute when the incoming is answered
                isIncomingCallStart = true;
                isIncomingCallRinging = false;
                if (DEBUG)Log.i(TAG, "incoming call is answered");
                if (mIsMtkPlatform) {
                    currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                    /*
                    if (currentConsumption.contains(" ")){
                        String[] currentConsumptionParts = currentConsumption.split(" ");
                      if (currentConsumptionParts.length == 3) {
                          mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                          mFgCurrentmAInfo = currentConsumptionParts[1];
                          mSysConsumptionValueInfo = currentConsumptionParts[2];
                      }
                    }
                    */
                    mSysConsumptionValueInfo = currentConsumption;
                    mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                } else {
                    mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                }
                if (mIsMtkPlatform && DEBUG)Log.i(TAG, "incoming call started-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                // print log execute when the incoming call is answered
                printReport(INCOMING_CALL_STARTED, String.valueOf(mPrevPowerSourceType),
                        String.valueOf(mPrevWifiState),
                        String.valueOf(mPrevBtState),
                        String.valueOf(mPrevGpsState),
                        mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                        String.valueOf(mPrevWifiAPState));
            }
            else if (callState != null && isIncomingCallStart
                    && callState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                String currentConsumption = "";
                String mCurrnetDrainFormChargerIc = "";
                String mFgCurrentmAInfo="";
                String mSysConsumptionValueInfo="";
                String mBatteryVoltageInfo = "";
                //This code will execute when the incoming call is disconnected
                isIncomingCallStart = false;
                if (DEBUG)Log.i(TAG, "incoming call is disconnected");
                // print log when the incoming call is disconnected
                if (mIsMtkPlatform) {
                    currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                    /*
                    if (currentConsumption.contains(" ")){
                        String[] currentConsumptionParts = currentConsumption.split(" ");
                      if (currentConsumptionParts.length == 3) {
                          mCurrnetDrainFormChargerIc = currentConsumptionParts[0];
                          mFgCurrentmAInfo = currentConsumptionParts[1];
                          mSysConsumptionValueInfo = currentConsumptionParts[2];
                      }
                    }
                    */
                    mSysConsumptionValueInfo = currentConsumption;
                    mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                } else {
                    mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                }
                if (mIsMtkPlatform && DEBUG)Log.i(TAG, "incoming call ended-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                printReport(INCOMING_CALL_ENDED, String.valueOf(mPrevPowerSourceType),
                         String.valueOf(mPrevWifiState),
                         String.valueOf(mPrevBtState),
                         String.valueOf(mPrevGpsState),
                         mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                         String.valueOf(mPrevWifiAPState));
            }
            else if (action.equals(Intent.ACTION_SHUTDOWN)) {
                Log.i(TAG, "action.equals(Intent.ACTION_SHUTDOWN)");
                Intent i = new Intent(mContext, PowerMonitorService.class);
                mContext.stopService(i);
            }
            else if (action.equals(WifiManager.WIFI_AP_STATE_CHANGED_ACTION)) {
                mNowWifiAPState = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
                if (DEBUG)Log.i(TAG, "PowerMonitorReceiver mNowWifiAPState:" + mNowWifiAPState + " mPrevWifiAPState:" + mPrevWifiAPState);
                if (((mNowWifiAPState == WifiManager.WIFI_AP_STATE_ENABLED) && (mNowWifiAPState != mPrevWifiAPState))
                        || ((mNowWifiAPState == WifiManager.WIFI_AP_STATE_DISABLED) && (mNowWifiAPState != mPrevWifiAPState))) {
                    String currentConsumption = "";
                    String mCurrnetDrainFormChargerIc = "";
                    String mFgCurrentmAInfo="";
                    String mSysConsumptionValueInfo="";
                    String mBatteryVoltageInfo = "";
                    if (mIsMtkPlatform) {
                        currentConsumption = readFileOneLine(FG_CURRENTCONSUMPTION_FOR_MTK);
                        mSysConsumptionValueInfo = currentConsumption;
                        mBatteryVoltageInfo = readFileOneLine(BATTERY_VOLTAGE_INFO_FOR_MTK);
                    } else {
                        mCurrnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                    }
                    if (mIsMtkPlatform && DEBUG)Log.i(TAG, "WifiAPState-currentConsumption:"+currentConsumption+" mBatteryVoltageInfo:"+mBatteryVoltageInfo);
                    printReport(WIFI_AP_STATE_CHANGED, String.valueOf(mPrevPowerSourceType),
                            String.valueOf(mNowWifiState),
                            String.valueOf(mPrevBtState),
                            String.valueOf(mPrevGpsState),
                            mFgCurrentmAInfo,mCurrnetDrainFormChargerIc,mSysConsumptionValueInfo,mBatteryVoltageInfo,
                            String.valueOf(mNowWifiAPState));
                }
                mPrevWifiAPState = mNowWifiAPState;
            }
        }
    };

    public String getCurrentTime(long nowMillis) {
        Time nowTime = new Time();
        nowTime.set(nowMillis);
        Date currentDate = new Date(nowTime.toMillis(true));
        return currentDate.toString();
    }

    public String getLogType(int type) {
        String logString = "";
        switch (type) {
            case START_RECORD:
                logString = "log type:start record";
                break;
            case BOOT_COMPLETED:
                logString = "log type:boot completed";
                break;
            case POLLING_ACTION:
                logString = "log type:polling";
                break;
            case SCREEN_ON:
                logString = "log type:screen on";
                break;
            case SCREEN_OFF:
                logString = "log type:screen off";
                break;
            case BATTERY_CHANGED:
                logString = "log type:battery change";
                break;
            case BATTERY_PLUGGED:
                logString = "log type:battery plugged change";
                break;
            case PREPARE_SUSPEND:
                logString = "log type:prepare suspend";
                break;
            case RESUME:
                logString = "log type:resume";
                break;
            case WIFI_STATE_CHANGED:
                logString = "log type:wifi state change";
                break;
            case BT_STATE_CHANGED:
                logString = "log type:bt state change";
                break;
            case GPS_STATE_CHANGED:
                logString = "log type:gps state change";
                break;
            case OUTGOING_CALL_STARTED:
                logString = "log type:outgoing call started";
                break;
            case OUTGOING_CALL_ENDED:
                logString = "log type:outgoing call ended";
                break;
            case INCOMING_CALL_STARTED:
                logString = "log type:incoming call started";
                break;
            case INCOMING_CALL_ENDED:
                logString = "log type:incoming call ended";
                break;
            case WIFI_AP_STATE_CHANGED:
                logString = "log type:wifi AP state change";
                break;
        }
        return logString;
    }

    public void startPolling(Context context) {
        long nowMillis = System.currentTimeMillis();
        Time pollingTime = new Time();
        long CHECK_TIME = 0;

        try {
            String setPollingPeriodPath = getAppPath() + "/pollingPeriod.txt";
            File setPollingPeriodFile = new File(setPollingPeriodPath);
            Log.i(TAG,"startPolling setPollingPeriodFile dir = " + setPollingPeriodFile + " dir.exists():" + setPollingPeriodFile.exists());
            //check the polling period setting file exist or not
            if (!setPollingPeriodFile.exists()) {
                String initCheckTime = String.valueOf(INIT_CHECK_TIME);
                FileOutputStream fos = new FileOutputStream(setPollingPeriodFile, true);
                fos.write(initCheckTime.getBytes());
                fos.close();
                CHECK_TIME = INIT_CHECK_TIME;
            } else {
                //get the polling period from the setting file
                String strCheckTime = readFileOneLine(setPollingPeriodPath);
                Log.i(TAG, "startPolling strCheckTime:" + strCheckTime);
                if (isNumeric(strCheckTime)) {
                    CHECK_TIME = Long.parseLong(strCheckTime);
                } else {
                    CHECK_TIME = INIT_CHECK_TIME;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "startPolling CHECK_TIME = " + CHECK_TIME);

        pollingTime.set(nowMillis + CHECK_TIME);
        Intent pollingIntent = new Intent(POWER_MONITOR_ACTION_POLLING);
        pollingIntent.setClass(context, PowerMonitorService.class);
        PendingIntent pendingIntnet = PendingIntent.getService(context, 0, pollingIntent, 0);
        Date date = new Date(pollingTime.toMillis(true));
        Log.i(TAG, "startPolling Polling time = " + date.toString());
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.set(AlarmManager.RTC_WAKEUP, pollingTime.toMillis(true), pendingIntnet);
    }

    public void cancelPolling(Context context) {
        Log.i(TAG, "cancelPolling");
        Intent pollingIntent = new Intent(POWER_MONITOR_ACTION_POLLING);
        pollingIntent.setClass(context, PowerMonitorService.class);
        PendingIntent pendingIntnet = PendingIntent.getService(context, 0, pollingIntent, 0);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pendingIntnet);
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        try {
            unregisterReceiver(mPMReceiver);
            //mPowerMonitorObserver.stopObserving();
            TelephonyManager telManager = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
            telManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            if (mHandler != null){
                mHandler.removeMessages(MSG_GET);
            }
        } catch (Exception e) {
            // ignore
            Log.i(TAG, "onDestroy fail:"+e.toString());
        }
    }

    //cpu usage
    private float readUsage() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();
            String[] toks = load.split(" ");
            long idle1 = Long.parseLong(toks[5]);
            long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3])
                    + Long.parseLong(toks[4]) + Long.parseLong(toks[6])
                    + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);
            try {
                Thread.sleep(360);
            } catch (Exception e) {
            }
            reader.seek(0);
            load = reader.readLine();
            reader.close();
            toks = load.split(" ");
            long idle2 = Long.parseLong(toks[5]);
            long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3])
                    + Long.parseLong(toks[4]) + Long.parseLong(toks[6])
                    + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            return (float) (cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    private void printReport(int type, String powerSourceTypeInfo,
            String wifiStateInfo, String btStateInfo, String gpsStateInfo) {
        mPrintThread = new Thread(new printWork(type, powerSourceTypeInfo,
                wifiStateInfo, btStateInfo, gpsStateInfo));
        mPrintThread.start();
    }

    private void printReport(int type, String powerSourceTypeInfo,
            String wifiStateInfo, String btStateInfo, String gpsStateInfo,
            String wakeUpInfo) {
        mPrintThread = new Thread(new printWork(type, powerSourceTypeInfo,
                wifiStateInfo, btStateInfo, gpsStateInfo, wakeUpInfo));
        mPrintThread.start();
    }

    private void printReport(int type, String powerSourceTypeInfo,
            String wifiStateInfo, String btStateInfo, String gpsStateInfo,
            String fgCurrentmAInfo,String currnetDrainFormChargerIc
            ,String sysConsumptionValueInfo,String batteryVoltageInfo) {
        mPrintThread = new Thread(new printWork(type, powerSourceTypeInfo,
                wifiStateInfo, btStateInfo, gpsStateInfo,
                fgCurrentmAInfo,currnetDrainFormChargerIc,
                sysConsumptionValueInfo,batteryVoltageInfo));
        mPrintThread.start();
    }
    
    private void printReport(int type, String powerSourceTypeInfo,
            String wifiStateInfo, String btStateInfo, String gpsStateInfo,
            String fgCurrentmAInfo,String currnetDrainFormChargerIc
            ,String sysConsumptionValueInfo,String batteryVoltageInfo,String wifiAPStateInfo) {
        mPrintThread = new Thread(new printWork(type, powerSourceTypeInfo,
                wifiStateInfo, btStateInfo, gpsStateInfo,
                fgCurrentmAInfo,currnetDrainFormChargerIc,
                sysConsumptionValueInfo,batteryVoltageInfo,wifiAPStateInfo));
        mPrintThread.start();
    }

    public class printWork implements Runnable {
        int printType;
        String powerSourceInfo;
        String wifiInfo;
        String btInfo;
        String gpsInfo;
        String wakeUpInfo;
        String fgCurrentmAInfo;
        String currnetDrainFormChargerIc;
        String sysConsumptionValueInfo;
        String batteryVoltageInfo;
        String wifiAPStateInfo;

        public printWork(int printType, String powerSourceInfo,
                String wifiInfo, String btInfo, String gpsInfo) {
            this.printType = printType;
            this.powerSourceInfo = powerSourceInfo;
            this.wifiInfo = wifiInfo;
            this.btInfo = btInfo;
            this.gpsInfo = gpsInfo;
        }

        public printWork(int printType, String powerSourceInfo,
                String wifiInfo, String btInfo, String gpsInfo,String wakeUpInfo) {
            this.printType = printType;
            this.powerSourceInfo = powerSourceInfo;
            this.wifiInfo = wifiInfo;
            this.btInfo = btInfo;
            this.gpsInfo = gpsInfo;
            this.wakeUpInfo = wakeUpInfo;
        }

        public printWork(int printType, String powerSourceInfo,
                String wifiInfo, String btInfo, String gpsInfo,
                String fgCurrentmAInfo, String currnetDrainFormChargerIc,
                String sysConsumptionValueInfo,String batteryVoltageInfo) {
            this.printType = printType;
            this.powerSourceInfo = powerSourceInfo;
            this.wifiInfo = wifiInfo;
            this.btInfo = btInfo;
            this.gpsInfo = gpsInfo;
            this.fgCurrentmAInfo = fgCurrentmAInfo;
            this.currnetDrainFormChargerIc = currnetDrainFormChargerIc;
            this.sysConsumptionValueInfo = sysConsumptionValueInfo;
            this.batteryVoltageInfo = batteryVoltageInfo;
        }

        public printWork(int printType, String powerSourceInfo,
                String wifiInfo, String btInfo, String gpsInfo,
                String fgCurrentmAInfo, String currnetDrainFormChargerIc,
                String sysConsumptionValueInfo,String batteryVoltageInfo,
                String wifiAPStateInfo) {
            this.printType = printType;
            this.powerSourceInfo = powerSourceInfo;
            this.wifiInfo = wifiInfo;
            this.btInfo = btInfo;
            this.gpsInfo = gpsInfo;
            this.fgCurrentmAInfo = fgCurrentmAInfo;
            this.currnetDrainFormChargerIc = currnetDrainFormChargerIc;
            this.sysConsumptionValueInfo = sysConsumptionValueInfo;
            this.batteryVoltageInfo = batteryVoltageInfo;
            this.wifiAPStateInfo = wifiAPStateInfo;
        }

        public void run() {
            //delay 3 second to catch the current drain value from charger ic
            //when the battery plugged state is changed
            if (DEBUG)Log.i(TAG, "run() mIsMtkPlatform:" + mIsMtkPlatform);
            if (!mIsMtkPlatform) {
                if (printType == BATTERY_PLUGGED) {
                    try {
                        Thread.sleep(3000);
                        //if (!mIsMtkPlatform) {
                            currnetDrainFormChargerIc = readFileOneLine(CURRENT_DRAIN_FORM_CHARGER_IC);
                        //}
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            try {

                long nowMillis = System.currentTimeMillis();
                String currentDateTime = getCurrentTime(nowMillis);
                //String cpuUsage = String.valueOf(readUsage() * 100);
                CpuTracker =new ProcessCpuTracker(false);
                CpuTracker.update();
                float totalCpuPrecent = CpuTracker.getTotalCpuPercent();
                String cpuUsage = String.valueOf(totalCpuPrecent);
                String loadAverage = readFileOneLine(LOAD_AVERAGE);

                String currnetBatteryLevel = readFileOneLine(BATTERY_CAPACITY);
                ComponentName foregroundAppInfo = getActivityForegroundApp();
                String strForegroundAppInfo = (foregroundAppInfo == null ? "" : foregroundAppInfo.toString());
                String strPowerSourceType = getPowerSourceType(Integer.parseInt(powerSourceInfo));
                String strWifiState = getWifiState(Integer.parseInt(wifiInfo));
                String strBtState = getBtState(Integer.parseInt(btInfo));
                String strGpsState = getGpsState(Integer.parseInt(gpsInfo));
                String lcdBacklight = readFileOneLine(LCD_BACKLIGHT);
                String strGsmSignalStrength = String.valueOf(gsmSignalStrength);
                String strMobileConnTxBytes = String.valueOf(mobileConnTxBytes);
                String strMobileConnRxBytes = String.valueOf(mobileConnRxBytes);
                String strWifiConnTxBytes = String.valueOf(wifiConnTxBytes);
                String strWifiConnRxBytes = String.valueOf(wifiConnRxBytes);

                NetworkInfo wifiInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                String strWifiConnectivityStatus = "";
                if (wifiInfo.isConnected()) {
                    strWifiConnectivityStatus = "wifi network connectivity exists";
                } else {
                    strWifiConnectivityStatus = "wifi network connectivity not exists";
                }
                mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = mWifiManager.getConnectionInfo();
                int wifiSignalLevel = WifiManager.calculateSignalLevel(info.getRssi(), 5);
                String strWifiRssi = String.valueOf(info.getRssi());
                String strWifiSignalLevel = String.valueOf(wifiSignalLevel);

                String strWakeUpInfo = (wakeUpInfo == null) ? "" : wakeUpInfo;

                String strCpuOnlineCoresInfo ="";

                //cpu info
                strCpuOnlineCoresInfo = readFileOneLine(CPU_ONLINE_CORES_INFO);
                //String strCpuFrequencyInfo = getCpuFrequencyInfo(strCpuOnlineCoresInfo);
                String strCpuFrequencyInfo = "";
                String strWifiAPInfo = getWifiAPState(Integer.parseInt(wifiAPStateInfo));
                if (DEBUG) {
                Log.i(TAG, "run() printType:" + printType + " currentDateTime:" + currentDateTime + " currnetDrainFormChargerIc:" + currnetDrainFormChargerIc);
                Log.i(TAG, "run() currnetBatteryLevel:" + currnetBatteryLevel + " strForegroundAppInfo: " + strForegroundAppInfo +" strPowerSourceType:" + strPowerSourceType);
                Log.i(TAG, "run() strWifiState:" + strWifiState + " strBtState:" + strBtState + " strGpsState:" + strGpsState);
                Log.i(TAG, "run() lcdBacklight:" + lcdBacklight +" strGsmSignalStrength:"+strGsmSignalStrength);
                Log.i(TAG, "run() strMobileConnTxBytes:" + strMobileConnTxBytes + " strMobileConnRxBytes:" + strMobileConnRxBytes
                              + " strWifiConnTxBytes:" + strWifiConnTxBytes + " strWifiConnRxBytes:" + strWifiConnRxBytes);
                Log.i(TAG, "run() strWifiConnectivityStatus:" + strWifiConnectivityStatus);
                Log.i(TAG, "run() strWifiRssi:" + strWifiRssi +" strWifiSignalLevel:"+strWifiSignalLevel);
                Log.i(TAG, "run() strWakeUpInfo:" + strWakeUpInfo);
                Log.i(TAG, "run() fgCurrentmAInfo:" + fgCurrentmAInfo);
                Log.i(TAG, "run() sysConsumptionValueInfo:" + sysConsumptionValueInfo);
                Log.i(TAG, "run() batteryVoltageInfo:" + batteryVoltageInfo);
                Log.i(TAG, "run() strCpuOnlineCoresInfo:" + strCpuOnlineCoresInfo);
                Log.i(TAG, "run() cpuUsage:" + cpuUsage);
                Log.i(TAG, "run() strWifiAPInfo:" + strWifiAPInfo);
                }
                //Log.i(TAG, "run() strCpuFrequencyInfo:" + strCpuFrequencyInfo);
                Message message;
                String top10SystemLoad = "\n";

                Process process = Runtime.getRuntime().exec(TOP_10_SYSTEM_LOAD_CMD);
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        process.getInputStream()));
                String line = null;
                while ((line = in.readLine()) != null) {
                    top10SystemLoad += line + "\n";
                }
                PowerMonitorLogItems logItems = new PowerMonitorLogItems(
                        currentDateTime, cpuUsage, loadAverage,
                        currnetDrainFormChargerIc, top10SystemLoad,
                        currnetBatteryLevel, strForegroundAppInfo,
                        strPowerSourceType, strWifiState, strBtState,
                        strGpsState, lcdBacklight, strGsmSignalStrength,
                        strMobileConnTxBytes, strMobileConnRxBytes,
                        strWifiConnTxBytes, strWifiConnRxBytes,
                        strWifiConnectivityStatus, strWifiRssi,
                        strWifiSignalLevel, strWakeUpInfo,
                        fgCurrentmAInfo, sysConsumptionValueInfo,batteryVoltageInfo,
                        strCpuOnlineCoresInfo, strCpuFrequencyInfo,strWifiAPInfo);
                message = handler.obtainMessage(printType, logItems);
                handler.sendMessage(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String powerMonitorContent = "";
            int printcode = msg.what;
            if (DEBUG)Log.i(TAG, "handleMessage printcode:" + printcode);
            PowerMonitorLogItems logItems = (PowerMonitorLogItems) msg.obj;
            switch (printcode) {
                case START_RECORD:
                case BOOT_COMPLETED:
                case POLLING_ACTION:
                case SCREEN_ON:
                case BATTERY_CHANGED:
                case BATTERY_PLUGGED:
                case PREPARE_SUSPEND:
                case OUTGOING_CALL_STARTED:
                case OUTGOING_CALL_ENDED:
                case INCOMING_CALL_STARTED:
                case INCOMING_CALL_ENDED:
                case SCREEN_OFF:
                case WIFI_STATE_CHANGED:
                case BT_STATE_CHANGED:
                case GPS_STATE_CHANGED:
                case RESUME:
                case WIFI_AP_STATE_CHANGED:
                    powerMonitorContent += "----------" + getLogType(printcode) + "----------" + "\n";
                    powerMonitorContent += "CurrentTime :";
                    powerMonitorContent += logItems.getCurrentDateTime() + "\n";
                    powerMonitorContent += "CurrentBatteryLevel :";
                    powerMonitorContent += logItems.getCurrnetBatteryLevel() + "%" + "\n";
                    powerMonitorContent += "CPU Usage :";
                    powerMonitorContent += logItems.getCpuUsage() + "%" + "\n";
                    powerMonitorContent += "LOAD_AVERAGE :";
                    powerMonitorContent += logItems.getLoadAverage() + "\n";
                    if (!mIsMtkPlatform) {
                    powerMonitorContent += "CURRENT_DRAIN_FORM_CHARGER_IC :";
                    powerMonitorContent += logItems.getCurrnetDrainFormChargerIc() + "\n";
                    }
                    powerMonitorContent += "Power Source Type :";
                    powerMonitorContent += logItems.getPowerSourceType() + "\n";
                    powerMonitorContent += "Foreground App Info :";
                    powerMonitorContent += logItems.getForegroundAppInfo() + "\n";
                    powerMonitorContent += "top10 System load :" + "\n";
                    powerMonitorContent += logItems.getTop10SystemLoad()+ "\n";
                    powerMonitorContent += "Wifi state :";
                    powerMonitorContent += logItems.getWifiState() + "\n";
                    powerMonitorContent += "Bt state :";
                    powerMonitorContent += logItems.getBtState() + "\n";
                    powerMonitorContent += "Gps state :";
                    powerMonitorContent += logItems.getGpsState() + "\n";
                    powerMonitorContent += "LcdBacklight :";
                    powerMonitorContent += logItems.getLcdBacklight() + "\n";
                    powerMonitorContent += "GsmSignalStrength :";
                    powerMonitorContent += logItems.getGsmSignalStrength() +"(asu)"+ "\n";
                    powerMonitorContent += "Mobile Connection Tx Bytes :";
                    powerMonitorContent += logItems.getMobileConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Mobile Connection Rx Bytes :";
                    powerMonitorContent += logItems.getMobileConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Tx Bytes :";
                    powerMonitorContent += logItems.getWifiConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Rx Bytes :";
                    powerMonitorContent += logItems.getWifiConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connectivity Status :";
                    powerMonitorContent += logItems.getWifiConnectivityStatus() + "\n";
                    powerMonitorContent += "Wifi Rssi :";
                    powerMonitorContent += logItems.getWifiRssi() + "\n";
                    powerMonitorContent += "Wifi Signal Level :";
                    powerMonitorContent += logItems.getWifiSignalLevel() + "\n";
                    if (mIsMtkPlatform) {
                    powerMonitorContent += "battery voltage info :";
                    powerMonitorContent += logItems.getBatteryVoltageInfo() + "\n";
                    //powerMonitorContent += "FgCurrentmA info :" + "\n";
                    //powerMonitorContent += logItems.getFgCurrentmAInfo() + "\n";
                    //powerMonitorContent += "SysConsumptionValue info :" + "\n";
                    //powerMonitorContent += logItems.getSysConsumptionValueInfo() + "\n";
                    powerMonitorContent += "FG_Battery_CurrentConsumption info :";
                    powerMonitorContent += logItems.getSysConsumptionValueInfo() + "\n";
                    }
                    powerMonitorContent += "CPU online cores info :";
                    powerMonitorContent += logItems.getCpuOnlineCoresInfo() + "\n";
                    //powerMonitorContent += "CPU frequency info :" + "\n";
                    //powerMonitorContent += logItems.getCpuFrequencyInfo() + "\n";
                    powerMonitorContent += "Wi-Fi AP state :";
                    powerMonitorContent += logItems.getWifiAPState() + "\n";
                    break;
                /*
                case POLLING_ACTION:
                case SCREEN_ON:
                case BATTERY_CHANGED:
                case BATTERY_PLUGGED:
                case PREPARE_SUSPEND:
                case OUTGOING_CALL_STARTED:
                case OUTGOING_CALL_ENDED:
                case INCOMING_CALL_STARTED:
                case INCOMING_CALL_ENDED:
                    powerMonitorContent += "----------" + getLogType(printcode) + "----------" + "\n";
                    powerMonitorContent += "CurrentTime :" + "\n";
                    powerMonitorContent += logItems.getCurrentDateTime() + "\n";
                    powerMonitorContent += "CurrentBatteryLevel :" + "\n";
                    powerMonitorContent += logItems.getCurrnetBatteryLevel() + "%" + "\n";
                    powerMonitorContent += "CPU Usage :" + "\n";
                    powerMonitorContent += logItems.getCpuUsage() + "%" + "\n";
                    powerMonitorContent += "LOAD_AVERAGE :" + "\n";
                    powerMonitorContent += logItems.getLoadAverage() + "\n";
                    powerMonitorContent += "CURRENT_DRAIN_FORM_CHARGER_IC :" + "\n";
                    powerMonitorContent += logItems.getCurrnetDrainFormChargerIc() + "\n";
                    powerMonitorContent += "Power Source Type :" + "\n";
                    powerMonitorContent += logItems.getPowerSourceType() + "\n";
                    powerMonitorContent += "Foreground App Info :" + "\n";
                    powerMonitorContent += logItems.getForegroundAppInfo() + "\n";
                    powerMonitorContent += "top10 System load :" + "\n";
                    powerMonitorContent += logItems.getTop10SystemLoad() + "\n";
                    powerMonitorContent += "LcdBacklight :" + "\n";
                    powerMonitorContent += logItems.getLcdBacklight() + "\n";
                    powerMonitorContent += "GsmSignalStrength :" + "\n";
                    powerMonitorContent += logItems.getGsmSignalStrength() +"(asu)"+ "\n";
                    powerMonitorContent += "Mobile Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Mobile Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connectivity Status :" + "\n";
                    powerMonitorContent += logItems.getWifiConnectivityStatus() + "\n";
                    powerMonitorContent += "Wifi Rssi :" + "\n";
                    powerMonitorContent += logItems.getWifiRssi() + "\n";
                    powerMonitorContent += "Wifi Signal Level :" + "\n";
                    powerMonitorContent += logItems.getWifiSignalLevel() + "\n";
                    powerMonitorContent += "battery voltage info :" + "\n";
                    powerMonitorContent += logItems.getBatteryVoltageInfo() + "\n";
                    powerMonitorContent += "FgCurrentmA info :" + "\n";
                    powerMonitorContent += logItems.getFgCurrentmAInfo() + "\n";
                    powerMonitorContent += "SysConsumptionValue info :" + "\n";
                    powerMonitorContent += logItems.getSysConsumptionValueInfo() + "\n";
                    powerMonitorContent += "CPU online cores info :" + "\n";
                    powerMonitorContent += logItems.getCpuOnlineCoresInfo() + "\n";
                    //powerMonitorContent += "CPU frequency info :" + "\n";
                    //powerMonitorContent += logItems.getCpuFrequencyInfo() + "\n";
                    break;
                case RESUME:
                    powerMonitorContent += "----------" + getLogType(printcode) + "----------" + "\n";
                    powerMonitorContent += "CurrentTime :" + "\n";
                    powerMonitorContent += logItems.getCurrentDateTime() + "\n";
                    powerMonitorContent += "CurrentBatteryLevel :" + "\n";
                    powerMonitorContent += logItems.getCurrnetBatteryLevel() + "%" + "\n";
                    powerMonitorContent += "CPU Usage :" + "\n";
                    powerMonitorContent += logItems.getCpuUsage() + "%" + "\n";
                    powerMonitorContent += "LOAD_AVERAGE :" + "\n";
                    powerMonitorContent += logItems.getLoadAverage() + "\n";
                    powerMonitorContent += "CURRENT_DRAIN_FORM_CHARGER_IC :" + "\n";
                    powerMonitorContent += logItems.getCurrnetDrainFormChargerIc() + "\n";
                    powerMonitorContent += "Power Source Type :" + "\n";
                    powerMonitorContent += logItems.getPowerSourceType() + "\n";
                    powerMonitorContent += "Foreground App Info :" + "\n";
                    powerMonitorContent += logItems.getForegroundAppInfo() + "\n";
                    powerMonitorContent += "top10 System load :" + "\n";
                    powerMonitorContent += logItems.getTop10SystemLoad() + "\n";
                    powerMonitorContent += "LcdBacklight :" + "\n";
                    powerMonitorContent += logItems.getLcdBacklight() + "\n";
                    powerMonitorContent += "GsmSignalStrength :" + "\n";
                    powerMonitorContent += logItems.getGsmSignalStrength() +"(asu)"+ "\n";
                    powerMonitorContent += "Mobile Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Mobile Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connectivity Status :" + "\n";
                    powerMonitorContent += logItems.getWifiConnectivityStatus() + "\n";
                    powerMonitorContent += "Wifi Rssi :" + "\n";
                    powerMonitorContent += logItems.getWifiRssi() + "\n";
                    powerMonitorContent += "Wifi Signal Level :" + "\n";
                    powerMonitorContent += logItems.getWifiSignalLevel() + "\n";
                    powerMonitorContent += "Wakeup Info :" + "\n";
                    powerMonitorContent += logItems.getWakeUpInfo() + "\n";
                    powerMonitorContent += "battery voltage info :" + "\n";
                    powerMonitorContent += logItems.getBatteryVoltageInfo() + "\n";
                    powerMonitorContent += "FgCurrentmA info :" + "\n";
                    powerMonitorContent += logItems.getFgCurrentmAInfo() + "\n";
                    powerMonitorContent += "SysConsumptionValue info :" + "\n";
                    powerMonitorContent += logItems.getSysConsumptionValueInfo() + "\n";
                    powerMonitorContent += "CPU online cores info :" + "\n";
                    powerMonitorContent += logItems.getCpuOnlineCoresInfo() + "\n";
                    //powerMonitorContent += "CPU frequency info :" + "\n";
                    //powerMonitorContent += logItems.getCpuFrequencyInfo() + "\n";
                    break;
                case SCREEN_OFF:
                    powerMonitorContent += "----------" + getLogType(printcode) + "----------" + "\n";
                    powerMonitorContent += "CurrentTime :" + "\n";
                    powerMonitorContent += logItems.getCurrentDateTime() + "\n";
                    powerMonitorContent += "CurrentBatteryLevel :" + "\n";
                    powerMonitorContent += logItems.getCurrnetBatteryLevel() + "%" + "\n";
                    powerMonitorContent += "Power Source Type :" + "\n";
                    powerMonitorContent += logItems.getPowerSourceType() + "\n";
                    powerMonitorContent += "Foreground App Info :" + "\n";
                    powerMonitorContent += logItems.getForegroundAppInfo() + "\n";
                    powerMonitorContent += "LcdBacklight :" + "\n";
                    powerMonitorContent += logItems.getLcdBacklight() + "\n";
                    powerMonitorContent += "GsmSignalStrength :" + "\n";
                    powerMonitorContent += logItems.getGsmSignalStrength() +"(asu)"+ "\n";
                    powerMonitorContent += "Mobile Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Mobile Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connectivity Status :" + "\n";
                    powerMonitorContent += logItems.getWifiConnectivityStatus() + "\n";
                    powerMonitorContent += "Wifi Rssi :" + "\n";
                    powerMonitorContent += logItems.getWifiRssi() + "\n";
                    powerMonitorContent += "Wifi Signal Level :" + "\n";
                    powerMonitorContent += logItems.getWifiSignalLevel() + "\n";
                    powerMonitorContent += "battery voltage info :" + "\n";
                    powerMonitorContent += logItems.getBatteryVoltageInfo() + "\n";
                    powerMonitorContent += "FgCurrentmA info :" + "\n";
                    powerMonitorContent += logItems.getFgCurrentmAInfo() + "\n";
                    powerMonitorContent += "SysConsumptionValue info :" + "\n";
                    powerMonitorContent += logItems.getSysConsumptionValueInfo() + "\n";
                    powerMonitorContent += "CPU online cores info :" + "\n";
                    powerMonitorContent += logItems.getCpuOnlineCoresInfo() + "\n";
                    //powerMonitorContent += "CPU frequency info :" + "\n";
                    //powerMonitorContent += logItems.getCpuFrequencyInfo() + "\n";
                    break;
                case WIFI_STATE_CHANGED:
                    powerMonitorContent += "----------" + getLogType(printcode) + "----------" + "\n";
                    powerMonitorContent += "CurrentTime :" + "\n";
                    powerMonitorContent += logItems.getCurrentDateTime() + "\n";
                    powerMonitorContent += "Wifi state :" + "\n";
                    powerMonitorContent += logItems.getWifiState() + "\n";
                    powerMonitorContent += "LcdBacklight :" + "\n";
                    powerMonitorContent += logItems.getLcdBacklight() + "\n";
                    powerMonitorContent += "GsmSignalStrength :" + "\n";
                    powerMonitorContent += logItems.getGsmSignalStrength() +"(asu)"+ "\n";
                    powerMonitorContent += "Mobile Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Mobile Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "battery voltage info :" + "\n";
                    powerMonitorContent += logItems.getBatteryVoltageInfo() + "\n";
                    powerMonitorContent += "FgCurrentmA info :" + "\n";
                    powerMonitorContent += logItems.getFgCurrentmAInfo() + "\n";
                    powerMonitorContent += "SysConsumptionValue info :" + "\n";
                    powerMonitorContent += logItems.getSysConsumptionValueInfo() + "\n";
                    powerMonitorContent += "CPU online cores info :" + "\n";
                    powerMonitorContent += logItems.getCpuOnlineCoresInfo() + "\n";
                    //powerMonitorContent += "CPU frequency info :" + "\n";
                    //powerMonitorContent += logItems.getCpuFrequencyInfo() + "\n";
                    break;
                case BT_STATE_CHANGED:
                    powerMonitorContent += "----------" + getLogType(printcode) + "----------" + "\n";
                    powerMonitorContent += "CurrentTime :" + "\n";
                    powerMonitorContent += logItems.getCurrentDateTime() + "\n";
                    powerMonitorContent += "Bt state :" + "\n";
                    powerMonitorContent += logItems.getBtState() + "\n";
                    powerMonitorContent += "LcdBacklight :" + "\n";
                    powerMonitorContent += logItems.getLcdBacklight() + "\n";
                    powerMonitorContent += "GsmSignalStrength :" + "\n";
                    powerMonitorContent += logItems.getGsmSignalStrength() +"(asu)"+ "\n";
                    powerMonitorContent += "Mobile Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Mobile Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "battery voltage info :" + "\n";
                    powerMonitorContent += logItems.getBatteryVoltageInfo() + "\n";
                    powerMonitorContent += "FgCurrentmA info :" + "\n";
                    powerMonitorContent += logItems.getFgCurrentmAInfo() + "\n";
                    powerMonitorContent += "SysConsumptionValue info :" + "\n";
                    powerMonitorContent += logItems.getSysConsumptionValueInfo() + "\n";
                    powerMonitorContent += "CPU online cores info :" + "\n";
                    powerMonitorContent += logItems.getCpuOnlineCoresInfo() + "\n";
                    //powerMonitorContent += "CPU frequency info :" + "\n";
                    //powerMonitorContent += logItems.getCpuFrequencyInfo() + "\n";
                    break;
                case GPS_STATE_CHANGED:
                    powerMonitorContent += "----------" + getLogType(printcode) + "----------" + "\n";
                    powerMonitorContent += "CurrentTime :" + "\n";
                    powerMonitorContent += logItems.getCurrentDateTime() + "\n";
                    powerMonitorContent += "Gps state :" + "\n";
                    powerMonitorContent += logItems.getGpsState() + "\n";
                    powerMonitorContent += "LcdBacklight :" + "\n";
                    powerMonitorContent += logItems.getLcdBacklight() + "\n";
                    powerMonitorContent += "GsmSignalStrength :" + "\n";
                    powerMonitorContent += logItems.getGsmSignalStrength() +"(asu)"+ "\n";
                    powerMonitorContent += "Mobile Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Mobile Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getMobileConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Tx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnTxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "Wifi Connection Rx Bytes :" + "\n";
                    powerMonitorContent += logItems.getWifiConnRxBytes() +"(Bytes)"+ "\n";
                    powerMonitorContent += "battery voltage info :" + "\n";
                    powerMonitorContent += logItems.getBatteryVoltageInfo() + "\n";
                    powerMonitorContent += "FgCurrentmA info :" + "\n";
                    powerMonitorContent += logItems.getFgCurrentmAInfo() + "\n";
                    powerMonitorContent += "SysConsumptionValue info :" + "\n";
                    powerMonitorContent += logItems.getSysConsumptionValueInfo() + "\n";
                    powerMonitorContent += "CPU online cores info :" + "\n";
                    powerMonitorContent += logItems.getCpuOnlineCoresInfo() + "\n";
                    //powerMonitorContent += "CPU frequency info :" + "\n";
                    //powerMonitorContent += logItems.getCpuFrequencyInfo() + "\n";
                    break;
                    */
            }
            if (DEBUG)Log.i(TAG, "printLog write to internal sd");
            /*
            Intent i = new Intent();
            i.setAction("com.compalcomm.transferreport.write");
            i.putExtra("path", REPORT_SOURCE_PATH + mOutFileName);
            i.putExtra("data", powerMonitorContent);
            mContext.startService(i);
            */
            try {
                //String path = intent.getStringExtra("path");
                //String data = intent.getStringExtra("data");
                String path = REPORT_SOURCE_PATH + mOutFileName;
                String data = powerMonitorContent;
                if (DEBUG)Log.i(TAG, "path = " + path);
                //Log.i(TAG, "data = " + data);

                File fileDir = new File(path.substring(0, path.lastIndexOf("/")));
                if(!fileDir.exists()){
                    Log.i(TAG, "mkdirs");
                    fileDir.mkdirs();
                }
                //check /storage/emulated/0 size > mStorageEmulatedThresholdSize(ex:50MB)
                File externalStoragePath = Environment.getExternalStorageDirectory();
                if (DEBUG)Log.i(TAG, "externalStoragePath = " + externalStoragePath);
                if (freeSpaceCalculation(externalStoragePath.getPath()) > mStorageEmulatedThresholdSize) {
                    File logFile = new File(path);
                    FileOutputStream fout = new FileOutputStream(logFile, true);
                    fout.write(data.getBytes());
                    fout.close();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    public String readFileOneLine(String filePath) {
        boolean flag = false;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(filePath))));
            String s = br.readLine();
            br.close();
            return s;
        } catch (Exception e) {
            e.printStackTrace();
            return "0";
        }
    }

    private ComponentName getActivityForegroundApp() {
        ComponentName activityComponentName = null;
        ActivityManager.RunningTaskInfo info;
        ActivityManager mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> list = mActivityManager.getRunningTasks(9999);
        Iterator<ActivityManager.RunningTaskInfo> iterator = list.iterator();
        info = iterator.next();
        activityComponentName = info.topActivity;
        return activityComponentName;
    }

    public final static boolean isNumeric(String str) {
        if (str != null && !"".equals(str.trim()))
            return str.matches("^[0-9]*$");
        else
            return false;
    }

    public String getPowerSourceType(int plugged) {
        String psTypeString = "";
        switch (plugged) {
            case 0:
                psTypeString = "on battery";
                break;
            case BatteryManager.BATTERY_PLUGGED_AC:
                psTypeString = "plugged ac";
                break;
            case BatteryManager.BATTERY_PLUGGED_USB:
                psTypeString = "plugged usb";
                break;
            default:
                psTypeString = "";
                break;
        }
        return psTypeString;
    }

    public String getWifiState(int state) {
        String wifiStateString = "";
        switch (state) {
            case WifiManager.WIFI_STATE_DISABLED:
                wifiStateString = "Wi-Fi is disabled";
                break;
            case WifiManager.WIFI_STATE_ENABLED:
                wifiStateString = "Wi-Fi is enabled";
                break;
            case WifiManager.WIFI_STATE_UNKNOWN:
                wifiStateString = "Wi-Fi is in an unknown state";
                break;
            default:
                wifiStateString = "";
                break;
        }
        return wifiStateString;
    }

    public String getBtState(int state) {
        String btStateString = "";
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                btStateString = "Bluetooth adapter is off";
                break;
            case BluetoothAdapter.STATE_ON:
                btStateString = "Bluetooth adapter is on";
                break;
            default:
                btStateString = "";
                break;
        }
        return btStateString;
    }

    public String getGpsState(int state) {
        String gpsStateString = "";
        switch (state) {
            case GPS_STATE_DISABLED:
                gpsStateString = "GPS is disabled";
                break;
            case GPS_STATE_ENABLED:
                gpsStateString = "GPS is enabled";
                break;
            default:
                gpsStateString = "";
                break;
        }
        return gpsStateString;
    }

    public void doExeCmd(String command) {
        DataOutputStream dos = null;
        try {
            Process process = Runtime.getRuntime().exec("su");
            dos = new DataOutputStream(process.getOutputStream());
            dos.writeBytes(command);
            dos.writeBytes("exit\n");
            dos.flush();
            dos.close();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (dos != null) {
                    dos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void dumpKlogcat(String abmormalType) {
        Date currentTime = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String dateString = formatter.format(currentTime);
        mKlogName = "Klog_" + dateString +"_"+abmormalType+ ".log";
        final String klogSrcPath = DATA_CRASH_PATH + mKlogName;
        final String command = "klogcat dump " + klogSrcPath;
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    java.lang.Process p = Runtime.getRuntime().exec("/system/bin/sh -");
                    DataOutputStream os = new DataOutputStream(p.getOutputStream());
                    os.writeBytes(command + "\n");
                    os.writeBytes("exit\n");
                    os.flush();

                    p.waitFor();
                    copyReportFile(klogSrcPath,mKlogName);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    //move klog from data/crash to /storage/sdcard0/powermonitor
    private void copyReportFile(String source,String klogname) {
        mKlogDest = REPORT_SOURCE_PATH + mKlogName;
        Intent intent = (new Intent())
                    .setClassName("com.compalcomm.transferreport", "com.compalcomm.transferreport.TransferReportService");
        intent.setAction(ACTION_TRANSFOR);
        intent.putExtra("source", DATA_CRASH_PATH + mKlogName + ".gz");
        intent.putExtra("destination", mKlogDest + ".gz");
        this.startService(intent);
    }

    //dump klogcat to /storage/sdcard0/powermonitor/
    private void dumpKlogcatToPowermonitorFolder(String abmormalType) {
        Date currentTime = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        String dateString = formatter.format(currentTime);
        mKlogName = "Klog_" + dateString +"_"+abmormalType+ ".log";
        final String klogSrcPath = REPORT_SOURCE_PATH + mKlogName;
        final String command = "klogcat > " + klogSrcPath;
        if (DEBUG)Log.e(TAG, "dumpKlogcatToPowermonitorFolder command:"+command);
        File fileDir = new File(REPORT_SOURCE_PATH);
        if(!fileDir.exists()){
            fileDir.mkdirs();
        }
        //check /storage/emulated/0 size > mStorageEmulatedThresholdSize(ex:50MB)
        File externalStoragePath = Environment.getExternalStorageDirectory();
        if (freeSpaceCalculation(externalStoragePath.getPath()) > mStorageEmulatedThresholdSize) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    java.lang.Process p = Runtime.getRuntime().exec("/system/bin/sh -");
                    DataOutputStream os = new DataOutputStream(p.getOutputStream());
                    os.writeBytes(command + "\n");
                    os.writeBytes("exit\n");
                    os.flush();

                    p.waitFor();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        thread.start();
        }
    }

    public void chargingOrConstantState() {
        //mBatteryLevelDropCounter set to 0 if charging state
        mBatteryLevelDropCounter = 0;
        Log.e(TAG,"chargingOrConstantState-mBatteryLevelDropCounter:"+mBatteryLevelDropCounter);
    }

    public void dischargingState() {
        // test case:battery level drop and drop 3% continually,dumpKlogcat
        mBatteryLevelDropCounter++;
        Log.i(TAG, "dischargingState() mBatteryLevelDropCounter:" + mBatteryLevelDropCounter);
        if (mBatteryLevelDropCounter == 3) {
            mDumpCounter++;
            Log.i(TAG,"dischargingState mDumpCounter:"+mDumpCounter);
            if (mDumpCounter <= 2) {
                Log.i(TAG,"dischargingState() mDumpCounter <= 2 dumpKlogcat");
                dumpKlogcat("batteryDropContinue");
                mBatteryLevelDropCounter = 0;
             } else {
               //do nothing if dumpKlogcat 2 times
               Log.e(TAG, "dischargingState() do nothing if dumpKlogcat 2 times");
               mBatteryLevelDropCounter = 99;
               mDumpCounter = 99;
             }
        }
    }

    public void SysConsumptionValuePassState(){
        //SysConsumptionValue set to 0 if charging state
        mSysConsumptionValueOverCounter = 0;
        SysConsumptionValueCounter = 0;
    }

    public void SysConsumptionValueNotPassState(){
        mSysConsumptionValueOverCounter++;
        SysConsumptionValueCounter = 0;
        Log.e(TAG, "SysConsumptionValueNotPassState() mSysConsumptionValueOverCounter:"+mSysConsumptionValueOverCounter);
        if (mSysConsumptionValueOverCounter == 5) {
            dumpKlogcat("currentAvgOver600mA");
            //if dumpKlogcat then wait 10 min
            waitFlag = true;
            mSysConsumptionValueOverCounter = 0;
        }
    }

    public void testSysConsumptionCurrent() {
        Log.i(TAG, "testSysConsumptionCurrent() mTestSysConsumptionValue:"+mTestSysConsumptionValue);
        //test whether the SysConsumptionCurrent > 600mA
        if (mTestSysConsumptionValue > 600) {
            mTestSysConsumptionValueArray[mTestSysConsumptionValueCount] = 1;
        } else {
            mTestSysConsumptionValueArray[mTestSysConsumptionValueCount] = 0;
        }
        Log.i(TAG,"testSysConsumptionCurrent() mTestSysConsumptionValueArray["+mTestSysConsumptionValueCount+"]:" + mTestSysConsumptionValueArray[mTestSysConsumptionValueCount]);
        //first run only to put raw data
        if (!mIsTestSysConsumptionCurrentFirstRun) {
            int tatalTestNum = 0;
            for (int i = 0; i < mTestSysConsumptionValueNum; i++) {
                tatalTestNum = mTestSysConsumptionValueArray[i] + tatalTestNum;
                // if The number of more than 600mA is over 50%,than dump klog
                if (tatalTestNum > (mTestSysConsumptionValueNum / 2)) {
                    dumpKlogcat("currentAvgOver600mA");
                }
            }
        }

        mTestSysConsumptionValueCount = ((mTestSysConsumptionValueCount + 1) % mTestSysConsumptionValueNum);//[0],[1],[2],..[179](30 min test index)
        Log.i(TAG,"testSysConsumptionCurrent() mTestSysConsumptionValueCount:" + mTestSysConsumptionValueCount);

        if (mTestSysConsumptionValueCount == mTestSysConsumptionValueNum - 1) {
            mIsTestSysConsumptionCurrentFirstRun = false;
        }
        Log.i(TAG,"testSysConsumptionCurrent()----------------------------------------");
    }

    public void testBatteryVoltage() {
        // 15 min test compare raw data [0],[89]
        if ((mTestBatteryVoltageValueCount == 0)
                || (mTestBatteryVoltageValueCount == (mTestBatteryVoltagePeriod1Num - 1))) {
            mTestBatteryVoltageArray[mTestBatteryVoltageValueCount] = mBatteryVoltage;

            Log.i(TAG, "testBatteryVoltage() mTestBatteryVoltageArray["+ mTestBatteryVoltageValueCount + "]:"
                    + mTestBatteryVoltageArray[mTestBatteryVoltageValueCount]);
        }

        if(!mIsTestBatteryVoltageFirstRun){
            //[89]-[0] < 0
            if (mTestBatteryVoltageArray[(mTestBatteryVoltagePeriod1Num - 1)]
                    - mTestBatteryVoltageArray[0] < 0) {
                //only save raw data [90],[209] to compare if it can up to the Threshold
                if((mTestBatteryVoltageValueCount == mTestBatteryVoltagePeriod1Num) ||
                        (mTestBatteryVoltageValueCount == (mTestBatteryVoltageNum-1))){
                    mTestBatteryVoltageArray[mTestBatteryVoltageValueCount] = mBatteryVoltage;

                    Log.i(TAG,"testBatteryVoltage() mTestBatteryVoltageArray["+ mTestBatteryVoltageValueCount+ "]:"
                                    + mTestBatteryVoltageArray[mTestBatteryVoltageValueCount]);
                }
                //if arrive 35 min [209]
                if(mTestBatteryVoltageValueCount == (mTestBatteryVoltageNum -1)){
                    //[0]~[89] (compute 15min data Gap)
                    int mTestBatteryVoltagePeriod1Gap = mTestBatteryVoltageArray[(mTestBatteryVoltagePeriod1Num - 1)]
                            - mTestBatteryVoltageArray[0];
                    Log.i(TAG,"testBatteryVoltage() mTestBatteryVoltagePeriod1Gap:" + mTestBatteryVoltagePeriod1Gap);
                    //[90]~[209] (compute 20min data Gap)
                    int mTestBatteryVoltagePeriod2Gap = mTestBatteryVoltageArray[mTestBatteryVoltageNum - 1]
                            - mTestBatteryVoltageArray[mTestBatteryVoltagePeriod1Num];
                    Log.i(TAG,"testBatteryVoltage() mTestBatteryVoltagePeriod2Gap:" + mTestBatteryVoltagePeriod2Gap);
                    // if BatteryVoltage still drop,dump klog
                    if (mTestBatteryVoltagePeriod2Gap < 0) {
                        Log.e(TAG,"testBatteryVoltage() mTestBatteryVoltagePeriod2Gap < 0 dumpKlogcat()");
                        dumpKlogcat("VoltageNotBackToThreshold");
                    }
                    // if BatteryVoltage can go up but not arrive at
                    // threshold(mTestBatteryVoltagePeriod1Gap/2),dumpKlogcat
                    if (mTestBatteryVoltagePeriod2Gap >= 0
                          && mTestBatteryVoltagePeriod2Gap < (Math.abs(mTestBatteryVoltagePeriod1Gap) / 2)) {
                        Log.e(TAG,"testBatteryVoltage() mTestBatteryVoltagePeriod2Gap not arrive at threshold dumpKlogcat()");
                        dumpKlogcat("VoltageNotBackToThreshold");
                    }
                }
            } else {
                //[89]-[0] >= 0,recompute 15min
                mTestBatteryVoltageValueCount = mTestBatteryVoltageNum - 1;
                mIsTestBatteryVoltageFirstRun = true;
                Log.e(TAG, "testBatteryVoltage(),recompute 15min");
            }
        }
        mTestBatteryVoltageValueCount = (mTestBatteryVoltageValueCount + 1) % mTestBatteryVoltageNum;//[0],[1],[2],...[209](35 min test index)

        Log.i(TAG, "testBatteryVoltage() mTestBatteryVoltageValueCount final:"+mTestBatteryVoltageValueCount);
        //first 15 min ([0],[1]..[89])only to put raw data
        if (mTestBatteryVoltageValueCount == (mTestBatteryVoltagePeriod1Num - 1)) {
            mIsTestBatteryVoltageFirstRun = false;
        }
        Log.i(TAG,"testBatteryVoltage()----------------------------------------");
    }

    //dump Info to /data/powermonitorbatterystats/ 
    //@param cmd ex:dumpsys batterystats
    //@param int ex: batterylevel
    //@param type ex: batterystats
    private void dumpInfoToPowermonitorFolder(String cmd, int batterylevel, String type) {
        String strBatterylevel = String.valueOf(batterylevel);
        mBatterystatsName = type+"_"+strBatterylevel+"%"+".log";
        final String command = cmd + " > " + BATTERYSTATS_SOURCE_PATH + mBatterystatsName;
        if (DEBUG)Log.i(TAG, "dumpInfoToPowermonitorFolder command:" + command);
        File fileDir = new File(BATTERYSTATS_SOURCE_PATH);
        if(!fileDir.exists()){
            fileDir.mkdirs();
        }
        //check data partion size > mDataPartionThresholdSize(ex:50MB)
        File path = Environment.getDataDirectory();
        if (freeSpaceCalculation(path.getPath()) > mDataPartionThresholdSize) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        java.lang.Process p = Runtime.getRuntime().exec("/system/bin/sh -");
                        DataOutputStream os = new DataOutputStream(p.getOutputStream());
                        os.writeBytes(command + "\n");
                        os.writeBytes("exit\n");
                        os.flush();

                        p.waitFor();
                        copyFiletoSdcard(BATTERYSTATS_SOURCE_PATH + mBatterystatsName,REPORT_SOURCE_PATH + mBatterystatsName);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            thread.start();
        }
    }

    //@param path to the file system
    //@return free space in MB
    private long freeSpaceCalculation(String path) {
        android.os.StatFs stat = new android.os.StatFs(path);
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks() * blockSize;
        long availableMB = availableBlocks / (1024 * 1024);
        return availableMB;
    }

    //move batterystats from data/powermonitorbatterystats to /storage/sdcard0/powermonitor
    private void copyFiletoSdcard(String source,String destination) {
        try {
            Boolean delSource = false;
            if (DEBUG)Log.i(TAG, "copyFiletoSdcard source = " + source);
            if (DEBUG)Log.i(TAG, "copyFiletoSdcard destination = " + destination);
            File srcFile = new File(source);
            File desFile = new File(destination);

            InputStream in = new FileInputStream(srcFile);
            OutputStream out = new FileOutputStream(desFile);

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0){
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
            if (delSource){
                Log.e(TAG, "Delete source file.");
                srcFile.delete();
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public String getWifiAPState(int state) {
        String wifiAPStateString = "";
        switch (state) {
            case WifiManager.WIFI_AP_STATE_DISABLED:
                wifiAPStateString = "Wi-Fi AP is disabled";
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                wifiAPStateString = "Wi-Fi AP is enabled";
                break;
            case WifiManager.WIFI_AP_STATE_FAILED:
                wifiAPStateString = "Wi-Fi AP is failed";
                break;
            default:
                wifiAPStateString = "";
                break;
        }
        return wifiAPStateString;
    }
  }