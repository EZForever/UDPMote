package net.ezforever.udpmote;

public class AtomicGyroscopeData {
    private final float[] v = new float[3];

    AtomicGyroscopeData() {
    }

    public synchronized void get(float[] r) {
        r[0] = this.v[0];
        r[1] = this.v[1];
        r[2] = this.v[2];
    }

    public synchronized void set(float[] n) {
        this.v[0] = n[0];
        this.v[1] = n[1];
        this.v[2] = n[2];
    }
}
