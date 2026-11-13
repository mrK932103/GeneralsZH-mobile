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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Strict orchestration for the first safe Fusion archive build milestone.
 *
 * This only combines non-overlapping archive entries. It deliberately refuses any repeated
 * virtual path or named INI definition; no load-order or semantic merge is guessed. It does
 * not claim that the resulting package is a playable multi-faction mod.
 */
final class FusionArchiveBuild {
    static final class Result {
        final File archive;
        final int sourceMods;
        final int sourceArchives;
        final int entries;
        final FusionPreflight.Result preflight;

        Result(File archive, int sourceMods, int sourceArchives, int entries,
               FusionPreflight.Result preflight) {
            this.archive = archive;
            this.sourceMods = sourceMods;
            this.sourceArchives = sourceArchives;
            this.entries = entries;
            this.preflight = preflight;
        }
    }

    private FusionArchiveBuild() {}

    /**
     * GeneralsX @feature author 13/11/2026 Build a strict non-overlapping Fusion BIGF package.
     */
    static Result build(List<ModManager.Mod> selectedMods, File destination) throws IOException {
        if (destination == null) throw new IOException("Fusion output destination is required");
        if (selectedMods == null || selectedMods.isEmpty()) {
            throw new IOException("Select at least one mod.");
        }
        if (java.nio.file.Files.isSymbolicLink(destination.toPath())) {
            throw new IOException("Fusion output cannot be a symbolic link.");
        }
        FusionPreflight.Result preflight = FusionPreflight.inspect(selectedMods);
        for (FusionPreflight.Issue issue : preflight.issues) {
            if (issue.blocking) throw new IOException(issue.message);
        }
        if (preflight.duplicatePathCount() != 0) {
            throw new IOException("Fusion build blocked: duplicate virtual file paths must be merged explicitly.");
        }
        if (preflight.duplicateDefinitionCount() != 0) {
            throw new IOException("Fusion build blocked: duplicate named INI definitions require semantic conflict resolution.");
        }
        File output = destination.getCanonicalFile();
        for (ModManager.Mod mod : selectedMods) {
            if (mod == null || mod.dir == null || !mod.dir.isDirectory()) {
                throw new IOException("A selected mod folder is missing.");
            }
            File root = mod.dir.getCanonicalFile();
            if (isWithin(output, root)) {
                throw new IOException("Fusion output must be outside every selected source mod folder.");
            }
        }

        List<FusionBigArchiveWriter.SourceEntry> entries = new ArrayList<>();
        int archiveCount = 0;
        for (ModManager.Mod mod : selectedMods) {
            List<File> archives = new ArrayList<>();
            collectArchives(mod.dir, archives, 0);
            for (File file : archives) {
                FusionBigArchive.Archive archive = FusionBigArchive.read(file);
                archiveCount++;
                for (FusionBigArchive.Entry entry : archive.entries) {
                    entries.add(new FusionBigArchiveWriter.SourceEntry(archive, entry));
                }
            }
        }
        if (entries.isEmpty()) throw new IOException("No BIGF entries found in selected mods.");
        FusionBigArchiveWriter.write(output, entries);
        return new Result(output, selectedMods.size(), archiveCount, entries.size(), preflight);
    }

    private static boolean isWithin(File candidate, File directory) throws IOException {
        String child = candidate.getCanonicalPath();
        String parent = directory.getCanonicalPath();
        return child.equals(parent) || child.startsWith(parent + File.separator);
    }

    private static void collectArchives(File directory, List<File> output, int depth) {
        if (directory == null || depth > 8 || !directory.isDirectory()
                || java.nio.file.Files.isSymbolicLink(directory.toPath())) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (java.nio.file.Files.isSymbolicLink(child.toPath())) continue;
            if (child.isDirectory()) collectArchives(child, output, depth + 1);
            else if (child.isFile() && child.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".big")) {
                output.add(child);
            }
        }
    }
}
