package net.ezforever.vgyro;

public class VGyroEvent {
    public long timestamp;
    public float[] values;

    VGyroEvent() {
        this.values = new float[3];
    }

    VGyroEvent(VGyroEvent src) {
        this.timestamp = src.timestamp;
        this.values = src.values.clone();
    }
}
