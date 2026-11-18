# Generals Mod Fusion — project status

Last updated: 2026-11-18

## Goal (acceptance criteria)

Generals Mod Fusion is an Android launcher/runtime integration for the Generals Zero Hour mobile port. A player can select multiple compatible mods (including Hypersonic, The End of Days, ROTR, Contra RRD, Project X, JOINT and others), then start one normal Skirmish where factions from the selected mods can fight each other.

Each faction must resolve its own definitions and dependencies from its originating mod. This includes, at minimum, INI definitions and all referenced gameplay/runtime assets: units, weapons, armor, upgrades, science, special powers, locomotors, command sets/buttons, projectiles, OCL, FX, particles, AI/build lists, scripts, audio, models and textures. No accidental cross-mod fallback or resource substitution is acceptable. Source mod files must remain unchanged.

A buildable APK alone is not acceptance. Completion requires a successful Android build and a verified mixed-mod Skirmish on the target mobile port.

## Repository state

- Repository: https://github.com/mrK932103/GeneralsZH-mobile
- Working branch: `claude/generals-mod-fusion`
- Pull request: https://github.com/mrK932103/GeneralsZH-mobile/pull/1
- PR status at last known check: open draft; not merged.
- Last known code head before this status file: `ab7c28a8b1a780f5e8bb6ce0415b7ae507084afb`

## Implemented in the branch (not equivalent to end-to-end completion)

- Android-side multi-mod selection and read-only BIGF/INI preflight work.
- INI declaration/reference inventory and conservative dependency tracing.
- Source-qualified faction-root catalog and N-way conflict diagnostics.
- Deterministic INI namespace planning and virtual asset-path planning.
- A conservative BIGF builder that refuses unsafe conflicts rather than silently overwriting source data.
- Native prototype work for source-tagged INI loads, PlayerTemplate source identity, combined Skirmish faction listing/selection, source propagation into player creation, and source-qualified weapon registration/lookup.
- Some source-aware validation and script matching.

## Critical known gap

The source-aware INI entry point tags a load with a source ID, but the generic file load path still resolves through the merged virtual filesystem. The archive API exposes `getFileInstanceForArchive(...)`, but the source-aware INI load path has not yet been fully wired to open the exact originating archive. Therefore source IDs do not yet guarantee that each faction reads its own copy of a same-named INI file.

The Java-side source labels previously used mod/archive display names and entry paths; they are not yet a proven globally unique mapping to exact archive paths.

## Remaining work — required before calling it complete

1. Define stable source identities mapped to exact absolute mod/archive paths, including duplicate archive names.
2. Wire every source-tagged INI open/include/reference through exact archive-instance resolution; prevent fallback to another mod's same-named file.
3. Extend source isolation beyond PlayerTemplate and WeaponTemplate to every runtime registry and lookup used by factions: ThingTemplate, Armor, Science, SpecialPower, Upgrade, Locomotor, CommandSet, CommandButton, projectile/OCL/FX/particle systems, AI/build lists, teams/scripts, audio and model/texture assets.
4. Make asset/dependency resolution source-aware at runtime, not only in preflight planning.
5. Add tests with two archives containing the same relative INI/resource paths but different contents; prove each selected source resolves its own content and that missing source assets fail safely rather than falling through to another mod.
6. Build the Android/native project, resolve compile/link/runtime failures, and run a real mixed-mod Skirmish on the Android port.
7. Package and verify the final APK and provide the exact tested build/version and limitations.

## Verification status

- Android build: NOT VERIFIED.
- Native build: NOT VERIFIED.
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
