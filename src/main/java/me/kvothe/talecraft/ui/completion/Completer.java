package me.kvothe.talecraft.ui.completion;

@FunctionalInterface
public interface Completer {

    Completion complete(String text, int caret);
}
