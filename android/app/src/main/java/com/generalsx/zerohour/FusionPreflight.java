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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        final List<Issue> issues;

        Result(int archiveCount, int entryCount, Map<String, List<String>> pathSources,
               Map<String, List<FusionIniIndex.Definition>> definitions, List<Issue> issues) {
            this.archiveCount = archiveCount;
            this.entryCount = entryCount;
            this.pathSources = Collections.unmodifiableMap(pathSources);
            this.definitions = Collections.unmodifiableMap(definitions);
            this.issues = Collections.unmodifiableList(issues);
        }

        int definitionCount() {
            int count = 0;
            for (List<FusionIniIndex.Definition> values : definitions.values()) {
                count += values.size();
            }
            return count;
        }

        int duplicateDefinitionCount() {
            int count = 0;
            for (List<FusionIniIndex.Definition> values : definitions.values()) {
                if (values.size() > 1) count++;
            }
            return count;
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

    private FusionPreflight() {}

    /**
     * GeneralsX @feature author 13/11/2026 Inventory selected mod BIGF files without mutation.
     */
    static Result inspect(List<ModManager.Mod> selectedMods) {
        Map<String, List<String>> sources = new LinkedHashMap<>();
        Map<String, List<FusionIniIndex.Definition>> definitions = new LinkedHashMap<>();
        List<Issue> issues = new ArrayList<>();
        int archives = 0;
        int entries = 0;

        if (selectedMods == null || selectedMods.isEmpty()) {
            issues.add(new Issue(true, "Select at least one mod."));
            return new Result(0, 0, sources, definitions, issues);
        }

        for (ModManager.Mod mod : selectedMods) {
            if (mod == null || mod.dir == null || !mod.dir.isDirectory()
                    || Files.isSymbolicLink(mod.dir.toPath())) {
                issues.add(new Issue(true, "A selected mod folder is missing or unsafe."));
                continue;
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
                            for (FusionIniIndex.Definition definition : FusionIniIndex.scan(bytes,
                                    source + "!" + entry.path)) {
                                List<FusionIniIndex.Definition> matches = definitions.get(definition.key());
                                if (matches == null) {
                                    matches = new ArrayList<>();
                                    definitions.put(definition.key(), matches);
                                }
                                matches.add(definition);
                            }
                        }
                    }
                } catch (IOException | IllegalArgumentException e) {
                    issues.add(new Issue(true, "Cannot use " + archiveFile.getName()
                        + " from " + mod.name + ": " + e.getMessage()));
                }
            }
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
        return new Result(archives, entries, sources, definitions, issues);
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
