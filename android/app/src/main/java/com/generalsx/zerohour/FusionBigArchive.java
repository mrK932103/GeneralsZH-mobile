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
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Read-only, bounds-checked reader for the BIGF archive variant written by {@link ModPacker}.
 * It deliberately does not extract or modify an archive. BIG4 and other variants are rejected
 * until their layouts have been independently verified.
 */
final class FusionBigArchive {
    private static final int MAX_ENTRIES = 200_000;
    private static final int MAX_PATH_BYTES = 4096;
    private static final long MAX_SIGNED_ARCHIVE_SIZE = 0x7fffffffL;

    static final class Entry {
        final String path;
        final String normalizedPath;
        final long offset;
        final long size;

        Entry(String path, long offset, long size) {
            this.path = path;
            this.normalizedPath = normalizePath(path);
            this.offset = offset;
            this.size = size;
        }
    }

    static final class Archive {
        final File file;
        final long archiveSize;
        final List<Entry> entries;

        Archive(File file, long archiveSize, List<Entry> entries) {
            this.file = file;
            this.archiveSize = archiveSize;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    private FusionBigArchive() {}

    /**
     * GeneralsX @feature author 13/11/2026 Read-only BIGF validation for Mod Fusion preflight.
     */
    static Archive read(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Archive does not exist");
        }
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            if (in.length() < 16) {
                throw new IOException("Archive is shorter than a BIGF header");
            }
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'B' || magic[1] != 'I' || magic[2] != 'G' || magic[3] != 'F') {
                String found = new String(magic, StandardCharsets.US_ASCII);
                throw new IOException("Unsupported archive signature: " + found + " (BIGF only)");
            }

            long declaredSize = readLittleEndianInt(in);
            long count = readUnsignedBigEndianInt(in);
            long headerSize = readUnsignedBigEndianInt(in);
            long actualSize = in.length();

            if (declaredSize < 16 || declaredSize > MAX_SIGNED_ARCHIVE_SIZE
                    || declaredSize != actualSize) {
                throw new IOException("Invalid BIGF archive size");
            }
            if (count > MAX_ENTRIES) {
                throw new IOException("BIGF entry count exceeds safety limit");
            }
            if (headerSize < 16 || headerSize > declaredSize) {
                throw new IOException("Invalid BIGF table boundary");
            }

            List<Entry> entries = new ArrayList<>((int) count);
            for (int i = 0; i < (int) count; i++) {
                if (in.getFilePointer() + 8 > headerSize) {
                    throw new IOException("BIGF table ends before entry " + i);
                }
                long offset = readUnsignedBigEndianInt(in);
                long size = readUnsignedBigEndianInt(in);
                String path = readPath(in, headerSize);
                String normalized = normalizePath(path);
                if (normalized.isEmpty()) {
                    throw new IOException("Empty BIGF path at entry " + i);
                }
                if (offset < headerSize || offset > declaredSize || size > declaredSize - offset) {
                    throw new IOException("BIGF entry is outside archive bounds: " + path);
                }
                entries.add(new Entry(path, offset, size));
            }
            if (in.getFilePointer() > headerSize) {
                throw new IOException("BIGF table overlaps file data");
            }
            return new Archive(file, declaredSize, entries);
        }
    }

    /**
     * Reads one validated entry into memory. The caller supplies a strict size ceiling so a
     * malformed or unexpectedly huge INI cannot exhaust the launcher's heap.
     */
    static byte[] readEntry(Archive archive, Entry entry, int maxBytes) throws IOException {
        if (archive == null || entry == null || maxBytes < 0 || entry.size > maxBytes
                || entry.size > Integer.MAX_VALUE) {
            throw new IOException("Entry exceeds the permitted read size");
        }
        if (entry.offset < 0 || entry.offset > archive.archiveSize
                || entry.size > archive.archiveSize - entry.offset) {
            throw new IOException("Entry is outside archive bounds");
        }
        byte[] data = new byte[(int) entry.size];
        try (RandomAccessFile in = new RandomAccessFile(archive.file, "r")) {
            in.seek(entry.offset);
            in.readFully(data);
        }
        return data;
    }

    private static String readPath(RandomAccessFile in, long headerSize) throws IOException {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < MAX_PATH_BYTES; i++) {
            if (in.getFilePointer() >= headerSize) {
                throw new IOException("Unterminated BIGF path");
            }
            int next = in.read();
            if (next < 0) {
                throw new IOException("Unexpected end of BIGF path");
            }
            if (next == 0) {
                return value.toString();
            }
            value.append((char) next);
        }
        throw new IOException("BIGF path exceeds safety limit");
    }

    /**
     * Converts archive separators to the engine's virtual-path form and rejects traversal.
     * The original path is retained separately so diagnostics can show the archive spelling.
     */
    static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String value = path.replace('/', '\\').trim();
        if (value.startsWith("\\") || value.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Absolute archive path: " + path);
        }
        String[] parts = value.split("\\\\+");
        List<String> safe = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new IllegalArgumentException("Archive path escapes its root: " + path);
            }
            safe.add(part);
        }
        return String.join("\\", safe).toLowerCase(Locale.ROOT);
    }

    private static long readLittleEndianInt(RandomAccessFile in) throws IOException {
        long a = in.readUnsignedByte();
        long b = in.readUnsignedByte();
        long c = in.readUnsignedByte();
        long d = in.readUnsignedByte();
        return a | (b << 8) | (c << 16) | (d << 24);
    }

    private static long readUnsignedBigEndianInt(RandomAccessFile in) throws IOException {
        return ((long) in.readUnsignedByte() << 24)
            | ((long) in.readUnsignedByte() << 16)
            | ((long) in.readUnsignedByte() << 8)
            | in.readUnsignedByte();
    }
}
