package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxTriggerDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxTriggerType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/** Per-source distance accumulator; independent of FPS, tick rate and particle motion. */
public final class VfxTravelTriggers {
    public record Hit(VfxTriggerDefinition trigger, Vec3 at) {}
    /** Displacements beyond this are treated as teleportation, not a traversed path. */
    public static final double MAX_SEGMENT_DISTANCE = 64.0;
    private static final int MAX_EVENTS_PER_TICK = 128;
    private final List<VfxTriggerDefinition> definitions;
    private final double[] progress;
    private final boolean[] onceFired;

    public VfxTravelTriggers(List<VfxTriggerDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
        this.progress = new double[definitions.size()];
        this.onceFired = new boolean[definitions.size()];
    }

    /** A new source starts a new path without re-arming one-shot events. */
    public void reset() {
        java.util.Arrays.fill(progress, 0.0);
    }

    /** Events from all distances are delivered in the spatial order crossed. */
    public void advance(Vec3 from, Vec3 to, BiConsumer<VfxTriggerDefinition, Vec3> fire) {
        double distance = from.distanceTo(to);
        if (!Double.isFinite(distance) || distance > MAX_SEGMENT_DISTANCE) {
            // No particles or triggers along a teleport. Restart looping spacing at the destination.
            for (int i = 0; i < definitions.size(); i++) {
                if (definitions.get(i).type() == VfxTriggerType.TRAVEL_DISTANCE_LOOPING)
                    progress[i] = 0;
            }
            return;
        }
        if (distance <= 1.0e-9) return;

        List<Crossing> crossings = new ArrayList<>();
        for (int i = 0; i < definitions.size(); i++) {
            VfxTriggerDefinition definition = definitions.get(i);
            VfxTriggerType type = definition.type();
            if (type != VfxTriggerType.TRAVEL_DISTANCE
                    && type != VfxTriggerType.TRAVEL_DISTANCE_LOOPING) continue;
            double interval = definition.distance();
            if (!Double.isFinite(interval) || interval <= 0) continue;
            if (type == VfxTriggerType.TRAVEL_DISTANCE && onceFired[i]) continue;
            double prior = progress[i];
            double total = prior + distance;
            if (type == VfxTriggerType.TRAVEL_DISTANCE) {
                if (total >= interval) {
                    onceFired[i] = true;
                    crossings.add(new Crossing((interval - prior) / distance, i));
                } else {
                    progress[i] = total;
                }
                continue;
            }
            long crossed = (long) Math.floor((total + 1.0e-9) / interval);
            progress[i] = Math.max(0, total - crossed * interval);
            for (long n = 0; n < crossed && n < MAX_EVENTS_PER_TICK; n++) {
                crossings.add(new Crossing((interval - prior + n * interval) / distance, i));
            }
        }
        crossings.sort(Comparator.comparingDouble(Crossing::fraction).thenComparingInt(Crossing::index));
        for (int i = 0; i < Math.min(MAX_EVENTS_PER_TICK, crossings.size()); i++) {
            Crossing crossing = crossings.get(i);
            fire.accept(definitions.get(crossing.index),
                    from.lerp(to, Math.max(0, Math.min(1, crossing.fraction))));
        }
    }

    private record Crossing(double fraction, int index) {}
}