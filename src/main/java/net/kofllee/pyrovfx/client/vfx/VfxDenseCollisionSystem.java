package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxMotionCollisionDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxCollisionType;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Collision-only component for Dense CPU simulation. Uses Minecraft's cached
 * block shapes and the same X/Y/Z bounce ordering as the Legacy particle.
 * A result is allocated only if the prospective collision box overlaps a block.
 * Emitters with ordered particle events remain on Legacy.
 */
final class VfxDenseCollisionSystem {
    private VfxDenseCollisionSystem() {}

    record Result(double x, double y, double z, double vx, double vy, double vz, boolean dead) {}

    static Result resolve(ClientLevel level, VfxMotionCollisionDefinition collision,
                          VfxExpressionContext context, BlockPos.MutableBlockPos block,
                          double x, double y, double z, double vx, double vy, double vz) {
        VfxVec3 size = collision.collisionSize().evaluate(context);
        double sx = Math.max(0.001, Math.abs(size.x()));
        double sy = Math.max(0.001, Math.abs(size.y()));
        double sz = Math.max(0.001, Math.abs(size.z()));
        if (collision.collisionType() == VfxCollisionType.SPHERE) {
            sx = sy = sz = Math.max(sx, Math.max(sy, sz));
        }
        if (!intersects(level, block, box(x + vx, y + vy, z + vz, sx, sy, sz)))
            return null;

        double bounce = Math.max(0.0, collision.bounciness().evaluateDouble(context));
        double drag = Math.max(0.0, collision.collisionDrag().evaluateDouble(context));
        boolean hit = false;

        if (intersects(level, block, box(x + vx, y, z, sx, sy, sz))) {
            vx = -vx * bounce;
            hit = true;
        } else {
            x += vx;
        }
        if (intersects(level, block, box(x, y + vy, z, sx, sy, sz))) {
            vy = -vy * bounce;
            hit = true;
        } else {
            y += vy;
        }
        if (intersects(level, block, box(x, y, z + vz, sx, sy, sz))) {
            vz = -vz * bounce;
            hit = true;
        } else {
            z += vz;
        }
        if (hit) {
            double multiplier = Math.exp(-drag * VfxTime.SECONDS_PER_TICK);
            vx *= multiplier;
            vy *= multiplier;
            vz *= multiplier;
        }
        return new Result(x, y, z, vx, vy, vz, hit && collision.expireOnContact());
    }

    private static AABB box(double x, double y, double z, double sx, double sy, double sz) {
        return new AABB(x - sx, y - sy, z - sz, x + sx, y + sy, z + sz);
    }

    private static boolean intersects(ClientLevel level, BlockPos.MutableBlockPos block, AABB box) {
        int minX = (int) Math.floor(box.minX), maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY), maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.floor(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    block.set(x, y, z);
                    if (VfxCollisionQueryCache.intersectsWorld(level, block, box)) return true;
                }
            }
        }
        return false;
    }
}
