package me.kvothe.talecraft.ui.completion;

import java.util.List;

public record Completion(int start, int end, List<String> candidates) {

    public Completion {
        candidates = List.copyOf(candidates);
    }
}
