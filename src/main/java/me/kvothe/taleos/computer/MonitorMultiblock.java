package me.kvothe.taleos.computer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure geometry for connected, same-facing monitor rectangles. */
public final class MonitorMultiblock {

    public static final int MAX_WIDTH = 8;
    public static final int MAX_HEIGHT = 8;

    private MonitorMultiblock() {
    }

    public record Pos(int x, int y, int z) implements Comparable<Pos> {
        @Override
        public int compareTo(Pos other) {
            int byY = Integer.compare(other.y, y);
            if (byY != 0) {
                return byY;
            }
            int byX = Integer.compare(x, other.x);
            return byX != 0 ? byX : Integer.compare(z, other.z);
        }

        public Pos offset(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }
    }

    public enum Facing {
        SOUTH(1, 0, 0.0),
        EAST(0, -1, Math.PI / 2),
        NORTH(-1, 0, Math.PI),
        WEST(0, 1, -Math.PI / 2);

        private final int rightX;
        private final int rightZ;
        private final double yaw;

        Facing(int rightX, int rightZ, double yaw) {
            this.rightX = rightX;
            this.rightZ = rightZ;
            this.yaw = yaw;
        }

        public double yaw() {
            return yaw;
        }

        public Pos right(Pos pos, int amount) {
            return pos.offset(rightX * amount, 0, rightZ * amount);
        }

        public int horizontal(Pos origin, Pos pos) {
            return (pos.x() - origin.x()) * rightX + (pos.z() - origin.z()) * rightZ;
        }

        public static Facing fromYaw(double yaw) {
            int quarter = Math.round((float) (yaw / (Math.PI / 2)));
            return values()[Math.floorMod(quarter, 4)];
        }
    }

    public record Block(Pos pos, Facing facing) {
    }

    public record Structure(Pos origin, int width, int height, Facing facing, List<Pos> blocks) {
        public Structure {
            blocks = List.copyOf(blocks);
        }

        public int pixelWidth() {
            return width * MonitorCanvas.WIDTH;
        }

        public int pixelHeight() {
            return height * MonitorCanvas.HEIGHT;
        }

        public boolean contains(Pos pos) {
            return blocks.contains(pos);
        }

        public int blockX(Pos pos) {
            if (!contains(pos)) {
                throw new IllegalArgumentException("block is outside the monitor structure");
            }
            return facing.horizontal(origin, pos);
        }

        public int blockY(Pos pos) {
            if (!contains(pos)) {
                throw new IllegalArgumentException("block is outside the monitor structure");
            }
            return origin.y() - pos.y();
        }

        public int pixelX(Pos pos) {
            return blockX(pos) * MonitorCanvas.WIDTH;
        }

        public int pixelY(Pos pos) {
            return blockY(pos) * MonitorCanvas.HEIGHT;
        }
    }

    /** Partitions every compatible connected component into deterministic rectangles. */
    public static List<Structure> rebuild(Collection<Block> blocks) {
        Set<Block> remaining = new LinkedHashSet<>(blocks);
        List<Structure> result = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Block seed = remaining.iterator().next();
            Set<Pos> component = component(seed, remaining);
            remaining.removeIf(block -> component.contains(block.pos()));
            partitionComponent(seed.facing(), component, result);
        }
        result.sort(Comparator.comparing(Structure::origin).thenComparing(s -> s.facing().ordinal()));
        return List.copyOf(result);
    }

    private static Set<Pos> component(Block seed, Set<Block> blocks) {
        Set<Pos> compatible = new HashSet<>();
        for (Block block : blocks) {
            if (block.facing() == seed.facing()) {
                compatible.add(block.pos());
            }
        }
        Set<Pos> component = new HashSet<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        queue.add(seed.pos());
        component.add(seed.pos());
        while (!queue.isEmpty()) {
            Pos pos = queue.removeFirst();
            for (Pos next : planarNeighbours(seed.facing(), pos)) {
                if (compatible.contains(next) && component.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return component;
    }

    private static void partitionComponent(Facing facing, Set<Pos> component,
            List<Structure> result) {
        Set<Pos> remaining = new HashSet<>(component);
        while (!remaining.isEmpty()) {
            Pos origin = remaining.stream().min(originComparator(facing)).orElseThrow();
            int width = 0;
            while (width < MAX_WIDTH && remaining.contains(facing.right(origin, width))) {
                width++;
            }
            int height = 1;
            heightLoop:
            while (height < MAX_HEIGHT) {
                Pos row = origin.offset(0, -height, 0);
                for (int x = 0; x < width; x++) {
                    if (!remaining.contains(facing.right(row, x))) {
                        break heightLoop;
                    }
                }
                height++;
            }
            List<Pos> rectangle = new ArrayList<>(width * height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Pos pos = facing.right(origin.offset(0, -y, 0), x);
                    rectangle.add(pos);
                    remaining.remove(pos);
                }
            }
            result.add(new Structure(origin, width, height, facing, rectangle));
        }
    }

    private static Comparator<Pos> originComparator(Facing facing) {
        Pos zero = new Pos(0, 0, 0);
        return Comparator.<Pos>comparingInt(Pos::y).reversed()
                .thenComparingInt(pos -> facing.horizontal(zero, pos))
                .thenComparing(Pos::x)
                .thenComparing(Pos::z);
    }

    private static List<Pos> planarNeighbours(Facing facing, Pos pos) {
        return List.of(facing.right(pos, 1), facing.right(pos, -1),
                pos.offset(0, 1, 0), pos.offset(0, -1, 0));
    }
}
