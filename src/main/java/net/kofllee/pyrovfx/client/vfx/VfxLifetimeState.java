package net.kofllee.pyrovfx.client.vfx;

public record VfxLifetimeState (int age, int localAge, int activeAge, double normalizedAge, boolean active, boolean finished) {
}
