package com.generalsx.zerohour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class FusionIniBlockParserTest {
    private static final Charset INI = Charset.forName("ISO-8859-1");

    private static byte[] bytes(String value) {
        return value.getBytes(INI);
    }

    @Test
    public void preservesCrLfBlockBytesAndLineNumbers() {
        String source = "; header\r\nObject TankA\r\n  DisplayName = NAME:TankA\r\n"
            + "  Behavior = TestBehavior\r\n    Foo = Bar\r\n  End\r\nEnd\r\n";
        FusionIniBlockParser.Result result =
            FusionIniBlockParser.parse(bytes(source), "mod.big!Data/INI/Object.ini");

        assertTrue(result.problems.toString(), result.problems.isEmpty());
        assertEquals(1, result.blocks.size());
        FusionIniBlockParser.Block block = result.blocks.get(0);
        assertTrue(block.safeForComparison);
        assertEquals(2, block.startLine);
        assertEquals(7, block.endLine);
        assertEquals(source.substring(source.indexOf("Object TankA"),
            source.lastIndexOf("End\r\n") + "End\r".length()),
            new String(block.copyExactBytes(), INI));
    }

    @Test
    public void unknownBareSyntaxMakesBlockUnsafe() {
        String source = "Object TankA\n  UnknownLegacySyntax value\nEnd";
        FusionIniBlockParser.Result result =
            FusionIniBlockParser.parse(bytes(source), "legacy.big!object.ini");

        assertEquals(1, result.blocks.size());
        assertFalse(result.blocks.get(0).safeForComparison);
        assertTrue(result.blocks.get(0).uncertainties.toString(),
            !result.blocks.get(0).uncertainties.isEmpty());
    }

    @Test
    public void classifiesThreeWayExactDuplicate() {
        String block = "Object TankA\n  DisplayName = NAME:TankA\nEnd";
        List<FusionIniBlockParser.Block> blocks = new ArrayList<>();
        blocks.add(FusionIniBlockParser.parse(bytes(block), "A.big!a.ini").blocks.get(0));
        blocks.add(FusionIniBlockParser.parse(bytes(block), "B.big!b.ini").blocks.get(0));
        blocks.add(FusionIniBlockParser.parse(bytes(block), "C.big!c.ini").blocks.get(0));

        List<FusionIniConflictAnalyzer.Conflict> conflicts =
            FusionIniConflictAnalyzer.analyze(blocks);
        assertEquals(1, conflicts.size());
        assertEquals(FusionIniConflictAnalyzer.Status.IDENTICAL_EXACT,
            conflicts.get(0).status);
        assertEquals(3, conflicts.get(0).participants.size());
    }

    @Test
    public void differingThirdSourceMakesNWayConflict() {
        List<FusionIniBlockParser.Block> blocks = new ArrayList<>();
        blocks.add(FusionIniBlockParser.parse(bytes(
            "Object TankA\n  BuildCost = 100\nEnd"), "A.big!a.ini").blocks.get(0));
        blocks.add(FusionIniBlockParser.parse(bytes(
            "Object TankA\n  BuildCost = 100\nEnd"), "B.big!b.ini").blocks.get(0));
        blocks.add(FusionIniBlockParser.parse(bytes(
            "Object TankA\n  BuildCost = 200\nEnd"), "C.big!c.ini").blocks.get(0));

        List<FusionIniConflictAnalyzer.Conflict> conflicts =
            FusionIniConflictAnalyzer.analyze(blocks);
        assertEquals(1, conflicts.size());
        assertEquals(FusionIniConflictAnalyzer.Status.DIFFERENT_CONTENT,
            conflicts.get(0).status);
        assertEquals(3, conflicts.get(0).participants.size());
    }

    @Test
    public void unsafeParticipantPreventsExactClassification() {
        List<FusionIniBlockParser.Block> blocks = new ArrayList<>();
        blocks.add(FusionIniBlockParser.parse(bytes(
            "Object TankA\n  DisplayName = NAME:TankA\nEnd"), "A.big!a.ini").blocks.get(0));
        blocks.add(FusionIniBlockParser.parse(bytes(
            "Object TankA\n  UnknownSyntax x\nEnd"), "B.big!b.ini").blocks.get(0));

        List<FusionIniConflictAnalyzer.Conflict> conflicts =
            FusionIniConflictAnalyzer.analyze(blocks);
        assertEquals(1, conflicts.size());
        assertEquals(FusionIniConflictAnalyzer.Status.UNSAFE_TO_COMPARE,
            conflicts.get(0).status);
    }

    @Test
    public void groupsNamesCaseInsensitivelyAcrossAllSources() {
        List<FusionIniBlockParser.Block> blocks = new ArrayList<>();
        blocks.add(FusionIniBlockParser.parse(bytes(
            "Object TankA\n  BuildCost = 100\nEnd"), "A.big!a.ini").blocks.get(0));
        blocks.add(FusionIniBlockParser.parse(bytes(
            "object tanka\n  BuildCost = 100\nEnd"), "B.big!b.ini").blocks.get(0));
        blocks.add(FusionIniBlockParser.parse(bytes(
            "OBJECT TANKA\n  BuildCost = 100\nEnd"), "C.big!c.ini").blocks.get(0));

        List<FusionIniConflictAnalyzer.Conflict> conflicts =
            FusionIniConflictAnalyzer.analyze(blocks);
        assertEquals(1, conflicts.size());
        assertEquals("object:tanka", conflicts.get(0).key);
        assertEquals(FusionIniConflictAnalyzer.Status.DIFFERENT_CONTENT,
            conflicts.get(0).status);
        assertEquals(3, conflicts.get(0).participants.size());
    }

    @Test
    public void distinctDeclarationTypesDoNotCollideByName() {
        List<FusionIniBlockParser.Block> blocks = new ArrayList<>();
        blocks.add(FusionIniBlockParser.parse(bytes(
            "Object SharedName\n  BuildCost = 100\nEnd"), "A.big!a.ini").blocks.get(0));
        blocks.add(FusionIniBlockParser.parse(bytes(
            "Weapon SharedName\n  AttackRange = 100\nEnd"), "B.big!b.ini").blocks.get(0));

        assertTrue(FusionIniConflictAnalyzer.analyze(blocks).isEmpty());
    }

    @Test
    public void finalBlockWithoutTrailingNewlineIsPreserved() {
        String source = "; comment\nObject TankA\n  BuildCost = 100\nEnd";
        FusionIniBlockParser.Result result =
            FusionIniBlockParser.parse(bytes(source), "mod.big!object.ini");
        assertTrue(result.problems.toString(), result.problems.isEmpty());
        assertEquals(1, result.blocks.size());
        assertEquals("Object TankA\n  BuildCost = 100\nEnd",
            new String(result.blocks.get(0).copyExactBytes(), INI));
        assertTrue(result.blocks.get(0).safeForComparison);
    }

    @Test
    public void returnedExactBytesAreDefensiveCopies() {
        FusionIniBlockParser.Block block = FusionIniBlockParser.parse(bytes(
            "Object TankA\n  BuildCost = 100\nEnd"), "A.big!a.ini").blocks.get(0);
        byte[] exposed = block.copyExactBytes();
        exposed[0] = 'X';

        assertEquals("Object TankA\n  BuildCost = 100\nEnd",
            new String(block.copyExactBytes(), INI));
    }

    @Test
    public void returnedExactBytesAreDefensiveCopies() {
        FusionIniBlockParser.Block block = FusionIniBlockParser.parse(bytes(
            "Object TankA\n  BuildCost = 100\nEnd"), "A.big!a.ini").blocks.get(0);
        byte[] exposed = block.copyExactBytes();
        exposed[0] = 'X';

        assertEquals("Object TankA\n  BuildCost = 100\nEnd",
            new String(block.copyExactBytes(), INI));
    }

    @Test
    public void conflictParticipantsCannotBeModified() {
        List<FusionIniBlockParser.Block> blocks = new ArrayList<>();
        blocks.add(FusionIniBlockParser.parse(bytes(
            "Object TankA\nEnd"), "A.big!a.ini").blocks.get(0));
        blocks.add(FusionIniBlockParser.parse(bytes(
            "Object TankA\nEnd"), "B.big!b.ini").blocks.get(0));
        FusionIniConflictAnalyzer.Conflict conflict =
            FusionIniConflictAnalyzer.analyze(blocks).get(0);

        try {
            conflict.participants.clear();
            throw new AssertionError("participants must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(2, conflict.participants.size());
        }
    }

    @Test
    public void emptyAndNullConflictInputsAreSafe() {
        assertTrue(FusionIniConflictAnalyzer.analyze(null).isEmpty());
        assertTrue(FusionIniConflictAnalyzer.analyze(new ArrayList<>()).isEmpty());
    }

}
