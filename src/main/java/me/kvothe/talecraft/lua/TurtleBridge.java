package me.kvothe.talecraft.lua;

import org.luaj.vm2.Varargs;

public interface TurtleBridge {
    Varargs call(String method, Varargs args);
}
