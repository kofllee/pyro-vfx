package net.kofllee.pyrovfx.client.vfx;

import net.minecraft.client.Minecraft;
import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ClientVfxManager {
    private static final List<ClientVfxInstance> INSTANCES = new ArrayList<>();

    private ClientVfxManager(){}

    public static void play(VfxDefinition definition, Vec3 position){
        INSTANCES.add(new ClientVfxInstance(definition, position));
    }

    public static void tick(){
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;

        if(level == null) {
            INSTANCES.clear();
            return;
        }

        Iterator<ClientVfxInstance> iterator = INSTANCES.iterator();
        while(iterator.hasNext()){
            ClientVfxInstance instance = iterator.next();
            instance.tick(minecraft.level);

            if(instance.isFinished()){
                iterator.remove();
            }
        }
    }

    public static void clear(){
        INSTANCES.clear();
    }

    public static int getActiveInstanceCount(){
        return INSTANCES.size();
    }
}
