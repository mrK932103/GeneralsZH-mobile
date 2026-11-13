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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provenance-preserving N-way merge assessment.
 *
 * This is deliberately a planning/report model, not a merger. Every collision remains unresolved
 * until a domain-specific rule has been implemented and validated against the engine.
 */
final class FusionMergePlan {
    enum Domain {
        FACTION_REGISTRY,
        COMMAND_UI,
        OBJECT_DEFINITION,
        COMBAT_AND_BEHAVIOR,
        GLOBAL_DEFINITION,
        ARCHIVE_FILE
    }

    enum Resolution {
        EXACT_DUPLICATE_CONTEXT_REVIEW,
        FIELD_LEVEL_MERGE_REQUIRED,
        MANUAL_PARSER_OR_ADAPTER_REQUIRED,
        FILE_CONTENT_VERIFICATION_REQUIRED
    }

    static final class Participant {
        final String source;
        final int startLine;
        final int endLine;

        Participant(String source, int startLine, int endLine) {
            this.source = source;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }

    static final class Item {
        final String key;
        final Domain domain;
        final Resolution resolution;
        final FusionIniConflictAnalyzer.Status comparison;
        final List<Participant> participants;
        final FusionPlayerTemplateAnalyzer.Assessment factionAssessment;
        final FusionCommandUiAnalyzer.Assessment commandUiAssessment;
        final FusionCommandButtonAnalyzer.Assessment commandButtonAssessment;

        Item(String key, Domain domain, Resolution resolution,
             FusionIniConflictAnalyzer.Status comparison, List<Participant> participants,
             FusionPlayerTemplateAnalyzer.Assessment factionAssessment,
             FusionCommandUiAnalyzer.Assessment commandUiAssessment,
             FusionCommandButtonAnalyzer.Assessment commandButtonAssessment) {
            this.key = key;
            this.domain = domain;
            this.resolution = resolution;
            this.comparison = comparison;
            this.participants = Collections.unmodifiableList(new ArrayList<>(participants));
            this.factionAssessment = factionAssessment;
            this.commandUiAssessment = commandUiAssessment;
            this.commandButtonAssessment = commandButtonAssessment;
        }
    }

    static final class Result {
        final List<Item> items;
        final List<String> blockingReasons;
        final int selectedArchiveCount;
        final int selectedEntryCount;

        Result(List<Item> items, List<String> blockingReasons,
               int selectedArchiveCount, int selectedEntryCount) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.blockingReasons = Collections.unmodifiableList(
                new ArrayList<>(blockingReasons));
            this.selectedArchiveCount = selectedArchiveCount;
            this.selectedEntryCount = selectedEntryCount;
        }

        int count(Domain domain) {
            int result = 0;
            for (Item item : items) if (item.domain == domain) result++;
            return result;
        }

        int unresolvedCount() {
            return items.size();
        }

        /**
         * A clean inventory is not proof of a playable merge. Domain rules, faction/UI validation,
         * loose-asset handling and engine-level validation are not complete yet.
         */
        boolean canProducePlayablePackage() {
            return false;
        }

        String summary(int limit) {
            StringBuilder out = new StringBuilder();
            int shown = 0;
            for (Item item : items) {
                if (shown >= limit) break;
                if (out.length() > 0) out.append("\n");
                out.append(item.domain).append(" / ").append(item.key)
                    .append(" -> ").append(item.resolution)
                    .append(" (").append(item.participants.size()).append(" source(s))");
                if (item.commandUiAssessment != null) {
                    for (FusionCommandUiAnalyzer.SlotConflict slot
                            : item.commandUiAssessment.conflicts) {
                        out.append("\n  CommandSet slot ").append(slot.slot)
                            .append(" conflict (").append(slot.values.size()).append(" source(s))");
                    }
                    for (String unresolved : item.commandUiAssessment.unresolved) {
                        out.append("\n  command UI review: ").append(unresolved);
                    }
                }
                if (item.commandButtonAssessment != null) {
                    for (FusionCommandButtonAnalyzer.FieldConflict field
                            : item.commandButtonAssessment.conflicts) {
                        out.append("\n  CommandButton field conflict: ").append(field.field)
                            .append(" (").append(field.participants.size()).append(" source(s))");
                    }
                    for (String unresolved : item.commandButtonAssessment.unresolved) {
                        out.append("\n  CommandButton review: ").append(unresolved);
                    }
                }
                if (item.factionAssessment != null) {
                    for (FusionPlayerTemplateAnalyzer.FieldConflict field
                            : item.factionAssessment.conflicts) {
                        out.append("\n  field conflict: ").append(field.field)
                            .append(" (").append(field.participants.size()).append(" value(s))");
                    }
                    for (String unresolved : item.factionAssessment.unresolved) {
                        out.append("\n  faction review: ").append(unresolved);
                    }
                }
                shown++;
            }
            if (items.size() > shown) {
                out.append("\n... and ").append(items.size() - shown).append(" more item(s)");
            }
            for (String reason : blockingReasons) {
                if (out.length() > 0) out.append("\n");
                out.append("BLOCKED: ").append(reason);
            }
            if (out.length() > 0) out.append("\n");
            out.append("Playable package: not enabled; semantic merge and engine validation are incomplete.");
            return out.toString();
        }
    }

    private FusionMergePlan() {}

    static Result assess(FusionPreflight.Result preflight) {
        List<Item> items = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        if (preflight == null) {
            blockers.add("Preflight result is missing.");
            return new Result(items, blockers, 0, 0);
        }

        for (FusionIniConflictAnalyzer.Conflict conflict : preflight.iniConflicts) {
            List<Participant> participants = new ArrayList<>();
            for (FusionIniBlockParser.Block block : conflict.participants) {
                participants.add(new Participant(block.source, block.startLine, block.endLine));
            }
            Domain domain = domainFor(conflict.participants.isEmpty()
                ? "" : conflict.participants.get(0).type);
            FusionPlayerTemplateAnalyzer.Assessment factionAssessment = null;
            FusionCommandUiAnalyzer.Assessment commandUiAssessment = null;
            FusionCommandButtonAnalyzer.Assessment commandButtonAssessment = null;
            if (domain == Domain.FACTION_REGISTRY) {
                factionAssessment = FusionPlayerTemplateAnalyzer.analyze(conflict.participants);
            } else if (domain == Domain.COMMAND_UI && !conflict.participants.isEmpty()) {
                if ("CommandSet".equalsIgnoreCase(conflict.participants.get(0).type)) {
                    commandUiAssessment = FusionCommandUiAnalyzer.analyze(
                        conflict.participants, preflight.iniBlocks);
                } else if ("CommandButton".equalsIgnoreCase(conflict.participants.get(0).type)) {
                    commandButtonAssessment = FusionCommandButtonAnalyzer.analyze(
                        conflict.participants);
                }
            }
            Resolution resolution;
            if (conflict.status == FusionIniConflictAnalyzer.Status.UNSAFE_TO_COMPARE) {
                resolution = Resolution.MANUAL_PARSER_OR_ADAPTER_REQUIRED;
            } else if (conflict.status == FusionIniConflictAnalyzer.Status.IDENTICAL_EXACT) {
                resolution = Resolution.EXACT_DUPLICATE_CONTEXT_REVIEW;
            } else if (domain == Domain.FACTION_REGISTRY
                    || domain == Domain.COMMAND_UI
                    || domain == Domain.GLOBAL_DEFINITION) {
                resolution = Resolution.FIELD_LEVEL_MERGE_REQUIRED;
            } else {
                resolution = Resolution.MANUAL_PARSER_OR_ADAPTER_REQUIRED;
            }
            items.add(new Item(conflict.key, domain, resolution, conflict.status, participants,
                factionAssessment, commandUiAssessment, commandButtonAssessment));
        }

        for (Map.Entry<String, List<String>> entry : preflight.pathSources.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            List<Participant> participants = new ArrayList<>();
            for (String source : entry.getValue()) {
                participants.add(new Participant(source, -1, -1));
            }
            items.add(new Item(entry.getKey(), Domain.ARCHIVE_FILE,
                Resolution.FILE_CONTENT_VERIFICATION_REQUIRED, null, participants, null, null));
        }

        for (FusionPreflight.Issue issue : preflight.issues) {
            if (issue.blocking) blockers.add(issue.message);
        }
        return new Result(items, blockers, preflight.archiveCount, preflight.entryCount);
    }

    private static Domain domainFor(String declarationType) {
        String type = declarationType == null
            ? "" : declarationType.toLowerCase(Locale.ROOT);
        if ("playertemplate".equals(type)) return Domain.FACTION_REGISTRY;
        if ("commandset".equals(type) || "commandbutton".equals(type)
                || "mappedimage".equals(type)) return Domain.COMMAND_UI;
        if ("object".equals(type) || "childobject".equals(type)) {
            return Domain.OBJECT_DEFINITION;
        }
        if ("weapon".equals(type) || "armor".equals(type) || "locomotor".equals(type)
                || "fxlist".equals(type) || "particlesystem".equals(type)
                || "objectcreationlist".equals(type) || "ocl".equals(type)) {
            return Domain.COMBAT_AND_BEHAVIOR;
        }
        return Domain.GLOBAL_DEFINITION;
    }
}
