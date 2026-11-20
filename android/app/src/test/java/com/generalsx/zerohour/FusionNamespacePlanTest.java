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
            + "  FireFX = FusionFireFX\n"
            + "  FireSound = FusionFireSound\n"
            + "  VeterancyFireFX = VETERANCY_ELITE FusionVeterancyFireFX\n"
            + "  VeterancyFireOCL = VETERANCY_ELITE CREATE_VET_MUZZLE\n"
            + "  VeterancyProjectileDetonationOCL = VETERANCY_HEROIC CREATE_VET_BLAST\n"
            + "  VeterancyProjectileExhaust = VETERANCY_ELITE FusionVeterancyExhaust\n"
            + "  VeterancyProjectileDetonationFX = VETERANCY_HEROIC FusionVeterancyDetonationFX\n"
            + "End\n";
        List<FusionIniReferences.Reference> references = FusionIniReferences.scan(
            ini.getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), source);

        assertEquals(14, references.size());
        List<FusionIniIndex.Definition> definitions = Arrays.asList(
            new FusionIniIndex.Definition("Object", "FusionProjectile", null, source, 20),
            new FusionIniIndex.Definition("Object", "FusionProjectileStream", null, source, 21),
            new FusionIniIndex.Definition("ObjectCreationList", "CREATE_FUSION_MUZZLE", null, source, 22),
            new FusionIniIndex.Definition("ObjectCreationList", "CREATE_FUSION_BLAST", null, source, 23),
            new FusionIniIndex.Definition("ParticleSystem", "FusionExhaust", null, source, 24),
            new FusionIniIndex.Definition("FXList", "FusionDetonationFX", null, source, 25),
            new FusionIniIndex.Definition("Weapon", "FusionBonusWeapon", null, source, 26),
            new FusionIniIndex.Definition("FXList", "FusionFireFX", null, source, 27),
            new FusionIniIndex.Definition("AudioEvent", "FusionFireSound", null, source, 28),
            new FusionIniIndex.Definition("FXList", "FusionVeterancyFireFX", null, source, 29),
            new FusionIniIndex.Definition("ObjectCreationList", "CREATE_VET_MUZZLE", null, source, 30),
            new FusionIniIndex.Definition("ObjectCreationList", "CREATE_VET_BLAST", null, source, 31),
            new FusionIniIndex.Definition("ParticleSystem", "FusionVeterancyExhaust", null, source, 32),
            new FusionIniIndex.Definition("FXList", "FusionVeterancyDetonationFX", null, source, 33));

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

    @Test
    public void objectCreationListsExposeNestedRuntimeDependencies() {
        String source = "Hypersonic/Gameplay.big!Data/INI/ObjectCreationList.ini";
        String ini = "ObjectCreationList CREATE_FUSION_PAYLOAD\n"
            + "  Transport = FusionTransport\n"
            + "  PutInContainer = FusionContainer\n"
            + "  Payload = FusionPayload 3\n"
            + "  ObjectNames = FusionDebrisA FusionDebrisB\n"
            + "  ParticleSystem = FusionDebrisSmoke\n"
            + "  FadeSound = FusionFadeSound\n"
            + "  FXFinal = FusionFinalFX\n"
            + "  BounceSound = FusionBounceSound\n"
            + "  FireWeapon\n"
            + "    Weapon = FusionOCLWeapon\n"
            + "  End\n"
            + "End\n";
        List<FusionIniReferences.Reference> references = FusionIniReferences.scan(
            ini.getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), source);

        assertEquals(10, references.size());
        assertEquals("Object", references.get(0).kind);
        assertEquals("FusionTransport", references.get(0).name);
        assertEquals("Object", references.get(1).kind);
        assertEquals("FusionContainer", references.get(1).name);
        assertEquals("Object", references.get(2).kind);
        assertEquals("FusionPayload", references.get(2).name);
        assertEquals("Object", references.get(3).kind);
        assertEquals("FusionDebrisA", references.get(3).name);
        assertEquals("Object", references.get(4).kind);
        assertEquals("FusionDebrisB", references.get(4).name);
        assertEquals("ParticleSystem", references.get(5).kind);
        assertEquals("AudioEvent", references.get(6).kind);
        assertEquals("FXList", references.get(7).kind);
        assertEquals("FusionFinalFX", references.get(7).name);
        assertEquals("AudioEvent", references.get(8).kind);
        assertEquals("FusionBounceSound", references.get(8).name);
        assertEquals("Weapon", references.get(9).kind);
        assertEquals("FusionOCLWeapon", references.get(9).name);
    }

    @Test
    public void fxListNuggetsExposeNestedAudioParticleAndFxReferences() {
        String source = "TEOD/Effects.big!Data/INI/FXList.ini";
        String ini = "FXList FUSION_COMPOSITE_FX\n"
            + "  Sound\n"
            + "    Name = FUSION_IMPACT_SOUND\n"
            + "  End\n"
            + "  ParticleSystem\n"
            + "    Name = FUSION_IMPACT_SMOKE\n"
            + "  End\n"
            + "  FXListAtBonePos\n"
            + "    FX = FUSION_SECONDARY_FX\n"
            + "  End\n"
            + "  RayEffect\n"
            + "    Name = FUSION_RAY_OBJECT\n"
            + "  End\n"
            + "  Tracer\n"
            + "    TracerName = FUSION_TRACER_OBJECT\n"
            + "  End\n"
            + "End\n";
        List<FusionIniReferences.Reference> references = FusionIniReferences.scan(
            ini.getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), source);

        assertEquals(5, references.size());
        assertEquals("AudioEvent", references.get(0).kind);
        assertEquals("FUSION_IMPACT_SOUND", references.get(0).name);
        assertEquals("ParticleSystem", references.get(1).kind);
        assertEquals("FUSION_IMPACT_SMOKE", references.get(1).name);
        assertEquals("FXList", references.get(2).kind);
        assertEquals("FUSION_SECONDARY_FX", references.get(2).name);
        assertEquals("Object", references.get(3).kind);
        assertEquals("FUSION_RAY_OBJECT", references.get(3).name);
        assertEquals("Object", references.get(4).kind);
        assertEquals("FUSION_TRACER_OBJECT", references.get(4).name);
    }

    @Test
    public void sameNamedFxNuggetDependenciesRemainSourceLocal() {
        String hypersonic = "Hypersonic/Effects.big!Data/INI/FXList.ini";
        String teod = "TEOD/Effects.big!Data/INI/FXList.ini";
        String fxIni = "FXList FUSION_IMPACT\n"
            + "  Sound\n"
            + "    Name = FUSION_IMPACT_SOUND\n"
            + "  End\n"
            + "  ParticleSystem\n"
            + "    Name = FUSION_IMPACT_SMOKE\n"
            + "  End\n"
            + "End\n";
        List<FusionIniReferences.Reference> references = new java.util.ArrayList<>();
        references.addAll(FusionIniReferences.scan(
            fxIni.getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), hypersonic));
        references.addAll(FusionIniReferences.scan(
            fxIni.getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), teod));
        List<FusionIniIndex.Definition> definitions = Arrays.asList(
            new FusionIniIndex.Definition("AudioEvent", "FUSION_IMPACT_SOUND", null,
                "Hypersonic/Audio.big!Data/INI/AudioEvent.ini", 5),
            new FusionIniIndex.Definition("AudioEvent", "FUSION_IMPACT_SOUND", null,
                "TEOD/Audio.big!Data/INI/AudioEvent.ini", 5),
            new FusionIniIndex.Definition("ParticleSystem", "FUSION_IMPACT_SMOKE", null,
                "Hypersonic/Particles.big!Data/INI/ParticleSystem.ini", 7),
            new FusionIniIndex.Definition("ParticleSystem", "FUSION_IMPACT_SMOKE", null,
                "TEOD/Particles.big!Data/INI/ParticleSystem.ini", 7));

        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(definitions, references);
        assertTrue(plan.unresolved.toString(), plan.isComplete());
        assertEquals(4, plan.references.size());
        assertNotEquals(plan.references.get(0).runtimeName, plan.references.get(2).runtimeName);
        assertNotEquals(plan.references.get(1).runtimeName, plan.references.get(3).runtimeName);
        assertEquals(plan.runtimeName("Hypersonic/Audio.big!Data/INI/AudioEvent.ini",
            "AudioEvent", "FUSION_IMPACT_SOUND"), plan.references.get(0).runtimeName);
        assertEquals(plan.runtimeName("TEOD/Audio.big!Data/INI/AudioEvent.ini",
            "AudioEvent", "FUSION_IMPACT_SOUND"), plan.references.get(2).runtimeName);
    }
    @Test
    public void childObjectDefinitionsResolveAsObjectReferences() {
        String source = "Hypersonic/Objects.big!Data/INI/Objects.ini";
        FusionIniIndex.Definition child = new FusionIniIndex.Definition(
            "ChildObject", "IranTankTurret", "DefaultTankTurret", source, 12);
        FusionIniReferences.Reference reference = new FusionIniReferences.Reference(
            "Object", "IranTankTurret", source, 30, "object:iran_tank");

        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(
            Arrays.asList(child), Arrays.asList(reference));

        assertTrue(plan.unresolved.toString(), plan.isComplete());
        assertEquals(plan.runtimeName(source, "ChildObject", "IranTankTurret"),
            plan.references.get(0).runtimeName);
        assertEquals(plan.runtimeName(source, "Object", "IranTankTurret"),
            plan.references.get(0).runtimeName);
    }

    @Test
    public void thirdDuplicateDeclarationInOneSourceRemainsRejected() {
        String source = "Hypersonic/Objects.big!Data/INI/Weapon.ini";
        List<FusionIniIndex.Definition> definitions = Arrays.asList(
            new FusionIniIndex.Definition("Weapon", "RepeatedWeapon", null, source, 1),
            new FusionIniIndex.Definition("Weapon", "RepeatedWeapon", null, source, 8),
            new FusionIniIndex.Definition("Weapon", "RepeatedWeapon", null, source, 15));

        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(
            definitions, Collections.<FusionIniReferences.Reference>emptyList());

        assertFalse(plan.isComplete());
        assertTrue(plan.unresolved.toString().contains("Duplicate declaration in source"));
        assertEquals(null, plan.runtimeName(source, "Weapon", "RepeatedWeapon"));
    }

    @Test
    public void oclAliasKeepsOclSpecificNestedDependencies() {
        String source = "TEOD/Gameplay.big!Data/INI/ObjectCreationList.ini";
        String ini = "OCL CREATE_TEOD_TRANSPORT\n"
            + "  Transport = TEOD_TransportObject\n"
            + "End\n";
        List<FusionIniReferences.Reference> references = FusionIniReferences.scan(
            ini.getBytes(java.nio.charset.Charset.forName("ISO-8859-1")), source);
        assertEquals(1, references.size());
        assertEquals("Object", references.get(0).kind);
        assertEquals("TEOD_TransportObject", references.get(0).name);

        FusionIniIndex.Definition alias = new FusionIniIndex.Definition(
            "OCL", "CREATE_TEOD_TRANSPORT", null, source, 1);
        FusionNamespacePlan.Result plan = FusionNamespacePlan.build(
            Arrays.asList(alias), Collections.<FusionIniReferences.Reference>emptyList());
        assertEquals(plan.runtimeName(source, "ObjectCreationList", "CREATE_TEOD_TRANSPORT"),
            plan.runtimeName(source, "OCL", "CREATE_TEOD_TRANSPORT"));
    }

}
