package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxEventDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Map;

/**
 * Per-emitter FIFO of collision-triggered event commands.
 * Collect then drain at each particle boundary: set_param remains immediately
 * visible to the next particle, and event-spawn RNG order remains unchanged.
 * Rendering and complex world access stay on the client game thread.
 */
public final class VfxEventCommandQueue {
    private String[] ids = new String[8];
    private Vec3[] positions = new Vec3[8];
    private int count;

    public void enqueue(String eventId, Vec3 position) {
        if (count == VfxRuntimeBudget.MAX_EVENTS_PER_TICK) {
            return;
        }
        if (count == ids.length) {
            int next = Math.min(VfxRuntimeBudget.MAX_EVENTS_PER_TICK, ids.length * 2);
            ids = Arrays.copyOf(ids, next);
            positions = Arrays.copyOf(positions, next);
        }
        ids[count] = eventId;
        positions[count] = position;
        count++;
    }

    public void drain(Map<String, VfxEventDefinition> events,
                      Map<String, ClientVfxEmitter> emittersById, ClientLevel level,
                      Vec3 effectPosition, VfxExpressionContext context,
                      VfxEventRuntime runtime, RandomSource random) {
        try {
            for (int i = 0; i < count; i++) {
                if (!runtime.hasEventBudget()) {
                    break;
                }
                VfxEventRunner.run(ids[i], events, emittersById, level,
                        effectPosition, positions[i], context, runtime, random);
            }
        } finally {
            Arrays.fill(ids, 0, count, null);
            Arrays.fill(positions, 0, count, null);
            count = 0;
        }
    }

    public int size() {
        return count;
    }
}
