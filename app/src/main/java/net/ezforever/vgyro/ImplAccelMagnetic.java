package net.ezforever.vgyro;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;

class ImplAccelMagnetic extends VGyroImpl {

    private static final float[] GRAVITY = new float[] {0f, 0f, SensorManager.GRAVITY_EARTH};
    private static final float NS2S = 1.0f / 1000000000.0f;
    private static final float LOWPASS_ALPHA = 0.5f;

    private static float lowPass(float alpha, float value, float prev) {
        return prev + alpha * (value - prev);
    }

    // ---

    // Base readings
    private float[] magneticValues = new float[3];
    private float[] accelerometerValues = new float[3];

    // Calculation things
    private float[] prevRotationMatrix = new float[9];
    private long prevTimestamp;

    // Filter things
    private float[] filterValues;

    private void getValues(float[] angularRates, long timestamp) {
        float[] rotationMatrix = new float[9];
        float[] gravityRot = new float[3];
        float[] angleChange = new float[3];
        float timeDelta = Math.abs((timestamp - this.prevTimestamp) * NS2S);

        //Log.d("ImplAccelMagnetic", String.format("{ %f, %f, %f }", this.magneticValues[0], this.magneticValues[1], this.magneticValues[2]));
        SensorManager.getRotationMatrix(rotationMatrix, null, this.accelerometerValues, this.magneticValues);
        gravityRot[0] = GRAVITY[0] * rotationMatrix[0] + GRAVITY[1] * rotationMatrix[3] + GRAVITY[2] * rotationMatrix[6];
        gravityRot[1] = GRAVITY[0] * rotationMatrix[1] + GRAVITY[1] * rotationMatrix[4] + GRAVITY[2] * rotationMatrix[7];
        gravityRot[2] = GRAVITY[0] * rotationMatrix[2] + GRAVITY[1] * rotationMatrix[5] + GRAVITY[2] * rotationMatrix[8];
        SensorManager.getRotationMatrix(rotationMatrix, null, gravityRot, this.magneticValues);

        SensorManager.getAngleChange(angleChange, rotationMatrix, this.prevRotationMatrix);
        angularRates[0] = -(angleChange[1]) / timeDelta;
        angularRates[1] = (angleChange[2]) / timeDelta;
        angularRates[2] = (angleChange[0]) / timeDelta;

        this.prevRotationMatrix = rotationMatrix;
        this.prevTimestamp = timestamp;
    }

    private void lowPassValues(float[] values) {
        // If this is the first set of values, just copy it as the "filtered" value
        if(this.filterValues == null) {
            this.filterValues = values.clone();
            return;
        }

        for(int i = 0; i < 3; i++)
            this.filterValues[i] = values[i] = lowPass(LOWPASS_ALPHA, values[i], this.filterValues[i]);
    }

    private void reportVGyroChanged(long timestamp) {
        // Calculate unfiltered values
        float[] values = new float[3];
        getValues(values, timestamp);

        // Run a low-pass filter
        lowPassValues(values);

        // Construct an event
        VGyroEvent event = new VGyroEvent();
        event.timestamp = timestamp;
        System.arraycopy(values, 0, event.values, 0, 3);

        // Fire the event
        host.onVGyroChanged(event);
    }

    ImplAccelMagnetic(VGyro host) {
        super(host);
    }

    // --- implements VGyroImpl->SensorEventListener

    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER: {
                this.accelerometerValues = event.values;
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
