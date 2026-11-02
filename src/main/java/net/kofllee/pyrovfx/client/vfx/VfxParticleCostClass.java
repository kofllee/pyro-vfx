package net.kofllee.pyrovfx.client.vfx;

/** Coarse cost groups for independent global active-particle hard ceilings. */
public enum VfxParticleCostClass {
    GPU_ANALYTIC, CPU_BASIC, CPU_COLLISION, CPU_MODEL;

    /** Independent resource dimensions; one particle may consume several. */
    public int bit() { return 1 << ordinal(); }
}