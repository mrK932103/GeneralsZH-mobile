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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds a faction dependency closure without crossing into another selected mod.
 *
 * A mod can contain several BIG archives, so ownership is the mod folder identity rather
 * than one archive filename. A dependency found only in another selected mod is reported
 * as a cross-source dependency; it is never silently rebound to that mod's definition.
 */
final class FusionFactionDependencyPlan {
    static final class FactionPlan {
        final String factionKey;
        final String sourceMod;
        final Set<String> definitions;
        final Set<String> unresolved;
        final Set<String> crossSource;

        FactionPlan(String factionKey, String sourceMod, Set<String> definitions,
                    Set<String> unresolved, Set<String> crossSource) {
            this.factionKey = factionKey;
            this.sourceMod = sourceMod;
            this.definitions = Collections.unmodifiableSet(new LinkedHashSet<>(definitions));
            this.unresolved = Collections.unmodifiableSet(new LinkedHashSet<>(unresolved));
            this.crossSource = Collections.unmodifiableSet(new LinkedHashSet<>(crossSource));
        }

        boolean isClosed() {
            return unresolved.isEmpty() && crossSource.isEmpty();
        }
    }

    private FusionFactionDependencyPlan() {}

    static List<FactionPlan> build(FusionPreflight.Result preflight,
                                   List<FusionFactionCatalog.FactionSource> factions) {
        if (preflight == null || factions == null) return Collections.emptyList();

        Map<String, List<FusionIniIndex.Definition>> byKey = new LinkedHashMap<>();
        for (List<FusionIniIndex.Definition> group : preflight.definitions.values()) {
            for (FusionIniIndex.Definition definition : group) {
                List<FusionIniIndex.Definition> sameName = byKey.get(definition.key());
                if (sameName == null) {
                    sameName = new ArrayList<>();
                    byKey.put(definition.key(), sameName);
                }
                sameName.add(definition);
            }
        }

        Map<String, List<FusionIniReferences.Reference>> refsByOwnerAndSource =
            new LinkedHashMap<>();
        for (List<FusionIniReferences.Reference> group : preflight.references.values()) {
            for (FusionIniReferences.Reference reference : group) {
                if (reference.ownerKey == null) continue;
                String key = ownerSourceKey(reference.source, reference.ownerKey);
                List<FusionIniReferences.Reference> owned = refsByOwnerAndSource.get(key);
                if (owned == null) {
                    owned = new ArrayList<>();
                    refsByOwnerAndSource.put(key, owned);
                }
                owned.add(reference);
            }
        }

        List<FactionPlan> result = new ArrayList<>();
        for (FusionFactionCatalog.FactionSource faction : factions) {
            String sourceMod = modIdentity(faction.sourceArchive);
            Set<String> visited = new LinkedHashSet<>();
            Set<String> unresolved = new LinkedHashSet<>();
            Set<String> crossSource = new LinkedHashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.add("playertemplate:" + faction.templateName.toLowerCase(Locale.ROOT));

            while (!pending.isEmpty()) {
                String key = pending.removeFirst();
                if (!visited.add(key)) continue;
                List<FusionIniIndex.Definition> candidates = byKey.get(key);
                FusionIniIndex.Definition owner = chooseSource(candidates, sourceMod);
                if (owner == null) {
                    classifyMissing(key, candidates, sourceMod, unresolved, crossSource);
                    continue;
                }

                if (owner.parent != null && !owner.parent.trim().isEmpty()) {
                    String parentKind = "childobject".equalsIgnoreCase(owner.type)
                        ? "object" : owner.type.toLowerCase(Locale.ROOT);
                    pending.add(parentKind + ":" + owner.parent.toLowerCase(Locale.ROOT));
                }

                List<FusionIniReferences.Reference> owned =
                    refsByOwnerAndSource.get(ownerSourceKey(owner.source, owner.key()));
                if (owned == null) continue;
                for (FusionIniReferences.Reference reference : owned) {
                    pending.add(reference.key());
                }
            }

            result.add(new FactionPlan(faction.stableKey(), sourceMod, visited,
                unresolved, crossSource));
        }
        return Collections.unmodifiableList(result);
    }

    private static FusionIniIndex.Definition chooseSource(
            List<FusionIniIndex.Definition> candidates, String sourceMod) {
        if (candidates == null) return null;
        FusionIniIndex.Definition found = null;
        for (FusionIniIndex.Definition candidate : candidates) {
            if (!sourceMod.equals(modIdentity(candidate.source))) continue;
            if (found != null) return null; // Same-mod duplicate: do not guess a winner.
            found = candidate;
        }
        return found;
    }

    private static void classifyMissing(String key,
            List<FusionIniIndex.Definition> candidates, String sourceMod,
            Set<String> unresolved, Set<String> crossSource) {
        if (candidates == null || candidates.isEmpty()) {
            unresolved.add(key + " (not present in selected mod archives; may be base-game data)");
            return;
        }
        Set<String> owners = new LinkedHashSet<>();
        for (FusionIniIndex.Definition candidate : candidates) {
            owners.add(modIdentity(candidate.source));
        }
        owners.remove(sourceMod);
        if (!owners.isEmpty()) {
            crossSource.add(key + " exists only in other selected source(s): " + owners);
        } else {
            unresolved.add(key + " has duplicate definitions inside source " + sourceMod);
        }
    }

    private static String ownerSourceKey(String source, String ownerKey) {
        return source + "::" + ownerKey.toLowerCase(Locale.ROOT);
    }

    /** Source strings are emitted as <mod-name>/<archive-name>!<entry-path>. */
    static String modIdentity(String source) {
        if (source == null) return "";
        int slash = source.indexOf('/');
        int bang = source.indexOf('!');
        int end = slash >= 0 ? slash : (bang >= 0 ? bang : source.length());
        return source.substring(0, end).toLowerCase(Locale.ROOT);
    }
}
