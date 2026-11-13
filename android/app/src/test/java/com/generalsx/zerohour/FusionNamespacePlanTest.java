package com.generalsx.zerohour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class FusionNamespacePlanTest {
    @Test
    public void sameNamedDefinitionsReceiveStableDistinctRuntimeNames() {
        List<FusionIniIndex.Definition> definitions = Arrays.asList(
            new FusionIniIndex.Definition("Weapon", "SharedCannon", null, "Hypersonic.big!weapon.ini", 4),
            new FusionIniIndex.Definition("Weapon", "SharedCannon", null, "TEOD.big!weapon.ini", 9));

        FusionNamespacePlan.Result first = FusionNamespacePlan.build(definitions,
            Collections.<FusionIniReferences.Reference>emptyList());
        FusionNamespacePlan.Result second = FusionNamespacePlan.build(definitions,
            Collections.<FusionIniReferences.Reference>emptyList());

        String hypersonic = first.runtimeName("Hypersonic.big!weapon.ini", "Weapon", "SharedCannon");
        String teod = first.runtimeName("TEOD.big!weapon.ini", "Weapon", "SharedCannon");
        assertNotEquals(hypersonic, teod);
        assertEquals(hypersonic, second.runtimeName("Hypersonic.big!weapon.ini", "Weapon", "SharedCannon"));
        assertEquals(teod, second.runtimeName("TEOD.big!weapon.ini", "Weapon", "SharedCannon"));
        assertTrue(first.isComplete());
    }

    @Test
    public void sameSourceReferenceResolvesToItsOwnDefinition() {
        String a = "Hypersonic.big!Data/INI/Weapon.ini";
        String b = "TEOD.big!Data/INI/Weapon.ini";
        List<FusionIniIndex.Definition> definitions = Arrays.asList(
            new FusionIniIndex.Definition("Weapon", "SharedCannon", null, a, 2),
            new FusionIniIndex.Definition("Weapon", "SharedCannon", null, b, 2));
        FusionIniReferences.Reference ref = new FusionIniReferences.Reference(
            "Weapon", "SharedCannon", a, 18, "object:iran_tank");

        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(definitions, Arrays.asList(ref));
        assertTrue(plan.isComplete());
        assertEquals(plan.runtimeName(a, "Weapon", "SharedCannon"),
            plan.references.get(0).runtimeName);
        assertNotEquals(plan.runtimeName(a, "Weapon", "SharedCannon"),
            plan.runtimeName(b, "Weapon", "SharedCannon"));
    }

    @Test
    public void ambiguousCrossSourceReferenceIsRejected() {
        List<FusionIniIndex.Definition> definitions = Arrays.asList(
            new FusionIniIndex.Definition("Weapon", "SharedCannon", null, "A.big!weapon.ini", 2),
            new FusionIniIndex.Definition("Weapon", "SharedCannon", null, "B.big!weapon.ini", 2));
        FusionIniReferences.Reference ref = new FusionIniReferences.Reference(
            "Weapon", "SharedCannon", "C.big!object.ini", 30, "object:third_faction");

        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(definitions, Arrays.asList(ref));
        assertFalse(plan.isComplete());
        assertTrue(plan.unresolved.toString().contains("ambiguous reference"));
    }

    @Test
    public void missingReferenceIsRejectedRatherThanGuessed() {
        FusionIniReferences.Reference ref = new FusionIniReferences.Reference(
            "Weapon", "MissingWeapon", "A.big!object.ini", 12, "object:test");
        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(
            Collections.<FusionIniIndex.Definition>emptyList(), Arrays.asList(ref));
        assertFalse(plan.isComplete());
        assertTrue(plan.unresolved.toString().contains("missing reference"));
    }

    @Test
    public void allBigArchivesInOneSelectedModShareOneNamespace() {
        String definitionSource = "Hypersonic/Units.big!Data/INI/Weapon.ini";
        String referenceSource = "Hypersonic/Gameplay.big!Data/INI/Object/Units.ini";
        FusionIniIndex.Definition definition = new FusionIniIndex.Definition(
            "Weapon", "IranTankCannon", null, definitionSource, 5);
        FusionIniReferences.Reference reference = new FusionIniReferences.Reference(
            "Weapon", "IranTankCannon", referenceSource, 44, "object:iran_tank");

        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(
            Arrays.asList(definition), Arrays.asList(reference));

        assertTrue(plan.isComplete());
        assertEquals(plan.runtimeName(definitionSource, "Weapon", "IranTankCannon"),
            plan.references.get(0).runtimeName);
    }


    @Test
    public void globallyUniqueDefinitionFromAnotherModIsNotAnImplicitFallback() {
        String definitionSource = "OnlyMod.big!Data/INI/Weapon.ini";
        FusionIniIndex.Definition definition = new FusionIniIndex.Definition(
            "Weapon", "UniqueCannon", null, definitionSource, 3);
        FusionIniReferences.Reference reference = new FusionIniReferences.Reference(
            "Weapon", "UniqueCannon", "AnotherMod/Gameplay.big!Data/INI/Object/Unit.ini",
            27, "object:unit");

        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(
            Arrays.asList(definition), Arrays.asList(reference));

        assertFalse(plan.isComplete());
        assertTrue(plan.unresolved.toString().contains("cross-mod reference rejected"));
    }

    @Test
    public void explicitlyDeclaredBaseGameDefinitionCanBeUsedAsFallback() {
        String baseSource = "BaseGame/INIZH.big!Data/INI/Weapon.ini";
        FusionIniIndex.Definition definition = new FusionIniIndex.Definition(
            "Weapon", "BaseCannon", null, baseSource, 3);
        FusionIniReferences.Reference reference = new FusionIniReferences.Reference(
            "Weapon", "BaseCannon", "Hypersonic/Units.big!Data/INI/Object/Unit.ini",
            27, "object:unit");

        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(
            Arrays.asList(definition), Arrays.asList(reference),
            Collections.singleton("BaseGame/INIZH.big"));

        assertTrue(plan.isComplete());
        assertEquals(plan.runtimeName(baseSource, "Weapon", "BaseCannon"),
            plan.references.get(0).runtimeName);
    }


    @Test
    public void weaponDelayedReferencesAreIncludedInSourceNamespacePlan() {
        String source = "Hypersonic/Weapons.big!Data/INI/Weapon.ini";
        String ini = "Weapon FusionWeapon\n"
            + "  ProjectileObject = FusionProjectile\n"
            + "  ProjectileStreamName = FusionProjectileStream\n"
            + "  FireOCL = CREATE_FUSION_MUZZLE\n"
            + "  ProjectileDetonationOCL = CREATE_FUSION_BLAST\n"
            + "  ProjectileExhaust = FusionExhaust\n"
            + "  ProjectileDetonationFX = FusionDetonationFX\n"
            + "  HistoricBonusWeapon = FusionBonusWeapon\n"
            + "End\n";
        List<FusionIniReferences.Reference> references = FusionIniReferences.scan(
            ini.getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), source);

        assertEquals(7, references.size());
        List<FusionIniIndex.Definition> definitions = Arrays.asList(
            new FusionIniIndex.Definition("Object", "FusionProjectile", null, source, 20),
            new FusionIniIndex.Definition("Object", "FusionProjectileStream", null, source, 21),
            new FusionIniIndex.Definition("ObjectCreationList", "CREATE_FUSION_MUZZLE", null, source, 22),
            new FusionIniIndex.Definition("ObjectCreationList", "CREATE_FUSION_BLAST", null, source, 23),
            new FusionIniIndex.Definition("ParticleSystem", "FusionExhaust", null, source, 24),
            new FusionIniIndex.Definition("FXList", "FusionDetonationFX", null, source, 25),
            new FusionIniIndex.Definition("Weapon", "FusionBonusWeapon", null, source, 26));

        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(definitions, references);
        assertTrue(plan.unresolved.toString(), plan.isComplete());
        for (FusionIniReferences.Reference reference : references) {
            assertEquals(plan.runtimeName(source, reference.kind, reference.name),
                plan.references.get(references.indexOf(reference)).runtimeName);
        }
    }

    @Test
    public void delayedWeaponDependenciesStayWithTheirOwningModWhenNamesCollide() {
        String hypersonic = "Hypersonic/Weapons.big!Data/INI/Weapon.ini";
        String teod = "TEOD/Weapons.big!Data/INI/Weapon.ini";
        String weaponIni = "Weapon FusionWeapon\n  ProjectileObject = FusionProjectile\nEnd\n";
        List<FusionIniReferences.Reference> references = new java.util.ArrayList<>();
        references.addAll(FusionIniReferences.scan(
            weaponIni.getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), hypersonic));
        references.addAll(FusionIniReferences.scan(
            weaponIni.getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), teod));
        List<FusionIniIndex.Definition> definitions = Arrays.asList(
            new FusionIniIndex.Definition("Object", "FusionProjectile", null,
                "Hypersonic/Objects.big!Data/INI/Projectile.ini", 10),
            new FusionIniIndex.Definition("Object", "FusionProjectile", null,
                "TEOD/Objects.big!Data/INI/Projectile.ini", 10));

        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(definitions, references);
        assertTrue(plan.unresolved.toString(), plan.isComplete());
        assertNotEquals(
            plan.references.get(0).runtimeName,
            plan.references.get(1).runtimeName);
        assertEquals(
            plan.runtimeName("Hypersonic/Objects.big!Data/INI/Projectile.ini",
                "Object", "FusionProjectile"),
            plan.references.get(0).runtimeName);
        assertEquals(
            plan.runtimeName("TEOD/Objects.big!Data/INI/Projectile.ini",
                "Object", "FusionProjectile"),
            plan.references.get(1).runtimeName);
    }
}
