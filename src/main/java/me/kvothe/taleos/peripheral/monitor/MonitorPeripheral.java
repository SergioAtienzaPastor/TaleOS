package me.kvothe.taleos.peripheral.monitor;

import java.util.List;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import me.kvothe.taleos.computer.MonitorCanvas;
import me.kvothe.taleos.computer.MonitorTerminal;
import me.kvothe.taleos.peripheral.Peripheral;

public final class MonitorPeripheral implements Peripheral {
    public static final String TYPE = "monitor";
    private static final List<String> METHODS = List.of("write", "blit", "clear", "clearLine",
            "scroll", "getSize", "getTerminalSize", "setCursorPos", "getCursorPos",
            "setCursorBlink", "getCursorBlink", "setTextColor", "getTextColor",
            "setBackgroundColor", "getBackgroundColor", "isColor", "setPixel", "getPixel", "fill");

    private MonitorCanvas canvas;
    private MonitorTerminal terminal;

    public MonitorPeripheral(MonitorCanvas canvas) {
        rebind(canvas);
    }

    public MonitorCanvas getCanvas() {
        return canvas;
    }

    public void rebind(MonitorCanvas canvas) {
        this.canvas = canvas;
        this.terminal = new MonitorTerminal(canvas);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public List<String> getMethods() {
        return METHODS;
    }

    @Override
    public boolean supportsMethod(String method) {
        return METHODS.contains(method) || "__getCell".equals(method) || "__setCell".equals(method);
    }

    @Override
    public Varargs call(String method, Varargs args) {
        return switch (method) {
            case "getSize" -> LuaValue.varargsOf(LuaValue.valueOf(canvas.getWidth()),
                    LuaValue.valueOf(canvas.getHeight()));
            case "getTerminalSize" -> LuaValue.varargsOf(LuaValue.valueOf(terminal.getWidth()),
                    LuaValue.valueOf(terminal.getHeight()));
            case "setPixel" -> {
                canvas.setPixel(args.arg(1).checkint() - 1, args.arg(2).checkint() - 1,
                        color(args.arg(3)));
                yield LuaValue.NIL;
            }
            case "getPixel" -> LuaValue.valueOf(MonitorCanvas.PALETTE[canvas.getPixel(
                    args.arg(1).checkint() - 1, args.arg(2).checkint() - 1)]);
            case "fill" -> {
                canvas.fill(args.arg(1).checkint() - 1, args.arg(2).checkint() - 1,
                        args.arg(3).checkint(), args.arg(4).checkint(), color(args.arg(5)));
                yield LuaValue.NIL;
            }
            case "write" -> {
                if (args.narg() < 3) {
                    terminal.write(args.arg1().tojstring());
                } else {
                    int color = args.arg(4).isnil() ? MonitorCanvas.colorIndex("white") : color(args.arg(4));
                    canvas.writeText(args.arg(1).checkint() - 1, args.arg(2).checkint() - 1,
                            args.arg(3).checkjstring(), color);
                }
                yield LuaValue.NIL;
            }
            case "blit" -> {
                terminal.blit(args.arg(1).checkjstring(), args.arg(2).checkjstring(),
                        args.arg(3).checkjstring());
                yield LuaValue.NIL;
            }
            case "clear" -> {
                if (args.narg() == 0) {
                    terminal.clear();
                } else {
                    canvas.clear(color(args.arg1()));
                }
                yield LuaValue.NIL;
            }
            case "clearLine" -> {
                terminal.clearLine();
                yield LuaValue.NIL;
            }
            case "scroll" -> {
                terminal.scroll(args.arg1().checkint());
                yield LuaValue.NIL;
            }
            case "setCursorPos" -> {
                terminal.setCursorPos(args.arg(1).checkint() - 1, args.arg(2).checkint() - 1);
                yield LuaValue.NIL;
            }
            case "getCursorPos" -> LuaValue.varargsOf(LuaValue.valueOf(terminal.getCursorX() + 1),
                    LuaValue.valueOf(terminal.getCursorY() + 1));
            case "setCursorBlink" -> {
                terminal.setCursorBlink(args.arg1().optboolean(false));
                yield LuaValue.NIL;
            }
            case "getCursorBlink" -> LuaValue.valueOf(terminal.getCursorBlink());
            case "setTextColor" -> {
                terminal.setTextColor(terminalColor(args.arg1()));
                yield LuaValue.NIL;
            }
            case "getTextColor" -> LuaValue.valueOf(1 << terminal.getTextColor());
            case "setBackgroundColor" -> {
                terminal.setBackgroundColor(terminalColor(args.arg1()));
                yield LuaValue.NIL;
            }
            case "getBackgroundColor" -> LuaValue.valueOf(1 << terminal.getBackgroundColor());
            case "isColor" -> LuaValue.valueOf(terminal.isColor());
            case "__getCell" -> {
                int x = args.arg(1).checkint() - 1;
                int y = args.arg(2).checkint() - 1;
                yield LuaValue.varargsOf(new LuaValue[] {
                        LuaValue.valueOf(String.valueOf(terminal.getChar(x, y))),
                        LuaValue.valueOf(1 << terminal.getCellTextColor(x, y)),
                        LuaValue.valueOf(1 << terminal.getCellBackgroundColor(x, y)) });
            }
            case "__setCell" -> {
                String character = args.arg(3).checkjstring();
                if (character.length() != 1) {
                    throw new LuaError("terminal cell must contain exactly one character");
                }
                terminal.setCell(args.arg(1).checkint() - 1, args.arg(2).checkint() - 1,
                        character.charAt(0), terminalColor(args.arg(4)), terminalColor(args.arg(5)));
                yield LuaValue.NIL;
            }
            default -> throw new LuaError("Unknown monitor method " + method);
        };
    }

    private static int terminalColor(LuaValue value) {
        if (value.isnumber()) {
            int color = value.checkint();
            if (color > 0 && color <= 32768 && (color & (color - 1)) == 0) {
                return Integer.numberOfTrailingZeros(color);
            }
            throw new LuaError("terminal color must be one power of two between 1 and 32768");
        }
        return color(value);
    }

    private static int color(LuaValue value) {
        String name = value.checkjstring();
        int index = MonitorCanvas.colorIndex(name);
        if (index < 0) {
            throw new LuaError(me.kvothe.taleos.I18n.t("Unknown color: ") + name);
        }
        return index;
    }
}
