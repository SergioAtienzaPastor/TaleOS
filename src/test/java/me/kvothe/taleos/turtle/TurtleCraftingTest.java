package me.kvothe.taleos.turtle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class TurtleCraftingTest {
    private record Material(String id, String resource, int count) {
    }

    private record Stack(String id, int count, int max, String resource) {
    }

    private static final TurtleCrafting.StackOps<Material, Stack> OPS = new TurtleCrafting.StackOps<>() {
        @Override
        public int quantity(Stack stack) {
            return stack == null ? 0 : stack.count();
        }

        @Override
        public int required(Material material) {
            return material.count();
        }

        @Override
        public int maxStack(Stack stack) {
            return stack.max();
        }

        @Override
        public boolean matches(Material material, Stack stack) {
            return material.id() != null ? material.id().equals(stack.id())
                    : Objects.equals(material.resource(), stack.resource());
        }

        @Override
        public boolean sameType(Stack first, Stack second) {
            return first.id().equals(second.id());
        }

        @Override
        public Stack withQuantity(Stack stack, int quantity) {
            return quantity == 0 ? null : new Stack(stack.id(), quantity, stack.max(), stack.resource());
        }

        @Override
        public Material withRequired(Material material, int quantity) {
            return new Material(material.id(), material.resource(), quantity);
        }
    };

    private static Stack stack(String id, int count) {
        return new Stack(id, count, 64, null);
    }

    @Test
    void craftsPartialLimitAndMergesOutputsWithoutMutatingInputOnFailure() {
        List<Stack> inventory = new java.util.ArrayList<>(java.util.Arrays.asList(
                stack("ore", 5), stack("fuel", 2), null));
        TurtleCrafting.Recipe<Material, Stack> recipe = new TurtleCrafting.Recipe<>(
                List.of(new Material("ore", null, 2), new Material("fuel", null, 1)),
                List.of(stack("bar", 1)));

        TurtleCrafting.Plan<Stack> plan = TurtleCrafting.plan(inventory, recipe, 4, OPS);

        assertEquals(2, plan.crafts());
        assertEquals(java.util.Arrays.asList(stack("ore", 1), null, stack("bar", 2)), plan.slots());
        assertEquals(java.util.Arrays.asList(stack("ore", 5), stack("fuel", 2), null), inventory);
    }

    @Test
    void outputCapacityFailureIsAtomic() {
        List<Stack> inventory = new java.util.ArrayList<>(List.of(stack("ore", 2), stack("other", 64)));
        TurtleCrafting.Recipe<Material, Stack> recipe = new TurtleCrafting.Recipe<>(
                List.of(new Material("ore", null, 2)), List.of(stack("other", 65)));

        TurtleCrafting.Plan<Stack> plan = TurtleCrafting.plan(inventory, recipe, 1, OPS);

        assertEquals(0, plan.crafts());
        assertTrue(plan.outputBlocked());
        assertEquals(inventory, plan.slots());
    }

    @Test
    void matchesResourceMaterialsAcrossSlots() {
        List<Stack> inventory = new java.util.ArrayList<>(List.of(
                new Stack("oak", 1, 64, "wood"), new Stack("birch", 2, 64, "wood")));
        TurtleCrafting.Recipe<Material, Stack> recipe = new TurtleCrafting.Recipe<>(
                List.of(new Material(null, "wood", 3)), List.of(stack("planks", 4)));

        TurtleCrafting.Plan<Stack> plan = TurtleCrafting.plan(inventory, recipe, 1, OPS);

        assertEquals(1, plan.crafts());
        assertEquals(java.util.Arrays.asList(stack("planks", 4), null), plan.slots());
    }
}
