package net.ezforever.udpmote;

public class AtomicButtonMask {
    private int mask = 0;

    AtomicButtonMask() {
    }

    public synchronized int get() {
        return this.mask;
    }

    public synchronized void xor(int v) {
        this.mask ^= v;
    }

    public synchronized void set(int v) {
        this.mask |= v;
    }

    public synchronized void clear(int v) {
        this.mask &= ~v;
    }
}
