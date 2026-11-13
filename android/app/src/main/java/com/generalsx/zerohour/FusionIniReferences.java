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
 * Block-scoped candidate reference scanner. References are attached to the nearest recognized
 * top-level declaration, rather than to every definition in the same INI file. This remains a
 * conservative lexical scanner, not a full evaluator of Generals macros or inheritance.
 */
final class FusionIniReferences {
    private static final Pattern FIELD = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");
    private static final Pattern DECLARATION = Pattern.compile(
        "^(Object|ChildObject|CommandSet|CommandButton|Weapon|Armor|Locomotor|FXList|"
        + "ParticleSystem|Upgrade|Science|SpecialPower|AudioEvent|CrateData|"
        + "MappedImage|PlayerTemplate|ObjectCreationList|OCL)\\s+([^\\s;]+)(?:\\s+([^\\s;]+))?\\s*$",
        Pattern.CASE_INSENSITIVE);

    static final class Reference {
        final String kind;
        final String name;
        final String source;
        final int line;
        final String ownerKey;

        Reference(String kind, String name, String source, int line, String ownerKey) {
            this.kind = kind;
            this.name = name;
            this.source = source;
            this.line = line;
            this.ownerKey = ownerKey;
        }

        String key() {
            return kind.toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
        }
    }

    private FusionIniReferences() {}

    /**
     * GeneralsX @feature author 13/11/2026 Bind INI references to their containing declaration.
     */
    static List<Reference> scan(byte[] bytes, String source) {
        String[] lines = new String(bytes, Charset.forName("ISO-8859-1"))
            .replace("\r", "").split("\n", -1);
        List<Reference> result = new ArrayList<>();
        String ownerKey = null;
        String pendingKey = null;
        int depth = 0;
        for (int i = 0; i < lines.length; i++) {
            String clean = stripComment(lines[i]).trim();
            if (clean.isEmpty() || clean.startsWith("#") || clean.startsWith("//")) continue;

            if (ownerKey == null) {
                Matcher declaration = DECLARATION.matcher(clean);
                if (declaration.matches()) {
                    String type = declaration.group(1).toLowerCase(Locale.ROOT);
                    String name = declaration.group(2).toLowerCase(Locale.ROOT);
                    pendingKey = type + ":" + name;
                }
            } else {
                Matcher field = FIELD.matcher(clean);
                if (field.matches()) {
                    String kind = referenceKind(field.group(1));
                    if (kind != null) {
                        String[] tokens = field.group(2).trim().split("\\s+");
                        int first = 0;
                        if (("Weapon".equals(kind) || "Locomotor".equals(kind))
                                && tokens.length > 1 && isSlot(tokens[0])) first = 1;
                        for (int token = first; token < tokens.length; token++) {
                            String value = cleanToken(tokens[token]);
                            if (isIdentifier(value)) {
                                result.add(new Reference(kind, value, source, i + 1, ownerKey));
                            }
                        }
                    }
                }
            }

            int[] braceChange = countBraces(clean);
            int opens = braceChange[0];
            int closes = braceChange[1];
            if (ownerKey == null && pendingKey != null && opens > 0) {
                ownerKey = pendingKey;
                pendingKey = null;
                depth = opens - closes;
                if (depth <= 0) ownerKey = null;
            } else if (ownerKey != null) {
                depth += opens - closes;
                if (depth <= 0) {
                    ownerKey = null;
                    depth = 0;
                }
            }
        }
        return result;
    }

    private static int[] countBraces(String line) {
        int opens = 0, closes = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\\\' && quoted) { escaped = true; continue; }
            if (c == '"') { quoted = !quoted; continue; }
            if (!quoted && c == '{') opens++;
            else if (!quoted && c == '}') closes++;
        }
        return new int[] { opens, closes };
    }

    private static String referenceKind(String field) {
        String key = field.toLowerCase(Locale.ROOT);
        if ("weapon".equals(key) || "primaryweapon".equals(key) || "secondaryweapon".equals(key)) return "Weapon";
        if ("locomotor".equals(key)) return "Locomotor";
        if ("armor".equals(key)) return "Armor";
        if ("commandset".equals(key)) return "CommandSet";
        if ("commandbutton".equals(key)) return "CommandButton";
        if ("fx".equals(key) || "fxlist".equals(key)) return "FXList";
        if ("ocl".equals(key) || "objectcreationlist".equals(key)) return "ObjectCreationList";
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
