package me.kvothe.talecraft.peripheral.usb;

import java.util.List;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import me.kvothe.talecraft.peripheral.Peripheral;

public final class UsbDrivePeripheral implements Peripheral {
    public interface Backend {
        Integer penDriveId();

        String penDriveLabel();

        void setPenDriveLabel(String label);

        String mountPath();

        boolean eject();
    }

    private static final List<String> METHODS = List.of("isPenDrivePresent", "getPenDriveID",
            "getPenDriveLabel", "setPenDriveLabel", "getMountPath", "eject");

    private final Backend backend;

    public UsbDrivePeripheral(Backend backend) {
        this.backend = backend;
    }

    @Override
    public String getType() {
        return "usb_drive";
    }

    @Override
    public List<String> getMethods() {
        return METHODS;
    }

    @Override
    public Varargs call(String method, Varargs args) {
        return switch (method) {
            case "isPenDrivePresent" -> LuaValue.valueOf(backend.penDriveId() != null);
            case "getPenDriveID" -> {
                Integer id = backend.penDriveId();
                yield id == null ? LuaValue.NIL : LuaValue.valueOf(id);
            }
            case "getPenDriveLabel" -> {
                requirePresent();
                yield LuaValue.valueOf(backend.penDriveLabel());
            }
            case "setPenDriveLabel" -> {
                requirePresent();
                backend.setPenDriveLabel(args.arg1().isnil() ? null : args.arg1().checkjstring());
                yield LuaValue.TRUE;
            }
            case "getMountPath" -> {
                requirePresent();
                String path = backend.mountPath();
                yield path == null ? LuaValue.NIL : LuaValue.valueOf(path);
            }
            case "eject" -> LuaValue.valueOf(backend.eject());
            default -> throw new LuaError("No such method " + method + " on usb_drive");
        };
    }

    private void requirePresent() {
        if (backend.penDriveId() == null) {
            throw new LuaError("No PenDrive present");
        }
    }
}
