package me.kvothe.talecraft.turtle;

import java.util.List;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.server.core.asset.type.item.config.damageData.DamageBreakdown;
import com.hypixel.hytale.server.core.inventory.ItemStack;

public final class TurtleCombat {
    public static final float BARE_HAND_DAMAGE = 1.0f;

    private TurtleCombat() {
    }

    public record AttackTarget<T>(T entity, double x, double y, double z,
            boolean itemEntity, boolean display, boolean damageable) {
    }

    public static <T> T selectTarget(List<AttackTarget<T>> candidates, BlockPosition targetBlock) {
        T nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        double centerX = targetBlock.x + 0.5;
        double centerY = targetBlock.y + 0.5;
        double centerZ = targetBlock.z + 0.5;
        for (AttackTarget<T> candidate : candidates) {
            if (candidate.itemEntity() || candidate.display() || !candidate.damageable()
                    || candidate.x() < targetBlock.x || candidate.x() >= targetBlock.x + 1
                    || candidate.y() < targetBlock.y || candidate.y() >= targetBlock.y + 1
                    || candidate.z() < targetBlock.z || candidate.z() >= targetBlock.z + 1) {
                continue;
            }
            double dx = candidate.x() - centerX;
            double dy = candidate.y() - centerY;
            double dz = candidate.z() - centerZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearest = candidate.entity();
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public static float basicDamage(ItemStack stack) {
        Item item = stack == null || stack.isEmpty() ? null : stack.getItem();
        ItemWeapon weapon = item == null ? null : item.getWeapon();
        DamageBreakdown breakdown = weapon == null ? null : weapon.getBasicDamageBreakdown();
        return basicDamage(breakdown);
    }

    public static float basicDamage(DamageBreakdown breakdown) {
        if (breakdown == null) {
            return BARE_HAND_DAMAGE;
        }
        for (DamageBreakdown.Entry entry : breakdown.entries()) {
            if (entry.labelKey() == null || entry.labelKey().isBlank()) {
                float damage = (entry.min() + entry.max()) / 2.0f;
                if (Float.isFinite(damage) && damage > 0) {
                    return damage;
                }
            }
        }
        return BARE_HAND_DAMAGE;
    }
}
