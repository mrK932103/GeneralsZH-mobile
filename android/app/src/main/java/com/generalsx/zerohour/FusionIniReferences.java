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
 * Conservative scanner for typed references inside Generals Zero Hour declaration blocks.
 * The game format is End-terminated, not brace-delimited. Nested module-like assignments are
 * tracked so an End closes the innermost block before it closes its owning declaration.
 */
final class FusionIniReferences {
    private static final Pattern FIELD = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");
    private static final Pattern DECLARATION = Pattern.compile(
        "^(Object|ChildObject|CommandSet|CommandButton|Weapon|Armor|Locomotor|FXList|"
        + "ParticleSystem|Upgrade|Science|SpecialPower|AudioEvent|CrateData|"
        + "MappedImage|PlayerTemplate|ObjectCreationList|OCL)\\s+([^\\s;]+)(?:\\s+([^\\s;]+))?\\s*$",
        Pattern.CASE_INSENSITIVE);

    // Assignment forms that introduce nested End-terminated blocks.
    private static final String[] BLOCK_FIELDS = {
        "behavior", "draw", "body", "clientupdate", "geometry",
        "conditionstate", "transitionstate"
    };

    // Common bare block headers used inside Draw and other INI modules.
    // Keep this list explicit: treating every unknown bare line as a block opener
    // would misread legacy properties that use whitespace instead of '='.
    private static final String[] BARE_BLOCKS = {
        "defaultconditionstate", "modelconditionstate", "transitionstate",
        "conditionstate", "idleState", "movingstate", "weaponset",
        "weaponSlot", "turret", "barrel", "passenger", "dock",
        "animationstate", "attackstate", "deathstate",
        // Nested nugget blocks in ObjectCreationList declarations.
        "createobject", "createdebris", "applyrandomforce", "deliverpayload",
        "fireweapon", "attack",
        // Nested nugget blocks in FXList declarations.
        "sound", "rayeffect", "tracer", "particlesystem", "fxlistatbonepos",
        "lightpulse", "viewshake", "terrainscorch"
    };

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
     * GeneralsX @bugfix author 13/11/2026 Track End-terminated INI blocks instead of braces.
     */
    static List<Reference> scan(byte[] bytes, String source) {
        String[] lines = new String(bytes, Charset.forName("ISO-8859-1"))
            .replace("\r", "").split("\n", -1);
        List<Reference> result = new ArrayList<>();
        String ownerKey = null;
        int blockDepth = 0;
        List<String> blockStack = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String clean = stripComment(lines[i]).trim();
            if (clean.isEmpty() || clean.startsWith("#") || clean.startsWith("//")) continue;

            Matcher declaration = DECLARATION.matcher(clean);
            if (declaration.matches()) {
                ownerKey = declaration.group(1).toLowerCase(Locale.ROOT) + ":"
                    + declaration.group(2).toLowerCase(Locale.ROOT);
                blockDepth = 1;
                blockStack.clear();
                continue;
            }

            if (ownerKey == null) continue;
            if ("End".equalsIgnoreCase(clean)) {
                blockDepth--;
                if (blockDepth <= 0) {
                    ownerKey = null;
                    blockDepth = 0;
                    blockStack.clear();
                } else if (!blockStack.isEmpty()) {
                    blockStack.remove(blockStack.size() - 1);
                }
                continue;
            }

            Matcher field = FIELD.matcher(clean);
            if (!field.matches()) {
                if (opensBareBlock(clean)) {
                    blockDepth++;
                    blockStack.add(tokensFirst(clean).toLowerCase(Locale.ROOT));
                }
                continue;
            }

            String fieldName = field.group(1);
            String kind = referenceKind(fieldName, ownerKey, blockStack);
            if (kind != null) {
                String[] tokens = field.group(2).trim().split("\\s+");
                int first = 0;
                String fieldKey = fieldName.toLowerCase(Locale.ROOT);
                if (fieldKey.startsWith("veterancy") && tokens.length > 1) {
                    // Native parsePerVetLevel* handlers consume the first token as a
                    // VeterancyLevel and the second token as the referenced resource name.
                    first = 1;
                } else if (("Weapon".equals(kind) || "Locomotor".equals(kind))
                        && tokens.length > 1 && isSlot(tokens[0])) {
                    first = 1;
                }
                for (int token = first; token < tokens.length; token++) {
                    String value = cleanToken(tokens[token]);
                    if (isIdentifier(value)) {
                        result.add(new Reference(kind, value, source, i + 1, ownerKey));
                    }
                }
            }

            if (opensNestedBlock(fieldName, field.group(2))) {
                blockDepth++;
                blockStack.add(fieldName.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static String tokensFirst(String line) {
        int end = line.indexOf(' ');
        return end < 0 ? line : line.substring(0, end);
    }

    private static boolean opensBareBlock(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length == 0 || tokens.length > 3) return false;
        for (String candidate : BARE_BLOCKS) {
            if (candidate.equalsIgnoreCase(tokens[0])) return true;
        }
        return false;
    }

    private static boolean opensNestedBlock(String field, String value) {
        String key = field.toLowerCase(Locale.ROOT);
        for (String candidate : BLOCK_FIELDS) {
            if (candidate.trim().equals(key)) return true;
        }
        return false;
    }

    private static String referenceKind(String field, String ownerKey, List<String> blockStack) {
        String key = field.toLowerCase(Locale.ROOT);
        String nestedBlock = blockStack.isEmpty() ? "" : blockStack.get(blockStack.size() - 1);
        if (ownerKey != null && ownerKey.startsWith("fxlist:")) {
            if ("sound".equals(nestedBlock) && "name".equals(key)) return "AudioEvent";
            if ("particlesystem".equals(nestedBlock) && "name".equals(key)) return "ParticleSystem";
        }
        // ObjectCreationList nuggets retain these names and resolve them later during
        // gameplay. Their meaning is specific to OCL blocks, so don't classify generic
        // fields with the same spelling in unrelated INI declarations.
        if (ownerKey != null && ownerKey.startsWith("objectcreationlist:")) {
            if ("transport".equals(key) || "putincontainer".equals(key)
                    || "payload".equals(key) || "objectnames".equals(key)) return "Object";
            if ("particlesystem".equals(key)) return "ParticleSystem";
            if ("fxfinal".equals(key)) return "FXList";
            if ("fadesound".equals(key) || "bouncesound".equals(key)) return "AudioEvent";
        }
        if ("weapon".equals(key) || "primaryweapon".equals(key) || "secondaryweapon".equals(key)
                || "historicbonusweapon".equals(key)) return "Weapon";
        // WeaponTemplate stores these names and resolves them after INI parsing. Keep them
        // in the dependency graph even though they are not ordinary immediate typed fields.
        if ("projectileobject".equals(key) || "projectilestreamname".equals(key)) return "Object";
        if ("fireocl".equals(key) || "veterancyfireocl".equals(key)
                || "projectiledetonationocl".equals(key)
                || "veterancyprojectiledetonationocl".equals(key)) return "ObjectCreationList";
        if ("projectileexhaust".equals(key)
                || "veterancyprojectileexhaust".equals(key)) return "ParticleSystem";
        if ("firefx".equals(key) || "veterancyfirefx".equals(key)
                || "projectiledetonationfx".equals(key)
                || "veterancyprojectiledetonationfx".equals(key)) return "FXList";
        if ("firesound".equals(key)) return "AudioEvent";
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
