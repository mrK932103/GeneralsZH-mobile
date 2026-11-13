/*
** Command & Conquer Generals Zero Hour(tm)
** Copyright 2025 Electronic Arts Inc.
**
** This program is free software: you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation, either version 3 of the License, or
** (at your option) any later version.
*/
package com.generalsx.zerohour;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds a deterministic, source-qualified rename plan for declarations and typed references.
 *
 * This is a planning/preflight primitive only. It does not rewrite source archives and does not
 * make the native engine source-aware. A plan is safe to consume only after every reference is
 * resolved and a later byte-preserving INI rewriter has validated every edit location.
 */
final class FusionNamespacePlan {
    static final class Entry {
        final String source;
        final String type;
        final String originalName;
        final String runtimeName;
        final int line;

        Entry(String source, String type, String originalName, String runtimeName, int line) {
            this.source = source;
            this.type = type;
            this.originalName = originalName;
            this.runtimeName = runtimeName;
            this.line = line;
        }

        String sourceKey() {
            return source + "::" + normalize(type) + ":" + normalize(originalName);
        }
    }

    static final class ReferenceMapping {
        final String source;
        final String kind;
        final String originalName;
        final String runtimeName;
        final int line;
        final String ownerKey;

        ReferenceMapping(FusionIniReferences.Reference ref, String runtimeName) {
            this.source = ref.source;
            this.kind = ref.kind;
            this.originalName = ref.name;
            this.runtimeName = runtimeName;
            this.line = ref.line;
            this.ownerKey = ref.ownerKey;
        }
    }

    static final class Result {
        final List<Entry> entries;
        final List<ReferenceMapping> references;
        final Set<String> unresolved;
        final Map<String, String> sourceNames;

        Result(List<Entry> entries, List<ReferenceMapping> references,
               Set<String> unresolved, Map<String, String> sourceNames) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.references = Collections.unmodifiableList(new ArrayList<>(references));
            this.unresolved = Collections.unmodifiableSet(new LinkedHashSet<>(unresolved));
            this.sourceNames = Collections.unmodifiableMap(new LinkedHashMap<>(sourceNames));
        }

        boolean isComplete() {
            return unresolved.isEmpty();
        }

        String runtimeName(String source, String type, String originalName) {
            return sourceNames.get(source + "::" + normalize(type) + ":"
                + normalize(originalName));
        }
    }

    private FusionNamespacePlan() {}

    static Result build(List<FusionIniIndex.Definition> definitions,
                        List<FusionIniReferences.Reference> references) {
        List<FusionIniIndex.Definition> ordered = new ArrayList<>();
        if (definitions != null) ordered.addAll(definitions);
        Collections.sort(ordered, new Comparator<FusionIniIndex.Definition>() {
            @Override public int compare(FusionIniIndex.Definition a, FusionIniIndex.Definition b) {
                int source = safe(a.source).compareTo(safe(b.source));
                if (source != 0) return source;
                int type = normalize(a.type).compareTo(normalize(b.type));
                if (type != 0) return type;
                int name = normalize(a.name).compareTo(normalize(b.name));
                if (name != 0) return name;
                return Integer.compare(a.line, b.line);
            }
        });

        Map<String, List<FusionIniIndex.Definition>> byTypedName = new LinkedHashMap<>();
        Map<String, FusionIniIndex.Definition> bySourceKey = new LinkedHashMap<>();
        for (FusionIniIndex.Definition def : ordered) {
            String typed = normalize(def.type) + ":" + normalize(def.name);
            List<FusionIniIndex.Definition> candidates = byTypedName.get(typed);
            if (candidates == null) {
                candidates = new ArrayList<>();
                byTypedName.put(typed, candidates);
            }
            candidates.add(def);
            String sourceKey = safe(def.source) + "::" + typed;
            FusionIniIndex.Definition previous = bySourceKey.put(sourceKey, def);
            if (previous != null) {
                // Duplicate declarations within one source are not silently disambiguated.
                bySourceKey.put(sourceKey, null);
            }
        }

        List<Entry> entries = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> unresolved = new LinkedHashSet<>();
        Set<String> allocated = new LinkedHashSet<>();
        for (FusionIniIndex.Definition def : ordered) {
            String source = safe(def.source);
            String sourceKey = source + "::" + normalize(def.type) + ":" + normalize(def.name);
            if (bySourceKey.get(sourceKey) == null) {
                unresolved.add("Duplicate declaration in source: " + sourceKey);
                continue;
            }
            String runtime = makeRuntimeName(source, def.type, def.name);
            String unique = normalize(runtime);
            if (!allocated.add(unique)) {
                unresolved.add("Generated runtime-name collision: " + runtime);
                continue;
            }
            names.put(sourceKey, runtime);
            entries.add(new Entry(source, def.type, def.name, runtime, def.line));
        }

        List<ReferenceMapping> mapped = new ArrayList<>();
        if (references != null) {
            for (FusionIniReferences.Reference ref : references) {
                String source = safe(ref.source);
                String typed = normalize(ref.kind) + ":" + normalize(ref.name);
                String localKey = source + "::" + typed;
                String target = names.get(localKey);
                if (target == null) {
                    List<FusionIniIndex.Definition> candidates = byTypedName.get(typed);
                    if (candidates != null && candidates.size() == 1) {
                        FusionIniIndex.Definition only = candidates.get(0);
                        target = names.get(safe(only.source) + "::" + typed);
                    } else if (candidates != null && candidates.size() > 1) {
                        unresolved.add(source + ":" + ref.line + " ambiguous reference "
                            + typed + " (no definition in the same source)");
                    } else {
                        unresolved.add(source + ":" + ref.line + " missing reference " + typed);
                    }
                }
                if (target != null) mapped.add(new ReferenceMapping(ref, target));
            }
        }
        return new Result(entries, mapped, unresolved, names);
    }

    private static String makeRuntimeName(String source, String type, String original) {
        String clean = original.replaceAll("[^A-Za-z0-9_]", "_");
        String prefix = "FUS_" + shortHash(source) + "_";
        // Keep the final identifier conservative for legacy NameKey-based registries.
        int maxOriginal = Math.max(1, 63 - prefix.length());
        if (clean.length() > maxOriginal) clean = clean.substring(0, maxOriginal);
        return prefix + clean;
    }

    private static String shortHash(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(Charset.forName("UTF-8")));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 4; i++) out.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
