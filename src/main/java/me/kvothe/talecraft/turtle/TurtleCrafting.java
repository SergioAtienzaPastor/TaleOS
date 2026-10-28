package me.kvothe.talecraft.turtle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TurtleCrafting {
    private TurtleCrafting() {
    }

    public interface StackOps<M, S> {
        int quantity(S stack);

        int required(M material);

        int maxStack(S stack);

        boolean matches(M material, S stack);

        boolean sameType(S first, S second);

        S withQuantity(S stack, int quantity);

        M withRequired(M material, int quantity);
    }

    public record Recipe<M, S>(List<M> inputs, List<S> outputs) {
    }

    public record Plan<S>(int crafts, List<S> slots, boolean outputBlocked) {
        public Plan {
            slots = Collections.unmodifiableList(new ArrayList<>(slots));
        }
    }

    public static <M, S> Plan<S> plan(List<S> inventory, Recipe<M, S> recipe, int limit,
            StackOps<M, S> ops) {
        if (limit <= 0) {
            throw new IllegalArgumentException("craft limit must be positive");
        }
        List<S> working = new ArrayList<>(inventory);
        int crafts = 0;
        boolean outputBlocked = false;
        while (crafts < limit) {
            List<S> before = new ArrayList<>(working);
            if (!consume(recipe.inputs(), 0, working, ops)) {
                working = before;
                break;
            }
            if (!addOutputs(recipe.outputs(), working, ops)) {
                working = before;
                outputBlocked = true;
                break;
            }
            crafts++;
        }
        return new Plan<>(crafts, working, outputBlocked);
    }

    private static <M, S> boolean consume(List<M> inputs, int inputIndex, List<S> slots,
            StackOps<M, S> ops) {
        if (inputIndex == inputs.size()) {
            return true;
        }
        M material = inputs.get(inputIndex);
        int required = ops.required(material);
        if (required <= 0) {
            return consume(inputs, inputIndex + 1, slots, ops);
        }
        for (int slot = 0; slot < slots.size(); slot++) {
            S stack = slots.get(slot);
            int available = ops.quantity(stack);
            if (available <= 0 || !ops.matches(material, stack)) {
                continue;
            }
            int taken = Math.min(required, available);
            slots.set(slot, ops.withQuantity(stack, available - taken));
            int remaining = required - taken;
            if (remaining == 0 && consume(inputs, inputIndex + 1, slots, ops)) {
                return true;
            }
            if (remaining > 0) {
                List<M> remainder = new ArrayList<>(inputs.subList(inputIndex, inputs.size()));
                remainder.set(0, ops.withRequired(material, remaining));
                List<M> nested = new ArrayList<>(inputs.subList(0, inputIndex));
                nested.addAll(remainder);
                if (consume(nested, inputIndex, slots, ops)) {
                    return true;
                }
            }
            slots.set(slot, stack);
        }
        return false;
    }

    private static <M, S> boolean addOutputs(List<S> outputs, List<S> slots, StackOps<M, S> ops) {
        for (S output : outputs) {
            int remaining = ops.quantity(output);
            if (remaining <= 0) {
                continue;
            }
            for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
                S stack = slots.get(slot);
                int count = ops.quantity(stack);
                if (count <= 0 || count >= ops.maxStack(stack) || !ops.sameType(stack, output)) {
                    continue;
                }
                int added = Math.min(remaining, ops.maxStack(stack) - count);
                slots.set(slot, ops.withQuantity(stack, count + added));
                remaining -= added;
            }
            for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
                if (ops.quantity(slots.get(slot)) != 0) {
                    continue;
                }
                int added = Math.min(remaining, ops.maxStack(output));
                slots.set(slot, ops.withQuantity(output, added));
                remaining -= added;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }
}
