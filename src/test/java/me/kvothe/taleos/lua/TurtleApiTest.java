package me.kvothe.taleos.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

class TurtleApiTest {

    private static final long BUDGET = 200_000;
    private static final Path PROGRAMS = Path.of("src/main/resources/lua/rom/programs");

    private static final class FakeTurtle implements TurtleBridge {
        final List<String> methods = new ArrayList<>();
        final List<Varargs> arguments = new ArrayList<>();
        final java.util.Set<String> dug = new java.util.HashSet<>();
        String lastMethod;
        Varargs lastArgs;
        int calls;
        int failAt;
        Runnable onCall;

        @Override
        public Varargs call(String method, Varargs args) {
            lastMethod = method;
            methods.add(method);
            LuaValue[] snapshot = new LuaValue[args.narg()];
            for (int i = 0; i < snapshot.length; i++) {
                snapshot[i] = args.arg(i + 1);
            }
            lastArgs = LuaValue.varargsOf(snapshot);
            arguments.add(lastArgs);
            calls++;
            if (onCall != null) {
                onCall.run();
            }
            if (calls == failAt) {
                return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf("blocked"));
            }
            if (method.startsWith("detect")
                    && dug.contains("dig" + method.substring("detect".length()))) {
                return LuaValue.FALSE;
            }
            if (method.startsWith("dig")) {
                dug.add(method);
            } else if (method.equals("forward")) {
                dug.clear();
            }
            return LuaValue.varargsOf(new LuaValue[] {
                    LuaValue.TRUE, LuaValue.valueOf("done"), LuaValue.valueOf(7) });
        }
    }

    private static final class FakeWorld implements WorldBridge {
        private final TurtleBridge turtle;

        FakeWorld(TurtleBridge turtle) {
            this.turtle = turtle;
        }

        @Override
        public String getBlockId(int x, int y, int z) {
            return null;
        }

        @Override
        public double getTime() {
            return 0;
        }

        @Override
        public void chat(String message) {
        }

        @Override
        public TurtleBridge turtle() {
            return turtle;
        }
    }

    private static LuaMachine machineWithTurtle(FakeTurtle turtle) {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.setWorldBridge(new FakeWorld(turtle));
        return machine;
    }

    @Test
    void turtleIsAbsentForNormalComputers() {
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.load("print(type(turtle))", "no-turtle");
        machine.tick();
        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("nil\n", machine.drainOutput());
    }

    @Test
    void installsEveryMethodAndRemovesTableWhenBridgeHasNoTurtle() {
        LuaMachine machine = machineWithTurtle(new FakeTurtle());
        machine.load("local names = { 'forward', 'back', 'up', 'down', 'turnLeft', 'turnRight',"
                + " 'detect', 'detectUp', 'detectDown', 'inspect', 'inspectUp', 'inspectDown',"
                + " 'select', 'getSelectedSlot', 'getItemCount', 'getItemSpace', 'getItemDetail',"
                + " 'transferTo', 'compare', 'compareUp', 'compareDown', 'compareTo',"
                + " 'equip', 'unequip', 'equipLeft', 'equipRight', 'getEquippedLeft', 'getEquippedRight',"
                + " 'getFuelLevel', 'getFuelLimit', 'refuel', 'dig', 'digUp', 'digDown',"
                + " 'attack', 'attackUp', 'attackDown', 'craft',"
                + " 'place', 'placeUp', 'placeDown', 'suck', 'suckUp', 'suckDown',"
                + " 'drop', 'dropUp', 'dropDown' };"
                + " for _, name in ipairs(names) do assert(type(turtle[name]) == 'function', name) end;"
                + " print(#names)", "all-methods");
        machine.tick();
        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("47\n", machine.drainOutput());

        LuaMachine removed = machineWithTurtle(new FakeTurtle());
        removed.setWorldBridge(new FakeWorld(null));
        removed.load("print(type(turtle))", "removed-turtle");
        removed.tick();
        assertEquals("nil\n", removed.drainOutput());
    }

    @Test
    void forwardsArgumentsAndReturnsAllResults() {
        FakeTurtle turtle = new FakeTurtle();
        LuaMachine machine = machineWithTurtle(turtle);
        machine.load("local a, b, c = turtle.transferTo(4, nil, 'cargo'); print(a, b, c)",
                "turtle-call");
        machine.tick();

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("transferTo", turtle.lastMethod);
        assertEquals(3, turtle.lastArgs.narg());
        assertEquals(4, turtle.lastArgs.arg(1).toint());
        assertTrue(turtle.lastArgs.arg(2).isnil());
        assertEquals("cargo", turtle.lastArgs.arg(3).tojstring());
        assertEquals("true\tdone\t7\n", machine.drainOutput());
    }

    @Test
    void attackAndCraftDispatchDirectionsSidesAndLimits() {
        FakeTurtle turtle = new FakeTurtle();
        LuaMachine machine = machineWithTurtle(turtle);
        machine.load("turtle.attackUp('left'); turtle.attackDown(); turtle.attack('right');"
                + " turtle.craft(3); turtle.craft()", "attack-craft-dispatch");
        machine.tick();

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals(List.of("attackUp", "attackDown", "attack", "craft", "craft"), turtle.methods);
        assertEquals("left", turtle.arguments.get(0).arg1().tojstring());
        assertTrue(turtle.arguments.get(1).arg1().isnil());
        assertEquals("right", turtle.arguments.get(2).arg1().tojstring());
        assertEquals(3, turtle.arguments.get(3).arg1().toint());
        assertTrue(turtle.arguments.get(4).arg1().isnil());
    }

    @Test
    void turtleCallsShareTheWorldCallTickCap() {
        FakeTurtle turtle = new FakeTurtle();
        LuaMachine machine = machineWithTurtle(turtle);
        machine.load("for i = 1, 40 do turtle.forward() end", "turtle-cap");
        machine.tick();

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals(32, turtle.calls);
    }

    @Test
    void terminationIsCheckedAfterTurtleYield() {
        FakeTurtle turtle = new FakeTurtle();
        LuaMachine machine = machineWithTurtle(turtle);
        turtle.onCall = machine::requestTerminate;
        machine.load("tale.setProgramRunning(true);"
                + " local ok, err = pcall(function() turtle.forward() end);"
                + " tale.setProgramRunning(false); print(ok, err)", "turtle-terminate");
        machine.tick();

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("false\t" + LuaMachine.TERMINATED_MESSAGE + "\n", machine.drainOutput());
    }

    @Test
    void goStopsAtFailedMoveAndPrintsReason() throws Exception {
        FakeTurtle turtle = new FakeTurtle();
        turtle.failAt = 2;
        LuaMachine machine = machineWithTurtle(turtle);
        machine.load(programWithArguments("go.lua",
                "local direction, countText = 'forward', '3'"), "go-test");
        machine.tick();

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals(List.of("forward", "forward"), turtle.methods);
        assertEquals("go: blocked\n", machine.drainOutput());
    }

    @Test
    void goSupportsTurningCommands() throws Exception {
        FakeTurtle turtle = new FakeTurtle();
        LuaMachine machine = machineWithTurtle(turtle);
        machine.load(programWithArguments("go.lua",
                "local direction, countText = 'left', '2'"), "go-turn-test");
        machine.tick();

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals(List.of("turnLeft", "turnLeft"), turtle.methods);
    }

    @Test
    void tunnelExcavatesThreeByThreeAndTurns() throws Exception {
        FakeTurtle turtle = new FakeTurtle();
        LuaMachine machine = machineWithTurtle(turtle);
        machine.load(programWithArguments("tunnel.lua", "local lengthText = '2'"), "tunnel-test");
        for (int tick = 0; tick < 500 && machine.isAlive(); tick++) {
            machine.tick();
        }

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertTrue(turtle.methods.contains("turnLeft"));
        assertTrue(turtle.methods.contains("turnRight"));
        assertTrue(turtle.methods.contains("up"));
        assertTrue(turtle.methods.contains("down"));
        assertTrue(turtle.methods.contains("digUp"));
        assertEquals(1, turtle.methods.stream().filter("forward"::equals).count());
        assertEquals("Digging 3x3 tunnel...\nTunnel completed: 2 sections\n",
                machine.drainOutput());
    }

    @Test
    void excavateClearsOneByThreeSections() throws Exception {
        FakeTurtle turtle = new FakeTurtle();
        LuaMachine machine = machineWithTurtle(turtle);
        machine.load(programWithArguments("excavate.lua", "local lengthText = '2'"), "excavate-test");
        for (int tick = 0; tick < 500 && machine.isAlive(); tick++) {
            machine.tick();
        }

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals(List.of("dig", "forward", "digUp", "digDown",
                "dig", "forward", "digUp", "digDown"), turtle.methods.stream()
                        .filter(name -> name.startsWith("dig") || name.equals("forward")).toList());
        assertEquals("Excavating 1x3 face...\nExcavation completed: 2 sections\n",
                machine.drainOutput());
    }

    @Test
    void excavateRejectsInvalidLengthAndStopsAtFailure() throws Exception {
        FakeTurtle invalid = new FakeTurtle();
        LuaMachine invalidMachine = machineWithTurtle(invalid);
        invalidMachine.load(programWithArguments("excavate.lua", "local lengthText = '0'"),
                "excavate-invalid");
        invalidMachine.tick();
        assertEquals("excavate: length must be a positive integer\n", invalidMachine.drainOutput());
        assertTrue(invalid.methods.isEmpty());

        FakeTurtle blocked = new FakeTurtle();
        blocked.failAt = 2;
        LuaMachine blockedMachine = machineWithTurtle(blocked);
        blockedMachine.load(programWithArguments("excavate.lua", "local lengthText = '2'"),
                "excavate-blocked");
        blockedMachine.tick();
        assertEquals(List.of("detect", "dig"), blocked.methods);
        assertEquals("Excavating 1x3 face...\nexcavate: blocked\n", blockedMachine.drainOutput());
    }

    @Test
    void turtleCdAcceptsMountedNasDriveId(@TempDir Path dir) throws Exception {
        LuaMachine machine = RomTestSupport.boot(dir.resolve("turtle"), null);
        machine.setWorldBridge(new FakeWorld(new FakeTurtle()));
        machine.getFileSystem().mount("nas/22", dir.resolve("drive22"), 1000);
        Files.createDirectories(dir.resolve("drive22"));
        Files.writeString(dir.resolve("drive22/prueba.txt"), "contenido");
        machine.tick();
        machine.drainOutput();

        machine.queueEvent("stdin", "cd 22");
        machine.tick();
        assertEquals("tale:/nas/22> ", machine.getPrompt());
        machine.queueEvent("stdin", "ls");
        machine.tick();
        assertTrue(machine.drainOutput().contains("prueba.txt"));
    }

    private static String programWithArguments(String name, String argumentDeclaration) throws Exception {
        String source = Files.readString(PROGRAMS.resolve(name));
        return argumentDeclaration + source.substring(source.indexOf('\n'));
    }
}
