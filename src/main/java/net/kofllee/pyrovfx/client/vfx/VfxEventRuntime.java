package net.kofllee.pyrovfx.client.vfx;

public interface VfxEventRuntime {
    void setParameter(String id, double value);

    boolean enterEvent();

    boolean hasEventBudget();

    int reserveSpawns(int requested);
}
