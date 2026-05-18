package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxEventDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxEventType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class VfxEventRunner {
    public static final int MAX_EVENT_DEPTH = 16;

    private VfxEventRunner(){}

    public static void run(
            String eventId,
            Map<String, VfxEventDefinition> events,
            Map<String, ClientVfxEmitter> emittersById,
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 callerPosition,
            VfxExpressionContext effectContext,
            RandomSource random
    ){
        run(
                eventId,
                events,
                emittersById,
                level,
                effectPosition,
                callerPosition,
                effectContext,
                random,
                0
        );

    }

    private static void run(String eventId,
                            Map<String, VfxEventDefinition> events,
                            Map<String, ClientVfxEmitter> emittersById,
                            ClientLevel level,
                            Vec3 effectPosition,
                            Vec3 callerPosition,
                            VfxExpressionContext effectContext,
                            RandomSource random,
                            int depth){
        if(depth >= MAX_EVENT_DEPTH){
            return;
        }

        VfxEventDefinition event = events.get(eventId);

        if(event == null){
            return;
        }

        if(event.type() == VfxEventType.EMIT){
            ClientVfxEmitter emitter = emittersById.get(event.emitterId());

            if(emitter == null){
                return;
            }

            Vec3 emitterOffset = emitter.definition().offset().evaluate(effectContext).toVec3();
            Vec3 emitterPosition = callerPosition.add(emitterOffset);

            VfxLifetimeState lifetimeState = emitter.lifetimeState();

            VfxExpressionContext emitterContext = ClientVfxExpressionContexts.emitterTick(
                    effectContext,
                    emitterPosition,
                    lifetimeState,
                    emitter.emittedParticles(),
                    emitter.emitterRandom()
            );

            emitter.emitManual(level, effectPosition, emitterPosition, emitterContext, random);

            return;
        }

        if (event.type() == VfxEventType.SEQUENCE) {
            for (String nestedEventId : event.eventIds()) {
                run(
                        nestedEventId,
                        events,
                        emittersById,
                        level,
                        effectPosition,
                        callerPosition,
                        effectContext,
                        random,
                        depth + 1
                );
            }

            return;
        }


        if (event.type() == VfxEventType.RANDOMIZE) {
            if (event.eventIds().isEmpty()) {
                return;
            }

            String nestedEventId = event.eventIds().get(
                    random.nextInt(event.eventIds().size())
            );

            run(
                    nestedEventId,
                    events,
                    emittersById,
                    level,
                    effectPosition,
                    callerPosition,
                    effectContext,
                    random,
                    depth + 1
            );
        }
    }
}
