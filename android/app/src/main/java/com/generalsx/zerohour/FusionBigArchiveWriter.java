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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes a single BIGF archive from already validated BIGF entries.
 *
 * This is a lossless archive combiner, not a semantic Generals INI merger. It rejects every
 * duplicate virtual path rather than applying load-order precedence. Callers must run semantic
 * preflight separately and must not describe this output as a playable faction/mod merge.
 */
final class FusionBigArchiveWriter {
    private static final long MAX_ARCHIVE_SIZE = 0x7fffffffL;
    private static final int MAX_ENTRIES = 200000;
    private static final int BUFFER_SIZE = 64 * 1024;

    static final class SourceEntry {
        final FusionBigArchive.Archive archive;
        final FusionBigArchive.Entry entry;

        SourceEntry(FusionBigArchive.Archive archive, FusionBigArchive.Entry entry) {
            if (archive == null || entry == null) {
                throw new IllegalArgumentException("Archive entry source is required");
            }
            this.archive = archive;
            this.entry = entry;
        }
    }

    private static final class PlannedEntry {
        final SourceEntry source;
        final byte[] path;
        final long offset;

        PlannedEntry(SourceEntry source, byte[] path, long offset) {
            this.source = source;
            this.path = path;
            this.offset = offset;
        }
    }

    private FusionBigArchiveWriter() {}

    /**
     * GeneralsX @feature author 13/11/2026 Write a collision-free BIGF archive without modifying inputs.
     */
    static void write(File destination, List<SourceEntry> sources) throws IOException {
        if (destination == null || sources == null || sources.isEmpty()) {
            throw new IOException("A destination and at least one source entry are required");
        }
        if (sources.size() > MAX_ENTRIES) {
            throw new IOException("Fusion archive entry count exceeds the safety limit");
        }

        Set<String> paths = new HashSet<>();
        List<PlannedEntry> plan = new ArrayList<>(sources.size());
        long headerSize = 16;
        long totalSize;
        for (SourceEntry source : sources) {
            FusionBigArchive.Entry entry = source.entry;
            String normalized;
            try {
                normalized = FusionBigArchive.normalizePath(entry.path);
            } catch (IllegalArgumentException e) {
                throw new IOException("Unsafe source path: " + entry.path, e);
            }
            if (normalized.isEmpty() || !paths.add(normalized)) {
                throw new IOException("Duplicate or empty virtual path; refusing ambiguous merge: "
                    + entry.path);
            }
            byte[] encoded = entry.path.replace('/', '\\').getBytes(StandardCharsets.ISO_8859_1);
            if (encoded.length == 0 || encoded.length > 4096) {
                throw new IOException("Invalid BIGF path length: " + entry.path);
            }
            for (byte b : encoded) {
                if (b == 0) {
                    throw new IOException("Path cannot be represented in BIGF: " + entry.path);
                }
            }
            if (entry.offset < 0 || entry.size < 0
                    || entry.offset > source.archive.archiveSize
                    || entry.size > source.archive.archiveSize - entry.offset) {
                throw new IOException("Source entry is outside its archive: " + entry.path);
            }
            headerSize += 8L + encoded.length + 1L;
            plan.add(new PlannedEntry(source, encoded, 0));
        }
        if (headerSize > MAX_ARCHIVE_SIZE) {
            throw new IOException("BIGF header exceeds format limit");
        }
        long offset = headerSize;
        List<PlannedEntry> positioned = new ArrayList<>(plan.size());
        for (PlannedEntry item : plan) {
            positioned.add(new PlannedEntry(item.source, item.path, offset));
            offset += item.source.entry.size;
            if (offset > MAX_ARCHIVE_SIZE) {
                throw new IOException("Combined archive exceeds BIGF 2 GB format limit");
            }
        }
        totalSize = offset;

        File canonicalDestination = destination.getCanonicalFile();
        if (canonicalDestination.exists() && (!destination.isFile()
                || java.nio.file.Files.isSymbolicLink(canonicalDestination.toPath()))) {
            throw new IOException("Fusion output must be a regular file, not a directory or symbolic link");
        }
        File parent = canonicalDestination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Cannot create Fusion output directory");
        }
        String tempPrefix = canonicalDestination.getName() + ".";
        while (tempPrefix.length() < 3) tempPrefix += "_";
        File temp = File.createTempFile(tempPrefix, ".fusion-tmp", parent);
        File backup = new File(parent, canonicalDestination.getName() + ".fusion-backup");
        if (backup.exists() && (!backup.isFile()
                || java.nio.file.Files.isSymbolicLink(backup.toPath()))) {
            temp.delete();
            throw new IOException("Fusion backup path is not a regular file");
        }
        if (backup.exists() && !backup.delete()) {
            temp.delete();
            throw new IOException("Cannot clear stale Fusion backup");
        }

        try {
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(temp), BUFFER_SIZE)) {
                out.write(new byte[] { 'B', 'I', 'G', 'F' });
                writeLE(out, (int) totalSize);
                writeBE(out, positioned.size());
                writeBE(out, (int) headerSize);
                for (PlannedEntry item : positioned) {
                    writeBE(out, (int) item.offset);
                    writeBE(out, (int) item.source.entry.size);
                    out.write(item.path);
                    out.write(0);
                }
                byte[] buffer = new byte[BUFFER_SIZE];
                for (PlannedEntry item : positioned) {
                    try (RandomAccessFile in = new RandomAccessFile(item.source.archive.file, "r")) {
                        if (in.length() != item.source.archive.archiveSize) {
                            throw new IOException("Source archive changed after preflight: "
                                + item.source.archive.file);
                        }
                        in.seek(item.source.entry.offset);
                        long remaining = item.source.entry.size;
                        while (remaining > 0) {
                            int wanted = (int) Math.min(buffer.length, remaining);
                            int read = in.read(buffer, 0, wanted);
                            if (read < 0) throw new IOException("Unexpected EOF in " + item.source.entry.path);
                            out.write(buffer, 0, read);
                            remaining -= read;
                        }
                    }
                }
            }
            if (temp.length() != totalSize) {
                throw new IOException("Fusion archive size verification failed");
            }
            FusionBigArchive.Archive verified = FusionBigArchive.read(temp);
            if (verified.entries.size() != positioned.size()) {
                throw new IOException("Fusion archive entry-count verification failed");
            }
            for (int i = 0; i < positioned.size(); i++) {
                FusionBigArchive.Entry actual = verified.entries.get(i);
                PlannedEntry expected = positioned.get(i);
                if (!actual.normalizedPath.equals(FusionBigArchive.normalizePath(
                        new String(expected.path, StandardCharsets.ISO_8859_1)))
                        || actual.offset != expected.offset
                        || actual.size != expected.source.entry.size) {
                    throw new IOException("Fusion archive table verification failed at entry " + i);
                }
            }
            boolean hadDestination = canonicalDestination.exists();
            if (hadDestination && !canonicalDestination.renameTo(backup)) {
                throw new IOException("Cannot preserve existing Fusion output before replacement");
            }
            try {
                Files.move(temp.toPath(), canonicalDestination.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                if (!temp.renameTo(canonicalDestination)) {
                    if (hadDestination && !backup.renameTo(canonicalDestination)) {
                        throw new IOException("Publish failed and previous output could not be restored",
                            atomicFailure);
                    }
                    throw new IOException("Cannot publish Fusion archive", atomicFailure);
                }
            }
            if (backup.exists()) backup.delete();
        } catch (IOException | RuntimeException e) {
            temp.delete();
            if (backup.exists() && !canonicalDestination.exists()) backup.renameTo(canonicalDestination);
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Fusion archive creation failed", e);
        }
    }

    private static void writeBE(OutputStream out, int value) throws IOException {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static void writeLE(OutputStream out, int value) throws IOException {
        out.write(value);
        out.write(value >>> 8);
        out.write(value >>> 16);
        out.write(value >>> 24);
    }
}
