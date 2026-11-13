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
4. Write a new BIG archive using `FusionBigArchiveWriter`; reject duplicate normalized virtual paths, enforce format bounds, validate offsets/sizes/table entries, and reopen it with `FusionBigArchive`. The writer is an archive primitive only; semantic INI merging remains a separate prerequisite.
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
- Reworked `FusionIniReferences.java` to scope typed references to recognized declaration owners using Generals-style `End` terminators and a conservative set of nested block fields; it no longer assumes C-like braces.
- Updated `FusionDependencyGraph.java` to index references by owner once before traversal, follow only references owned by the current definition, and resolve `ChildObject` parents against `Object` definitions.
- Remaining parser limitations: this is still a lexical heuristic, not a complete Generals INI parser. Nested block openers are not yet exhaustively modeled; macros, dynamic references, inherited fields, and unrecognized assignment types can be missed. Results remain advisory and must not drive automatic merge/extraction yet.
- Confirmed the existing `ModPacker` documents the BIGF header layout used by the reader: little-endian archive size and big-endian entry count/header size.
- Added `FusionBigArchiveWriter.java` as a collision-rejecting, streaming BIGF archive assembly primitive with post-write reopen/table validation.
- Added `FusionArchiveBuild.java` as a strict orchestration layer. It reruns preflight, blocks duplicate virtual paths and duplicate named INI definitions, prevents output inside source mod folders, and combines only non-overlapping entries. It does not yet merge same-path INI files, resolve faction/UI conflicts, include loose files, or integrate with the launcher.
- Hardened `FusionBigArchiveWriter.java`: unique temporary output file, preservation/rollback of an existing destination, source archive size re-check while copying, and verification of output offsets as well as paths and sizes.
- Hardened `FusionPreflight.java` to detect loose files under the game's `Data`, `Art`, `Window`, `Maps`, `Shaders`, `Movies`, `Audio`, and `Fonts` layout roots and block the build. This prevents a BIGF-only output from silently dropping mod content; loose-file packing is still not implemented. The existing engine managed-mod list still mounts separate mod folders in priority order; Fusion output must be a single generated package and must not be represented as an ordinary multi-mod overlay. No APK build or device test has been run.


### Writer safety follow-up

- `FusionBigArchiveWriter` now resolves the output path canonically, refuses an existing directory or symbolic-link target, pads short temporary-file prefixes, and consistently publishes/restores via the canonical destination. This is still an archive-level safety primitive, not semantic mod fusion.


### Additional safety audit (13 November 2026)

- Rejected BIGF entry paths that cannot round-trip through the archive's ISO-8859-1 path encoding, preventing silent replacement-character corruption.
- Changed backup handling to use a unique temporary backup name per build, avoiding collisions with stale backups left by interrupted earlier runs.
- Added an explicit symbolic-link check in the build orchestrator before canonicalizing the requested output path.
- Moved the empty-selection check ahead of preflight work.
- Added a preflight blocker for .gib files because the current reader and builder support validated .big/BIGF archives only; unsupported archives are no longer silently omitted.
- These are source-level hardening changes only; they have not been compiled or device-tested.


### Byte-identical path handling (13 November 2026)

- The archive build now permits a repeated virtual path only when the entry bytes are exactly identical. It hashes colliding entries with SHA-256 using a bounded streaming buffer, keeps one copy, and rejects any content mismatch. Unique entries are not pre-hashed.
- This is file-level deduplication only. It does not merge INI definitions or decide mod load order. Duplicate named INI definitions remain blocked, including identical definitions until the INI-level merger can prove a safe combination.
- Hashing is an additional full read for colliding files; source archive length is checked before reading. The writer still performs its own source consistency checks when publishing.
- Not compiled or device-tested.


### Identical INI archive-entry indexing (13 November 2026)

- Preflight now hashes each selected `Data\\INI\\*.ini` entry and indexes each distinct byte sequence once per normalized virtual path.
- If the same INI path is repeated byte-for-byte across selected archives, it no longer creates artificial duplicate declaration/reference records in the dependency index.
- If one virtual INI path has multiple different byte variants, each distinct variant is indexed so conflicting declarations remain visible. Repeated copies of any already-seen variant are skipped.
- This affects diagnostics and dependency indexing only. Archive assembly still independently verifies repeated file bytes, and duplicate named definitions across different INI paths remain conflicts.
- The INI payload is already read under the existing 16 MiB entry limit; SHA-256 is computed in memory for that bounded payload. This has not been compiled or device-tested.


### Nested INI block scanner refinement (13 November 2026)

- Extended the typed-reference scanner to recognize a conservative explicit list of common bare nested block headers (for example, `DefaultConditionState`, `ModelConditionState`, `WeaponSet`, and `Turret`) in addition to assignment-style module openers.
- This prevents a nested `End` from prematurely closing the owning object in these recognized forms, which previously could cause later references in the same object to be missed.
- The list is intentionally explicit. Unknown bare syntax is not guessed to be a block because some legacy INI properties may use whitespace syntax. The scanner remains heuristic and is not safe as an automatic merge parser.
- No representative in-repository mod INI corpus or unit-test source set was found for exhaustive parser validation. No build/device test has been run.


### Deterministic input selection hardening (13 November 2026)

- The archive builder now rejects selecting the same canonical mod directory more than once and rejects missing, non-directory, or symbolic-link source roots before archive collection.
- Source archive traversal is sorted case-insensitively by file name so the generated BIGF entry order is deterministic for a fixed selected-mod order and file tree.
- Removed a no-op duplicate-path branch; byte-level duplicate handling remains in the actual entry collection path.
- Source-only change. It has not been compiled or run through Android Gradle; semantic INI/faction merging and launcher integration remain unfinished.


### INI scope diagnostics follow-up (13 November 2026)

- Added `FusionIniStructure.java`, a read-only structural diagnostic pass for recognized End-terminated declaration scopes and common nested block headers.
- Preflight now reports unmatched recognized `End` tokens, declarations that begin before a recognized scope closes, and recognized declarations/nested blocks that reach EOF without a matching `End`.
- These findings are warnings, not proof that a file is invalid: the scanner intentionally recognizes only a bounded subset of Generals INI syntax. Unknown syntax is not rewritten, and these diagnostics do not authorize automatic INI merging.
- The new pass runs only for distinct INI byte variants already admitted by the existing bounded 16 MiB entry read. It does not add archive writes or extract files.
- No Android compilation, fixture tests, APK build, or device validation has been performed yet.


### INI scope stack refinement (13 November 2026)

- Replaced the single integer nesting counter in `FusionIniStructure` with explicit recognized scope frames carrying block names, opening line numbers, and declaration identity.
- A premature named declaration now reports the exact previous declaration and opening line before recovery; EOF diagnostics identify the innermost unclosed block and its containing declaration.
- This improves diagnostic specificity and avoids losing the identity of the declaration when nested blocks remain open.
- The scanner remains a bounded lexical heuristic. It does not validate unknown block forms or make automatic merge decisions. No compile, fixture run, or device test has been performed.


### Conflict diagnostics refinement (13 November 2026)

- Added bounded, source-located summaries for duplicate INI declaration keys and duplicate virtual archive paths in preflight results.
- The strict archive builder now includes up to 20 duplicate declaration keys and their archive/file/line locations in its blocking error, including parent names where present.
- This is diagnostics only: duplicate definitions remain blocked; no conflicting declaration is selected or silently overwritten.
- No Android compile, automated fixture test, or device validation has been performed.


### Source-aware collision diagnostics (13 November 2026)

- Added a lookup that maps a normalized virtual path back to all source mod/archive names found during preflight.
- When two archives contain different bytes at the same virtual path, the strict builder now reports all known source archives alongside the conflicting path.
- This remains a refusal, not a winner-selection rule. The builder still does not merge same-path INI content or reconcile game semantics.
- Repository-only edits have not been compiled; no APK or in-game validation is claimed.


### General multi-mod compatibility requirement (13 November 2026)

Fusion is an N-mod system, not a TEOD/Hypersonic integration. No implementation may hardcode a supported pair or assume a fixed faction count.

- The input model must accept any number of selected Zero Hour mods, preserve an explicit user-selected order, and record each mod's source identity, version/metadata when available, archive inventory, loose files, and detected game target.
- Compatibility analysis must be based on content domains and engine constraints, not mod names: object definitions and inheritance, weapons/behaviors, upgrades/sciences/special powers, command sets/buttons, player templates/faction registries, command-bar/UI assets, maps, scripts, audio, models, textures, and global INI settings.
- Merge rules must be domain-specific and composable across N inputs. A pairwise “A + B” patch is not an acceptable architecture. The same rules must handle three or more mods and identify conflicts involving any subset of them.
- Define explicit support outcomes: (1) automatically mergeable content, (2) mergeable only with user-resolved choices or generated ID remapping, (3) requires a manual compatibility adapter/port, and (4) unsupported. Never label categories 2–4 as automatically compatible.
- Preserve provenance for every emitted or rejected definition/file: originating mod, archive, virtual path, and INI line/block where available. Conflict reports must identify all participants, not just the first collision.
- Add no mod-name allowlist as the compatibility mechanism. Optional adapter profiles may be added for known mod families, but they must use the same generic merge interfaces and remain independently testable.
- Total conversions may change the same global systems in incompatible ways. Fusion must analyze those global changes and either apply a validated adapter or explain why the selected set cannot be fused; “all mods supported” means they can be analyzed and classified, not that every arbitrary combination is guaranteed to become playable.
- Acceptance fixtures must include one-mod baseline, two compatible mods, at least three mods, duplicate and conflicting definitions, multiple total conversions, and mixed asset/loose-file sources. Eventually include real-world samples from multiple mod families, not just TEOD and Hypersonic.

Implementation consequence: build a reusable parser/model/merge-rule registry and provenance-aware N-way merge planner before adding launcher controls. Do not build a pair-specific faction merger or a generic text-concatenation shortcut.


### Conservative declaration-block extraction (13 November 2026)

- Added `FusionIniBlockParser`, which extracts recognized named Generals INI declarations as bounded blocks and retains their original source text, comments, whitespace, and line endings.
- It tracks recognized End-terminated nested blocks and records exact source line ranges.
- Any unrecognized bare syntax inside a declaration marks that block ineligible for automatic comparison. Unclosed declarations and declarations that begin before a previous block closes are reported.
- Preflight now runs this extractor for each distinct INI payload and surfaces parser uncertainty as review warnings.
- This is not yet a complete Generals INI grammar. It is not used to rewrite or concatenate files, allocate faction slots, or build a playable multi-mod package. Unknown syntax must remain a blocker for automated transformation until explicitly modeled.
- Source changes have not been compiled or fixture-tested.


### N-way duplicate-definition classification (13 November 2026)

- Added `FusionIniConflictAnalyzer`. It groups every extracted declaration from every selected mod by case-insensitive type/name key and evaluates the entire N-way participant set in one pass.
- Outcomes are `IDENTICAL_EXACT`, `DIFFERENT_CONTENT`, and `UNSAFE_TO_COMPARE`. Exact equality is byte-for-byte, deliberately conservative; whitespace or line-ending differences are not normalized.
- Every outcome retains all participant source paths and line ranges. Preflight now reports this classification alongside existing duplicate declaration warnings.
- No participant is selected as a winner. Even byte-identical duplicates remain blocked by the current archive builder until safe deduplication is integrated with same-path INI file assembly and provenance rules.
- Not compiled or fixture-tested.


### Structured conflict result follow-up (13 November 2026)

- Fusion preflight now retains the N-way INI conflict classifications as structured result data, rather than exposing them only as formatted warning strings.
- Added per-class counts and bounded summaries for `IDENTICAL_EXACT`, `DIFFERENT_CONTENT`, and `UNSAFE_TO_COMPARE`. This gives the future report UI a stable data source without parsing diagnostic prose.
- The archive builder still blocks every duplicate named definition. Exact byte identity is not yet sufficient to make a definition safe to deduplicate because the containing INI file may have unrelated unique definitions, ordering requirements, or global side effects.
- Source-only change; not compiled, unit-tested, or device-tested.


### Parser regression-test milestone (13 November 2026)

- Added JUnit tests for exact CRLF block preservation and line locations, unknown bare syntax being marked unsafe, three-way exact duplicates, three-way divergent duplicates, and unsafe participants.
- Added JUnit 4 as an Android app test dependency.
- These tests are committed but not yet executed. They are not evidence of a successful Gradle build; the Android native toolchain and mod archives are not available in this editing session.
