package net.ezforever.vgyro;

import android.hardware.Sensor;

// Public data class
public enum VGyroImplInfo {
    GYROSCOPE("Gyroscope", Sensor.TYPE_GYROSCOPE),
    ROTATION_VECTOR("Rotation Vector", Sensor.TYPE_ROTATION_VECTOR),
    ORIENTATION("Orientation", Sensor.TYPE_ORIENTATION),
    GRAVITY_MAGNETIC("Gravity-Magnetic", Sensor.TYPE_GRAVITY, Sensor.TYPE_MAGNETIC_FIELD),
    ACCEL_MAGNETIC("Accel-Magnetic", Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)
    ;

    // ---

    public final String name;
    public final int[] requiredSensors;

    VGyroImplInfo(String name, int... requiredSensors) {
        this.name = name;
        this.requiredSensors = requiredSensors;
    }

    VGyroImpl get(VGyro host) {
        switch(this) {
            case GYROSCOPE:
                return new ImplGyroscope(host);
            case ROTATION_VECTOR:
                return new ImplRotationVector(host);
            case ORIENTATION:
                return new ImplOrientation(host);
            case GRAVITY_MAGNETIC:
                return new ImplGravityMagnetic(host);
            case ACCEL_MAGNETIC:
                return new ImplAccelMagnetic(host);
            default:
                return null; // Impossible
        }
    }
}
