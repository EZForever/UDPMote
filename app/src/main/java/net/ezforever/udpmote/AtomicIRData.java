package net.ezforever.udpmote;

public class AtomicIRData {
    private final float[] v = new float[2];

    public AtomicIRData() {
        this.v[0] = 0.5f;
        this.v[1] = 0.5f;
    }

    public synchronized void get(float[] r) {
        r[0] = this.v[0];
        r[1] = this.v[1];
    }

    public synchronized void add(float x, float y) {
        float[] fArr = this.v;
        fArr[0] = fArr[0] + x;
        this.v[0] = Math.max(Math.min(this.v[0], 1.1f), -1.1f);
        float[] fArr2 = this.v;
        fArr2[1] = fArr2[1] + y;
        this.v[1] = Math.max(Math.min(this.v[1], 1.1f), -1.1f);
    }
}
