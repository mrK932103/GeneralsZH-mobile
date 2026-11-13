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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only N-way analysis of CommandSet slot assignments and CommandButton references.
 * Does not rewrite command sets or resolve duplicate button definitions.
 */
final class FusionCommandUiAnalyzer {
    private static final Charset CHARSET = Charset.forName("ISO-8859-1");
    private static final Pattern SLOT = Pattern.compile("^([1-9]|1[0-8])\\s*=\\s*(\\S+)\\s*$");

    static final class SlotValue {
        final int slot;
        final String button;
        final String source;
        final int line;

        SlotValue(int slot, String button, String source, int line) {
            this.slot = slot;
            this.button = button;
            this.source = source;
            this.line = line;
        }
    }

    static final class SlotConflict {
        final int slot;
        final List<SlotValue> values;

        SlotConflict(int slot, List<SlotValue> values) {
            this.slot = slot;
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    static final class Assessment {
        final String setName;
        final List<SlotValue> assignments;
        final List<SlotConflict> conflicts;
        final List<String> unresolved;

        Assessment(String setName, List<SlotValue> assignments,
                   List<SlotConflict> conflicts, List<String> unresolved) {
            this.setName = setName;
            this.assignments = Collections.unmodifiableList(new ArrayList<>(assignments));
            this.conflicts = Collections.unmodifiableList(new ArrayList<>(conflicts));
            this.unresolved = Collections.unmodifiableList(new ArrayList<>(unresolved));
        }

        boolean hasSlotConflicts() { return !conflicts.isEmpty(); }
        boolean isFullyComparable() { return unresolved.isEmpty(); }
    }

    private FusionCommandUiAnalyzer() {}

    static Assessment analyze(List<FusionIniBlockParser.Block> setBlocks,
                              List<FusionIniBlockParser.Block> allBlocks) {
        if (setBlocks == null || setBlocks.isEmpty()) {
            return new Assessment("", Collections.<SlotValue>emptyList(),
                Collections.<SlotConflict>emptyList(), Collections.<String>emptyList());
        }
        String name = setBlocks.get(0).name;
        List<String> unresolved = new ArrayList<>();
        Map<Integer, List<SlotValue>> slots = new LinkedHashMap<>();
        Set<String> buttonNames = new LinkedHashSet<>();
        if (allBlocks == null || allBlocks.isEmpty()) {
            unresolved.add("The selected-mod CommandButton inventory is unavailable; references cannot be verified.");
        }
        if (allBlocks != null) {
            for (FusionIniBlockParser.Block block : allBlocks) {
                if (block != null && "CommandButton".equalsIgnoreCase(block.type)) {
                    buttonNames.add(block.name.toLowerCase(Locale.ROOT));
                }
            }
        }

        for (FusionIniBlockParser.Block block : setBlocks) {
            Set<Integer> seenSlotsInBlock = new LinkedHashSet<>();
            if (block == null || !"CommandSet".equalsIgnoreCase(block.type)
                    || !name.equalsIgnoreCase(block.name)) {
                unresolved.add("Mixed or non-CommandSet declaration supplied.");
                continue;
            }
            if (!block.safeForComparison) {
                unresolved.add(block.source + ": CommandSet block is unsafe to compare.");
                continue;
            }
            String[] lines = new String(block.copyExactBytes(), CHARSET).split("\\n", -1);
            for (int i = 1; i < lines.length - 1; i++) {
                String line = stripComment(lines[i]).trim();
                if (line.isEmpty() || line.equalsIgnoreCase("End")) continue;
                Matcher matcher = SLOT.matcher(line);
                if (!matcher.matches()) {
                    unresolved.add(block.source + ":" + (block.startLine + i)
                        + ": unsupported CommandSet directive: " + line);
                    continue;
                }
                int slot = Integer.parseInt(matcher.group(1));
                if (!seenSlotsInBlock.add(slot)) {
                    unresolved.add(block.source + ":" + (block.startLine + i)
                        + ": CommandSet slot " + slot
                        + " is assigned more than once in one declaration; engine load order overwrites earlier values.");
                }
                String button = matcher.group(2);
                SlotValue value = new SlotValue(slot, button, block.source, block.startLine + i);
                List<SlotValue> values = slots.get(slot);
                if (values == null) {
                    values = new ArrayList<>();
                    slots.put(slot, values);
                }
                values.add(value);
                if (!buttonNames.contains(button.toLowerCase(Locale.ROOT))) {
                    unresolved.add(block.source + ":" + value.line
                        + ": CommandSet slot " + slot + " references missing CommandButton " + button);
                }
            }
        }

        List<SlotValue> assignments = new ArrayList<>();
        List<SlotConflict> conflicts = new ArrayList<>();
        for (Map.Entry<Integer, List<SlotValue>> entry : slots.entrySet()) {
            List<SlotValue> values = entry.getValue();
            assignments.addAll(values);
            String first = values.get(0).button;
            boolean differs = false;
            for (int i = 1; i < values.size(); i++) {
                if (!first.equalsIgnoreCase(values.get(i).button)) {
                    differs = true;
                    break;
                }
            }
            if (differs) conflicts.add(new SlotConflict(entry.getKey(), values));
        }
        return new Assessment(name, assignments, conflicts, unresolved);
    }

    private static String stripComment(String line) {
        int semicolon = line.indexOf(';');
        return semicolon < 0 ? line : line.substring(0, semicolon);
    }
}
