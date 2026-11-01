package net.kofllee.pyrovfx.client.vfx;

/** Detect identity changes even when the client switches directly between non-null worlds. */
public final class VfxWorldIdentity {
    private Object current;

    public boolean update(Object world) {
        if (current == world) {
            return false;
        }
        current = world;
        return true;
    }
}
