package net.kofllee.pyrovfx.client.vfx;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class ClientVfxManager {
    private static final List<ClientVfxInstance> INSTANCES = new ArrayList<>();
    private static final VfxWorldIdentity WORLD_IDENTITY = new VfxWorldIdentity();
    private static final VfxGlobalBudget GLOBAL_BUDGET = new VfxGlobalBudget();

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientVfxManager(){}

    public static void play(VfxDefinition definition, Vec3 position) {
        play(definition, position, VfxPlayOptions.empty());
    }

    public static void play(VfxDefinition definition, Vec3 position, VfxPlayOptions options) {
        tryPlay(definition, position, options);
    }

    public static boolean tryPlay(VfxDefinition definition, Vec3 position, VfxPlayOptions options) {
        trackWorld();
        if (!GLOBAL_BUDGET.admitInstance(INSTANCES.size())) return false;
        INSTANCES.add(new ClientVfxInstance(definition, position, options));
        return true;
    }

    public static void playSeeded(VfxDefinition definition, Vec3 position, VfxPlayOptions options, long seed) {
        trackWorld();
        if (!GLOBAL_BUDGET.admitInstance(INSTANCES.size())) return;
        INSTANCES.add(new ClientVfxInstance(definition, position, options, seed));
    }

    public static void tick(){
        ClientLevel level = trackWorld();

        if(level == null) {
            for (ClientVfxInstance instance : INSTANCES) instance.close();
            INSTANCES.clear();
            GLOBAL_BUDGET.reset();
            VfxCollisionQueryCache.clear();
            return;
        }

        VfxCollisionQueryCache.beginTick(level);
        boolean heavy = !net.neoforged.fml.loading.FMLEnvironment.production
                && Boolean.getBoolean("pyrovfx.bench.allowHeavy");
        GLOBAL_BUDGET.beginTick(getActiveParticleCount(), heavy);
        Iterator<ClientVfxInstance> iterator = INSTANCES.iterator();
        while(iterator.hasNext()){
            ClientVfxInstance instance = iterator.next();

            try {
                instance.tick(level, GLOBAL_BUDGET);
            } catch (Exception e) {
                LOGGER.error("Failed to tick Pyro VFX instance: {}", instance.definition().id(), e);
                instance.close();
                iterator.remove();
                continue;
            }

            if(instance.isFinished()){
                instance.close();
                iterator.remove();
            }
        }
    }

    private static ClientLevel trackWorld() {
        ClientLevel level = Minecraft.getInstance().level;
        if (WORLD_IDENTITY.update(level)) {
            for (ClientVfxInstance instance : INSTANCES) instance.close();
            INSTANCES.clear();
            GLOBAL_BUDGET.reset();
        }
        return level;
    }

    public static void clear(){
        for (ClientVfxInstance instance : INSTANCES) instance.close();
        INSTANCES.clear();
        GLOBAL_BUDGET.reset();
        VfxCollisionQueryCache.clear();
    }

    public static VfxGlobalBudget globalBudget() { return GLOBAL_BUDGET; }

    public static int getActiveInstanceCount(){
        return INSTANCES.size();
    }

    public static List<ClientVfxInstance> instances() {
        return Collections.unmodifiableList(INSTANCES);
    }

    public static int getActiveParticleCount(){
        int count = 0;

        for(ClientVfxInstance instance : INSTANCES){
            count += instance.particleCount();
        }

        return count;
    }
}
