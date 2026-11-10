
package net.kofllee.pyroui;

/** Private KeyMapping state bridge, active only while a world viewport has focus. */
public interface GameKeyAccess {
    void pyroUi$setGameKey(boolean pressed,boolean click);
    boolean pyroUi$rawGameKeyDown();
}
