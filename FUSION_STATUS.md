# Generals Mod Fusion — project status

Last updated: 2026-11-18

Latest implementation change: added a source-qualified ParticleSystemManager::findParentTemplate overload. It resolves slave/parent particle references only inside the owning Fusion source namespace and deliberately does not fall back to base-game or another mod. The existing unqualified overload remains unchanged for legacy callers. FXList carries its owning Fusion source ID into each FX nugget. Tracer and ray-effect ThingTemplate references and particle-system references resolve through that source namespace (with the existing explicit base-game fallback), rather than using unqualified global lookups. This closes three concrete cross-mod collision paths inside FX execution. The mod-folder-aware INI lookup remains the previous foundational change. The native filesystem resolves a requested virtual file to the highest-priority matching archive whose full path is inside that mod folder, while preserving loose-file precedence. INI single-file opens and directory membership checks use this mod-folder lookup; directory scans are filtered to files from that selected mod. Android preflight now tags INI definitions with the selected mod-folder identity, so sibling `.big` archives share one faction namespace. These changes are foundational only; they are not yet compiled or tested, and non-INI runtime registries/assets remain incomplete.

## Goal (acceptance criteria)

Generals Mod Fusion is an Android launcher/runtime integration for the Generals Zero Hour mobile port. A player can select multiple compatible mods (including Hypersonic, The End of Days, ROTR, Contra RRD, Project X, JOINT and others), then start one normal Skirmish where factions from the selected mods can fight each other.

Each faction must resolve its own definitions and dependencies from its originating mod. This includes, at minimum, INI definitions and all referenced gameplay/runtime assets: units, weapons, armor, upgrades, science, special powers, locomotors, command sets/buttons, projectiles, OCL, FX, particles, AI/build lists, scripts, audio, models and textures. No accidental cross-mod fallback or resource substitution is acceptable. Source mod files must remain unchanged.

A buildable APK alone is not acceptance. Completion requires a successful Android build and a verified mixed-mod Skirmish on the target mobile port.

## Repository state

- Repository: https://github.com/mrK932103/GeneralsZH-mobile
- Working branch: `claude/generals-mod-fusion`
- Pull request: https://github.com/mrK932103/GeneralsZH-mobile/pull/1
- PR status at last known check: open draft; not merged.
- Latest code commit: `25c6f9407a563444576bc09312746ec68b3ba786`

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
- Some source-aware validation and script matching.

## Critical known gap

The source-aware INI entry point now parses the selected mod-folder path from the source identity and resolves files through `getFileInstanceForMod(...)`. That lookup searches the per-virtual-path archive priority list and accepts only archives whose full archive path is within the selected mod folder. The public FileSystem wrapper accounts for loose-file precedence. INI directory enumeration is still sourced from the merged virtual filesystem, then filtered by the mod-folder lookup before loading. This path has not been compiled or tested; verify path normalization, archive ordering, duplicate files across sibling archives, and missing-file fail-closed behavior.

All non-INI runtime resource lookups still need source isolation. A mod-folder-aware INI loader does not by itself isolate ThingTemplate, Armor, Science, SpecialPower, Upgrade, Locomotor, CommandSet, CommandButton, projectiles, OCL, FX, particles, AI/build lists, scripts, audio, models or textures.

## Remaining work — required before calling it complete

1. Verify mod-folder source lookup against actual managed archive paths and archive priority ordering; add tests for sibling archives, duplicate paths and loose-file precedence.
2. Wire every source-tagged INI load path through the mod-folder lookup, including all startup INI entry points; verify missing source files fail safely.
3. Extend source isolation beyond PlayerTemplate and WeaponTemplate to every runtime registry and lookup used by factions: ThingTemplate, Armor, Science, SpecialPower, Upgrade, Locomotor, CommandSet, CommandButton, projectile/OCL/FX/particle systems, AI/build lists, teams/scripts, audio and model/texture assets.
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
