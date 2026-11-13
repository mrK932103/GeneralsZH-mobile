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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative structural diagnostics for End-terminated Generals INI declarations.
 *
 * This is deliberately not a full parser. It only validates scopes it can recognize and reports
 * uncertainty as a warning; it never rewrites source text or authorizes a semantic merge.
 */
final class FusionIniStructure {
    private static final Pattern DECLARATION = Pattern.compile(
        "^(Object|ChildObject|CommandSet|CommandButton|Weapon|Armor|Locomotor|FXList|"
        + "ParticleSystem|Upgrade|Science|SpecialPower|AudioEvent|CrateData|"
        + "MappedImage|PlayerTemplate|ObjectCreationList|OCL)\\s+([^\\s;]+)(?:\\s+([^\\s;]+))?\\s*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD = Pattern.compile(
        "^([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");
    private static final String[] BLOCK_FIELDS = {
        "behavior", "draw", "body", "clientupdate", "geometry",
        "conditionstate", "transitionstate"
    };
    private static final String[] BARE_BLOCKS = {
        "defaultconditionstate", "modelconditionstate", "transitionstate",
        "conditionstate", "idlestate", "movingstate", "weaponset",
        "weaponslot", "turret", "barrel", "passenger", "dock",
        "animationstate", "attackstate", "deathstate"
    };

    static final class Problem {
        final String source;
        final int line;
        final String message;

        Problem(String source, int line, String message) {
            this.source = source;
            this.line = line;
            this.message = message;
        }

        String describe() {
            return source + ":" + line + ": " + message;
        }
    }

    private FusionIniStructure() {}

    static List<Problem> scan(byte[] bytes, String source) {
        String[] lines = new String(bytes, Charset.forName("ISO-8859-1"))
            .replace("\r", "").split("\n", -1);
        List<Problem> problems = new ArrayList<>();
        int depth = 0;
        String declaration = null;
        int declarationLine = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            Matcher header = DECLARATION.matcher(line);
            if (header.matches()) {
                if (depth != 0) {
                    problems.add(new Problem(source, i + 1,
                        "A named declaration starts before the previous declaration is closed."));
                }
                declaration = header.group(1) + " " + header.group(2);
                declarationLine = i + 1;
                depth = 1;
                continue;
            }

            if ("End".equalsIgnoreCase(line)) {
                if (depth == 0) {
                    problems.add(new Problem(source, i + 1,
                        "End has no matching recognized declaration or nested block."));
                } else if (--depth == 0) {
                    declaration = null;
                    declarationLine = 0;
                }
                continue;
            }

            if (depth == 0) continue;
            Matcher field = FIELD.matcher(line);
            if (field.matches()) {
                if (opensFieldBlock(field.group(1))) depth++;
            } else if (opensBareBlock(line)) {
                depth++;
            }
        }

        if (depth > 0) {
            problems.add(new Problem(source, declarationLine,
                "Declaration or nested block is not closed by End before end of file: "
                    + (declaration == null ? "unknown block" : declaration)));
        }
        return problems;
    }

    private static boolean opensFieldBlock(String field) {
        String key = field.toLowerCase(Locale.ROOT);
        for (String candidate : BLOCK_FIELDS) {
            if (candidate.equals(key)) return true;
        }
        return false;
    }

    private static boolean opensBareBlock(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length == 0 || tokens.length > 3) return false;
        for (String candidate : BARE_BLOCKS) {
            if (candidate.equalsIgnoreCase(tokens[0])) return true;
        }
        return false;
    }

    private static String stripComment(String line) {
        int semicolon = line.indexOf(';');
        return semicolon < 0 ? line : line.substring(0, semicolon);
    }
}
