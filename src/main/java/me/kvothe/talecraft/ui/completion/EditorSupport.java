package me.kvothe.talecraft.ui.completion;

public final class EditorSupport {

    private EditorSupport() {
    }

    public static String runCommandFor(String path) {
        return path.endsWith(".lua") ? path.substring(0, path.length() - 4) : path;
    }
}
