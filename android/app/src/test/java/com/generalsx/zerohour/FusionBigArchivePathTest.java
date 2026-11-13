package com.generalsx.zerohour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class FusionBigArchivePathTest {
    @Test
    public void normalizesSeparatorsAndCaseForVirtualPathComparison() {
        assertEquals("data\\ini\\object\\units.ini",
            FusionBigArchive.normalizePath("Data/INI/Object/Units.ini"));
        assertEquals("art\\w3d\\tank.w3d",
            FusionBigArchive.normalizePath("Art\\\\W3D\\Tank.W3D"));
    }

    @Test
    public void ignoresRedundantCurrentDirectorySegments() {
        assertEquals("data\\ini\\object.ini",
            FusionBigArchive.normalizePath("./Data//INI/./Object.ini"));
    }

    @Test
    public void rejectsParentTraversal() {
        assertRejected("../outside.ini");
        assertRejected("Data/../../outside.ini");
    }

    @Test
    public void rejectsAbsoluteAndDriveQualifiedPaths() {
        assertRejected("/Data/INI/Object.ini");
        assertRejected("\\\\server\\share\\file.big");
        assertRejected("C:/Windows/file.ini");
    }

    @Test
    public void rejectsNullAndEmptyPathsAsEmpty() {
        assertEquals("", FusionBigArchive.normalizePath(null));
        assertEquals("", FusionBigArchive.normalizePath("./"));
    }

    private static void assertRejected(String path) {
        try {
            FusionBigArchive.normalizePath(path);
            fail("Expected unsafe path to be rejected: " + path);
        } catch (IllegalArgumentException expected) {
            // Expected: archive paths must remain inside their virtual root.
        }
    }
}
