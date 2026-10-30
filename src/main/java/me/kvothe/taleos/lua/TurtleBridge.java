package me.kvothe.taleos.lua;

import org.luaj.vm2.Varargs;

public interface TurtleBridge {
    Varargs call(String method, Varargs args);
}
