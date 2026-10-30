package me.kvothe.taleos.turtle;

import org.joml.Matrix4d;
import org.joml.Vector3f;

import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;

final class TurtleToolDisplay {

    private static final float IDLE_TTL_SECONDS = 0.18f;
    private static final float ANIMATED_TTL_SECONDS = 0.055f;
    private static final Vector3f WOOD = new Vector3f(0.32f, 0.19f, 0.09f);

    private final TurtleController turtle;
    private int ticks;
    private int swingLeft;
    private int swingRight;
    private float frameTtl = IDLE_TTL_SECONDS;
    private String lastFrame = "";

    TurtleToolDisplay(TurtleController turtle) {
        this.turtle = turtle;
    }

    void swing(int slot) {
        if (slot == TurtleController.LEFT_TOOL_SLOT) {
            swingLeft = 7;
        } else {
            swingRight = 7;
        }
    }

    void tick() {
        boolean moving = turtle.isMoving();
        if (++ticks % 3 != 0 && swingLeft == 0 && swingRight == 0 && !moving) {
            return;
        }
        frameTtl = moving || swingLeft > 0 || swingRight > 0
                ? ANIMATED_TTL_SECONDS : IDLE_TTL_SECONDS;
        ItemStack left = turtle.getTool(TurtleController.LEFT_TOOL_SLOT);
        ItemStack right = turtle.getTool(TurtleController.RIGHT_TOOL_SLOT);
        String frame = itemKey(left) + "|" + itemKey(right) + "|" + swingLeft + "|" + swingRight;
        if (frame.equals(lastFrame) && ticks % 3 != 0 && !moving) {
            return;
        }
        lastFrame = frame;
        render(TurtleController.LEFT_TOOL_SLOT, left, swingLeft);
        render(TurtleController.RIGHT_TOOL_SLOT, right, swingRight);
        if (swingLeft > 0) {
            swingLeft--;
        }
        if (swingRight > 0) {
            swingRight--;
        }
    }

    private void render(int slot, ItemStack stack, int swing) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        boolean left = slot == TurtleController.LEFT_TOOL_SLOT;
        double side = left ? -0.57 : 0.57;
        double angle = swing == 0 ? 0 : Math.sin((7 - swing) / 7.0 * Math.PI) * 0.8;
        Matrix4d base = baseMatrix().translate(side, 0.43, 0.03)
                .rotateX(left ? angle : -angle).translate(-side, -0.43, -0.03);
        TurtleUpgradeRegistry.Kind upgrade = TurtleUpgradeRegistry.defaults().kindOf(stack);
        if (upgrade == TurtleUpgradeRegistry.Kind.MODEM) {
            shape(base, side, 0.48, 0.03, 0.28, 0.30, 0.22, new Vector3f(0.18f, 0.22f, 0.25f));
            shape(base, side, 0.78, 0.03, 0.04, 0.38, 0.04, new Vector3f(0.65f, 0.70f, 0.74f));
            shape(base, side, 0.99, 0.03, 0.10, 0.10, 0.10, new Vector3f(0.20f, 0.75f, 0.85f));
            return;
        }
        if (upgrade == TurtleUpgradeRegistry.Kind.CRAFTING) {
            shape(base, side, 0.48, 0.03, 0.34, 0.34, 0.24, WOOD);
            for (int row = -1; row <= 1; row++) {
                for (int column = -1; column <= 1; column++) {
                    shape(base, side + column * 0.09, 0.48 + row * 0.09, 0.17,
                            0.065, 0.065, 0.03, new Vector3f(0.72f, 0.55f, 0.30f));
                }
            }
            return;
        }
        if (upgrade == TurtleUpgradeRegistry.Kind.SPEAKER) {
            shape(base, side, 0.48, 0.03, 0.34, 0.40, 0.24, new Vector3f(0.15f, 0.15f, 0.17f));
            shape(base, side, 0.48, 0.17, 0.20, 0.20, 0.04, new Vector3f(0.45f, 0.45f, 0.48f));
            return;
        }
        Vector3f metal = metalColor(stack.getItemId());
        shape(base, side, 0.45, 0.03, 0.065, 0.68, 0.065, WOOD);
        shape(base, side, 0.73, 0.03, 0.085, 0.08, 0.085, metal);

        String id = stack.getItemId().toLowerCase(java.util.Locale.ROOT);
        if (id.contains("shovel")) {
            shape(base, side, 0.11, 0.03, 0.09, 0.20, 0.24, metal);
            shape(base, side, 0.23, 0.03, 0.075, 0.07, 0.15, metal);
            shape(base, side, 0.035, 0.03, 0.065, 0.06, 0.15, metal);
        } else if (id.contains("pickaxe")) {
            shape(base, side, 0.80, 0.03, 0.085, 0.08, 0.50, metal);
            shape(base, side, 0.72, -0.22, 0.075, 0.18, 0.09, metal);
            shape(base, side, 0.72, 0.28, 0.075, 0.18, 0.09, metal);
        } else if (id.contains("axe")) {
            shape(base, side, 0.73, 0.12, 0.09, 0.27, 0.30, metal);
            shape(base, side, 0.77, -0.06, 0.075, 0.15, 0.09, metal);
        } else if (id.contains("sword")) {
            shape(base, side, 0.98, 0.03, 0.055, 0.52, 0.12, metal);
            shape(base, side, 1.25, 0.03, 0.04, 0.08, 0.07, metal);
            shape(base, side, 0.69, 0.03, 0.075, 0.07, 0.34, metal);
        } else {
            shape(base, side, 0.79, 0.03, 0.09, 0.18, 0.30, metal);
        }
    }

    private Matrix4d baseMatrix() {
        var logicalPosition = turtle.getPosition();
        var visualPosition = turtle.getVisualPosition();
        SectionReader reader = new SectionReader(turtle.getWorld().getChunkStore());
        double yaw = RotationTuple.get(reader.getRotationIndex(
                logicalPosition.x, logicalPosition.y, logicalPosition.z)).yaw().getRadians();
        return new Matrix4d().translate(visualPosition.x, visualPosition.y, visualPosition.z)
                .rotateY(turtle.getVisualYaw(yaw));
    }

    private void shape(Matrix4d base, double x, double y, double z,
            double sx, double sy, double sz, Vector3f color) {
        Matrix4d matrix = new Matrix4d(base).translate(x, y, z).scale(sx, sy, sz);
        DebugUtils.add(turtle.getWorld(), DebugShape.Cube, matrix, color, 1.0f,
                frameTtl, DebugUtils.FLAG_NO_WIREFRAME);
    }

    private static String itemKey(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : stack.getItemId() + ":" + stack.getDurability();
    }

    private static Vector3f metalColor(String id) {
        String material = id.toLowerCase(java.util.Locale.ROOT);
        if (material.contains("copper")) {
            return new Vector3f(0.72f, 0.32f, 0.15f);
        }
        if (material.contains("thorium")) {
            return new Vector3f(0.25f, 0.75f, 0.65f);
        }
        if (material.contains("cobalt")) {
            return new Vector3f(0.18f, 0.35f, 0.85f);
        }
        if (material.contains("adamantite")) {
            return new Vector3f(0.65f, 0.15f, 0.35f);
        }
        if (material.contains("gold")) {
            return new Vector3f(0.95f, 0.72f, 0.18f);
        }
        return new Vector3f(0.65f, 0.70f, 0.74f);
    }
}
