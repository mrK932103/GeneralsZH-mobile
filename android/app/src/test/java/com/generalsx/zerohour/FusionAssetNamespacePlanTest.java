package com.generalsx.zerohour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class FusionAssetNamespacePlanTest {
    @Test
    public void sameVirtualPathFromDifferentModsGetsDistinctRuntimePaths() {
        Map<String, java.util.List<String>> paths = new LinkedHashMap<>();
        paths.put("Art\\Textures\\shared.tga",
            Arrays.asList("Hypersonic/Hypersonic.big", "TEOD/TEOD.big"));

        FusionAssetNamespacePlan.Result plan = FusionAssetNamespacePlan.build(paths);

        String hypersonic = plan.runtimePath("Hypersonic/Hypersonic.big", "Art/Textures/shared.tga");
        String teod = plan.runtimePath("TEOD/TEOD.big", "Art/Textures/shared.tga");
        assertTrue(plan.isComplete());
        assertNotEquals(hypersonic, teod);
        assertTrue(hypersonic.startsWith("__fusion\\"));
        assertTrue(teod.startsWith("__fusion\\"));
        assertEquals(2, plan.remappedCount());
    }

    @Test
    public void uniqueAssetPathIsNotRenamed() {
        Map<String, java.util.List<String>> paths = new LinkedHashMap<>();
        paths.put("Art\\Textures\\iran.tga", Arrays.asList("Hypersonic/Hypersonic.big"));

        FusionAssetNamespacePlan.Result plan = FusionAssetNamespacePlan.build(paths);

        assertTrue(plan.isComplete());
        assertEquals("art\\textures\\iran.tga",
            plan.runtimePath("Hypersonic/Hypersonic.big", "Art/Textures/iran.tga"));
        assertEquals(0, plan.remappedCount());
    }

    @Test
    public void duplicateEntryInsideSameSourceIsRejected() {
        Map<String, java.util.List<String>> paths = new LinkedHashMap<>();
        paths.put("Art\\Textures\\shared.tga",
            Arrays.asList("Hypersonic/Hypersonic.big", "Hypersonic/Hypersonic.big"));

        FusionAssetNamespacePlan.Result plan = FusionAssetNamespacePlan.build(paths);

        assertFalse(plan.isComplete());
        assertTrue(plan.unresolved.toString().contains("Duplicate asset entry"));
    }

    @Test
    public void absoluteAndTraversalPathsAreRejected() {
        Map<String, java.util.List<String>> paths = new LinkedHashMap<>();
        paths.put("..\\outside.tga", Arrays.asList("Hypersonic/Hypersonic.big"));

        FusionAssetNamespacePlan.Result plan = FusionAssetNamespacePlan.build(paths);

        assertFalse(plan.isComplete());
        assertTrue(plan.unresolved.toString().contains("Invalid virtual asset path"));
    }

    @Test
    public void duplicatePathAcrossArchivesOfSameModIsRejected() {
        Map<String, java.util.List<String>> paths = new LinkedHashMap<>();
        paths.put("Art\\Textures\\shared.tga",
            Arrays.asList("Hypersonic/Art.big", "Hypersonic/Units.big"));

        FusionAssetNamespacePlan.Result plan = FusionAssetNamespacePlan.build(paths);

        assertFalse(plan.isComplete());
        assertTrue(plan.unresolved.toString().contains("Duplicate asset entry"));
    }

}
