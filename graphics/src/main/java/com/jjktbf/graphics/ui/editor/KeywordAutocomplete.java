package com.jjktbf.graphics.ui.editor;

import com.jjktbf.model.text.KeywordDescriptionCatalog;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure matching and replacement logic for keyword description autocomplete. */
final class KeywordAutocomplete {

    private static final Comparator<KeywordDescriptionCatalog.Entry> TERM_ORDER =
        Comparator.comparing(KeywordDescriptionCatalog.Entry::term,
            String.CASE_INSENSITIVE_ORDER);

    private KeywordAutocomplete() { }

    static Query query(
        String text,
        int cursor,
        List<KeywordDescriptionCatalog.Entry> catalogEntries
    ) {
        String value = text == null ? "" : text;
        int caret = Math.max(0, Math.min(cursor, value.length()));
        List<KeywordDescriptionCatalog.Entry> entries = catalogEntries == null
            ? List.of()
            : catalogEntries;
        Query variableQuery = variableQuery(value, caret, entries);
        if (variableQuery != null) return variableQuery;
        if (caret == 0 || !isUppercaseWordCharacter(value.charAt(caret - 1))) return null;

        int runStart = caret - 1;
        while (runStart > 0 && isUppercaseFragmentCharacter(value.charAt(runStart - 1))) {
            runStart--;
        }
        while (runStart < caret && value.charAt(runStart) == ' ') runStart++;

        for (int candidateStart = runStart; candidateStart < caret;) {
            String fragment = value.substring(candidateStart, caret);
            List<KeywordDescriptionCatalog.Entry> matches = entries.stream()
                .filter(entry -> !isVariableEntry(entry))
                .filter(entry -> entry != null && entry.term() != null && !entry.term().isBlank())
                .filter(entry -> startsWithIgnoreCase(entry.term(), fragment))
                .sorted(TERM_ORDER)
                .toList();
            if (!matches.isEmpty()) {
                int replacementEnd = caret;
                while (replacementEnd < value.length()
                    && isUppercaseWordCharacter(value.charAt(replacementEnd))) {
                    replacementEnd++;
                }
                return new Query(candidateStart, replacementEnd, fragment, matches, false);
            }

            int nextWord = value.indexOf(' ', candidateStart);
            if (nextWord < 0 || nextWord >= caret) break;
            candidateStart = nextWord + 1;
            while (candidateStart < caret && value.charAt(candidateStart) == ' ') {
                candidateStart++;
            }
        }
        return null;
    }

    static String complete(String text, Query query, String term) {
        if (query == null || term == null) return text == null ? "" : text;
        String value = text == null ? "" : text;
        int start = Math.max(0, Math.min(query.start(), value.length()));
        int end = Math.max(start, Math.min(query.end(), value.length()));
        if (query.variable()) {
            return value.substring(0, start) + term + value.substring(end);
        }
        int typedLength = end - start;
        if (typedLength <= term.length()
            && value.regionMatches(true, start, term, 0, typedLength)) {
            int continuationEnd = end;
            while (continuationEnd < value.length()
                && isUppercaseFragmentCharacter(value.charAt(continuationEnd))) {
                continuationEnd++;
            }
            boolean hasContinuationWord = false;
            for (int index = end; index < continuationEnd; index++) {
                if (isUppercaseWordCharacter(value.charAt(index))) {
                    hasContinuationWord = true;
                    break;
                }
            }
            int continuationLength = Math.min(
                continuationEnd - end,
                term.length() - typedLength);
            int matchedEnd = end + continuationLength;
            if (hasContinuationWord && continuationLength > 0
                && value.regionMatches(true, end, term, typedLength, continuationLength)
                && (matchedEnd >= value.length()
                    || !isWordCharacter(value.charAt(matchedEnd)))) {
                end = matchedEnd;
            }
        }
        return value.substring(0, start) + term + value.substring(end);
    }

    static String insertionText(KeywordDescriptionCatalog.Entry entry) {
        return isVariableEntry(entry)
            ? entry.term()
            : entry.term().toUpperCase(Locale.ROOT);
    }

    private static Query variableQuery(
        String value,
        int caret,
        List<KeywordDescriptionCatalog.Entry> entries
    ) {
        if (caret == 0) return null;
        int opening = value.lastIndexOf(':', caret - 1);
        if (opening < 0) return null;

        // A colon that closes an effect token must not immediately reopen the menu.
        int previousColon = value.lastIndexOf(':', opening - 1);
        if (previousColon >= 0
            && isVariableBody(value.substring(previousColon + 1, opening))) {
            return null;
        }

        String fragment = value.substring(opening + 1, caret);
        if (!fragment.chars().allMatch(character -> isVariableCharacter((char) character))) {
            return null;
        }
        List<KeywordDescriptionCatalog.Entry> matches = entries.stream()
            .filter(KeywordAutocomplete::isVariableEntry)
            .filter(entry -> variableMatches(entry.term(), fragment))
            .sorted(TERM_ORDER)
            .toList();
        if (matches.isEmpty()) return null;

        int replacementEnd = caret;
        while (replacementEnd < value.length()
            && isVariableCharacter(value.charAt(replacementEnd))) {
            replacementEnd++;
        }
        if (replacementEnd < value.length() && value.charAt(replacementEnd) == ':') {
            replacementEnd++;
        }
        return new Query(opening, replacementEnd, fragment, matches, true);
    }

    private static boolean variableMatches(String token, String fragment) {
        String body = token.substring(1, token.length() - 1);
        if (startsWithIgnoreCase(body, fragment)) return true;
        int separator = body.indexOf('.');
        return separator >= 0
            && startsWithIgnoreCase(body.substring(separator + 1), fragment);
    }

    private static boolean isVariableEntry(KeywordDescriptionCatalog.Entry entry) {
        return entry != null && entry.term() != null && entry.term().length() > 2
            && entry.term().charAt(0) == ':'
            && entry.term().charAt(entry.term().length() - 1) == ':'
            && isVariableBody(entry.term().substring(1, entry.term().length() - 1));
    }

    private static boolean isVariableBody(String value) {
        if (value == null || value.isEmpty()) return false;
        int separator = value.indexOf('.');
        if (separator <= 0 || separator == value.length() - 1
            || value.indexOf('.', separator + 1) >= 0) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (index == separator) continue;
            if (!isVariableCharacter(value.charAt(index))) return false;
        }
        return true;
    }

    private static boolean isVariableCharacter(char character) {
        return Character.isLetterOrDigit(character)
            || character == '_'
            || character == '-'
            || character == '.';
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.length() >= prefix.length()
            && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean isUppercaseFragmentCharacter(char character) {
        return character == ' '
            || isUppercaseWordCharacter(character);
    }

    private static boolean isUppercaseWordCharacter(char character) {
        return Character.isUpperCase(character)
            || Character.isDigit(character)
            || character == '_'
            || character == '-';
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character)
            || character == '_'
            || character == '-';
    }

    record Query(
        int start,
        int end,
        String fragment,
        List<KeywordDescriptionCatalog.Entry> matches,
        boolean variable
    ) {
        Query {
            matches = List.copyOf(matches);
        }
    }
}
