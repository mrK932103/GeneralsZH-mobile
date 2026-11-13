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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic reference scanner for common Generals INI fields. It reports candidates only:
 * engine macros, inherited definitions, and references supplied by the base game are not
 * distinguishable without a complete semantic parser and base-game index.
 */
final class FusionIniReferences {
    private static final Pattern FIELD = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");

    static final class Reference {
        final String kind;
        final String name;
        final String source;
        final int line;

        Reference(String kind, String name, String source, int line) {
            this.kind = kind;
            this.name = name;
            this.source = source;
            this.line = line;
        }

        String key() {
            return kind.toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
        }
    }

    private FusionIniReferences() {}

    /**
     * GeneralsX @feature author 13/11/2026 Index common typed references from INI assignments.
     */
    static List<Reference> scan(byte[] bytes, String source) {
        String text = new String(bytes, java.nio.charset.Charset.forName("ISO-8859-1"));
        String[] lines = text.replace("\r", "").split("\n", -1);
        List<Reference> result = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
            Matcher matcher = FIELD.matcher(line);
            if (!matcher.matches()) continue;
            String kind = referenceKind(matcher.group(1));
            if (kind == null) continue;
            String[] tokens = matcher.group(2).trim().split("\\s+");
            if (tokens.length == 0) continue;
            int first = 0;
            if (("Weapon".equals(kind) || "Locomotor".equals(kind))
                    && tokens.length > 1 && isSlot(tokens[0])) {
                first = 1;
            }
            for (int token = first; token < tokens.length; token++) {
                String value = cleanToken(tokens[token]);
                if (isIdentifier(value)) {
                    result.add(new Reference(kind, value, source, i + 1));
                }
            }
        }
        return result;
    }

    private static String referenceKind(String field) {
        String key = field.toLowerCase(Locale.ROOT);
        if ("weapon".equals(key) || "primaryweapon".equals(key) || "secondaryweapon".equals(key)) return "Weapon";
        if ("locomotor".equals(key)) return "Locomotor";
        if ("armor".equals(key)) return "Armor";
        if ("commandset".equals(key)) return "CommandSet";
        if ("commandbutton".equals(key)) return "CommandButton";
        if ("fx".equals(key) || "fxlist".equals(key)) return "FXList";
        if ("ocl".equals(key) || "objectcreationlist".equals(key)) return "OCL";
        if ("particle".equals(key) || "particlesystem".equals(key)) return "ParticleSystem";
        if ("sound".equals(key) || "soundevent".equals(key)) return "AudioEvent";
        if ("upgrade".equals(key)) return "Upgrade";
        if ("science".equals(key)) return "Science";
        if ("specialpower".equals(key)) return "SpecialPower";
        if ("object".equals(key) || "objectname".equals(key)) return "Object";
        return null;
    }

    private static boolean isSlot(String value) {
        String v = value.toUpperCase(Locale.ROOT);
        return "PRIMARY".equals(v) || "SECONDARY".equals(v) || "TERTIARY".equals(v)
            || "SET_NORMAL".equals(v) || "SET_NORMAL_UPGRADED".equals(v)
            || "SET_SLUGS".equals(v) || "SET_TURRET".equals(v);
    }

    private static String cleanToken(String token) {
        return token.replaceAll("^[\\[\\(\\{\\\"']+|[\\]\\)\\}\\\"',;]+$", "");
    }

    private static boolean isIdentifier(String value) {
        if (value.length() < 2 || value.length() > 128) return false;
        if (value.startsWith("$") || value.startsWith("%") || value.startsWith("@")) return false;
        if ("NONE".equalsIgnoreCase(value) || "NULL".equalsIgnoreCase(value)
                || "YES".equalsIgnoreCase(value) || "NO".equalsIgnoreCase(value)) return false;
        return value.matches("[A-Za-z_][A-Za-z0-9_:.\\-]*");
    }

    private static String stripComment(String line) {
        int semicolon = line.indexOf(';');
        return semicolon < 0 ? line : line.substring(0, semicolon);
    }
}
