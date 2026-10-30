package me.kvothe.taleos.turtle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.item.config.damageData.DamageBreakdown;

class TurtleCombatTest {
    @Test
    void selectsNearestDamageableEntityInsideTargetCell() {
        BlockPosition block = new BlockPosition(4, 64, -2);
        List<TurtleCombat.AttackTarget<String>> candidates = List.of(
                new TurtleCombat.AttackTarget<>("far", 4.9, 64.5, -1.5, false, false, true),
                new TurtleCombat.AttackTarget<>("near", 4.2, 64.5, -1.5, false, false, true),
                new TurtleCombat.AttackTarget<>("item", 4.0, 64.5, -1.5, true, false, true),
                new TurtleCombat.AttackTarget<>("display", 4.0, 64.5, -1.5, false, true, true),
                new TurtleCombat.AttackTarget<>("not-living", 4.0, 64.5, -1.5, false, false, false),
                new TurtleCombat.AttackTarget<>("outside", 5.0, 64.5, -1.5, false, false, true));

        assertEquals("near", TurtleCombat.selectTarget(candidates, block));
    }

    @Test
    void returnsNullWhenOnlyExcludedEntitiesArePresent() {
        BlockPosition block = new BlockPosition(0, 0, 0);
        assertNull(TurtleCombat.selectTarget(List.of(
                new TurtleCombat.AttackTarget<>("item", 0.5, 0.5, 0.5, true, false, true),
                new TurtleCombat.AttackTarget<>("display", 0.5, 0.5, 0.5, false, true, true)), block));
    }

    @Test
    void usesOnlyUnlabelledBasicWeaponDamageAndBareHandFallback() {
        DamageBreakdown breakdown = new DamageBreakdown(List.of(
                new DamageBreakdown.Entry(null, 4, 8),
                new DamageBreakdown.Entry("charged", 20, 30),
                new DamageBreakdown.Entry("backstab", 50, 50)));
        assertEquals(1.0f, TurtleCombat.basicDamage((com.hypixel.hytale.server.core.inventory.ItemStack) null));
        assertEquals(6.0f, TurtleCombat.basicDamage(breakdown));
        assertEquals(1.0f, TurtleCombat.basicDamage(new DamageBreakdown(List.of(
                new DamageBreakdown.Entry("charged", 20, 30)))));
    }
}
