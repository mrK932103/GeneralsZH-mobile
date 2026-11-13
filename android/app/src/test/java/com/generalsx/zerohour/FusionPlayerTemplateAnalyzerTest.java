package com.generalsx.zerohour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class FusionPlayerTemplateAnalyzerTest {
    private static final Charset INI = Charset.forName("ISO-8859-1");

    private static FusionIniBlockParser.Block block(String body, String source) {
        return FusionIniBlockParser.parse(body.getBytes(INI), source).blocks.get(0);
    }

    @Test
    public void reportsConflictingEngineConfirmedFactionFieldsAcrossThreeMods() {
        List<FusionIniBlockParser.Block> blocks = Arrays.asList(
            block("PlayerTemplate FactionA\n Side = USA\n PlayableSide = Yes\nEnd",
                "A.big!factions.ini"),
            block("PlayerTemplate FactionA\n Side = Russia\n PlayableSide = Yes\nEnd",
                "B.big!factions.ini"),
            block("PlayerTemplate FactionA\n Side = China\n PlayableSide = Yes\nEnd",
                "C.big!factions.ini"));

        FusionPlayerTemplateAnalyzer.Assessment result =
            FusionPlayerTemplateAnalyzer.analyze(blocks);
        assertEquals("FactionA", result.templateName);
        assertEquals(1, result.conflicts.size());
        assertEquals("side", result.conflicts.get(0).field);
        assertEquals(3, result.conflicts.get(0).participants.size());
        assertTrue(result.isFullyComparable());
    }

    @Test
    public void identicalValuesAndOmittedFieldsDoNotCreateConflict() {
        List<FusionIniBlockParser.Block> blocks = Arrays.asList(
            block("PlayerTemplate FactionA\n Side = USA\nEnd", "A.big!a.ini"),
            block("PlayerTemplate FactionA\n Side = USA\n PlayableSide = Yes\nEnd",
                "B.big!b.ini"),
            block("PlayerTemplate FactionA\nEnd", "C.big!c.ini"));

        FusionPlayerTemplateAnalyzer.Assessment result =
            FusionPlayerTemplateAnalyzer.analyze(blocks);
        assertTrue(result.conflicts.isEmpty());
        assertTrue(result.isFullyComparable());
    }

    @Test
    public void customEngineDirectiveIsNeverGuessedAsScalar() {
        FusionPlayerTemplateAnalyzer.Assessment result =
            FusionPlayerTemplateAnalyzer.analyze(Arrays.asList(
                block("PlayerTemplate FactionA\n ProductionCostChange = TankA 10\nEnd",
                    "A.big!a.ini"),
                block("PlayerTemplate FactionA\n ProductionCostChange = TankA 20\nEnd",
                    "B.big!b.ini")));

        assertFalse(result.isFullyComparable());
        assertTrue(result.unresolved.toString().contains("ProductionCostChange"));
        assertTrue(result.conflicts.isEmpty());
    }

    @Test
    public void unknownFieldBlocksFullComparison() {
        FusionPlayerTemplateAnalyzer.Assessment result =
            FusionPlayerTemplateAnalyzer.analyze(Arrays.asList(
                block("PlayerTemplate FactionA\n ModSpecificField = value\nEnd",
                    "A.big!a.ini"),
                block("PlayerTemplate FactionA\n ModSpecificField = other\nEnd",
                    "B.big!b.ini")));

        assertFalse(result.isFullyComparable());
        assertTrue(result.unresolved.toString().contains("ModSpecificField"));
    }
}
