package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.VfxDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ClientVfxInstance {
    private final VfxDefinition definition;
    private final Vec3 position;
    private final List<ClientVfxEmitter> emitters = new ArrayList<>();
    private final RandomSource random = RandomSource.create();

    private int age;

    public ClientVfxInstance(VfxDefinition definition, Vec3 postion){
        this.definition = definition;
        this.position = postion;

        for(var emitterDef : definition.emitters()){
            emitters.add(new ClientVfxEmitter(emitterDef));
        }
    }

    public void tick(ClientLevel level){
        for(var emitter : emitters){
            emitter.tick(level, position, random);
        }

        age++;
    }

    public boolean isFinished(){
        return age >= definition.lifeTimeTicks();
    }
}
