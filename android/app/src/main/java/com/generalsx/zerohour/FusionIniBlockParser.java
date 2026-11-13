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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loss-preserving extractor for a conservative subset of named Generals INI blocks.
 *
 * It retains each block's exact original bytes (including comments and whitespace). Any
 * unrecognized bare syntax inside a declaration makes that block unsafe for automatic merging.
 * This class is an analysis primitive only: it never rewrites or merges INI content.
 */
final class FusionIniBlockParser {
    private static final Charset CHARSET = Charset.forName("ISO-8859-1");
    private static final Pattern DECLARATION = Pattern.compile(
        "^(Object|ChildObject|CommandSet|CommandButton|Weapon|Armor|Locomotor|FXList|"
        + "ParticleSystem|Upgrade|Science|SpecialPower|AudioEvent|CrateData|"
        + "MappedImage|PlayerTemplate|ObjectCreationList|OCL)\\s+([^\\s;]+)(?:\\s+([^\\s;]+))?\\s*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD = Pattern.compile(
        "^([A-Za-z][A-Za-z0-9_]*|[1-9]|1[0-8])\\s*=\\s*(.*?)\\s*$");
    private static final String[] FIELD_BLOCKS = {
        "behavior", "draw", "body", "clientupdate", "geometry",
        "conditionstate", "transitionstate"
    };
    private static final String[] BARE_BLOCKS = {
        "defaultconditionstate", "modelconditionstate", "transitionstate",
        "conditionstate", "idlestate", "movingstate", "weaponset",
        "weaponslot", "turret", "barrel", "passenger", "dock",
        "animationstate", "attackstate", "deathstate"
    };

    static final class Block {
        final String key;
        final String type;
        final String name;
        final String parent;
        final String source;
        final int startLine;
        final int endLine;
        private final byte[] exactBytes;
        final boolean safeForComparison;
        final List<String> uncertainties;

        Block(String key, String type, String name, String parent, String source,
              int startLine, int endLine, byte[] exactBytes, boolean safeForComparison,
              List<String> uncertainties) {
            this.key = key;
            this.type = type;
            this.name = name;
            this.parent = parent;
            this.source = source;
            this.startLine = startLine;
            this.endLine = endLine;
            this.exactBytes = exactBytes.clone();
            this.safeForComparison = safeForComparison;
            this.uncertainties = Collections.unmodifiableList(
                new ArrayList<>(uncertainties));
        }

        byte[] copyExactBytes() {
            return exactBytes.clone();
        }

        boolean hasSameExactBytes(Block other) {
            return other != null && Arrays.equals(exactBytes, other.exactBytes);
        }
    }

    static final class Result {
        final List<Block> blocks;
        final List<String> problems;

        Result(List<Block> blocks, List<String> problems) {
            this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
            this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
        }
    }

    private static final class Frame {
        final String name;
        Frame(String name) { this.name = name; }
    }

    private FusionIniBlockParser() {}

    static Result parse(byte[] bytes, String source) {
        String text = new String(bytes, CHARSET);
        String[] lines = text.split("\\n", -1);
        int[] lineStarts = new int[lines.length];
        int cursor = 0;
        for (int i = 0; i < lines.length; i++) {
            lineStarts[i] = cursor;
            cursor += lines[i].length() + 1;
        }
        List<Block> blocks = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<Frame> stack = new ArrayList<>();
        List<String> uncertainty = new ArrayList<>();
        int start = -1;
        String type = null, name = null, parent = null;
        int blockEnd = -1;

        for (int i = 0; i < lines.length; i++) {
            String clean = stripComment(lines[i]).trim();
            if (clean.isEmpty() || clean.startsWith("#") || clean.startsWith("//")) continue;
            Matcher declaration = DECLARATION.matcher(clean);
            if (declaration.matches()) {
                if (start >= 0) {
                    problems.add(source + ":" + (i + 1)
                        + ": new declaration begins before " + type + " " + name + " closes");
                    blocks.add(makeBlock(type, name, parent, source, start, i - 1,
                        text, lineStarts, false, uncertainty));
                }
                type = declaration.group(1);
                name = declaration.group(2);
                parent = declaration.group(3);
                start = i;
                blockEnd = -1;
                uncertainty = new ArrayList<>();
                stack.clear();
                stack.add(new Frame(type + " " + name));
                continue;
            }
            if (start < 0) continue;

            if ("End".equalsIgnoreCase(clean)) {
                if (stack.isEmpty()) {
                    uncertainty.add("unmatched End at line " + (i + 1));
                    continue;
                }
                stack.remove(stack.size() - 1);
                if (stack.isEmpty()) {
                    blockEnd = i;
                    blocks.add(makeBlock(type, name, parent, source, start, blockEnd,
                        text, lineStarts, uncertainty.isEmpty(), uncertainty));
                    start = -1;
                    type = name = parent = null;
                }
                continue;
            }

            Matcher field = FIELD.matcher(clean);
            if (field.matches()) {
                if (isFieldBlock(field.group(1))) stack.add(new Frame(field.group(1)));
            } else if (isBareBlock(clean)) {
                stack.add(new Frame(clean));
            } else {
                uncertainty.add("unrecognized bare syntax at line " + (i + 1));
            }
        }

        if (start >= 0) {
            problems.add(source + ":" + (start + 1) + ": unclosed declaration "
                + type + " " + name);
            blocks.add(makeBlock(type, name, parent, source, start, lines.length - 1,
                text, lineStarts, false, uncertainty));
        }
        return new Result(blocks, problems);
    }

    private static Block makeBlock(String type, String name, String parent, String source,
                                   int start, int end, String originalText, int[] lineStarts,
                                   boolean safe, List<String> uncertainty) {
        int from = lineStarts[start];
        int to = from + originalText.substring(from).split("\\n", -1)[0].length();
        if (end > start) {
            int endStart = lineStarts[end];
            to = endStart + originalText.substring(endStart).split("\\n", -1)[0].length();
        }
        String key = type.toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
        String raw = originalText.substring(from, to);
        return new Block(key, type, name, parent, source, start + 1, end + 1,
            raw.getBytes(CHARSET), safe, new ArrayList<>(uncertainty));
    }

    private static boolean isFieldBlock(String field) {
        for (String candidate : FIELD_BLOCKS) {
            if (candidate.equalsIgnoreCase(field)) return true;
        }
        return false;
    }

    private static boolean isBareBlock(String line) {
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
