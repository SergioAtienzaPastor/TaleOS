package me.kvothe.talecraft.ui.completion;

import java.util.List;
import java.util.Objects;

public final class CompletionSession {

    private final Completer completer;
    private String value = "";
    private int caret;
    private List<String> candidates = List.of();
    private String commonPrefix = "";
    private int replacementStart;
    private int replacementEnd;
    private int candidateIndex = -1;

    public CompletionSession(Completer completer) {
        this.completer = Objects.requireNonNull(completer);
    }

    public void reset(String initialValue) {
        value = initialValue == null ? "" : initialValue;
        caret = value.length();
        invalidate();
    }

    public void valueChanged(String changedValue) {
        String next = changedValue == null ? "" : changedValue;
        if (next.equals(value)) {
            return;
        }
        int prefix = 0;
        int limit = Math.min(value.length(), next.length());
        while (prefix < limit && value.charAt(prefix) == next.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < limit - prefix
                && value.charAt(value.length() - 1 - suffix) == next.charAt(next.length() - 1 - suffix)) {
            suffix++;
        }
        caret = next.length() - suffix;
        value = next;
        invalidate();
    }

    public boolean complete() {
        if (candidates.isEmpty()) {
            Completion completion = completer.complete(value, caret);
            if (completion == null || completion.candidates().isEmpty()
                    || completion.start() < 0 || completion.end() < completion.start()
                    || completion.end() > value.length()) {
                invalidate();
                return false;
            }
            replacementStart = completion.start();
            replacementEnd = completion.end();
            candidates = completion.candidates();
            commonPrefix = commonPrefix(candidates);
            String fragment = value.substring(replacementStart, replacementEnd);
            if (commonPrefix.length() > fragment.length()) {
                candidateIndex = candidates.indexOf(commonPrefix);
                return apply(commonPrefix);
            }
            candidateIndex = 0;
            return apply(candidates.getFirst());
        }
        candidateIndex = (candidateIndex + 1 + candidates.size()) % candidates.size();
        return apply(candidates.get(candidateIndex));
    }

    private boolean apply(String replacement) {
        String completed = value.substring(0, replacementStart) + replacement
                + value.substring(replacementEnd);
        caret = replacementStart + replacement.length();
        replacementEnd = caret;
        boolean changed = !completed.equals(value);
        value = completed;
        return changed;
    }

    public void invalidate() {
        candidates = List.of();
        commonPrefix = "";
        candidateIndex = -1;
    }

    public String getValue() {
        return value;
    }

    public int getCaret() {
        return caret;
    }

    public List<String> getCandidates() {
        return candidates;
    }

    public String getCommonPrefix() {
        return commonPrefix;
    }

    private static String commonPrefix(List<String> values) {
        String prefix = values.getFirst();
        for (int index = 1; index < values.size() && !prefix.isEmpty(); index++) {
            String candidate = values.get(index);
            int length = Math.min(prefix.length(), candidate.length());
            int match = 0;
            while (match < length && prefix.charAt(match) == candidate.charAt(match)) {
                match++;
            }
            prefix = prefix.substring(0, match);
        }
        return prefix;
    }
}
