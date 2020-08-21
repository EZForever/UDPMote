package net.ezforever.vgyro;

import android.hardware.Sensor;
import android.hardware.SensorEvent;

class ImplOrientation extends VGyroImpl {

    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float DEG2RAD = (float) ((2f * Math.PI) / 360f);

    // ---

    // Calculation things
    private float[] prevOrientation = new float[3];
    private long prevTimestamp;

    ImplOrientation(VGyro host) {
        super(host);
    }

    // --- implements VGyroImpl->SensorEventListener

    public void onSensorChanged(SensorEvent event) {
        float[] orientation = new float[3];
        float[] angleChange = new float[3];
        float timeDelta = Math.abs((event.timestamp - this.prevTimestamp) * NS2S);

        // It is seen that, on some devices, the angle can accumulate to ~1000deg.
        orientation[0] = event.values[0] % 360f - 180f; // [0, 359] -> [-180, 179]
        orientation[1] = event.values[1] % 360f;
        orientation[2] = event.values[2] % 360f;

        // TODO: Bug here: What if pitch = -179deg -> 179deg? (+358deg or -2deg?)
        angleChange[0] = (orientation[0] - this.prevOrientation[0]);
        angleChange[1] = (orientation[1] - this.prevOrientation[1]);
        angleChange[2] = (orientation[2] - this.prevOrientation[2]);

        VGyroEvent vGyroEvent = new VGyroEvent();
        vGyroEvent.timestamp = event.timestamp;
        vGyroEvent.values[0] = -(angleChange[1] * DEG2RAD) / timeDelta;
        vGyroEvent.values[1] = (angleChange[2] * DEG2RAD) / timeDelta;
        vGyroEvent.values[2] = (angleChange[0] * DEG2RAD) / timeDelta;

        this.prevOrientation = orientation;
        this.prevTimestamp = event.timestamp;

        this.host.onVGyroChanged(vGyroEvent);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing to do
    }

}
