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
 * Read-only field comparison for PlayerTemplate declarations.
 *
 * Only engine-confirmed scalar fields are compared. Custom directives, unknown fields and
 * nested syntax are reported as unresolved rather than guessed. This class does not emit INI.
 */
final class FusionPlayerTemplateAnalyzer {
    private static final Charset CHARSET = Charset.forName("ISO-8859-1");
    private static final Pattern ASSIGNMENT = Pattern.compile(
        "^([A-Za-z][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");
    private static final String[] SCALAR_FIELDS = {
        "Side", "BaseSide", "PlayableSide", "DisplayName", "StartMoney",
        "PreferredColor", "StartingBuilding",
        "StartingUnit0", "StartingUnit1", "StartingUnit2", "StartingUnit3",
        "StartingUnit4", "StartingUnit5", "StartingUnit6", "StartingUnit7",
        "StartingUnit8", "StartingUnit9", "IntrinsicSciences",
        "PurchaseScienceCommandSetRank1", "PurchaseScienceCommandSetRank3",
        "PurchaseScienceCommandSetRank8", "SpecialPowerShortcutCommandSet",
        "SpecialPowerShortcutWinName", "SpecialPowerShortcutButtonCount",
        "IsObserver", "OldFaction", "IntrinsicSciencePurchasePoints",
        "ScoreScreenImage", "LoadScreenImage", "LoadScreenMusic", "ScoreScreenMusic",
        "HeadWaterMark", "FlagWaterMark", "EnabledImage", "SideIconImage",
        "GeneralImage", "BeaconName", "ArmyTooltip", "Features",
        "MedallionRegular", "MedallionHilite", "MedallionSelect"
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
        final String templateName;
        final List<FieldConflict> conflicts;
        final List<String> unresolved;

        Assessment(String templateName, List<FieldConflict> conflicts, List<String> unresolved) {
            this.templateName = templateName;
            this.conflicts = Collections.unmodifiableList(new ArrayList<>(conflicts));
            this.unresolved = Collections.unmodifiableList(new ArrayList<>(unresolved));
        }

        boolean isFullyComparable() {
            return unresolved.isEmpty();
        }
    }

    private static final class FieldValue {
        final String value;
        final Participant participant;

        FieldValue(String value, Participant participant) {
            this.value = value;
            this.participant = participant;
        }
    }

    private FusionPlayerTemplateAnalyzer() {}

    static Assessment analyze(List<FusionIniBlockParser.Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new Assessment("", Collections.<FieldConflict>emptyList(),
                Collections.<String>emptyList());
        }
        String templateName = blocks.get(0).name;
        Map<String, List<FieldValue>> values = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();

        for (FusionIniBlockParser.Block block : blocks) {
            if (block == null || !"PlayerTemplate".equalsIgnoreCase(block.type)) {
                unresolved.add("A non-PlayerTemplate declaration was supplied.");
                continue;
            }
            if (!templateName.equalsIgnoreCase(block.name)) {
                unresolved.add("Mixed PlayerTemplate names were supplied: " + block.name);
                continue;
            }
            if (!block.safeForComparison) {
                unresolved.add(block.source + ": block is structurally unsafe to compare.");
                continue;
            }
            String[] lines = new String(block.copyExactBytes(), CHARSET).split("\\n", -1);
            for (int i = 1; i < lines.length - 1; i++) {
                String line = stripComment(lines[i]).trim();
                if (line.isEmpty() || line.equalsIgnoreCase("End")) continue;
                Matcher matcher = ASSIGNMENT.matcher(line);
                if (!matcher.matches()) {
                    unresolved.add(block.source + ":" + (block.startLine + i)
                        + ": unsupported PlayerTemplate directive: " + line);
                    continue;
                }
                String field = matcher.group(1);
                if (!isScalarField(field)) {
                    unresolved.add(block.source + ":" + (block.startLine + i)
                        + ": field is not in the engine-confirmed scalar allowlist: " + field);
                    continue;
                }
                String key = field.toLowerCase(Locale.ROOT);
                List<FieldValue> fieldValues = values.get(key);
                if (fieldValues == null) {
                    fieldValues = new ArrayList<>();
                    values.put(key, fieldValues);
                }
                fieldValues.add(new FieldValue(matcher.group(2).trim(),
                    new Participant(block.source, block.startLine + i, matcher.group(2).trim())));
            }
        }

        List<FieldConflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<FieldValue>> entry : values.entrySet()) {
            List<FieldValue> fieldValues = entry.getValue();
            boolean differs = false;
            String first = fieldValues.get(0).value;
            for (int i = 1; i < fieldValues.size(); i++) {
                if (!first.equals(fieldValues.get(i).value)) {
                    differs = true;
                    break;
                }
            }
            if (differs) {
                List<Participant> participants = new ArrayList<>();
                for (FieldValue value : fieldValues) participants.add(value.participant);
                conflicts.add(new FieldConflict(entry.getKey(), participants));
            }
        }
        return new Assessment(templateName, conflicts, unresolved);
    }

    private static boolean isScalarField(String field) {
        for (String candidate : SCALAR_FIELDS) {
            if (candidate.equalsIgnoreCase(field)) return true;
        }
        return false;
    }

    private static String stripComment(String line) {
        int semicolon = line.indexOf(';');
        return semicolon < 0 ? line : line.substring(0, semicolon);
    }
}
