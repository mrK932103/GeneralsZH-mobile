/*
**  Command & Conquer Generals Zero Hour(tm)
**  Copyright 2025 Electronic Arts Inc.
**
**  This program is free software: you can redistribute it and/or modify
**  it under the terms of the GNU General Public License as published by
**  the Free Software Foundation, either version 3 of the License, or
**  (at your option) any later version.
*/

package com.generalsx.zerohour;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only catalog of faction roots and their original source archives.
 *
 * A catalog entry is deliberately source-qualified: two PlayerTemplate blocks with the same
 * name from different mods remain two candidates. This is the first step toward source-isolated
 * faction loading; it does not claim that the engine can yet resolve their dependencies in
 * separate namespaces.
 */
final class FusionFactionCatalog {
    private static final Charset CHARSET = Charset.forName("ISO-8859-1");
    private static final Pattern ASSIGNMENT = Pattern.compile(
        "^([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");

    static final class FactionSource {
        final String templateName;
        final String sourceArchive;
        final int startLine;
        final int endLine;
        final String side;
        final String displayName;
        final boolean playable;
        final List<String> unresolved;

        FactionSource(String templateName, String sourceArchive, int startLine, int endLine,
                      String side, String displayName, boolean playable, List<String> unresolved) {
            this.templateName = templateName;
            this.sourceArchive = sourceArchive;
            this.startLine = startLine;
            this.endLine = endLine;
            this.side = side;
            this.displayName = displayName;
            this.playable = playable;
            this.unresolved = Collections.unmodifiableList(new ArrayList<>(unresolved));
        }

        String stableKey() {
            return sourceArchive + "::PlayerTemplate::" + templateName;
        }

        boolean isUsableRoot() {
            return playable && side != null && !side.isEmpty() && unresolved.isEmpty();
        }

        String describe() {
            return templateName + " [" + (displayName.isEmpty() ? templateName : displayName)
                + "] from " + sourceArchive + ":" + startLine
                + (isUsableRoot() ? " (playable root candidate)" : " (needs review)");
        }
    }

    private FusionFactionCatalog() {}

    static List<FactionSource> discover(List<FusionIniBlockParser.Block> blocks) {
        List<FactionSource> result = new ArrayList<>();
        if (blocks == null) return Collections.unmodifiableList(result);
        for (FusionIniBlockParser.Block block : blocks) {
            if (block == null || !"PlayerTemplate".equalsIgnoreCase(block.type)) continue;
            result.add(read(block));
        }
        return Collections.unmodifiableList(result);
    }

    private static FactionSource read(FusionIniBlockParser.Block block) {
        List<String> unresolved = new ArrayList<>();
        if (!block.safeForComparison) {
            unresolved.add("PlayerTemplate block is structurally unsafe to inspect.");
        }
        String side = "";
        String displayName = "";
        boolean playable = false;
        boolean sawPlayableSide = false;
        String[] lines = new String(block.copyExactBytes(), CHARSET).split("\\n", -1);
        for (int i = 1; i < lines.length - 1; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty() || line.equalsIgnoreCase("End")) continue;
            Matcher matcher = ASSIGNMENT.matcher(line);
            if (!matcher.matches()) {
                unresolved.add(block.source + ":" + (block.startLine + i)
                    + ": malformed PlayerTemplate line: " + line);
                continue;
            }
            // PlayerTemplate contains many legitimate engine fields (StartingBuilding,
            // StartingUnit0..n, IntrinsicSciences, PurchaseScienceCommandSet, colors, etc.).
            // They belong to the faction's dependency closure, but are not required to
            // identify its root. Do not reject a faction just because this catalog does not
            // interpret every engine-specific field yet.
            String field = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = unquote(matcher.group(2).trim());
            if ("side".equals(field)) {
                if (!side.isEmpty() && !side.equals(value)) {
                    unresolved.add("Conflicting Side values inside one PlayerTemplate.");
                }
                side = value;
            } else if ("displayname".equals(field)) {
                displayName = value;
            } else if ("playableside".equals(field)) {
                sawPlayableSide = true;
                playable = "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)
                    || "1".equals(value);
            }
        }
        if (side.isEmpty()) unresolved.add("Missing required Side field.");
        if (!sawPlayableSide) unresolved.add("PlayableSide is not explicitly declared.");
        return new FactionSource(block.name, block.source, block.startLine, block.endLine,
            side, displayName, playable, unresolved);
    }

    private static String stripComment(String line) {
        int semicolon = line.indexOf(';');
        int slash = line.indexOf("//");
        int cut = semicolon < 0 ? slash : (slash < 0 ? semicolon : Math.min(semicolon, slash));
        return cut < 0 ? line : line.substring(0, cut);
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
