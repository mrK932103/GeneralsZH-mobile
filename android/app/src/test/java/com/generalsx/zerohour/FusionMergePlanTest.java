package com.generalsx.zerohour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class FusionMergePlanTest {
    private static final Charset INI = Charset.forName("ISO-8859-1");

    private static FusionIniBlockParser.Block block(String body, String source) {
        return FusionIniBlockParser.parse(body.getBytes(INI), source).blocks.get(0);
    }

    @Test
    public void assessesThreeWayFactionConflictAndRetainsEverySource() {
        List<FusionIniBlockParser.Block> blocks = Arrays.asList(
            block("PlayerTemplate FactionA\n Side = USA\nEnd", "A.big!factions.ini"),
            block("PlayerTemplate FactionA\n Side = Russia\nEnd", "B.big!factions.ini"),
            block("PlayerTemplate FactionA\n Side = China\nEnd", "C.big!factions.ini"));
        List<FusionIniConflictAnalyzer.Conflict> conflicts =
            FusionIniConflictAnalyzer.analyze(blocks);
        FusionPreflight.Result preflight = preflight(conflicts, Collections.emptyMap());

        FusionMergePlan.Result plan = preflight.assessMergePlan();
        assertEquals(1, plan.items.size());
        FusionMergePlan.Item item = plan.items.get(0);
        assertEquals(FusionMergePlan.Domain.FACTION_REGISTRY, item.domain);
        assertEquals(FusionMergePlan.Resolution.FIELD_LEVEL_MERGE_REQUIRED, item.resolution);
        assertEquals(3, item.participants.size());
        assertFalse(plan.canProducePlayablePackage());
    }

    @Test
    public void includesNWayArchivePathCollisionsAsUnresolvedItems() {
        Map<String, List<String>> paths = new LinkedHashMap<>();
        paths.put("data\\ini\\object.ini",
            Arrays.asList("A/object.big", "B/object.big", "C/object.big"));

        FusionMergePlan.Result plan = preflight(
            Collections.emptyList(), paths).assessMergePlan();
        assertEquals(1, plan.items.size());
        assertEquals(FusionMergePlan.Domain.ARCHIVE_FILE, plan.items.get(0).domain);
        assertEquals(FusionMergePlan.Resolution.FILE_CONTENT_VERIFICATION_REQUIRED,
            plan.items.get(0).resolution);
        assertEquals(3, plan.items.get(0).participants.size());
    }

    @Test
    public void assessmentDoesNotClaimPlayableOutputForCleanInventory() {
        FusionMergePlan.Result plan = preflight(
            Collections.emptyList(), Collections.emptyMap()).assessMergePlan();
        assertTrue(plan.items.isEmpty());
        assertTrue(plan.blockingReasons.isEmpty());
        assertFalse(plan.canProducePlayablePackage());
        assertTrue(plan.summary(5).contains("semantic merge and engine validation are incomplete"));
    }

    private static FusionPreflight.Result preflight(
            List<FusionIniConflictAnalyzer.Conflict> conflicts,
            Map<String, List<String>> paths) {
        return new FusionPreflight.Result(0, 0, paths,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            conflicts,
            new ArrayList<FusionPreflight.Issue>());
    }
}
