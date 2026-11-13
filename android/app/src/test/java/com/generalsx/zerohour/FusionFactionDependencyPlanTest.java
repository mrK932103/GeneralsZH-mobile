package com.generalsx.zerohour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class FusionFactionDependencyPlanTest {
    private static final Charset INI = Charset.forName("ISO-8859-1");

    private static FusionIniBlockParser.Block block(String text, String source) {
        return FusionIniBlockParser.parse(text.getBytes(INI), source).blocks.get(0);
    }

    private static FusionPreflight.Result preflight(FusionIniBlockParser.Block... blocks) {
        Map<String, List<FusionIniIndex.Definition>> definitions = new LinkedHashMap<>();
        Map<String, List<FusionIniReferences.Reference>> references = new LinkedHashMap<>();
        for (FusionIniBlockParser.Block block : blocks) {
            for (FusionIniIndex.Definition definition :
                    FusionIniIndex.scan(block.copyExactBytes(), block.source)) {
                definitions.computeIfAbsent(definition.key(), ignored -> new java.util.ArrayList<>())
                    .add(definition);
            }
            for (FusionIniReferences.Reference reference :
                    FusionIniReferences.scan(block.copyExactBytes(), block.source)) {
                references.computeIfAbsent(reference.key(), ignored -> new java.util.ArrayList<>())
                    .add(reference);
            }
        }
        return new FusionPreflight.Result(0, 0, Collections.emptyMap(), definitions,
            references, Collections.emptyMap(),
            Collections.<FusionIniConflictAnalyzer.Conflict>emptyList(),
            Arrays.asList(blocks), Collections.<FusionPreflight.Issue>emptyList());
    }

    @Test
    public void factionClosureUsesOnlyItsOwnModAcrossMultipleArchives() {
        FusionPreflight.Result input = preflight(
            block("PlayerTemplate IranFaction\n Side = Iran\n PlayableSide = Yes\n"
                + " StartingBuilding = IranCommandCenter\nEnd",
                "Hypersonic/factions.big!Data/INI/PlayerTemplate.ini"),
            block("Object IranCommandCenter\n WeaponSet\nEnd\nEnd",
                "Hypersonic/units.big!Data/INI/Object/iran.ini"),
            block("Object IranDozer\nEnd",
                "Hypersonic/units.big!Data/INI/Object/dozer.ini"),
            block("Object IranCommandCenter\nEnd",
                "TEOD/units.big!Data/INI/Object/iran.ini"));

        FusionFactionCatalog.FactionSource faction =
            FusionFactionCatalog.discover(input.iniBlocks).get(0);
        FusionFactionDependencyPlan.FactionPlan plan =
            FusionFactionDependencyPlan.build(input, Arrays.asList(faction)).get(0);

        assertTrue(plan.definitions.contains("playertemplate:iranfaction"));
        assertTrue(plan.definitions.contains("object:irancommandcenter"));
        assertFalse(plan.crossSource.toString().contains("irancommandcenter"));
        assertEquals("hypersonic", plan.sourceMod);
    }

    @Test
    public void dependencyFoundOnlyInAnotherModIsNotSilentlyImported() {
        FusionPreflight.Result input = preflight(
            block("PlayerTemplate IranFaction\n Side = Iran\n PlayableSide = Yes\n"
                + " StartingBuilding = SharedLookingName\nEnd",
                "Hypersonic/factions.big!Data/INI/PlayerTemplate.ini"),
            block("Object SharedLookingName\nEnd",
                "TEOD/units.big!Data/INI/Object/shared.ini"));

        FusionFactionCatalog.FactionSource faction =
            FusionFactionCatalog.discover(input.iniBlocks).get(0);
        FusionFactionDependencyPlan.FactionPlan plan =
            FusionFactionDependencyPlan.build(input, Arrays.asList(faction)).get(0);

        assertFalse(plan.isClosed());
        assertTrue(plan.crossSource.toString().contains("object:sharedlookingname"));
    }
}
