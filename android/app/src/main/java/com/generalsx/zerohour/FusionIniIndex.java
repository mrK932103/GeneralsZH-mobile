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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative declaration index for Generals INI files. This is intentionally not a text
 * merger: it identifies common top-level named definitions while preserving source locations.
 */
final class FusionIniIndex {
    private static final Charset INI_CHARSET = Charset.forName("ISO-8859-1");
    private static final Pattern DECLARATION = Pattern.compile(
        "^(Object|ChildObject|CommandSet|CommandButton|Weapon|Armor|Locomotor|FXList|"
        + "ParticleSystem|Upgrade|Science|SpecialPower|AudioEvent|CrateData|"
        + "MappedImage|PlayerTemplate)\\s+([^\\s;]+)(?:\\s+([^\\s;]+))?\\s*$",
        Pattern.CASE_INSENSITIVE);

    static final class Definition {
        final String type;
        final String name;
        final String parent;
        final String source;
        final int line;

        Definition(String type, String name, String parent, String source, int line) {
            this.type = type;
            this.name = name;
            this.parent = parent;
            this.source = source;
            this.line = line;
        }

        String key() {
            return type.toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
        }
    }

    private FusionIniIndex() {}

    /**
     * GeneralsX @feature author 13/11/2026 Index named INI declarations without rewriting content.
     */
    static List<Definition> scan(byte[] bytes, String source) {
        String text = new String(bytes, INI_CHARSET);
        String[] lines = text.replace("\r", "").split("\n", -1);
        List<Definition> found = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("//")
                    || line.startsWith("#")) {
                continue;
            }
            Matcher matcher = DECLARATION.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String type = matcher.group(1);
            String name = matcher.group(2);
            String parent = matcher.group(3);
            found.add(new Definition(type, name, parent, source, i + 1));
        }
        return found;
    }

    static Map<String, List<Definition>> group(List<Definition> definitions) {
        Map<String, List<Definition>> grouped = new LinkedHashMap<>();
        for (Definition definition : definitions) {
            List<Definition> list = grouped.get(definition.key());
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(definition.key(), list);
            }
            list.add(definition);
        }
        return grouped;
    }

    static List<Definition> immutable(List<Definition> definitions) {
        return Collections.unmodifiableList(definitions);
    }
}
