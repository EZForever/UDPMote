package net.ezforever.vgyro;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

class ImplRotationVector extends VGyroImpl {

    private static final float NS2S = 1.0f / 1000000000.0f;

    // ---

    // Calculation things
    private float[] prevRotationMatrix = new float[9];
    private long prevTimestamp;

    ImplRotationVector(VGyro host) {
        super(host);
    }

    // --- implements VGyroImpl->SensorEventListener

    public void onSensorChanged(SensorEvent event) {
        float[] rotationMatrix = new float[9];
        float[] angleChange = new float[3];
        float timeDelta = Math.abs((event.timestamp - this.prevTimestamp) * NS2S);

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        SensorManager.getAngleChange(angleChange, rotationMatrix, this.prevRotationMatrix);

        VGyroEvent vGyroEvent = new VGyroEvent();
        vGyroEvent.timestamp = event.timestamp;
        vGyroEvent.values[0] = -(angleChange[1]) / timeDelta;
        vGyroEvent.values[1] = (angleChange[2]) / timeDelta;
        vGyroEvent.values[2] = (angleChange[0]) / timeDelta;

        this.prevRotationMatrix = rotationMatrix;
        this.prevTimestamp = event.timestamp;

        this.host.onVGyroChanged(vGyroEvent);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing to do
    }

}
