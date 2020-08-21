package net.ezforever.vgyro;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

class ImplGravityMagnetic extends VGyroImpl {

    private static final float NS2S = 1.0f / 1000000000.0f;

    // ---

    // Base readings
    private float[] gravityValues = new float[3];
    private float[] magneticValues = new float[3];

    // Calculation things
    private float[] prevRotationMatrix = new float[9];
    private long prevTimestamp;

    private void getValues(float[] angularRates, long timestamp) {
        float[] rotationMatrix = new float[9];
        float[] angleChange = new float[3];
        float timeDelta = Math.abs((timestamp - this.prevTimestamp) * NS2S);

        SensorManager.getRotationMatrix(rotationMatrix, null, this.gravityValues, this.magneticValues);
        SensorManager.getAngleChange(angleChange, rotationMatrix, this.prevRotationMatrix);
        angularRates[0] = -(angleChange[1]) / timeDelta;
        angularRates[1] = (angleChange[2]) / timeDelta;
        angularRates[2] = (angleChange[0]) / timeDelta;

        this.prevRotationMatrix = rotationMatrix;
        this.prevTimestamp = timestamp;
    }

    private void reportVGyroChanged(long timestamp) {
        // Calculate unfiltered values
        float[] values = new float[3];
        getValues(values, timestamp);

        // Construct an event
        VGyroEvent event = new VGyroEvent();
        event.timestamp = timestamp;
        System.arraycopy(values, 0, event.values, 0, 3);

        // Fire the event
        host.onVGyroChanged(event);
    }

    ImplGravityMagnetic(VGyro host) {
        super(host);
    }

    // --- implements VGyroImpl->SensorEventListener

    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
            case Sensor.TYPE_GRAVITY: {
                this.gravityValues = event.values;
                reportVGyroChanged(event.timestamp);
                break;
            }
            case Sensor.TYPE_MAGNETIC_FIELD: {
                this.magneticValues = event.values;
                break;
            }
            default:
                // Do nothing
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing to do
    }

}
