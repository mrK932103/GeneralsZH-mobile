# Generals Mod Fusion — implementation plan

## Goal

Add a real, opt-in mod-fusion workflow to the existing Android launcher. A player should be able to select compatible Zero Hour mods, run a preflight analysis, build a separate Fusion output, and launch that output without changing the original mod folders or the retail game archives.

The feature is not the same as enabling several managed mods. The current managed-mod system mounts folders in priority order; it does not combine INI definitions or reconcile faction/UI registries.

## Repository findings (13 November 2026)

- The Android launcher already has `ModManagerActivity`, `ModManager`, `ModAnalysis`, `ModPacker`, and `ModImportService`.
- `ModManager.writeActiveList()` writes the enabled mod folders to the engine's managed-mod list. The existing order is priority order, not a semantic merge.
- `ModAnalysis` reads BIG/BIG4 file tables and detects overlapping paths. It does not extract archive entries or parse/merge INI sections.
- Two total conversions are explicitly reported as a red conflict. This is correct for ordinary overlay mode and must not be bypassed by Fusion.
- `ModPacker` builds a BIGF archive from loose game files. It is a useful format reference, but its current writer is not a multi-archive content merger.
- The launcher supports both Zero Hour and Generals, each with a separate game root. Fusion must be scoped to Zero Hour first; Generals must remain unaffected.
- The repository requires English source comments/docs, localized launcher strings in every locale, a monthly development diary, and local Android builds only. Do not dispatch GitHub Actions.

## Non-goals and safety rules

- Do not overwrite, rename, or modify source mod archives.
- Do not write Fusion output into the game's retail archive directory.
- Do not silently choose a winner for duplicate INI objects, command sets, command buttons, factions, or global registries.
- Do not claim arbitrary total conversions can be fused. Some combinations require manual porting of global INI/UI/faction-slot changes.
- Do not enable a Fusion set automatically after a failed build or failed game start.
- Do not alter `main`; all work stays on `claude/generals-mod-fusion`.

## Implementation status

### Completed — first archive-inventory layer

- Added `FusionBigArchive.java`: read-only BIGF parser with header/table bounds checks, entry-count and path-length limits, archive-size validation, and rejection of absolute/traversal paths. BIG4 is deliberately rejected until its layout is verified.
- Added `FusionPreflight.java`: inventories BIGF archives from selected managed-mod folders, records normalized virtual paths and their source archives, and reports invalid archives and duplicate paths. It does not modify source files.
- Added bounded entry reads to `FusionBigArchive`, allowing selected INI entries to be inspected without extracting arbitrary archive contents to disk.
- Added `FusionIniIndex.java`: a conservative line-level index for common named Generals definitions (Object, ChildObject, CommandSet, CommandButton, Weapon, Armor, Locomotor, FXList, ParticleSystem, Upgrade, Science, SpecialPower, AudioEvent, CrateData, MappedImage, PlayerTemplate, ObjectCreationList, OCL).
- Extended preflight to count these declarations and report duplicate definition names with their source archive and line. A duplicate is a review warning, not an automatic merge or proof of semantic incompatibility.
- Added `FusionIniReferences.java`: a heuristic scanner for typed references in common INI assignments (weapons, locomotors, armor, command sets/buttons, FX, OCL, particles, audio, upgrades, sciences, special powers, and objects).
- Extended preflight to index references and identify names not defined inside the selected mod archives. Such references are warnings only because the base game and loose INI files may supply them.
- These classes are not yet connected to the launcher UI or the game launch path. The scanner does not parse nested block semantics, resolve full transitive dependencies, or generate a playable merged mod.

## Planned architecture

### Phase 1 — inventory and deterministic preflight

1. Reuse the existing managed-mod library and per-game folder model.
2. Add a Fusion selection model that stores an explicit ordered list independently from the normal enabled-mod list.
3. Inspect each selected archive's file table and validate archive bounds, path safety, duplicate paths, and supported BIG variants.
4. Extract candidate INI files into a temporary workspace without changing source archives.
5. Parse INI structure conservatively: comments, sections, object blocks, key/value fields, and nested blocks must be retained. Unknown syntax is preserved, not rewritten.
6. Produce a report with hard blockers and review warnings. Initial hard blockers include malformed archives, unsafe paths, unsupported archive variants, unresolved duplicate object definitions, incompatible game targets, and conflicting total-conversion global/UI definitions.

### Phase 2 — semantic INI merge

1. Define merge rules by INI domain rather than applying a generic text concatenation.
2. Preserve unique object definitions and their transitive references.
3. Detect duplicate object names and distinguish identical definitions from conflicting definitions.
4. Treat global registries and faction/UI files as explicit conflict domains. No automatic faction-slot allocation until the engine's faction, command-bar, and UI assumptions are fully mapped.
5. Emit a machine-readable merge manifest and a human-readable report for every generated build.

### Phase 3 — asset and archive assembly

1. Resolve source files by normalized virtual path and explicit priority.
2. Merge only INI files with a validated semantic merge rule.
3. For models, textures, icons, audio, maps, and other binary assets, use deterministic source selection and report every collision.
4. Write a new BIG archive using a tested writer, validate offsets/sizes/table entries, and reopen it with the project's archive reader.
5. Build in a temporary directory and atomically publish only after all checks pass.

### Phase 4 — launcher and engine integration

1. Add a Fusion screen reachable from the existing Mods screen.
2. Provide select/reorder, Analyze, Build Fusion, and Play actions.
3. Store Fusion output outside the game root, in an app-managed per-game location.
4. Feed only the generated Fusion package to the existing managed-mod mount path; preserve normal mod mode.
5. Add recovery behavior: if Fusion fails to build or the game fails to boot, offer vanilla/normal-mod launch without deleting the generated package.
6. Localize every new launcher string in all supported values-* folders.

### Phase 5 — validation

- Unit tests for BIGF/BIG4 parsing, bounds checks, path normalization, duplicate handling, and INI parser round-trips.
- Fixture tests for unique objects, identical duplicate definitions, conflicting object definitions, faction/UI collisions, and transitive dependency references.
- Build and install the Android debug APK locally; do not start Actions.
- Device test with one compatible gameplay mod pair before attempting any total-conversion combination.
- Only after that, test a deliberately supported TEOD/Hypersonic subset. Full TEOD + Hypersonic faction fusion is not an acceptance claim until all global and UI conflicts are resolved.

## First implementation milestone

Complete a source-of-truth audit of the managed-mod engine mount path and the launcher entry points, then implement the archive reader and preflight report as a separately testable layer. No Fusion button should be presented as functional until the build pipeline can produce and validate a real output archive.

## Latest dependency-graph milestone

- Added `FusionDependencyGraph.java` to build a candidate dependency closure from indexed declarations, typed references, and declared parent/inheritance names.
- Exposed dependency tracing through `FusionPreflight.Result.traceDependencies(...)`.
- Reworked `FusionIniReferences.java` to bind recognized typed references to the nearest recognized declaration block using brace depth, instead of assigning every reference in an INI file to every definition in that file.
- Updated `FusionDependencyGraph.java` to follow only references whose owner key matches the current definition. Also corrected `ChildObject` parent lookup to resolve against an `Object` definition.
- Remaining parser limitations: this is still a lexical scanner, not a complete Generals INI parser. It assumes recognized declarations open brace-delimited blocks, does not model every nested semantic construct, and cannot resolve macros, dynamic references, or fields not yet included in the typed reference map. Results remain advisory and must not drive automatic merge/extraction yet.
- Confirmed the existing `ModPacker` documents the BIGF header layout used by the reader: little-endian archive size and big-endian entry count/header size.
- No UI, merge writer, or engine launch integration has been implemented yet. No APK build or device test has been run.
