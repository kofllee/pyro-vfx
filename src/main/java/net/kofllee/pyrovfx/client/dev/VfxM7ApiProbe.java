package net.kofllee.pyrovfx.client.dev;

import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;
import net.kofllee.pyrovfx.api.VfxOptions;
import net.minecraft.client.Minecraft;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** Real-client API contract check, run by the existing Minecraft benchmark harness. */
final class VfxM7ApiProbe {
    private final ResourceLocation effect;
    private final Vec3 origin;
    private VfxHandle handle;
    private VfxHandle reentrantStop;
    private boolean verified;

    private VfxM7ApiProbe(ResourceLocation effect, Vec3 origin) {
        this.effect = effect;
        this.origin = origin;
    }

    static VfxM7ApiProbe start(ResourceLocation effect, Vec3 origin) {
        var test = new VfxM7ApiProbe(effect, origin);
        var unknown = Vfx.play(ResourceLocation.fromNamespaceAndPath("pyro_vfx", "missing_api_fixture"), origin);
        if (unknown.isActive() || unknown.stop()) throw new IllegalStateException("Unknown effect accepted");
        if (Vfx.play(effect, partial -> null).isActive())
            throw new IllegalStateException("Unavailable provider accepted");
        if (Vfx.play(effect, origin, Map.of("unknown", 3.0)).isActive())
            throw new IllegalStateException("Unknown parameter accepted");
        test.handle = Vfx.play(effect, Minecraft.getInstance().level, origin, Map.of("stage", 5.0));
        if (!test.handle.isActive() || ClientVfxManager.getActiveInstanceCount() != 1
                || ClientVfxManager.instances().getFirst().parameters().get("stage") != 5.0)
            throw new IllegalStateException("Valid managed start or parameter override failed");
        return test;
    }

    void afterTick(int index) {
        if (index == 0) {
            if (!handle.setParameter("stage", 7.0) || handle.setParameter("absent", 1.0)
                    || handle.setParameter("stage", Double.NaN))
                throw new IllegalStateException("Parameter validation failed");
            if (!handle.trigger("increment") || handle.trigger("missing_event"))
                throw new IllegalStateException("External event validation failed");
            if (handle.attach(partial -> null))
                throw new IllegalStateException("Unavailable replacement provider accepted");
            if (!handle.attach(partial -> VfxTransform.at(origin.add(2, 0, 0))) || !handle.detach())
                throw new IllegalStateException("Custom reattachment/detachment failed");
        }
        if (index == 1) {
            if (ClientVfxManager.instances().getFirst().parameters().get("stage") != 8.0)
                throw new IllegalStateException("Queued event did not observe the updated parameter");
            if (!handle.stop() || handle.stop() || handle.isActive()
                    || handle.trigger("increment") || handle.detach()
                    || ClientVfxManager.getActiveInstanceCount() != 0
                    || ClientVfxManager.getActiveParticleCount() != 0)
                throw new IllegalStateException("Stop was not idempotent or did not release particles");
        }
        if (index == 2) {
            var second = Vfx.play(effect, partial -> VfxTransform.at(origin));
            if (!second.isActive() || handle.isActive())
                throw new IllegalStateException("Old handle was revived by a new instance");
            ClientVfxManager.clear();
            if (second.isActive() || second.stop())
                throw new IllegalStateException("Clear did not invalidate live handles");
            var attached = Vfx.play(effect, Minecraft.getInstance().player,
                    VfxOptions.DEFAULT.withParameters(Map.of("stage", 2.0)));
            if (!attached.isActive() || ClientVfxManager.getActiveInstanceCount() != 1
                    || !attached.stop() || attached.isActive())
                throw new IllegalStateException("Automatic client Entity routing failed");
        }
        if (index == 3) {
            var reference = new java.util.concurrent.atomic.AtomicReference<VfxHandle>();
            var sampled = new java.util.concurrent.atomic.AtomicInteger();
            reentrantStop = Vfx.play(effect, partial -> {
                if (sampled.incrementAndGet() == 2 && reference.get() != null)
                    reference.get().stop();
                return VfxTransform.at(origin);
            });
            reference.set(reentrantStop);
            if (!reentrantStop.isActive())
                throw new IllegalStateException("Reentrant-stop fixture did not start");
        }
        if (index == 4) {
            if (reentrantStop.isActive() || ClientVfxManager.getActiveInstanceCount() != 0)
                throw new IllegalStateException("Provider stop() modified manager iteration unsafely");
            verified = true;
        }
    }

    boolean verified() { return verified; }
}