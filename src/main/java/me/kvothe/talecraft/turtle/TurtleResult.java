package me.kvothe.talecraft.turtle;

public record TurtleResult(boolean success, String error) {
    public static final TurtleResult OK = new TurtleResult(true, null);

    public static TurtleResult fail(String error) {
        return new TurtleResult(false, me.kvothe.talecraft.I18n.t(error));
    }
}
