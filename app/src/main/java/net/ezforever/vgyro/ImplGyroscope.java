package net.ezforever.vgyro;

import android.hardware.Sensor;
import android.hardware.SensorEvent;

class ImplGyroscope extends VGyroImpl {

    ImplGyroscope(VGyro host) {
        super(host);
    }

    // --- implements VGyroImpl->SensorEventListener

    public void onSensorChanged(SensorEvent event) {
        VGyroEvent vGyroEvent = new VGyroEvent();
        vGyroEvent.timestamp = event.timestamp;
        System.arraycopy(event.values, 0, vGyroEvent.values, 0, 3);
        this.host.onVGyroChanged(vGyroEvent);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing to do
    }

}