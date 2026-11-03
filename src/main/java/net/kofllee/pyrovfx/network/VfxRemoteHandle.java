package net.kofllee.pyrovfx.network;

import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.ref.WeakReference;
import java.util.UUID;

/**
 * A server-side instance token. All controls require the owning logical server thread.
 * This handle holds no particles, source entity or strong world reference.
 */
public final class VfxRemoteHandle {
    private final WeakReference<ServerLevel> level;
    private final UUID id;

    VfxRemoteHandle(ServerLevel level, UUID id) {
        this.level = new WeakReference<>(level);
        this.id = id;
    }

    static VfxRemoteHandle inactive() { return new VfxRemoteHandle(null, null); }

    public boolean isActive() { return ServerVfxNetwork.active(level.get(), id); }
    public boolean stop() { return ServerVfxNetwork.stop(level.get(), id); }
    public boolean setParameter(String name, double value) {
        return ServerVfxNetwork.parameter(level.get(), id, name, value);
    }
    public boolean trigger(String event) { return ServerVfxNetwork.trigger(level.get(), id, event); }
    public boolean attach(Entity entity) { return ServerVfxNetwork.attach(level.get(), id, entity); }
    public boolean attach(BlockPos pos) { return ServerVfxNetwork.attach(level.get(), id, pos); }
    public boolean attach(BlockEntity blockEntity) { return ServerVfxNetwork.attach(level.get(), id, blockEntity); }
    public boolean attach(VfxTransform pose) { return ServerVfxNetwork.attach(level.get(), id, pose); }
    public boolean attachStreamed(VfxTransform pose) {
        return ServerVfxNetwork.attachStreamed(level.get(), id, pose);
    }
    /** Continue the same effect at its last server-sampled pose without an attachment. */
    public boolean detach() { return ServerVfxNetwork.detach(level.get(), id); }

    /** Only valid for playStreamed(...); packets are throttled and filtered by movement threshold. */
    public boolean updatePose(VfxTransform pose) { return ServerVfxNetwork.stream(level.get(), id, pose); }
}
