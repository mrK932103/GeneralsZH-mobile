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

    private static final class Scope {
        final String name;
        final int line;
        final boolean declaration;

        Scope(String name, int line, boolean declaration) {
            this.name = name;
            this.line = line;
            this.declaration = declaration;
        }
    }

    private FusionIniStructure() {}

    static List<Problem> scan(byte[] bytes, String source) {
        String[] lines = new String(bytes, Charset.forName("ISO-8859-1"))
            .replace("\\r", "").split("\\n", -1);
        List<Problem> problems = new ArrayList<>();
        List<Scope> scopes = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            Matcher header = DECLARATION.matcher(line);
            if (header.matches()) {
                if (!scopes.isEmpty()) {
                    Scope owner = findDeclaration(scopes);
                    String ownerName = owner == null ? "recognized nested block"
                        : owner.name;
                    problems.add(new Problem(source, lineNumber,
                        "A named declaration starts before " + ownerName
                            + " is closed (opened at line "
                            + (owner == null ? scopes.get(0).line : owner.line) + ")."));
                    scopes.clear();
                }
                scopes.add(new Scope(header.group(1) + " " + header.group(2),
                    lineNumber, true));
                continue;
            }

            if ("End".equalsIgnoreCase(line)) {
                if (scopes.isEmpty()) {
                    problems.add(new Problem(source, lineNumber,
                        "End has no matching recognized declaration or nested block."));
                } else {
                    scopes.remove(scopes.size() - 1);
                }
                continue;
            }

            if (scopes.isEmpty()) continue;
            Matcher field = FIELD.matcher(line);
            if (field.matches()) {
                if (opensFieldBlock(field.group(1))) {
                    scopes.add(new Scope(field.group(1), lineNumber, false));
                }
            } else if (opensBareBlock(line)) {
                scopes.add(new Scope(line, lineNumber, false));
            }
        }

        if (!scopes.isEmpty()) {
            Scope owner = findDeclaration(scopes);
            Scope unclosed = scopes.get(scopes.size() - 1);
            String ownerName = owner == null ? "recognized INI scope" : owner.name;
            problems.add(new Problem(source, unclosed.line,
                "Unclosed " + (unclosed.declaration ? "declaration " : "nested block ")
                    + unclosed.name + " at end of file; containing scope "
                    + ownerName + " opened at line "
                    + (owner == null ? scopes.get(0).line : owner.line) + "."));
        }
        return problems;
    }

    private static Scope findDeclaration(List<Scope> scopes) {
        for (Scope scope : scopes) {
            if (scope.declaration) return scope;
        }
        return null;
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
