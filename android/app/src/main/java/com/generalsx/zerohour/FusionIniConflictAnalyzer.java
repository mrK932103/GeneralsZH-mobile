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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * N-way duplicate-definition classifier. It compares all selected sources together, never
 * pairwise-patches a preferred mod, and never chooses a winner.
 */
final class FusionIniConflictAnalyzer {
    enum Status {
        IDENTICAL_EXACT,
        DIFFERENT_CONTENT,
        UNSAFE_TO_COMPARE
    }

    static final class Conflict {
        final String key;
        final Status status;
        final List<FusionIniBlockParser.Block> participants;

        Conflict(String key, Status status, List<FusionIniBlockParser.Block> participants) {
            this.key = key;
            this.status = status;
            this.participants = participants;
        }

        String describe() {
            StringBuilder out = new StringBuilder(key).append(" [").append(status).append("]: ");
            for (int i = 0; i < participants.size(); i++) {
                if (i > 0) out.append("; ");
                FusionIniBlockParser.Block block = participants.get(i);
                out.append(block.source).append(":").append(block.startLine)
                    .append("-").append(block.endLine);
            }
            return out.toString();
        }
    }

    private FusionIniConflictAnalyzer() {}

    static List<Conflict> analyze(List<FusionIniBlockParser.Block> blocks) {
        Map<String, List<FusionIniBlockParser.Block>> grouped = new LinkedHashMap<>();
        if (blocks == null) return new ArrayList<>();
        for (FusionIniBlockParser.Block block : blocks) {
            if (block == null) continue;
            List<FusionIniBlockParser.Block> group = grouped.get(block.key);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(block.key, group);
            }
            group.add(block);
        }

        List<Conflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<FusionIniBlockParser.Block>> entry : grouped.entrySet()) {
            List<FusionIniBlockParser.Block> group = entry.getValue();
            if (group.size() < 2) continue;
            boolean safe = true;
            for (FusionIniBlockParser.Block block : group) {
                if (!block.safeForComparison) {
                    safe = false;
                    break;
                }
            }
            Status status;
            if (!safe) {
                status = Status.UNSAFE_TO_COMPARE;
            } else {
                byte[] first = group.get(0).exactBytes;
                boolean identical = true;
                for (int i = 1; i < group.size(); i++) {
                    if (!Arrays.equals(first, group.get(i).exactBytes)) {
                        identical = false;
                        break;
                    }
                }
                status = identical ? Status.IDENTICAL_EXACT : Status.DIFFERENT_CONTENT;
            }
            conflicts.add(new Conflict(entry.getKey(), status, new ArrayList<>(group)));
        }
        return conflicts;
    }
}
