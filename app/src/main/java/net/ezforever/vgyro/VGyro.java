package net.ezforever.vgyro;

import android.content.Context;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VGyro {

    private final Object listenersLock = new Object();

    private VGyroImpl impl;
    private VGyroImplInfo implInfo;
    private int samplingPeriodUs;
    private SensorManager sensorManager;
    private Set<VGyroEventListener> listeners;

    private void registerImpl() {
        for(int sensor : this.implInfo.requiredSensors)
            this.sensorManager.registerListener(this.impl, this.sensorManager.getDefaultSensor(sensor), this.samplingPeriodUs);
    }

    private void unregisterImpl() {
        this.sensorManager.unregisterListener(this.impl);
    }

    // For impls to report their events
    void onVGyroChanged(VGyroEvent event) {
        synchronized (this.listenersLock) {
            for (VGyroEventListener listener : this.listeners)
                listener.onVGyroChanged(new VGyroEvent(event));
        }
    }

    public VGyro(Context context, int samplingPeriodUs) {
        this.impl = null;
        this.implInfo = null;
        this.samplingPeriodUs = samplingPeriodUs;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.listeners = new HashSet<>();
    }

    public List<VGyroImplInfo> getAvailableImpls() {
        List<VGyroImplInfo> ret = new ArrayList<>();
        for(VGyroImplInfo info : VGyroImplInfo.values()) {
            boolean available = true;
            for(int sensor : info.requiredSensors) {
                if(this.sensorManager.getDefaultSensor(sensor) == null) {
                    available = false;
                    break;
                }
            }
            if(available)
                ret.add(info);
        }
        return ret;
    }

    public VGyroImplInfo getImpl() {
        return this.implInfo;
    }

    public void setImpl(VGyroImplInfo info) {
        synchronized (this.listenersLock) {
            if(!this.listeners.isEmpty())
                this.unregisterImpl();

            this.implInfo = info;
            this.impl = info.get(this);

            if(!this.listeners.isEmpty())
                this.registerImpl();
        }
    }

    public void registerListener(VGyroEventListener listener) {
        synchronized (this.listenersLock) {
            if(this.listeners.isEmpty() && this.listeners.add(listener))
                this.registerImpl();
        }
    }

    public void unregisterListener(VGyroEventListener listener) {
        synchronized (this.listenersLock) {
            if(this.listeners.remove(listener) && this.listeners.isEmpty())
                this.unregisterImpl();
        }
    }

}
