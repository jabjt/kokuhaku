package com.kokuhaku.entity;


import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;

import net.minecraft.util.math.MathHelper;

/**
 * Mobs snap round up to 90 degrees a tick and are at full speed from a standing
 * start. People aren't. This turns a little at a time, gets up to speed, eases
 * off into a corner and takes a couple of steps to stop, and their head goes
 * round the corner a moment before the rest of them does.
 */
public class HumanMoveControl extends MoveControl {
    private static final float TURN_PER_TICK = 11f, SPEEDING_UP = 0.022f, EASE_OFF = 0.72f, HEAD_LEADS_FROM = 12f, HEAD_TURN = 13f;
    /** How much of their pace they keep through a tight turn, and through a slight one. */
    private static final float SHARP_TURN = 70f, SLIGHT_TURN = 30f, SHARP_PACE = 0.3f, SLIGHT_PACE = 0.72f;
    private static final float STOPPED = 0.01f;

    private float current = 0f;

    public HumanMoveControl(MobEntity entity) {
        super(entity);
    }

    @Override
    public void tick() {
        if (state != State.MOVE_TO) {
            if (state == State.JUMPING) {
                super.tick();
                return;
            }
            // They take a step or two to stop.
            current = current * EASE_OFF < STOPPED ? 0f : current * EASE_OFF;
            entity.setForwardSpeed(current);
            return;
        }
        state = State.WAIT;
        double dx = targetX - entity.getX(), dz = targetZ - entity.getZ(), dy = targetY - entity.getY();
        if (dx * dx + dy * dy + dz * dz < 2.5e-7) {
            entity.setForwardSpeed(0f);
            return;
        }
        float wanted = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        float off = Math.abs(MathHelper.wrapDegrees(wanted - entity.getYaw()));
        float passing = entity instanceof StudentEntity student ? student.passing() : 0f;
        entity.setYaw(wrapDegrees(entity.getYaw(), wanted + passing, TURN_PER_TICK));
        if (off > HEAD_LEADS_FROM) entity.getLookControl().lookAt(targetX, entity.getEyeY(), targetZ, HEAD_TURN, 10f);

        float pace = (float) (speed * entity.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
        if (off > SHARP_TURN) pace *= SHARP_PACE;
        else if (off > SLIGHT_TURN) pace *= SLIGHT_PACE;
        current += MathHelper.clamp(pace - current, -SPEEDING_UP * 2f, SPEEDING_UP);
        entity.setMovementSpeed(current);
    }
}
