# Generals Mod Fusion — project status

Last updated: 2026-11-19

Latest implementation changes include a repaired three-source faction dependency regression fixture (`b4ac6683`), mod-folder archive path separator normalization (`0845b4a1`), the Zero Hour exact-source ThingTemplate BuildVariations/ObjectReskin parity fix (`f1a7976b`, `1f23a867`), and the earlier source-qualified runtime registry and projectile save/restore work: completed the Locomotor source-aware runtime chain audit and source-qualified current-locomotor save/load restoration; then isolated weapon-linked projectile-stream, laser, and projectile detonation-weapon lookups by their owning source. It resolves slave/parent particle references only inside the owning Fusion source namespace and deliberately does not fall back to base-game or another mod. The existing unqualified overload remains unchanged for legacy callers. FXList carries its owning Fusion source ID into each FX nugget. Tracer and ray-effect ThingTemplate references and particle-system references resolve through that source namespace (with the existing explicit base-game fallback), rather than using unqualified global lookups. This closes three concrete cross-mod collision paths inside FX execution. The mod-folder-aware INI lookup remains the previous foundational change. The native filesystem resolves a requested virtual file to the highest-priority matching archive whose full path is inside that mod folder, while preserving loose-file precedence. INI single-file opens and directory membership checks use this mod-folder lookup; directory scans are filtered to files from that selected mod. Android preflight now tags INI definitions with the selected mod-folder identity, so sibling `.big` archives share one faction namespace. These changes are foundational only; they are not yet compiled or tested, and non-INI runtime registries/assets remain incomplete. OCL and production-prerequisite reference paths have been audited for source-aware lookup; DamageFX now has a source-qualified registry and source-first reference lookup; remaining registries and visual/audio assets still need a full call-site audit.

## Goal (acceptance criteria)

Generals Mod Fusion is an Android launcher/runtime integration for the Generals Zero Hour mobile port. A player can select multiple compatible mods (including Hypersonic, The End of Days, ROTR, Contra RRD, Project X, JOINT and others), then start one normal Skirmish where factions from the selected mods can fight each other.

Each faction must resolve its own definitions and dependencies from its originating mod. This includes, at minimum, INI definitions and all referenced gameplay/runtime assets: units, weapons, armor, upgrades, science, special powers, locomotors, command sets/buttons, projectiles, OCL, FX, particles, AI/build lists, scripts, audio, models and textures. No accidental cross-mod fallback or resource substitution is acceptable. Source mod files must remain unchanged.

A buildable APK alone is not acceptance. Completion requires a successful Android build and a verified mixed-mod Skirmish on the target mobile port.

## Repository state

- Repository: https://github.com/mrK932103/GeneralsZH-mobile
- Working branch: `claude/generals-mod-fusion`
- Pull request: https://github.com/mrK932103/GeneralsZH-mobile/pull/1
- PR status at last known check: open draft; not merged.
- Latest native runtime code commit: `88f0a24f3d9396146df94f0913876f7f968dbfe0` (the newer commits add projectile save/restore fixes and N-way test/documentation coverage).

## Latest continuation — Locomotor source registry

The Locomotor template parser now reads `INI::getSourceId()` and stores Fusion-owned locomotor templates in a separate source-tagged registry instead of the legacy global NameKey map. A source-qualified lookup scans only templates owned by that source and then permits an explicit base-game lookup; it never selects a locomotor belonging to another selected mod. Same-source override lookup is source-local. The Fusion registry also participates in reset/override-chain cleanup and destruction. The change is mirrored in both `Generals` and `GeneralsMD`.

Files changed:
- `Generals/Code/GameEngine/Include/GameLogic/Locomotor.h`
- `Generals/Code/GameEngine/Source/GameLogic/Object/Locomotor.cpp`
- `GeneralsMD/Code/GameEngine/Include/GameLogic/Locomotor.h`
- `GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Locomotor.cpp`

Runtime chain audit update: the owning ThingTemplate's `Locomotor` field is parsed by `AIUpdateModuleData::parseLocomotorSet`, which reads `ini->getSourceId()` and resolves each template through the source-qualified LocomotorStore lookup. `AIUpdateInterface::chooseLocomotorSetExplicit` then builds that object's LocomotorSet directly from the resolved template pointers. This wiring is present in both `Generals` and `GeneralsMD`; it is no longer an unverified gap. A separate save/load ambiguity was fixed: `LocomotorSet::xferSelfAndCurLocoPtr` now writes a version-2 source ID alongside the current locomotor name and restores by both fields. Version-1 saves remain readable using the old name-only match. The regular LocomotorSet snapshot already carries per-entry source IDs in version 2. These code paths have been inspected, but no compile, save/load test, or mixed-mod runtime test was run.

### Weapon-linked runtime references

- `Weapon::newProjectileFired` now resolves the projectile-stream ThingTemplate using the weapon template's Fusion source ID, with the ThingFactory's explicit source-first/base-game fallback and no sibling-mod lookup.
- `Weapon::createLaser` now resolves the laser ThingTemplate through the same source-aware path. Required projectile, projectile-stream, and laser references retain the ThingFactory's checked lookup behavior, so missing dependencies still trigger the existing diagnostic path instead of silently producing a null template.
- `DumbProjectileBehavior::xfer` restores its detonation WeaponTemplate using the projectile object's owning ThingTemplate source ID and the WeaponStore's source-first/base-game lookup. This changes no serialized fields and retains the existing save format.
- Mirrored in `Generals` and `GeneralsMD` (`Weapon.cpp` and `DumbProjectileBehavior.cpp`).
- Commits: `2b69eacd153d34ce8d92ee0c40a8decd8bddca3e`, `3f7d2affcb4e5a8033418e31e16e1b10afbb6354`, `38724936c837078b708ff41b280dbaf423201d08`, `d8a596fecfdf5d5794bd7ffbd750de11be712f70`, `e8fea79f40ab3c144580d39e8034921ed38cbeba`, `e4028e5914412e3c70c7d20f5348bf24fc4a7294`.
- `WeaponSet::xfer` now writes the owning ThingTemplate source ID in snapshot version 3 (non-retail-compatible save mode) and restores the selected weapon set by source-qualified ThingTemplate lookup. Existing version 1/2 saves remain readable. In `RETAIL_COMPATIBLE_XFER_SAVE` mode the format intentionally remains version 1 and therefore retains legacy name-only restoration to avoid changing the retail-compatible wire format.
- Commits: `e47f224d0fcebbe4a50a359d8b07fff76e159fc8` (`Generals`) and `9cd40e9d1eae9c5f1853efb806e19bf50ea451c3` (`GeneralsMD`).
- Verification: source inspection only; no native build, save/load execution, Android build, or mixed-mod gameplay test.

### Locomotor runtime and save/load continuation

- Verified source propagation from the `ThingTemplate` `Locomotor` field through `AIUpdateModuleData::parseLocomotorSet` into `AIUpdateInterface::chooseLocomotorSetExplicit` and each object's `LocomotorSet`; the source-aware parsing/lookup is present in both `Generals` and `GeneralsMD`.
- `LocomotorSet::xferSelfAndCurLocoPtr` now serializes the current locomotor's source ID as version 2 and restores by name plus source. Version 1 save data still uses its historical name-only match. This prevents the current-locomotor pointer from binding to a same-named sibling-mod template after load.
- Commits: `5073ec1ceaa145e8b88f38ac498a27eed6481da2` (`Generals`) and `88f0a24f3d9396146df94f0913876f7f968dbfe0` (`GeneralsMD`).
- Verification: source inspection only; no native build, save/load execution, Android build, or mixed-mod Skirmish test.

## Implemented in the branch (not equivalent to end-to-end completion)

- Particle-system snapshot serialization is version 2: each saved particle system carries its owning Fusion source ID, and load resolves the template in that exact source namespace. Version-1 snapshots remain readable through the legacy name-only path. Destroyed/non-saveable entries write the matching empty source field in version 2 so the stream stays aligned.
- Particle texture preloading iterates both the base-game template registry and the source-qualified Fusion template registry, so textures declared by selected mods are included in the preload pass.
- ParticleSystemManager exposes a source-qualified parent/slave-template lookup. The legacy overload is unchanged; the new overload is exact-source only. Runtime `SlaveSystem` resolution now uses exact-source lookup for Fusion templates, with no cross-mod or base-game fallback.

- FXList now propagates its source ID into every FXNugget. TracerName, RayEffect template references and ParticleSystem nugget references use source-qualified registries, preventing same-named effects from another selected mod from being substituted at runtime.

- Android-side multi-mod selection and read-only BIGF/INI preflight work.
- INI declaration/reference inventory and conservative dependency tracing.
- Source-qualified faction-root catalog and N-way conflict diagnostics.
- Deterministic INI namespace planning and virtual asset-path planning.
- A conservative BIGF builder that refuses unsafe conflicts rather than silently overwriting source data.
- Native prototype work for source-tagged INI loads, mod-folder-aware archive lookup for INI files, PlayerTemplate source identity, combined Skirmish faction listing/selection, source propagation into player creation, and source-qualified weapon registration/lookup.
- ThingFactory has an explicit exact-source lookup API; ThingTemplate BuildVariations and ObjectReskin references now use exact-source resolution for Fusion-owned definitions.
- ArmorStore has a separate Fusion armor registry keyed by source identity and armor name; INI ArmorSet references resolve against their source mod first, then base-game definitions. Mirrored in Generals and GeneralsMD.
- UpgradeTemplate carries source identity; UpgradeCenter registers and resolves same-named upgrades within the owning Fusion source, while legacy lookups are restricted to base-game entries. INI Upgrade references use the source-aware lookup. Mirrored in Generals and GeneralsMD.
- SpecialPowerTemplate carries source identity; same-named powers are registered in separate source namespaces, source-local overrides remain isolated, and INI references prefer the selected source with base-game fallback. Mirrored in Generals and GeneralsMD.
- Some source-aware validation and script matching.

## Continuation — repair the three-source dependency regression fixture

The existing N-way regression test for AlphaMod, BravoMod and CharlieMod used double-escaped newline sequences in its Java string literals. At runtime those strings contained literal `\\n` text rather than line breaks, so the test did not reliably feed three valid PlayerTemplate/Object INI blocks to the parser. Replaced the 15 double-escaped sequences in that test fixture with normal Java newline escapes. The test now supplies actual multi-line INI text for all three mod sources while keeping the same-name `SharedCommandCenter` collision scenario.

File changed:
- `android/app/src/test/java/com/generalsx/zerohour/FusionFactionDependencyPlanTest.java`

Commit: `b4ac668341324996a5a217e6bad5688f2915d22b`.

Verification: inspected the committed fixture text and confirmed the Java source now contains ordinary `\\n` escapes. The JUnit test itself was not executed; no Android/Gradle build was run.

## Continuation — normalize mod-folder archive path separators

The native mod-folder archive resolver previously compared the selected mod-folder string and each archive path as-is. Android and archive registration can supply equivalent paths with different slash styles, causing a valid source archive to be skipped. `ArchiveFileSystem::getFileInstanceForMod` now normalizes backslashes to forward slashes in both paths, trims duplicate trailing separators from the folder, and keeps a trailing slash for a directory-boundary match. This prevents prefix collisions such as a folder named `ModA` matching a sibling `ModAB`.

File changed:
- `Core/GameEngine/Source/Common/System/ArchiveFileSystem.cpp`

Commit: `0845b4a135767e0a2af4f172ece311ee8881309f`.

Verification: source-level review only. No native build or runtime archive lookup test was run. Add regression coverage for mixed separators, sibling folders, priority order, loose-file precedence and missing source files before relying on this path in a live match.

## Continuation — exact-source ThingTemplate references in the Zero Hour engine

A parity gap was found between the two engine variants. The Generals engine already exposed an exact-source ThingTemplate lookup (no base-game substitution) and used it for `BuildVariations` and `ObjectReskin`. The Zero Hour / `GeneralsMD` engine had the same source-qualified registry, but its corresponding references still used the source-first lookup that may fall back to a base-game template. This could silently select a base-game definition when the selected mod's own referenced variation or reskin was missing.

The Zero Hour engine now mirrors the exact-source API and uses it for both `BuildVariations` and `ObjectReskin`. A missing same-source reference remains missing instead of being replaced by a same-named base-game definition. This does not search another selected mod and does not change the general source-first/base-game fallback API used by callers that intentionally allow shared base-game dependencies.

Files changed:
- `GeneralsMD/Code/GameEngine/Include/Common/ThingFactory.h`
- `GeneralsMD/Code/GameEngine/Source/Common/Thing/ThingFactory.cpp`

Commits:
- `f1a7976b6fc45fbbb5b75441161e516344073613` — declare exact-source lookup in the Zero Hour ThingFactory.
- `1f23a867c08db5ea27fcfefeaea2912400c35355` — use exact-source lookup for Zero Hour build variations and reskins.

Verification: source inspection and anchor-checked edits only. No native build, Android build, unit test, save/load test or mixed-mod Skirmish was run.

## Science isolation implementation

Science IDs are now generated from the source-qualified key `sourceId::scienceName` for Fusion sources; base-game entries retain their legacy unqualified key. INI single-science fields and science vectors pass the active INI source into the resolver, so prerequisite references and fields such as `RequiredScience` resolve to the correct mod namespace. `ScienceInfo` stores source ID and original internal name; duplicate/override matching is source-local. Player science state, acquisition, prerequisite checks, purchasing, and existing save vectors store the resulting `ScienceType`, so the qualified ID is carried through those paths without changing the save-vector layout. The API also has a source-aware `getScienceFromInternalName` overload. Mirrored in Generals and GeneralsMD.

Known limitation: the engine's `ScienceType` remains a NameKey hash value, so a theoretical hash collision is still possible. Build, save/load, multiplayer synchronization, UI selection and mixed-mod match tests are not verified. Source-first/base fallback is implemented in both Generals and GeneralsMD.


## Critical known gap

The source-aware INI entry point now parses the selected mod-folder path from the source identity and resolves files through `getFileInstanceForMod(...)`. That lookup searches the per-virtual-path archive priority list and accepts only archives whose full archive path is within the selected mod folder. The public FileSystem wrapper accounts for loose-file precedence. INI directory enumeration is still sourced from the merged virtual filesystem, then filtered by the mod-folder lookup before loading. The lookup now normalizes slash/backslash separators in both the selected mod-folder path and archive path before checking the folder boundary. This path has not been compiled or tested; archive priority, sibling archives, duplicate paths and missing-file fail-closed behavior remain to be verified.

Many non-INI runtime registries now have source-qualified lookup paths, but every caller still needs a complete source-context audit. The remaining high-risk areas include AI/build lists, teams and scripts, plus runtime audio, model, texture, animation and other asset loading. The source-aware registries do not by themselves prove that every gameplay path carries the correct faction source.

## Remaining work — required before calling it complete

1. Verify mod-folder source lookup against actual managed archive paths and archive priority ordering; add tests for mixed slash styles, sibling archives, duplicate paths, loose-file precedence and missing-source fail-closed behavior.
2. Wire every source-tagged INI load path through the mod-folder lookup, including all startup INI entry points; verify missing source files fail safely.
3. Complete the call-site and source-context audit across faction-dependent runtime systems: ThingTemplate (the registry is source-qualified; remaining callers still need auditing), Armor, Science, SpecialPower, Upgrade, Locomotor, CommandSet, CommandButton, projectile/OCL/FX/particle systems, AI/build lists, teams/scripts, audio and model/texture assets. Do not treat a source-aware registry as complete until all relevant callers use it.
4. Make asset/dependency resolution source-aware at runtime, not only in preflight planning.
5. Add tests with two archives containing the same relative INI/resource paths but different contents; prove each selected source resolves its own content and that missing source assets fail safely rather than falling through to another mod.
6. Build the Android/native project, resolve compile/link/runtime failures, and run a real mixed-mod Skirmish on the Android port.
7. Package and verify the final APK and provide the exact tested build/version and limitations.

## Verification status

- Android build: NOT VERIFIED.
- Native build: NOT VERIFIED (including the new particle-system snapshot version-2 change).
- Automated tests: NOT VERIFIED.
- Android device/emulator test: NOT VERIFIED.
- Mixed-mod Skirmish: NOT VERIFIED.
- Final APK: NOT BUILT / NOT DELIVERED.

Do not describe the project as finished until all required checks above have evidence.

## Continuation protocol for future chats

1. Read this file first, then inspect the latest commit on `claude/generals-mod-fusion` and the open PR.
2. Continue on this same branch unless there is a documented reason not to.
3. Before editing, inspect the current implementation and relevant call sites; do not rely on an old chat summary as a substitute for source inspection.
4. Commit each coherent change. After each change, update this file with the new commit, what changed, and exactly what was or was not verified.
5. Never claim a build, test, APK, or playable match exists unless it was actually run and its result recorded here.
6. Do not ask the user to restate the project requirements in a new chat. This file is the continuity record.


ObjectCreationList dependency audit: OCL definitions are stored in a source-qualified registry for Fusion INI sources. OCL references resolve the selected source first and may explicitly fall back to the base-game registry; they do not search another selected mod. OCL nuggets carry their owning source ID into runtime object/particle lookups, including transport/container/payload ThingTemplate references and debris object creation. ProductionPrerequisite unit names resolve against the owning source without cross-mod fallback. This audit confirms the current call paths in `ObjectCreationList.cpp`, `INI.cpp`, and `ProductionPrerequisite.cpp`; it is source inspection only, not a compile or gameplay test.


DamageFX source isolation: Fusion `DamageFX` definitions now use a separate registry keyed by the composite `sourceId::DamageFXName`, while ordinary base-game definitions remain in the legacy registry. INI references resolve the active source first and may fall back to base-game DamageFX, but never to another selected mod. The change is mirrored in Generals and GeneralsMD; the shared INI parser now passes its active source ID into the lookup. This prevents same-named damage effect sets in different mods from silently replacing each other. NameKey hashing retains the engine's theoretical collision risk. Compile and runtime validation remain outstanding.

## Continuation — source-aware LocomotorSet parsing

The runtime link identified as missing in the previous continuation is now wired: `AIUpdateModuleData::parseLocomotorSet` obtains the active INI source ID and resolves each named Locomotor through the source-qualified `LocomotorStore::findLocomotorTemplate(name, sourceId)` overload. When the INI has no Fusion source ID, the parser retains the original legacy NameKey lookup. This means same-named Locomotor definitions in two selected mods no longer cause a unit's LocomotorSet to select whichever mod happened to populate the global name map. The change is mirrored in both `Generals` and `GeneralsMD`.

The Zero Hour / `GeneralsMD` path retains its existing Android robustness behavior for unresolved Locomotor names (warn and skip the bad entry rather than aborting the whole INI load). The `Generals` path retains its original missing-template error behavior. The parser change is source inspection only: native compilation and mixed-mod runtime tests have not been run.

Files changed:
- `Generals/Code/GameEngine/Source/GameLogic/Object/Update/AIUpdate.cpp`
- `GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Update/AIUpdate.cpp`

Next required audit: check LocomotorSet upgrade/replacement and any other runtime paths that resolve locomotor names outside this INI parser; then continue with CommandSet/CommandButton and the remaining source-dependent registries/assets.

## Continuation — preserve Locomotor source identity in snapshots

A second Locomotor cross-mod path was found during the call-site audit: `LocomotorSet::xfer` previously saved only a template name, then restored it through the legacy global name map. That could reconstruct a mixed-mod unit with a sibling mod's same-named Locomotor after save/load or snapshot transfer.

The LocomotorSet transfer format is now version 2 and writes each Locomotor's source ID beside its template name. Version-2 loads use the source-qualified lookup; version-1 data remains readable and uses the legacy name-only lookup because old saves have no Fusion source metadata. `Locomotor` exposes a read-only accessor to its template's source ID for this transfer. Changes are mirrored in Generals and GeneralsMD.

Files changed:
- `Generals/Code/GameEngine/Include/GameLogic/Locomotor.h`
- `Generals/Code/GameEngine/Source/GameLogic/Object/Locomotor.cpp`
- `GeneralsMD/Code/GameEngine/Include/GameLogic/Locomotor.h`
- `GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Locomotor.cpp`

This is source-level implementation and compatibility inspection only. No native build, save/load test, network test, or mixed-mod Skirmish has been run. The LocomotorSet source identity is now preserved on its transfer path; continue auditing other snapshot/reference paths and then proceed to the next runtime registry.

## Continuation — source-qualified CommandSet and CommandButton namespaces

The shared ControlBar layer now gives each parsed CommandButton and CommandSet a Fusion source ID. Parsing uses the active INI source to find/create definitions in that source namespace; same-named buttons/sets from sibling mods no longer override one another. CommandSet entries resolve their referenced CommandButtons using the owning CommandSet's source ID, with an explicit base-game fallback and no sibling-mod fallback. Legacy name-only ControlBar lookups are now restricted to untagged base-game definitions, so old callers cannot accidentally select a Fusion mod's same-named UI definition.

Runtime call sites now use the source-aware lookup for:
- Object command sets used by ControlBar's special-power availability scan, using the owning ThingTemplate source ID.
- Player-template science-purchase and special-power-shortcut command sets, using PlayerTemplate's Fusion source ID.
- CommandButtonHuntUpdate's owning object and target object command sets in both Generals and GeneralsMD.

Files changed:
- `Core/GameEngine/Include/GameClient/ControlBar.h`
- `Core/GameEngine/Source/GameClient/GUI/ControlBar/ControlBar.cpp`
- `Generals/Code/GameEngine/Source/Common/INI/INICommandButton.cpp`
- `GeneralsMD/Code/GameEngine/Source/Common/INI/INICommandButton.cpp`
- `Generals/Code/GameEngine/Source/GameLogic/Object/Update/CommandButtonHuntUpdate.cpp`
- `GeneralsMD/Code/GameEngine/Source/GameLogic/Object/Update/CommandButtonHuntUpdate.cpp`

Important remaining work: audit every other ControlBar and gameplay caller that obtains a CommandSet or CommandButton by name, plus the CommandButton field references (ThingTemplate, UpgradeTemplate, SpecialPowerTemplate, weapon/science/asset references) to ensure each reference resolves from its owning source. This change is source-level only; no native build or mixed-mod UI/gameplay test has been run.

Additional hardening: the legacy `findNonConstCommandButton(name)` and `findNonConstCommandSet(name)` implementations now explicitly ignore source-tagged Fusion entries. This preserves their base-game semantics and prevents unconverted legacy callers from accidentally selecting whichever mod was loaded most recently.


Weapon / projectile / OCL / FX follow-up audit (source inspection): the current WeaponTemplate stores its Fusion source ID and registers same-named weapons in a separate source-qualified collection; weapon overrides are resolved within that source. WeaponTemplate::postProcessLoad resolves its projectile ThingTemplate, fire OCL, and projectile-detonation OCL from the owning source first, with the explicit base-game fallback only. The Weapon save transfer writes the source ID in version 4 and restores the template by (name, source ID); older versions retain the legacy name-only path. Projectile-stream and laser ThingTemplates are resolved from the owning weapon source. DumbProjectileBehavior restores its detonation weapon using the projectile ThingTemplate's source ID.

The OCL store uses a source-qualified registry; its nuggets inherit the OCL source ID and use it for transport/container/payload/object/particle references and nested fire-weapon references. FXList definitions likewise use a source-qualified registry; each FX nugget inherits the list's source ID, and the inspected tracer, ray-effect, particle-system and audio-event paths preserve that identity. The hard-coded GenericDebris template remains an intentional shared base-game helper, not a mod-declared dependency. This pass was an audit of existing implementation and did not add new runtime code. It does not prove all projectile behavior modules or every asset class are isolated; additional call-site audits, a native build, save/load validation, and mixed-mod Skirmish testing are still required.


Additional projectile save/load fixes: both Generals and GeneralsMD now restore MissileAIUpdate's detonation WeaponTemplate and exhaust ParticleSystemTemplate using the owning projectile object's ThingTemplate source ID, with an explicit base-game fallback and no sibling-mod fallback. Both NeutronMissileUpdate implementations restore their exhaust ParticleSystemTemplate the same way. Existing transfer versions and serialized fields are unchanged; the source is derived from the already-restored owning object template, so no save-format bump is required. These changes were source-edited and committed, but have not yet been compiled or exercised through an actual save/load cycle.


## N-way dependency-closure regression coverage

Added a three-source fixture to `FusionFactionDependencyPlanTest`: AlphaMod, BravoMod and CharlieMod each define a faction and a different `SharedCommandCenter` body under the same INI name. The test asserts that all three faction plans remain independently closed and that each resolves the shared name within its own mod-folder namespace. This directly guards the N-way requirement; it is a test addition only. The test has not been executed in this environment, and it does not prove native runtime isolation or a playable mixed-mod match.

## Continuation — trace the audio asset boundary

A source-context audit followed Fusion audio definitions from INI parsing into playback filename generation. `AudioEventInfo` records are stored under a composite source-qualified key, and `AudioEventRTS` carries the Fusion source ID when copied. `AudioManager::getInfoForAudioEvent` resolves the event definition using that source ID, with the explicit base-game fallback.

The audit also confirms that this is not yet end-to-end audio asset isolation: `AudioEventRTS::generateFilename` builds a normal shared virtual path from the event's filename/folder settings, and its localization probe calls the global `TheFileSystem->doesFileExist(...)`. The music-availability scan likewise checks generated paths through the global filesystem. These checks do not select the originating mod folder, so same-relative-path audio files can still be resolved from whichever archive wins the global virtual-file priority. No change was made to audio playback in this pass because the actual backend file-open/cache path must be traced before changing its ownership and lifetime semantics.

Files inspected:
- `Core/GameEngine/Source/Common/INI/INIAudioEventInfo.cpp`
- `Core/GameEngine/Source/Common/Audio/GameAudio.cpp`
- `Core/GameEngine/Source/Common/Audio/AudioEventRTS.cpp`
- `Core/GameEngine/Include/Common/AudioEventRTS.h`

Next audio task: trace the device/backend path from `AudioEventRTS::getFilename()` to the actual sample/stream open and cache operation, then pass source identity into that operation and test duplicate relative filenames across two selected mod folders. Also cover attack/decay samples and localized variants. This pass is source inspection only; no native build, audio playback test, Android build, or mixed-mod Skirmish was run.

Follow-up dispatch trace: `SoundManager::addAudioEvent` only queues an `AudioRequest`; the common `SoundManager::getFilenameForPlayFromAudioEvent` implementation returns an empty string. Therefore the final file-open path is not in this common implementation and must be located in the platform-specific sound backend/override before source-aware audio routing can be safely added. Do not route audio by changing only `AudioEventRTS::generateFilename`: that would still leave the global localized-file probe and backend cache identity unresolved.

## Audio backend audit correction — OpenAL is already source-aware

A deeper device-layer inspection found that the earlier audio note above stopped too early and must not be read as saying the Android OpenAL path is still globally routed. The Android OpenAL implementation already contains source-aware handling:

- `OpenALAudioFileCache::getBufferForFile` extracts the owning mod folder from the event's Fusion source ID, resolves the file with `getFileInstanceForMod`, and opens it using that exact file instance.
- Its cache key includes both the full Fusion source identity and the relative filename, preventing two selected mods with the same audio filename from sharing one decoded buffer.
- The OpenAL streaming path in `OpenALAudioManager::playAudioEvent` also resolves and opens the stream through the event's source folder.
- Attack, sound, and decay portions use the same source-qualified cache path.

This means the Android/OpenAL audio path has meaningful source isolation already implemented in the device layer. The global `TheFileSystem->doesFileExist` check in filename generation remains a separate localization/availability concern and should be checked for incorrect cross-mod fallback; it does not negate the exact-source open performed by the OpenAL backend.

A remaining cross-backend gap was found in the legacy Miles implementation: `AudioFileCache::openFile` still keys its cache only by relative filename and calls the global `TheFileSystem->openFile`. This can collide across mods if Fusion events are routed through Miles. It is not evidence of an Android/OpenAL failure, but it should be fixed or explicitly excluded by the supported-platform matrix before claiming cross-platform parity.

Files inspected:
- `Core/GameEngineDevice/Source/OpenALAudioDevice/OpenALAudioCache.cpp`
- `Core/GameEngineDevice/Source/OpenALAudioDevice/OpenALAudioManager.cpp`
- `Core/GameEngineDevice/Source/MilesAudioDevice/MilesAudioManager.cpp`
- `Core/GameEngineDevice/Include/MilesAudioDevice/MilesAudioManager.h`

No source code was changed in this audit pass. Native builds, Android builds, cache collision tests, and actual audio playback with two mods were not run. Next: add focused regression coverage for same-named audio files in two mod folders on OpenAL, inspect localized filename selection, and decide the Miles compatibility policy.
