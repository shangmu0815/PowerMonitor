package com.cci.powermonitor;

public class PowerMonitorLogItems {
    public String currentDateTime;
    public String cpuUsage;
    public String loadAverage;
    public String currnetDrainFormChargerIc;
    public String top10SystemLoad;
    public String currnetBatteryLevel;
    public String foregroundAppInfo;
    public String powerSourceType;
    public String wifiState;
    public String btState;
    public String gpsState;
    public String lcdBacklight;
    public String gsmSignalStrength;
    public String mobileConnTxBytes;
    public String mobileConnRxBytes;
    public String wifiConnTxBytes;
    public String wifiConnRxBytes;
    public String wifiConnectivityStatus;
    public String wifiRssi;
    public String wifiSignalLevel;
    public String wakeUpInfo;
    public String fgCurrentmAInfo;
    public String sysConsumptionValueInfo;
    public String batteryVoltageInfo;
    public String cpuOnlineCoresInfo;
    public String cpuFrequencyInfo;
    public String wifiAPState;

    public PowerMonitorLogItems(String currentDateTime, String cpuUsage,
                String loadAverage, String currnetDrainFormChargerIc,
                String top10SystemLoad, String currnetBatteryLevel,
                String foregroundAppInfo, String powerSourceType, String wifiState,
                String btState, String gpsState, String lcdBacklight,
                String gsmSignalStrength, String mobileConnTxBytes,
                String mobileConnRxBytes, String wifiConnTxBytes,
                String wifiConnRxBytes, String wifiConnectivityStatus,
                String wifiRssi, String wifiSignalLevel, String wakeUpInfo,
                String fgCurrentmAInfo, String sysConsumptionValueInfo,
                String batteryVoltageInfo,String cpuOnlineCoresInfo,
                String cpuFrequencyInfo,String wifiAPState) {
            super();
            this.currentDateTime = currentDateTime;
            this.cpuUsage = cpuUsage;
            this.loadAverage = loadAverage;
            this.currnetDrainFormChargerIc = currnetDrainFormChargerIc;
            this.top10SystemLoad = top10SystemLoad;
            this.currnetBatteryLevel = currnetBatteryLevel;
            this.foregroundAppInfo = foregroundAppInfo;
            this.powerSourceType = powerSourceType;
            this.wifiState = wifiState;
            this.btState = btState;
            this.gpsState = gpsState;
            this.lcdBacklight = lcdBacklight;
            this.gsmSignalStrength = gsmSignalStrength;
            this.mobileConnTxBytes = mobileConnTxBytes;
            this.mobileConnRxBytes = mobileConnRxBytes;
            this.wifiConnTxBytes = wifiConnTxBytes;
            this.wifiConnRxBytes = wifiConnRxBytes;
            this.wifiConnectivityStatus = wifiConnectivityStatus;
            this.wifiRssi = wifiRssi;
            this.wifiSignalLevel = wifiSignalLevel;
            this.wakeUpInfo = wakeUpInfo;
            this.fgCurrentmAInfo = fgCurrentmAInfo;
            this.sysConsumptionValueInfo = sysConsumptionValueInfo;
            this.batteryVoltageInfo = batteryVoltageInfo;
            this.cpuOnlineCoresInfo = cpuOnlineCoresInfo;
            this.cpuFrequencyInfo = cpuFrequencyInfo;
            this.wifiAPState = wifiAPState;
        }

    public String getCurrentDateTime() {
        return currentDateTime;
    }
    public void setCurrentDateTime(String currentDateTime) {
        this.currentDateTime = currentDateTime;
    }
    public String getCpuUsage() {
        return cpuUsage;
    }
    public void setCpuUsage(String cpuUsage) {
        this.cpuUsage = cpuUsage;
    }
    public String getLoadAverage() {
        return loadAverage;
    }
    public void setLoadAverage(String loadAverage) {
        this.loadAverage = loadAverage;
    }
    public String getCurrnetDrainFormChargerIc() {
        return currnetDrainFormChargerIc;
    }
    public void setCurrnetDrainFormChargerIc(String currnetDrainFormChargerIc) {
        this.currnetDrainFormChargerIc = currnetDrainFormChargerIc;
    }
    public String getTop10SystemLoad() {
        return top10SystemLoad;
    }
    public void setTop10SystemLoad(String top10SystemLoad) {
        this.top10SystemLoad = top10SystemLoad;
    }
    public String getCurrnetBatteryLevel() {
        return currnetBatteryLevel;
    }
    public void setCurrnetBatteryLevel(String currnetBatteryLevel) {
        this.currnetBatteryLevel = currnetBatteryLevel;
    }
    public String getForegroundAppInfo() {
        return foregroundAppInfo;
    }
    public void setForegroundAppInfo(String foregroundAppInfo) {
        this.foregroundAppInfo = foregroundAppInfo;
    }
    public String getPowerSourceType() {
        return powerSourceType;
    }
    public void setPowerSourceType(String powerSourceType) {
        this.powerSourceType = powerSourceType;
    }
    public String getWifiState() {
        return wifiState;
    }
    public void setWifiState(String wifiState) {
        this.wifiState = wifiState;
    }
    public String getBtState() {
        return btState;
    }
    public void setBtState(String btState) {
        this.btState = btState;
    }
    public String getGpsState() {
        return gpsState;
    }
    public void setGpsState(String gpsState) {
        this.gpsState = gpsState;
    }
    public String getLcdBacklight() {
        return lcdBacklight;
    }
    public void setLcdBacklight(String lcdBacklight) {
        this.lcdBacklight = lcdBacklight;
    }
    public String getGsmSignalStrength() {
        return gsmSignalStrength;
    }
    public void setGsmSignalStrength(String gsmSignalStrength) {
        this.gsmSignalStrength = gsmSignalStrength;
    }
    public String getMobileConnTxBytes() {
        return mobileConnTxBytes;
    }
    public void setMobileConnTxBytes(String mobileConnTxBytes) {
        this.mobileConnTxBytes = mobileConnTxBytes;
    }
    public String getMobileConnRxBytes() {
        return mobileConnRxBytes;
    }
    public void setMobileConnRxBytes(String mobileConnRxBytes) {
        this.mobileConnRxBytes = mobileConnRxBytes;
    }
    public String getWifiConnTxBytes() {
        return wifiConnTxBytes;
    }
    public void setWifiConnTxBytes(String wifiConnTxBytes) {
        this.wifiConnTxBytes = wifiConnTxBytes;
    }
    public String getWifiConnRxBytes() {
        return wifiConnRxBytes;
    }
    public void setWifiConnRxBytes(String wifiConnRxBytes) {
        this.wifiConnRxBytes = wifiConnRxBytes;
    }
    public String getWifiConnectivityStatus() {
        return wifiConnectivityStatus;
    }
    public void setWifiConnectivityStatus(String wifiConnectivityStatus) {
        this.wifiConnectivityStatus = wifiConnectivityStatus;
    }
    public String getWifiRssi() {
        return wifiRssi;
    }
    public void setWifiRssi(String wifiRssi) {
        this.wifiRssi = wifiRssi;
    }
    public String getWifiSignalLevel() {
        return wifiSignalLevel;
    }
    public void setWifiSignalLevel(String wifiSignalLevel) {
        this.wifiSignalLevel = wifiSignalLevel;
    }
    public String getWakeUpInfo() {
        return wakeUpInfo;
    }
    public void setWakeUpInfo(String wakeUpInfo) {
        this.wakeUpInfo = wakeUpInfo;
    }
    public String getBatteryVoltageInfo() {
        return batteryVoltageInfo;
    }
    public void setBatteryVoltageInfo(String batteryVoltageInfo) {
        this.batteryVoltageInfo = batteryVoltageInfo;
    }
    public String getFgCurrentmAInfo() {
        return fgCurrentmAInfo;
    }
    public void setFgCurrentmAInfo(String fgCurrentmAInfo) {
        this.fgCurrentmAInfo = fgCurrentmAInfo;
    }
    public String getSysConsumptionValueInfo() {
        return sysConsumptionValueInfo;
    }
    public void setSysConsumptionValueInfo(String sysConsumptionValueInfo) {
        this.sysConsumptionValueInfo = sysConsumptionValueInfo;
    }
    public String getCpuOnlineCoresInfo() {
        return cpuOnlineCoresInfo;
    }
    public void setCpuOnlineCoresInfo(String cpuOnlineCoresInfo) {
        this.cpuOnlineCoresInfo = cpuOnlineCoresInfo;
    }
    public String getCpuFrequencyInfo() {
        return cpuFrequencyInfo;
    }
    public void setCpuFrequencyInfo(String cpuFrequencyInfo) {
        this.cpuFrequencyInfo = cpuFrequencyInfo;
    }
    public String getWifiAPState() {
        return wifiAPState;
    }
    public void setWifiAPState(String wifiAPState) {
        this.wifiAPState = wifiAPState;
    }
}
