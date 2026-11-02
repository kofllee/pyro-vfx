package net.kofllee.pyrovfx.client.vfx;

public interface VfxEventRuntime {
    void setParameter(String id, double value);

    boolean enterEvent();

    boolean hasEventBudget();

    int reserveSpawns(int requested);

    default int reserveSpawns(int requested, VfxParticleCostClass group) {
        return reserveSpawns(requested);
    }

    default int reserveSpawns(int requested, int costMask) {
        return reserveSpawns(requested);
    }
}
