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

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Conservative, read-only archive inventory for a proposed Fusion set.
 * Duplicate virtual paths are reported, never silently resolved.
 */
final class FusionPreflight {
    static final class Issue {
        final boolean blocking;
        final String message;

        Issue(boolean blocking, String message) {
            this.blocking = blocking;
            this.message = message;
        }
    }

    static final class Result {
        final int archiveCount;
        final int entryCount;
        final Map<String, List<String>> pathSources;
        final Map<String, List<FusionIniIndex.Definition>> definitions;
        final Map<String, List<FusionIniReferences.Reference>> references;
        final Map<String, List<FusionIniReferences.Reference>> referencesOutsideSelection;
        final List<FusionIniConflictAnalyzer.Conflict> iniConflicts;
        final List<FusionIniBlockParser.Block> iniBlocks;
        final List<Issue> issues;

        Result(int archiveCount, int entryCount, Map<String, List<String>> pathSources,
               Map<String, List<FusionIniIndex.Definition>> definitions,
               Map<String, List<FusionIniReferences.Reference>> references,
               Map<String, List<FusionIniReferences.Reference>> referencesOutsideSelection,
               List<FusionIniConflictAnalyzer.Conflict> iniConflicts, List<Issue> issues) {
            this(archiveCount, entryCount, pathSources, definitions, references,
                referencesOutsideSelection, iniConflicts, Collections.<FusionIniBlockParser.Block>emptyList(),
                issues);
        }

        Result(int archiveCount, int entryCount, Map<String, List<String>> pathSources,
               Map<String, List<FusionIniIndex.Definition>> definitions,
               Map<String, List<FusionIniReferences.Reference>> references,
               Map<String, List<FusionIniReferences.Reference>> referencesOutsideSelection,
               List<FusionIniConflictAnalyzer.Conflict> iniConflicts,
               List<FusionIniBlockParser.Block> iniBlocks, List<Issue> issues) {
            this.archiveCount = archiveCount;
            this.entryCount = entryCount;
            this.pathSources = freezeMapOfLists(pathSources);
            this.definitions = freezeMapOfLists(definitions);
            this.references = freezeMapOfLists(references);
            this.referencesOutsideSelection = freezeMapOfLists(referencesOutsideSelection);
            this.iniConflicts = Collections.unmodifiableList(new ArrayList<>(iniConflicts));
            this.iniBlocks = Collections.unmodifiableList(new ArrayList<>(iniBlocks));
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        }

        int definitionCount() {
            int count = 0;
            for (List<FusionIniIndex.Definition> values : definitions.values()) {
                count += values.size();
            }
            return count;
        }

        int referenceCount() {
            int count = 0;
            for (List<FusionIniReferences.Reference> values : references.values()) count += values.size();
            return count;
        }

        int referenceOutsideSelectionCount() {
            int count = 0;
            for (List<FusionIniReferences.Reference> values : referencesOutsideSelection.values()) count += values.size();
            return count;
        }

        int iniConflictCount(FusionIniConflictAnalyzer.Status status) {
            int count = 0;
            for (FusionIniConflictAnalyzer.Conflict conflict : iniConflicts) {
                if (conflict.status == status) count++;
            }
            return count;
        }

        String iniConflictSummary(FusionIniConflictAnalyzer.Status status, int limit) {
            StringBuilder summary = new StringBuilder();
            int shown = 0;
            int remaining = 0;
            for (FusionIniConflictAnalyzer.Conflict conflict : iniConflicts) {
                if (conflict.status != status) continue;
                if (shown >= limit) {
                    remaining++;
                    continue;
                }
                if (summary.length() > 0) summary.append("\n");
                summary.append(conflict.describe());
                shown++;
            }
            if (remaining > 0) {
                summary.append("\n... and ").append(remaining).append(" more conflict(s)");
            }
            return summary.toString();
        }

        FusionDependencyGraph.Result traceDependencies(List<String> roots) {
            return FusionDependencyGraph.trace(this, roots);
        }

        FusionMergePlan.Result assessMergePlan() {
            return FusionMergePlan.assess(this);
        }

        /** Build a deterministic source-qualified plan for every indexed INI definition/reference. */
        FusionNamespacePlan.Result assessDefinitionNamespaces() {
            return assessDefinitionNamespaces(Collections.<String>emptySet());
        }

        /**
         * Assess source isolation, allowing cross-source lookup only for explicitly supplied
         * base-game archive identities. Selected mods must never be added to this set.
         */
        FusionNamespacePlan.Result assessDefinitionNamespaces(Set<String> baseGameSources) {
            List<FusionIniIndex.Definition> allDefinitions = new ArrayList<>();
            for (List<FusionIniIndex.Definition> values : definitions.values()) {
                allDefinitions.addAll(values);
            }
            List<FusionIniReferences.Reference> allReferences = new ArrayList<>();
            for (List<FusionIniReferences.Reference> values : references.values()) {
                allReferences.addAll(values);
            }
            return FusionNamespacePlan.build(allDefinitions, allReferences, baseGameSources);
        }

        /** Build a deterministic source-qualified plan for colliding virtual asset paths. */
        FusionAssetNamespacePlan.Result assessAssetNamespaces() {
            return FusionAssetNamespacePlan.build(pathSources);
        }

        int duplicateDefinitionCount() {
            int count = 0;
            for (List<FusionIniIndex.Definition> values : definitions.values()) {
                if (values.size() > 1) count++;
            }
            return count;
        }

        /**
         * Describe duplicate declarations with exact source locations for actionable conflict reports.
         */
        String duplicateDefinitionSummary(int limit) {
            StringBuilder summary = new StringBuilder();
            int shown = 0;
            int remaining = 0;
            for (Map.Entry<String, List<FusionIniIndex.Definition>> entry : definitions.entrySet()) {
                List<FusionIniIndex.Definition> values = entry.getValue();
                if (values.size() < 2) continue;
                if (shown >= limit) {
                    remaining++;
                    continue;
                }
                if (summary.length() > 0) summary.append("\n");
                summary.append(entry.getKey()).append(": ");
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) summary.append("; ");
                    FusionIniIndex.Definition item = values.get(i);
                    summary.append(item.source).append(":").append(item.line);
                    if (item.parent != null && !item.parent.isEmpty()) {
                        summary.append(" (parent ").append(item.parent).append(")");
                    }
                }
                shown++;
            }
            if (remaining > 0) {
                summary.append("\n... and ").append(remaining)
                    .append(" more duplicate definition key(s)");
            }
            return summary.toString();
        }

        /** Return every source archive for one normalized virtual path. */
        String pathSourceSummary(String normalizedPath) {
            List<String> owners = pathSources.get(normalizedPath);
            return owners == null || owners.isEmpty()
                ? "source archives unavailable" : String.join("; ", owners);
        }

        /** Describe duplicate virtual paths, retaining every archive source. */
        String duplicatePathSummary(int limit) {
            StringBuilder summary = new StringBuilder();
            int shown = 0;
            int remaining = 0;
            for (Map.Entry<String, List<String>> entry : pathSources.entrySet()) {
                if (entry.getValue().size() < 2) continue;
                if (shown >= limit) {
                    remaining++;
                    continue;
                }
                if (summary.length() > 0) summary.append("\n");
                summary.append(entry.getKey()).append(": ")
                    .append(String.join("; ", entry.getValue()));
                shown++;
            }
            if (remaining > 0) {
                summary.append("\n... and ").append(remaining)
                    .append(" more duplicate path(s)");
            }
            return summary.toString();
        }

        boolean canBuildArchiveOverlay() {
            for (Issue issue : issues) {
                if (issue.blocking) return false;
            }
            return true;
        }

        int duplicatePathCount() {
            int count = 0;
            for (List<String> sources : pathSources.values()) {
                if (sources.size() > 1) count++;
            }
            return count;
        }
    }

    private static <T> Map<String, List<T>> freezeMapOfLists(
            Map<String, List<T>> source) {
        Map<String, List<T>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<T>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(
                new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private FusionPreflight() {}

    /**
     * GeneralsX @feature author 13/11/2026 Inventory selected mod BIGF files without mutation.
     */
    static Result inspect(List<ModManager.Mod> selectedMods) {
        Map<String, List<String>> sources = new LinkedHashMap<>();
        Map<String, List<FusionIniIndex.Definition>> definitions = new LinkedHashMap<>();
        Map<String, List<FusionIniReferences.Reference>> references = new LinkedHashMap<>();
        Map<String, List<FusionIniReferences.Reference>> outside = new LinkedHashMap<>();
        List<Issue> issues = new ArrayList<>();
        Map<String, List<byte[]>> indexedIniDigests = new LinkedHashMap<>();
        List<FusionIniBlockParser.Block> iniBlocks = new ArrayList<>();
        int archives = 0;
        int entries = 0;

        if (selectedMods == null || selectedMods.isEmpty()) {
            issues.add(new Issue(true, "Select at least one mod."));
            return new Result(0, 0, sources, definitions, references, outside,
                new ArrayList<FusionIniConflictAnalyzer.Conflict>(),
                new ArrayList<FusionIniBlockParser.Block>(), issues);
        }

        for (ModManager.Mod mod : selectedMods) {
            if (mod == null || mod.dir == null || !mod.dir.isDirectory()
                    || Files.isSymbolicLink(mod.dir.toPath())) {
                issues.add(new Issue(true, "A selected mod folder is missing or unsafe."));
                continue;
            }
            if (containsUnsupportedGib(mod.dir, 0)) {
                issues.add(new Issue(true, "Unsupported .gib archive found in " + mod.name
                    + "; Fusion currently supports validated BIGF (.big) archives only."));
            }
            if (hasLooseGameFiles(mod.dir, 0)) {
                issues.add(new Issue(true, "Loose game files are present in " + mod.name
                    + "; Fusion currently accepts BIGF archives only and will not omit loose assets."));
            }
            List<File> files = new ArrayList<>();
            collectArchives(mod.dir, files, 0);
            if (files.isEmpty()) {
                issues.add(new Issue(true, "No BIGF archives found in " + mod.name + "."));
                continue;
            }
            for (File archiveFile : files) {
                archives++;
                try {
                    FusionBigArchive.Archive archive = FusionBigArchive.read(archiveFile);
                    for (FusionBigArchive.Entry entry : archive.entries) {
                        entries++;
                        String source = mod.name + "/" + archiveFile.getName();
                        String key = entry.normalizedPath;
                        List<String> owners = sources.get(key);
                        if (owners == null) {
                            owners = new ArrayList<>();
                            sources.put(key, owners);
                        }
                        owners.add(source);
                        if (key.startsWith("data\\ini\\") && key.endsWith(".ini")) {
                            byte[] bytes = FusionBigArchive.readEntry(archive, entry, 16 * 1024 * 1024);
                            byte[] digest = sha256(bytes);
                            List<byte[]> knownDigests = indexedIniDigests.get(key);
                            boolean alreadyIndexed = false;
                            if (knownDigests != null) {
                                for (byte[] knownDigest : knownDigests) {
                                    if (MessageDigest.isEqual(knownDigest, digest)) {
                                        alreadyIndexed = true;
                                        break;
                                    }
                                }
                            } else {
                                knownDigests = new ArrayList<>();
                                indexedIniDigests.put(key, knownDigests);
                            }
                            if (!alreadyIndexed) {
                                knownDigests.add(digest);
                                String iniSource = source + "!" + entry.path;
                                for (FusionIniIndex.Definition definition : FusionIniIndex.scan(bytes,
                                        iniSource)) {
                                    List<FusionIniIndex.Definition> matches = definitions.get(definition.key());
                                    if (matches == null) {
                                        matches = new ArrayList<>();
                                        definitions.put(definition.key(), matches);
                                    }
                                    matches.add(definition);
                                }
                                for (FusionIniReferences.Reference reference : FusionIniReferences.scan(bytes,
                                        iniSource)) {
                                    List<FusionIniReferences.Reference> refs = references.get(reference.key());
                                    if (refs == null) {
                                        refs = new ArrayList<>();
                                        references.put(reference.key(), refs);
                                    }
                                    refs.add(reference);
                                }
                                for (FusionIniStructure.Problem problem : FusionIniStructure.scan(bytes,
                                        iniSource)) {
                                    issues.add(new Issue(false, "INI structure warning: "
                                        + problem.describe()));
                                }
                                FusionIniBlockParser.Result parsedBlocks =
                                    FusionIniBlockParser.parse(bytes, iniSource);
                                for (String problem : parsedBlocks.problems) {
                                    issues.add(new Issue(false, "INI block parser warning: " + problem));
                                }
                                for (FusionIniBlockParser.Block block : parsedBlocks.blocks) {
                                    if (!block.safeForComparison) {
                                        issues.add(new Issue(false, "INI block is not eligible for "
                                            + "automatic comparison: " + block.source + ":"
                                            + block.startLine + " (" + block.key + "); "
                                            + String.join(", ", block.uncertainties)));
                                    }
                                }
                                iniBlocks.addAll(parsedBlocks.blocks);
                            }
                        }
                    }
                } catch (IOException | IllegalArgumentException e) {
                    issues.add(new Issue(true, "Cannot use " + archiveFile.getName()
                        + " from " + mod.name + ": " + e.getMessage()));
                }
            }
        }

        List<FusionIniConflictAnalyzer.Conflict> iniConflicts =
            FusionIniConflictAnalyzer.analyze(iniBlocks);
        for (FusionIniConflictAnalyzer.Conflict conflict : iniConflicts) {
            issues.add(new Issue(false, "N-way INI duplicate classification: "
                + conflict.describe()));
        }

        for (Map.Entry<String, List<String>> path : sources.entrySet()) {
            if (path.getValue().size() > 1) {
                issues.add(new Issue(false, "Duplicate virtual path: " + path.getKey()
                    + " (" + String.join(", ", path.getValue()) + ")"));
            }
        }
        for (Map.Entry<String, List<FusionIniIndex.Definition>> definition : definitions.entrySet()) {
            if (definition.getValue().size() > 1) {
                List<String> locations = new ArrayList<>();
                for (FusionIniIndex.Definition item : definition.getValue()) {
                    locations.add(item.source + ":" + item.line);
                }
                issues.add(new Issue(false, "Duplicate INI definition " + definition.getKey()
                    + " (" + String.join(", ", locations) + ")"));
            }
        }
        for (Map.Entry<String, List<FusionIniReferences.Reference>> reference : references.entrySet()) {
            if (!definitions.containsKey(reference.getKey())) {
                outside.put(reference.getKey(), reference.getValue());
            }
        }
        if (!outside.isEmpty()) {
            issues.add(new Issue(false, outside.size()
                + " typed reference(s) are not defined in the selected mods; they may be supplied by the base game or other INI files."));
        }
        return new Result(archives, entries, sources, definitions, references, outside,
            iniConflicts, iniBlocks, issues);
    }


    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }


    /** Detect loose game-layout files not represented by the BIGF-only inventory. */
    private static boolean hasLooseGameFiles(File directory, int depth) {
        if (directory == null || depth > 8 || !directory.isDirectory()
                || Files.isSymbolicLink(directory.toPath())) return false;
        File[] children = directory.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath())) continue;
            if (child.isDirectory()) {
                if (ModImportService.GAME_FOLDERS.contains(child.getName().toLowerCase(Locale.ROOT))
                        && containsLooseFile(child, 0)) return true;
                if (hasLooseGameFiles(child, depth + 1)) return true;
            }
        }
        return false;
    }

    private static boolean containsLooseFile(File directory, int depth) {
        if (depth > 24 || Files.isSymbolicLink(directory.toPath())) return false;
        File[] children = directory.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath())) continue;
            if (child.isDirectory()) {
                if (containsLooseFile(child, depth + 1)) return true;
            } else if (child.isFile()
                    && !child.getName().toLowerCase(Locale.ROOT).endsWith(".big")) {
                return true;
            }
        }
        return false;
    }

    /** Reject archive variants that are not parsed or copied by the current BIGF-only builder. */
    private static boolean containsUnsupportedGib(File directory, int depth) {
        if (directory == null || depth > 8 || !directory.isDirectory()
                || Files.isSymbolicLink(directory.toPath())) return false;
        File[] children = directory.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath())) continue;
            if (child.isDirectory()) {
                if (containsUnsupportedGib(child, depth + 1)) return true;
            } else if (child.isFile()
                    && child.getName().toLowerCase(Locale.ROOT).endsWith(".gib")) {
                return true;
            }
        }
        return false;
    }

    private static void collectArchives(File directory, List<File> output, int depth) {
        if (depth > 8 || Files.isSymbolicLink(directory.toPath())) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath())) continue;
            if (child.isDirectory()) {
                collectArchives(child, output, depth + 1);
            } else if (child.isFile()
                    && child.getName().toLowerCase(Locale.ROOT).endsWith(".big")) {
                output.add(child);
            }
        }
    }
}
