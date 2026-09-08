package com.jjktbf.model.text;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code *move:id*} / {@code *ability:id*} / {@code *domain:id*}
 * reference tokens in player-facing text to the referenced content's current
 * display name, so descriptions never hardcode names that drift when content
 * is renamed.
 *
 * Move, ability, and Domain IDs share the same numeric namespace, so every
 * token carries a type prefix. Unknown references are left verbatim, mirroring
 * how {@link MoveDescriptionVariables} treats unknown {@code :effect-x.y:}
 * tokens.
 */
public final class ContentNameTokens {

    public static final String MOVE_PREFIX = "move";
    public static final String ABILITY_PREFIX = "ability";
    public static final String DOMAIN_PREFIX = "domain";

    private static final String PREFIXES =
        MOVE_PREFIX + "|" + ABILITY_PREFIX + "|" + DOMAIN_PREFIX;

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "\\*(" + PREFIXES + "):([A-Za-z0-9_-]+)\\*",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_CANDIDATE = Pattern.compile(
        "\\*(" + PREFIXES + "):\\S*\\*",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern UNCLOSED_START = Pattern.compile(
        "\\*(" + PREFIXES + "):",
        Pattern.CASE_INSENSITIVE);

    private ContentNameTokens() {
    }

    /** Supplies the display name for a {@link #MOVE_PREFIX}/{@link #ABILITY_PREFIX}/{@link #DOMAIN_PREFIX} id; null when unknown. */
    @FunctionalInterface
    public interface NameLookup {
        String nameOf(String type, String id);
    }

    /** Builds a lookup over ready-made id-to-name maps; null maps resolve nothing. */
    public static NameLookup of(
        Map<String, ? extends String> moveNamesById,
        Map<String, ? extends String> abilityNamesById
    ) {
        return of(moveNamesById, abilityNamesById, null);
    }

    /** Builds a lookup over ready-made id-to-name maps; null maps resolve nothing. */
    public static NameLookup of(
        Map<String, ? extends String> moveNamesById,
        Map<String, ? extends String> abilityNamesById,
        Map<String, ? extends String> domainNamesById
    ) {
        return (type, id) -> {
            Map<String, ? extends String> names = MOVE_PREFIX.equalsIgnoreCase(type)
                ? moveNamesById
                : ABILITY_PREFIX.equalsIgnoreCase(type) ? abilityNamesById : domainNamesById;
            return names != null && id != null ? names.get(id) : null;
        };
    }

    /** Builds a lookup over two id-to-name functions (e.g. repository {@code findById} chains). */
    public static NameLookup of(
        Function<String, String> moveNameById,
        Function<String, String> abilityNameById
    ) {
        return of(moveNameById, abilityNameById, null);
    }

    /** Builds a lookup over three id-to-name functions (e.g. repository {@code findById} chains). */
    public static NameLookup of(
        Function<String, String> moveNameById,
        Function<String, String> abilityNameById,
        Function<String, String> domainNameById
    ) {
        return (type, id) -> {
            Function<String, String> names = MOVE_PREFIX.equalsIgnoreCase(type)
                ? moveNameById
                : ABILITY_PREFIX.equalsIgnoreCase(type) ? abilityNameById : domainNameById;
            return names != null && id != null ? names.apply(id) : null;
        };
    }

    /** Replaces every known reference token with the referenced content's name. */
    public static String resolve(String text, NameLookup lookup) {
        if (text == null || text.isEmpty() || lookup == null) {
            return text == null ? "" : text;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        if (!matcher.find()) return text;
        StringBuffer resolved = new StringBuffer(text.length());
        do {
            String name = lookup.nameOf(matcher.group(1).toLowerCase(java.util.Locale.ROOT),
                matcher.group(2));
            matcher.appendReplacement(resolved,
                Matcher.quoteReplacement(name == null ? matcher.group() : name));
        } while (matcher.find());
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    /**
     * Returns an editor-facing error for an unknown or unclosed reference
     * token, or null when the text references only known content.
     */
    public static String validationError(String text, NameLookup lookup) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = TOKEN_CANDIDATE.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            Matcher tokenMatcher = TOKEN_PATTERN.matcher(token);
            if (!tokenMatcher.matches()) {
                return "Malformed content reference " + token + ".";
            }
            String name = lookup == null ? null
                : lookup.nameOf(tokenMatcher.group(1).toLowerCase(java.util.Locale.ROOT),
                    tokenMatcher.group(2));
            if (name == null) {
                return "Unknown " + tokenMatcher.group(1).toLowerCase(java.util.Locale.ROOT)
                    + " reference " + token + ".";
            }
        }
        Matcher startMatcher = UNCLOSED_START.matcher(text);
        int searchFrom = 0;
        while (startMatcher.find(searchFrom)) {
            int start = startMatcher.start();
            int end = text.indexOf('*', start + 1);
            if (end < 0) {
                return "Close the content reference that starts at character "
                    + (start + 1) + ".";
            }
            String token = text.substring(start, end + 1);
            if (!TOKEN_PATTERN.matcher(token).matches()) {
                return "Malformed content reference " + token + ".";
            }
            searchFrom = end + 1;
        }
        return null;
    }
}
