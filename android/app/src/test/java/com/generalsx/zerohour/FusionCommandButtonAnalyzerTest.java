package com.generalsx.zerohour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class FusionCommandButtonAnalyzerTest {
    private static final Charset INI = Charset.forName("ISO-8859-1");

    private static FusionIniBlockParser.Block block(String body, String source) {
        return FusionIniBlockParser.parse(body.getBytes(INI), source).blocks.get(0);
    }

    @Test
    public void reportsThreeWayCommandAndImageConflicts() {
        List<FusionIniBlockParser.Block> blocks = Arrays.asList(
            block("CommandButton ButtonA\n Command = COMMAND_ATTACK\n ButtonImage = IconA\nEnd", "A.big!a.ini"),
            block("CommandButton ButtonA\n Command = COMMAND_STOP\n ButtonImage = IconA\nEnd", "B.big!b.ini"),
            block("CommandButton ButtonA\n Command = COMMAND_GUARD\n ButtonImage = IconB\nEnd", "C.big!c.ini"));

        FusionCommandButtonAnalyzer.Assessment result =
            FusionCommandButtonAnalyzer.analyze(blocks);
        assertEquals("ButtonA", result.buttonName);
        assertEquals(2, result.conflicts.size());
        assertEquals("command", result.conflicts.get(0).field);
        assertEquals(3, result.conflicts.get(0).participants.size());
        assertTrue(result.isFullyComparable());
    }

    @Test
    public void unknownFieldIsUnresolvedRatherThanIgnored() {
        FusionCommandButtonAnalyzer.Assessment result =
            FusionCommandButtonAnalyzer.analyze(Arrays.asList(
                block("CommandButton ButtonA\n ModSpecificFlag = Yes\nEnd", "A.big!a.ini")));
        assertFalse(result.isFullyComparable());
        assertTrue(result.unresolved.toString().contains("ModSpecificFlag"));
    }

    @Test
    public void repeatedFieldInOneDeclarationRequiresReview() {
        FusionCommandButtonAnalyzer.Assessment result =
            FusionCommandButtonAnalyzer.analyze(Arrays.asList(
                block("CommandButton ButtonA\n Command = COMMAND_ATTACK\n Command = COMMAND_STOP\nEnd",
                    "A.big!a.ini")));
        assertFalse(result.isFullyComparable());
        assertTrue(result.unresolved.toString().contains("repeated field assignment"));
    }
}
