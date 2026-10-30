package me.kvothe.taleos.ui.completion;

@FunctionalInterface
public interface Completer {

    Completion complete(String text, int caret);
}
