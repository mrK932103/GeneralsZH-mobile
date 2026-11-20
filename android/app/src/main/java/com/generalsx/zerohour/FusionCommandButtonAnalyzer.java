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
 * Read-only CommandButton field comparison based on the native engine field-parse table.
 * Unknown fields and duplicate assignments remain unresolved; this class never rewrites INI.
 */
final class FusionCommandButtonAnalyzer {
    private static final Charset CHARSET = Charset.forName("ISO-8859-1");
    private static final Pattern ASSIGNMENT = Pattern.compile(
        "^([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");
    private static final String[] ENGINE_FIELDS = {
        "Command", "Options", "Object", "Upgrade", "WeaponSlot", "MaxShotsToFire",
        "Science", "SpecialPower", "TextLabel", "DescriptLabel", "PurchasedLabel",
        "ConflictingLabel", "ButtonImage", "CursorName", "InvalidCursorName",
        "ButtonBorderType", "RadiusCursorType", "UnitSpecificSound"
    };

    static final class Participant {
        final String source;
        final int line;
        final String value;

        Participant(String source, int line, String value) {
            this.source = source;
            this.line = line;
            this.value = value;
        }
    }

    static final class FieldConflict {
        final String field;
        final List<Participant> participants;

        FieldConflict(String field, List<Participant> participants) {
            this.field = field;
            this.participants = Collections.unmodifiableList(new ArrayList<>(participants));
        }
    }

    static final class Assessment {
        final String buttonName;
        final List<FieldConflict> conflicts;
        final List<String> unresolved;

        Assessment(String buttonName, List<FieldConflict> conflicts, List<String> unresolved) {
            this.buttonName = buttonName;
            this.conflicts = Collections.unmodifiableList(new ArrayList<>(conflicts));
            this.unresolved = Collections.unmodifiableList(new ArrayList<>(unresolved));
        }

        boolean isFullyComparable() { return unresolved.isEmpty(); }
    }

    private static final class Value {
        final String text;
        final Participant participant;
        Value(String text, Participant participant) {
            this.text = text;
            this.participant = participant;
        }
    }

    private FusionCommandButtonAnalyzer() {}

    static Assessment analyze(List<FusionIniBlockParser.Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new Assessment("", Collections.<FieldConflict>emptyList(),
                Collections.<String>emptyList());
        }
        String name = blocks.get(0).name;
        Map<String, List<Value>> fields = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        for (FusionIniBlockParser.Block block : blocks) {
            if (block == null || !"CommandButton".equalsIgnoreCase(block.type)
                    || !name.equalsIgnoreCase(block.name)) {
                unresolved.add("Mixed or non-CommandButton declaration supplied.");
                continue;
            }
            if (!block.safeForComparison) {
                unresolved.add(block.source + ": CommandButton block is structurally unsafe.");
                continue;
            }
            Map<String, Integer> perBlockCounts = new LinkedHashMap<>();
            String[] lines = new String(block.copyExactBytes(), CHARSET).split("\\n", -1);
            for (int i = 1; i < lines.length - 1; i++) {
                String line = stripComment(lines[i]).trim();
                if (line.isEmpty() || line.equalsIgnoreCase("End")) continue;
                Matcher matcher = ASSIGNMENT.matcher(line);
                if (!matcher.matches()) {
                    unresolved.add(block.source + ":" + (block.startLine + i)
                        + ": unsupported CommandButton syntax: " + line);
                    continue;
                }
                String field = matcher.group(1);
                if (!isEngineField(field)) {
                    unresolved.add(block.source + ":" + (block.startLine + i)
                        + ": field is not in the engine field-parse table: " + field);
                    continue;
                }
                String key = field.toLowerCase(Locale.ROOT);
                Integer count = perBlockCounts.get(key);
                perBlockCounts.put(key, count == null ? 1 : count + 1);
                if (perBlockCounts.get(key) > 1) {
                    unresolved.add(block.source + ":" + (block.startLine + i)
                        + ": repeated field assignment requires engine-order review: " + field);
                }
                List<Value> values = fields.get(key);
                if (values == null) {
                    values = new ArrayList<>();
                    fields.put(key, values);
                }
                String value = matcher.group(2).trim();
                values.add(new Value(value,
                    new Participant(block.source, block.startLine + i, value)));
            }
        }

        List<FieldConflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<Value>> entry : fields.entrySet()) {
            List<Value> values = entry.getValue();
            String first = values.get(0).text;
            boolean differs = false;
            for (int i = 1; i < values.size(); i++) {
                if (!first.equals(values.get(i).text)) {
                    differs = true;
                    break;
                }
            }
            if (differs) {
                List<Participant> participants = new ArrayList<>();
                for (Value value : values) participants.add(value.participant);
                conflicts.add(new FieldConflict(entry.getKey(), participants));
            }
        }
        return new Assessment(name, conflicts, unresolved);
    }

    private static boolean isEngineField(String field) {
        for (String candidate : ENGINE_FIELDS) {
            if (candidate.equalsIgnoreCase(field)) return true;
        }
        return false;
    }

    private static String stripComment(String line) {
        int semicolon = line.indexOf(';');
        return semicolon < 0 ? line : line.substring(0, semicolon);
    }
}
