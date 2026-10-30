package me.kvothe.taleos.lua;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import me.kvothe.taleos.os.VirtualFileSystem;

final class RomTestSupport {
    private static final Path ROM_DIR = Path.of("src/main/resources/lua/rom");

    private RomTestSupport() {
    }

    static LuaMachine boot(Path root, String startup) throws Exception {
        Map<String, String> overlay = new HashMap<>();
        try (var walk = Files.walk(ROM_DIR)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                overlay.put("rom/" + ROM_DIR.relativize(path).toString().replace('\\', '/'),
                        Files.readString(path));
            }
        }
        if (startup != null) {
            Files.createDirectories(root);
            Files.writeString(root.resolve("startup.lua"), startup);
        }
        VirtualFileSystem fs = new VirtualFileSystem(root, 1_000_000);
        fs.mountRom(overlay);
        LuaMachine machine = new LuaMachine(200_000, fs);
        machine.load(overlay.get("rom/bios.lua"), "taleos:bios");
        return machine;
    }
}
