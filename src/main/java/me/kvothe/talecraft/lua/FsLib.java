package me.kvothe.talecraft.lua;

import java.io.IOException;
import java.util.List;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import me.kvothe.talecraft.os.VirtualFileSystem;

/**
 * The Lua-facing {@code fs} API of a computer, backed by a jailed
 * {@link VirtualFileSystem}. FS failures raise ordinary Lua errors (scripts
 * may pcall them); jail violations and quota overflows are errors too.
 */
public final class FsLib {

    private FsLib() {
    }

    public static LuaTable create(VirtualFileSystem fs) {
        LuaTable t = new LuaTable();
        t.set("exists", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                return LuaValue.valueOf(fs.exists(a.checkjstring(1)));
            }
        });
        t.set("isDir", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                return LuaValue.valueOf(fs.isDir(a.checkjstring(1)));
            }
        });
        t.set("list", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                List<String> names = fs.list(a.checkjstring(1));
                LuaTable out = new LuaTable();
                for (int i = 0; i < names.size(); i++) {
                    out.set(i + 1, LuaValue.valueOf(names.get(i)));
                }
                return out;
            }
        });
        t.set("read", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                String content = fs.read(a.checkjstring(1));
                return content == null ? LuaValue.NIL : LuaValue.valueOf(content);
            }
        });
        t.set("write", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                fs.write(a.checkjstring(1), a.checkjstring(2));
                return LuaValue.TRUE;
            }
        });
        t.set("append", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                fs.append(a.checkjstring(1), a.checkjstring(2));
                return LuaValue.TRUE;
            }
        });
        t.set("mkdir", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                fs.mkdir(a.checkjstring(1));
                return LuaValue.TRUE;
            }
        });
        t.set("delete", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                fs.delete(a.checkjstring(1));
                return LuaValue.TRUE;
            }
        });
        t.set("getSize", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                return LuaValue.valueOf(fs.getSize(a.checkjstring(1)));
            }
        });
        t.set("getCapacity", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                return LuaValue.valueOf(a.arg1().isnil() ? fs.getQuotaBytes()
                        : fs.getQuotaBytes(a.arg1().checkjstring()));
            }
        });
        t.set("getFreeSpace", new Fn(fs) {
            @Override
            LuaValue run(Varargs a) throws Exception {
                return LuaValue.valueOf(a.arg1().isnil() ? fs.getFreeBytes()
                        : fs.getFreeBytes(a.arg1().checkjstring()));
            }
        });
        return t;
    }

    /** Base class translating Java-side failures into Lua errors. */
    private abstract static class Fn extends VarArgFunction {
        protected final VirtualFileSystem fs;

        Fn(VirtualFileSystem fs) {
            this.fs = fs;
        }

        abstract LuaValue run(Varargs a) throws Exception;

        @Override
        public final Varargs invoke(Varargs a) {
            try {
                return run(a);
            } catch (Exception e) {
                throw new LuaError("fs: " + e.getMessage());
            }
        }
    }
}
