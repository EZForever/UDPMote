package net.ezforever.vgyro;

import android.hardware.SensorEventListener;

// The interface for VGyro to control implementations
abstract class VGyroImpl implements SensorEventListener {
    protected VGyro host;

    protected VGyroImpl(VGyro host) {
        this.host = host;
    }
}
