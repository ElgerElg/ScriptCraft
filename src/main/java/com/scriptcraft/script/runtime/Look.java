package com.scriptcraft.script.runtime;

import com.scriptcraft.config.ScriptCraftConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Куда смотрит игрок. Блок и сущность: ближе побеждает.
 * Сквозь блоки луч не проходит, наблюдатели не считаются.
 */
public final class Look {
    private Look() {
    }

    public record Sight(@Nullable Entity entity, @Nullable BlockHitResult block) {
    }

    public static Sight sight(ServerPlayer viewer) {
        double range = ScriptCraftConfig.targetedPlayerRange();
        Vec3 eye = viewer.getEyePosition();
        Vec3 look = viewer.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = viewer.level().clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        double blockDist = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceToSqr(block.getLocation());
        Vec3 entityEnd = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB box = viewer.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(viewer, eye, entityEnd, box,
                e -> e != viewer && !e.isSpectator() && e.isPickable(), range * range);
        Entity entity = null;
        if (hit != null && eye.distanceToSqr(hit.getLocation()) <= blockDist) {
            entity = hit.getEntity();
            block = null;
        } else if (block.getType() == HitResult.Type.MISS) {
            block = null;
        }
        return new Sight(entity, block);
    }
}
