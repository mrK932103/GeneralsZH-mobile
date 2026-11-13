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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Plans collision-free virtual asset paths for selected mod archives.
 *
 * This does not rewrite archives or prove that all references to an asset can be found and
 * rewritten. It is an inventory-backed plan that a future asset-reference rewriter/resolver
 * can consume only after every source reference has been audited.
 */
final class FusionAssetNamespacePlan {
    static final class Mapping {
        final String source;
        final String originalPath;
        final String runtimePath;
        final boolean remapped;

        Mapping(String source, String originalPath, String runtimePath, boolean remapped) {
            this.source = source;
            this.originalPath = originalPath;
            this.runtimePath = runtimePath;
            this.remapped = remapped;
        }
    }

    static final class Result {
        final List<Mapping> mappings;
        final Set<String> unresolved;
        private final Map<String, String> bySourcePath;

        Result(List<Mapping> mappings, Set<String> unresolved, Map<String, String> bySourcePath) {
            this.mappings = Collections.unmodifiableList(new ArrayList<>(mappings));
            this.unresolved = Collections.unmodifiableSet(new LinkedHashSet<>(unresolved));
            this.bySourcePath = Collections.unmodifiableMap(new LinkedHashMap<>(bySourcePath));
        }

        boolean isComplete() {
            return unresolved.isEmpty();
        }

        String runtimePath(String source, String originalPath) {
            String normalized = normalizePath(originalPath);
            return bySourcePath.get(sourceId(source) + "::" + normalized);
        }

        int remappedCount() {
            int count = 0;
            for (Mapping mapping : mappings) if (mapping.remapped) count++;
            return count;
        }
    }

    private FusionAssetNamespacePlan() {}

    /**
     * Every owner of a colliding virtual path gets a distinct path. This avoids making the
     * selected archive order decide which mod's model/texture/audio file wins.
     */
    static Result build(Map<String, List<String>> pathSources) {
        List<String> paths = new ArrayList<>();
        if (pathSources != null) paths.addAll(pathSources.keySet());
        Collections.sort(paths, String.CASE_INSENSITIVE_ORDER);

        List<Mapping> mappings = new ArrayList<>();
        Set<String> unresolved = new LinkedHashSet<>();
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> allocated = new LinkedHashSet<>();

        for (String rawPath : paths) {
            final String path;
            try {
                path = normalizePath(rawPath);
            } catch (IllegalArgumentException invalid) {
                unresolved.add("Invalid virtual asset path " + rawPath + ": " + invalid.getMessage());
                continue;
            }
            List<String> owners = pathSources.get(rawPath);
            if (owners == null || owners.isEmpty()) {
                unresolved.add("No source owner for virtual asset path: " + path);
                continue;
            }

            Set<String> uniqueOwners = new LinkedHashSet<>();
            for (String owner : owners) {
                String source = sourceId(owner);
                if (source.isEmpty()) {
                    unresolved.add("Empty source identity for asset path: " + path);
                    continue;
                }
                if (!uniqueOwners.add(source)) {
                    unresolved.add("Duplicate asset entry in the same source: " + source + " :: " + path);
                }
            }

            boolean collision = uniqueOwners.size() > 1;
            for (String source : uniqueOwners) {
                String runtime = collision ? remappedPath(source, path) : path;
                String key = source + "::" + path;
                if (!allocated.add(runtime.toLowerCase(Locale.ROOT))) {
                    unresolved.add("Generated asset path collision: " + runtime);
                    continue;
                }
                result.put(key, runtime);
                mappings.add(new Mapping(source, path, runtime, collision));
            }
        }
        return new Result(mappings, unresolved, result);
    }

    private static String remappedPath(String source, String originalPath) {
        return "__fusion\\" + shortHash(source) + "\\" + originalPath;
    }

    private static String normalizePath(String path) {
        String normalized = FusionBigArchive.normalizePath(path);
        if (normalized.isEmpty()) throw new IllegalArgumentException("empty path");
        return normalized;
    }

    private static String sourceId(String source) {
        if (source == null) return "";
        String value = source.replace('\\', '/');
        int member = value.indexOf('!');
        if (member >= 0) value = value.substring(0, member);
        return value.toLowerCase(Locale.ROOT);
    }

    private static String shortHash(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(Charset.forName("UTF-8")));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                out.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}
