package com.generalsx.zerohour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class FusionFactionCatalogTest {
    private static final Charset INI = Charset.forName("ISO-8859-1");

    private static FusionIniBlockParser.Block block(String body, String source) {
        return FusionIniBlockParser.parse(body.getBytes(INI), source).blocks.get(0);
    }

    @Test
    public void keepsSameNamedFactionsFromDifferentModsAsSeparateSources() {
        List<FusionFactionCatalog.FactionSource> factions = FusionFactionCatalog.discover(
            Arrays.asList(
                block("PlayerTemplate FactionIran\n Side = Iran\n DisplayName = Iran\n"
                    + " PlayableSide = Yes\nEnd", "/mods/Hypersonic.big!Data/INI/PlayerTemplate.ini"),
                block("PlayerTemplate FactionIran\n Side = Iran\n DisplayName = Iran Alt\n"
                    + " PlayableSide = Yes\nEnd", "/mods/OtherMod.big!Data/INI/PlayerTemplate.ini")));

        assertEquals(2, factions.size());
        assertTrue(factions.get(0).isUsableRoot());
        assertTrue(factions.get(1).isUsableRoot());
        assertFalse(factions.get(0).stableKey().equals(factions.get(1).stableKey()));
        assertTrue(factions.get(0).sourceArchive.contains("Hypersonic"));
        assertTrue(factions.get(1).sourceArchive.contains("OtherMod"));
    }

    @Test
    public void marksIncompleteFactionRootsForReviewInsteadOfGuessing() {
        FusionFactionCatalog.FactionSource faction = FusionFactionCatalog.discover(
            Arrays.asList(block("PlayerTemplate FactionX\n Side = X\nEnd", "mod.big!factions.ini")))
            .get(0);

        assertFalse(faction.isUsableRoot());
        assertTrue(faction.unresolved.toString().contains("PlayableSide"));
    }

    @Test
    public void ignoresNonFactionDefinitions() {
        List<FusionFactionCatalog.FactionSource> factions = FusionFactionCatalog.discover(
            Arrays.asList(block("Weapon RifleWeapon\n PrimaryDamage = 10\nEnd", "mod.big!weapon.ini")));
        assertTrue(factions.isEmpty());
    }
    @Test
    public void acceptsRecognizedRootAlongsideOtherValidPlayerTemplateFields() {
        FusionFactionCatalog.FactionSource faction = FusionFactionCatalog.discover(
            Arrays.asList(block("PlayerTemplate FactionIran\\n"
                + " Side = Iran\\n"
                + " DisplayName = Iran\\n"
                + " PlayableSide = Yes\\n"
                + " StartingBuilding = IranCommandCenter\\n"
                + " StartingUnit0 = IranDozer\\n"
                + " IntrinsicSciences = SCIENCE_AMERICA\\n"
                + " PurchaseScienceCommandSet = IranScienceCommandSet\\n"
                + "End", "Hypersonic.big!Data/INI/PlayerTemplate.ini"))).get(0);

        assertTrue(faction.isUsableRoot());
        assertTrue(faction.unresolved.isEmpty());
    }

    @Test
    public void stillReportsMalformedPlayerTemplateLines() {
        FusionFactionCatalog.FactionSource faction = FusionFactionCatalog.discover(
            Arrays.asList(block("PlayerTemplate FactionIran\\n"
                + " Side = Iran\\n"
                + " PlayableSide = Yes\\n"
                + " StartingUnit0 IranDozer\\n"
                + "End", "Hypersonic.big!Data/INI/PlayerTemplate.ini"))).get(0);

        assertFalse(faction.isUsableRoot());
        assertTrue(faction.unresolved.toString().contains("malformed PlayerTemplate line"));
    }

}
