package net.kofllee.pyrovfx.vfx.resource;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class VfxReloadListener extends SimplePreparableReloadListener<ResourceManager> {
    private final Runnable afterReload;

    public VfxReloadListener(Runnable afterReload) {
        this.afterReload = afterReload;
    }

    @Override
    protected ResourceManager prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        return resourceManager;
    }

    @Override
    protected void apply(ResourceManager resourceManager, ResourceManager resourceManager2, ProfilerFiller profilerFiller) {
        try {
            VfxJsonLoader.reload(resourceManager);
        } finally {
            afterReload.run();
        }
    }
}
