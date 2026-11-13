package com.generalsx.zerohour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.Arrays;
import org.junit.Test;

public class FusionCommandUiAnalyzerTest {
    private static final Charset INI = Charset.forName("ISO-8859-1");

    private static FusionIniBlockParser.Block block(String body, String source) {
        return FusionIniBlockParser.parse(body.getBytes(INI), source).blocks.get(0);
    }

    @Test
    public void reportsThreeWayDifferentButtonAssignmentsForSameSlot() {
        FusionIniBlockParser.Block a = block(
            "CommandSet SharedSet\n 1 = CommandA\nEnd", "A.big!ui.ini");
        FusionIniBlockParser.Block b = block(
            "CommandSet SharedSet\n 1 = CommandB\nEnd", "B.big!ui.ini");
        FusionIniBlockParser.Block c = block(
            "CommandSet SharedSet\n 1 = CommandC\nEnd", "C.big!ui.ini");
        FusionIniBlockParser.Block buttons = block(
            "CommandButton CommandA\n Command = DOZER_CONSTRUCT\nEnd\n"
            + "CommandButton CommandB\n Command = DOZER_CONSTRUCT\nEnd\n"
            + "CommandButton CommandC\n Command = DOZER_CONSTRUCT\nEnd", "buttons.big!ui.ini");

        FusionCommandUiAnalyzer.Assessment result = FusionCommandUiAnalyzer.analyze(
            Arrays.asList(a, b, c), Arrays.asList(a, b, c, buttons));
        assertEquals(1, result.conflicts.size());
        assertEquals(1, result.conflicts.get(0).slot);
        assertEquals(3, result.conflicts.get(0).values.size());
        assertTrue(result.isFullyComparable());
    }

    @Test
    public void allowsNonOverlappingSlotsButChecksButtonReferences() {
        FusionIniBlockParser.Block a = block(
            "CommandSet SharedSet\n 1 = CommandA\nEnd", "A.big!ui.ini");
        FusionIniBlockParser.Block b = block(
            "CommandSet SharedSet\n 2 = CommandB\nEnd", "B.big!ui.ini");
        FusionIniBlockParser.Block buttons = block(
            "CommandButton CommandA\n Command = DOZER_CONSTRUCT\nEnd\n"
            + "CommandButton CommandB\n Command = DOZER_CONSTRUCT\nEnd", "buttons.big!ui.ini");

        FusionCommandUiAnalyzer.Assessment result = FusionCommandUiAnalyzer.analyze(
            Arrays.asList(a, b), Arrays.asList(a, b, buttons));
        assertTrue(result.conflicts.isEmpty());
        assertEquals(2, result.assignments.size());
        assertTrue(result.isFullyComparable());
    }

    @Test
    public void missingCommandButtonReferenceBlocksAssessment() {
        FusionIniBlockParser.Block a = block(
            "CommandSet SharedSet\n 1 = MissingButton\nEnd", "A.big!ui.ini");
        FusionCommandUiAnalyzer.Assessment result = FusionCommandUiAnalyzer.analyze(
            Arrays.asList(a), Arrays.asList(a));
        assertFalse(result.isFullyComparable());
        assertTrue(result.unresolved.toString().contains("MissingButton"));
    }

    @Test
    public void unsupportedCommandSetSyntaxIsNotGuessed() {
        FusionIniBlockParser.Block a = block(
            "CommandSet SharedSet\n 1 = CommandA\nCustomLayout = X\nEnd", "A.big!ui.ini");
        FusionCommandUiAnalyzer.Assessment result = FusionCommandUiAnalyzer.analyze(
            Arrays.asList(a), Arrays.asList(a));
        assertFalse(result.isFullyComparable());
        assertTrue(result.unresolved.toString().contains("CustomLayout"));
    }

    @Test
    public void engineSlotRangeIncludesEighteenButRejectsNineteen() {
        FusionIniBlockParser.Block valid = block(
            "CommandSet SharedSet\\n 18 = CommandA\\nEnd", "A.big!ui.ini");
        FusionIniBlockParser.Block invalid = block(
            "CommandSet SharedSet\\n 19 = CommandB\\nEnd", "B.big!ui.ini");

        FusionCommandUiAnalyzer.Assessment accepted = FusionCommandUiAnalyzer.analyze(
            Arrays.asList(valid), Arrays.asList(valid));
        FusionCommandUiAnalyzer.Assessment rejected = FusionCommandUiAnalyzer.analyze(
            Arrays.asList(invalid), Arrays.asList(invalid));

        assertTrue(accepted.isFullyComparable());
        assertFalse(rejected.isFullyComparable());
        assertTrue(rejected.unresolved.toString().contains("19 = CommandB"));
    }

    @Test
    public void duplicateAssignmentToSameSlotIsReportedAsConflict() {
        FusionIniBlockParser.Block duplicate = block(
            "CommandSet SharedSet\\n 1 = CommandA\\n 1 = CommandB\\nEnd",
            "A.big!ui.ini");
        FusionIniBlockParser.Block buttons = block(
            "CommandButton CommandA\\n Command = DOZER_CONSTRUCT\\nEnd\\n"
            + "CommandButton CommandB\\n Command = DOZER_CONSTRUCT\\nEnd",
            "buttons.big!ui.ini");

        FusionCommandUiAnalyzer.Assessment result = FusionCommandUiAnalyzer.analyze(
            Arrays.asList(duplicate), Arrays.asList(duplicate, buttons));
        assertEquals(1, result.conflicts.size());
        assertEquals(2, result.conflicts.get(0).values.size());
        assertFalse(result.isFullyComparable());
        assertTrue(result.unresolved.toString().contains("assigned more than once"));
    }
}
