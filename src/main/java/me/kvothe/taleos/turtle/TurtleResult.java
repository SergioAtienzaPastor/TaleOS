package me.kvothe.taleos.turtle;

public record TurtleResult(boolean success, String error) {
    public static final TurtleResult OK = new TurtleResult(true, null);

    public static TurtleResult fail(String error) {
        return new TurtleResult(false, me.kvothe.taleos.I18n.t(error));
    }
}
