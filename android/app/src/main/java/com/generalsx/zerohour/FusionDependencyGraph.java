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
import java.util.Map;
import java.util.Set;

/**
 * Builds a conservative typed dependency graph from the declarations and references found by
 * Fusion preflight. References are scoped to recognized INI declaration blocks; lexical parsing
 * still cannot resolve macros, dynamic names, or all engine-specific semantics.
 */
final class FusionDependencyGraph {
    static final class Node {
        final String key;
        final List<FusionIniIndex.Definition> definitions;
        final List<FusionIniReferences.Reference> references;

        Node(String key, List<FusionIniIndex.Definition> definitions,
             List<FusionIniReferences.Reference> references) {
            this.key = key;
            this.definitions = Collections.unmodifiableList(definitions);
            this.references = Collections.unmodifiableList(references);
        }
    }

    static final class Result {
        final Set<String> visited;
        final Set<String> unresolved;
        final Map<String, List<String>> edges;

        Result(Set<String> visited, Set<String> unresolved, Map<String, List<String>> edges) {
            this.visited = Collections.unmodifiableSet(visited);
            this.unresolved = Collections.unmodifiableSet(unresolved);
            this.edges = Collections.unmodifiableMap(edges);
        }
    }

    private FusionDependencyGraph() {}

    /**
     * GeneralsX @feature author 13/11/2026 Build block-scoped candidate dependency closure.
     */
    static Result trace(FusionPreflight.Result preflight, List<String> roots) {
        Map<String, List<FusionIniIndex.Definition>> definitions = preflight.definitions;
        Map<String, List<FusionIniReferences.Reference>> references = preflight.references;
        Set<String> visited = new LinkedHashSet<>();
        Set<String> unresolved = new LinkedHashSet<>();
        Map<String, List<String>> edges = new LinkedHashMap<>();
        Deque<String> pending = new ArrayDeque<>();

        if (roots != null) {
            for (String root : roots) {
                if (root != null && !root.trim().isEmpty()) {
                    String key = normalizeRoot(root);
                    if (!pending.contains(key)) pending.addLast(key);
                }
            }
        }

        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (!definitions.containsKey(current)) {
                unresolved.add(current);
                continue;
            }

            List<String> outgoing = new ArrayList<>();
            for (FusionIniIndex.Definition definition : definitions.get(current)) {
                if (definition.parent != null && !definition.parent.trim().isEmpty()) {
                    String parentKind = "ChildObject".equalsIgnoreCase(definition.type)
                        ? "object" : definition.type.toLowerCase(java.util.Locale.ROOT);
                    String parentKey = parentKind + ":"
                        + definition.parent.toLowerCase(java.util.Locale.ROOT);
                    outgoing.add(parentKey);
                    if (!visited.contains(parentKey)) pending.addLast(parentKey);
                }
            }
            for (FusionIniIndex.Definition definition : definitions.get(current)) {
                for (List<FusionIniReferences.Reference> values : references.values()) {
                    for (FusionIniReferences.Reference reference : values) {
                        if (reference.ownerKey == null
                                || !reference.ownerKey.equals(definition.key())) continue;
                        String target = reference.key();
                        if (!outgoing.contains(target)) outgoing.add(target);
                        if (!visited.contains(target)) pending.addLast(target);
                    }
                }
            }
            edges.put(current, outgoing);
        }
        return new Result(visited, unresolved, edges);
    }

    static String normalizeRoot(String value) {
        String trimmed = value.trim();
        int separator = trimmed.indexOf(':');
        if (separator > 0) {
            return trimmed.substring(0, separator).toLowerCase(java.util.Locale.ROOT)
                + ":" + trimmed.substring(separator + 1).toLowerCase(java.util.Locale.ROOT);
        }
        return "object:" + trimmed.toLowerCase(java.util.Locale.ROOT);
    }
}
