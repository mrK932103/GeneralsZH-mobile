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


### Engine-level faction and Skirmish findings (13 November 2026)

The native Zero Hour engine source was inspected before designing faction reconciliation:

- `PlayerTemplateStore::parsePlayerTemplateDefinition` looks up an existing template by `NameKey`. If found, it calls `INI::initFromINI` on that existing object; otherwise it constructs and appends a new template. Therefore repeated declarations with the same template name are field updates applied in INI load order, not an unconditional whole-template replacement. This is not a safe generic merge rule: later fields can override earlier values, omitted fields remain from the earlier template, and load order matters.
- The parsed template contains gameplay and UI-facing fields, including `Side`, `BaseSide`, `PlayableSide`, `StartingBuilding`, up to ten `StartingUnitN` entries, science command sets, special-power shortcut UI, score/load-screen images and music, side icons, beacon template, and display/tooltip text. A faction is not just a single name in a registry.
- `SidesList::prepareForMP_or_Skirmish` copies the current map sides into its Skirmish-side list, then removes non-civilian sides from the map-side list; it loads default AI scripts from `Data\\Scripts\\SkirmishScripts.scb` when the sides do not already carry scripts. This means map-side records and Skirmish faction templates/scripts are distinct systems and must not be conflated.
- `SidesList::addPlayerByTemplate` derives the player name from the faction template name and creates a default team. Thus template-name collisions and faction naming conventions can affect player/team identity.
- The Skirmish preferences code stores the selected faction by numeric template index and validates the index against the current template store; persisted indices can become stale when the template registry changes order.

**Fusion implications:** never concatenate all `PlayerTemplate` blocks blindly and never assume that adding template definitions alone makes every faction playable. The merge plan must preserve stable template identities, identify field-level collisions, validate required faction fields (at minimum playable-side intent and starting building), account for faction-specific starting units and UI assets/command sets, and verify Skirmish script coverage. Any automatic merge must be deterministic and explicit about precedence; unresolved field conflicts must block output. Reordering or expanding the registry also requires invalidating/remapping persisted numeric faction selections rather than trusting old indices.

These are source-inspection findings only. No engine code was changed, and no native/Android compilation or in-game test has been performed.


### PlayerTemplate field-level conflict assessment (13 November 2026)

- Added a read-only FusionPlayerTemplateAnalyzer for same-name faction templates.
- The scalar allowlist is grounded in the native PlayerTemplate::getFieldParse() table. It compares values for engine-confirmed scalar fields such as Side, BaseSide, PlayableSide, StartingBuilding, StartingUnit0..9, science command sets, faction UI assets, and display metadata.
- Differing values are reported with every source and line. A field omitted by a participant is not treated as an explicit conflicting value because the engine updates existing templates field-by-field and leaves omitted members intact.
- Custom parser directives (ProductionCostChange, ProductionTimeChange, ProductionVeterancyLevel), unknown fields, unsafe blocks, and unsupported syntax remain unresolved; they are never guessed as scalar values.
- FusionMergePlan now includes this field-level assessment for PlayerTemplate collisions and surfaces it in its summary.
- This is analysis only. It does not combine faction templates, choose precedence, validate referenced objects/assets, allocate stable faction identities, or guarantee Skirmish/AI support. The allowlist is specific to the inspected engine source and may need version-aware adapters for modded engine forks.
- Added tests for three-way Side conflicts, equal/omitted fields, custom directives, and unknown fields. Tests are committed but have not been executed; no Gradle build or device test has been run.


### CommandButton field-level conflict assessment (13 November 2026)

- Inspected the native CommandButton field-parse table in Core/GameEngine/Source/GameClient/GUI/ControlBar/ControlBar.cpp and used its supported field names as the analyzer allowlist: Command, Options, Object, Upgrade, WeaponSlot, MaxShotsToFire, Science, SpecialPower, TextLabel, DescriptLabel, PurchasedLabel, ConflictingLabel, ButtonImage, CursorName, InvalidCursorName, ButtonBorderType, RadiusCursorType, and UnitSpecificSound.
- Added FusionCommandButtonAnalyzer to compare same-name CommandButton definitions across all selected mods, retaining each differing field value with source and line.
- Unknown fields, unsupported syntax, unsafe blocks, and repeated assignment of a field within one declaration are unresolved; the analyzer does not assume custom mod fields or parser semantics.
- Integrated the field report into FusionMergePlan. CommandSet slot analysis remains a separate pass and checks slot numbers 1 through 18 against the native engine table, as well as whether referenced CommandButton names occur in the scanned declaration inventory.
- Added tests for three-way CommandButton conflicts, unknown/repeated fields, and merge-plan integration.
- This is still diagnostic only. It does not rewrite CommandSet/CommandButton data, reconcile button IDs or command semantics, prove all referenced assets/text/audio exist, or validate per-unit command-set compatibility. No playable package is authorized.
- Tests have not been executed; no Android/Gradle build or device validation has been performed.


### Command UI field and slot analysis (13 November 2026)

- Added read-only N-way analysis for CommandSet numeric slot assignments and CommandButton fields.
- Cross-checked CommandButton's supported field allowlist against the native CommandButton field-parse table in ControlBar.cpp. Unknown fields and repeated assignments inside one declaration are reported for manual review.
- CommandSet analysis records slot-to-button references across all participants, reports different buttons assigned to the same slot, and checks referenced CommandButton names against the parsed INI inventory.
- Integrated both assessments into FusionMergePlan. The plan retains source participants and emits field/slot conflict details; it still does not rewrite command sets, resolve duplicate button names, validate every referenced asset/text/command semantic, or produce a playable package.
- Fixed a malformed static import in the CommandSet analyzer test and corrected the preflight result constructor so the parsed INI block inventory is retained for cross-reference checks. Added a backward-compatible constructor for tests that do not provide block inventory.
- Native CommandSet loading rejects duplicate same-name definitions in ordinary load mode and supports explicit override objects in override mode. This reinforces that concatenating same-name command sets is not a valid merge strategy.
- Test sources are committed but have not been executed. No Gradle build or device test has been run.


### CommandSet and CommandButton conflict analysis (13 November 2026)

- Added a read-only N-way CommandSet analyzer. The accepted slot names 1 through 18 are grounded in the native CommandSet field-parse table; every other directive is unresolved rather than silently ignored.
- It records each slot assignment with source and line, reports disagreements for the same slot across all participants, and checks referenced CommandButton names against the selected INI declaration inventory when that inventory contains button definitions.
- Added a read-only CommandButton analyzer grounded in the native CommandButton field-parse table. It reports per-field disagreements across all versions of a named button, retains each source location, and flags unknown syntax, unknown fields, unsafe blocks, and repeated assignments within one declaration.
- Integrated both assessments into FusionMergePlan so CommandSet slot conflicts and CommandButton field conflicts appear in the same provenance-aware N-way report.
- Confirmed from the native engine source that CommandSet slots are 1..18 and each slot resolves a named CommandButton; an unknown button causes INI_INVALID_DATA. Also confirmed that duplicate CommandButton declarations are rejected in ordinary loading unless the engine is explicitly in override mode. Fusion must not silently turn a duplicate into an override.
- These analyzers do not merge command sets, rename button IDs, rewrite references, validate every transitive gameplay/UI dependency, or prove that a faction's selected command set is usable. They are diagnostics only; all collisions remain blocked.
- Added tests for three-way slot conflicts, non-overlapping slots, missing button references, unsupported directives, slot 18/19 bounds, duplicate assignments, three-way CommandButton field conflicts, unknown fields, and repeated field assignments.
- Source and test changes have not been compiled or executed. No Android build or device test has been performed.


### CommandSet and CommandButton field/slot assessment (13 November 2026)

- Added read-only N-way analyzers for CommandSet slot assignments and CommandButton fields. They preserve source locations, compare all participants, and never emit rewritten INI.
- The CommandSet slot range is grounded in the native CommandSet::m_commandSetFieldParseTable: slots 1 through 18 are valid. The native header confirms MAX_COMMANDS_PER_SET = 18; the Android UI currently displays 14 cells, while the remaining slots are reserved for script-only commands.
- Each selected CommandSet is now checked, including sets that have no duplicate declaration. This catches missing CommandButton references that would otherwise be invisible to a duplicate-only conflict report.
- Repeated assignments to the same slot within one declaration are marked unresolved because the native parser writes into the indexed array and later assignments overwrite earlier values.
- The CommandButton scalar field allowlist is grounded in CommandButton::s_commandButtonFieldParseTable in the native ControlBar implementation. Unknown fields and repeated fields remain unresolved. Different values are reported with every participant.
- Native loading behavior confirms that duplicate same-name CommandSet declarations outside override loading trigger a duplicate error, while duplicate same-name CommandButton declarations also trigger an error; overrides use separate engine paths. Fusion must not treat duplicate declarations as ordinary concatenation.
- Corrected test fixtures that accidentally encoded literal backslash-n sequences instead of actual line breaks, and added plan-level coverage for missing button references in a unique CommandSet.
- These analyzers are diagnostics only. They do not merge command slots, resolve button-name collisions, validate all referenced objects/upgrades/sciences/special powers/assets/text labels, or guarantee a working control bar. No package is authorized by this phase.
- Tests are committed but not executed; no Android build or device test has been performed.



### Source-qualified faction discovery and isolation design (13 November 2026)

The product requirement is source isolation, not archive overlay:

- Each discovered faction must retain an immutable origin identity (source mod folder, source archive, INI path, declaration and line range). Two factions with the same template name from different mods must remain separate catalog entries.
- Original mod folders and BIG archives are read-only inputs. Fusion may create app-managed metadata and generated runtime data, but must never patch source archives in place.
- The target is N-way: the implementation must not contain a two-mod or named-mod special case. A selected faction's unit, weapon, building, upgrade, science, special-power, command/UI, AI and asset dependencies must resolve against its own source provenance.
- A simple multi-folder mount is not sufficient. The existing managed-mod mechanism mounts folders into one shared archive directory tree with priority/overwrite behavior. The INI loader then registers named definitions in engine-global stores. This cannot provide per-faction isolation by itself.
- The engine path already resolves a player's faction through `ThePlayerTemplateStore->findPlayerTemplate(NAMEKEY(...))` in `Player::initFromDict`. PlayerTemplate fields include side, starting building/units, intrinsic sciences and command-set names. Skirmish player creation also consults the shared template store when matching a player's side to a skirmish side. A source-qualified faction identity therefore has to survive from the Skirmish selection record through Player initialization; a launcher-only list is not enough.
- The Skirmish options UI uses fixed slot arrays (`MAX_SLOTS`) and the engine player list uses `MAX_PLAYER_COUNT`. These are separate limits and must be audited before claiming arbitrary faction counts. Do not raise either limit without checking save/network/snapshot and UI assumptions.
- The current engine's global definition stores (ThingTemplate, Weapon, Armor, Locomotor, CommandSet, CommandButton, Science, Upgrade, SpecialPower, FX/OCL and related registries) are not partitioned by mod. The feasible first implementation path is a source-aware runtime resolver plus deterministic symbol qualification/remapping for definitions and typed references, while preserving source archive provenance. This still needs an engine-side lookup context or a generated isolated runtime namespace; simply retaining the source archive path in a catalog does not prevent global-name collisions.
- Binary asset paths also need source-aware lookup or deterministic remapping. Identical virtual paths with different bytes cannot be treated as safe just because the owning faction differs.

#### Implemented in this step

- Added `FusionFactionCatalog.java`, a read-only catalog that extracts PlayerTemplate faction roots from the already parsed INI block inventory and preserves source archive, declaration name, line range, Side, DisplayName and PlayableSide.
- Duplicate PlayerTemplate names from different source archives remain distinct candidates using a source-qualified stable key; no winner is selected by load order.
- Missing Side, missing explicit PlayableSide, unsafe block structure and unrecognized directives are marked for review. The catalog does not infer a playable faction from a name alone.
- Added `FusionFactionCatalogTest.java` for duplicate faction names from two sources, incomplete faction roots and filtering of non-faction definitions.
- Integrated the source-qualified faction catalog into the Fusion preflight report. The UI now labels the existing archive-overlay operation as an archive overlay, not a faction merger, and reports that catalogued PlayerTemplate entries are only roots whose dependency closure and runtime isolation remain incomplete.
- Source mods remain read-only. The new catalog only reads parser output; it does not edit INI or BIG files.

#### Still required before playable cross-mod Skirmish

1. Map every relevant engine-global registry and its lookup path; identify which definitions can be namespaced and which use enums, fixed arrays, numeric IDs or non-string references.
2. Carry a stable source/faction owner ID from the selected Skirmish faction through Player creation and all gameplay lookups that need isolation.
3. Build a typed dependency closure from each faction root, including inherited Object/ChildObject chains, command/UI references, AI scripts, science/upgrade/special-power links, OCL/FX/particle/audio references and binary assets.
4. Implement a semantic reference rewriter or runtime source-aware resolver for every supported reference domain. Unknown syntax and unresolved dependencies must block that faction combination, not silently fall back to another mod.
5. Add faction-aware Skirmish registry entries and verify player-template indices, save/replay compatibility, AI scripts, command-bar setup and map-side matching.
6. Add three-or-more-source integration fixtures with deliberately colliding object, weapon, command and asset names. Verify both factions keep their own behavior in the same match.
7. Run local unit tests and Android/native builds, then install and test on device. No source build or device test has been run for the source-qualified catalog at this point.

No playable faction merge is claimed by this milestone. The current BIG overlay builder remains a separate, conservative archive utility and must not be presented as the requested source-isolated faction runtime.


### Player-template source isolation audit (14 November 2026)

A follow-up read of the current native engine sources found that the branch already contains a partial source-aware faction-template path:

- `PlayerTemplate` carries `m_fusionSourceId`; the value is assigned from `INI::getSourceId()` while parsing a `PlayerTemplate` definition.
- `PlayerTemplateStore::findPlayerTemplate(name, sourceId)` resolves a faction within the requested source. The legacy overload intentionally continues to search only untagged templates, preserving legacy map behavior.
- The store exposes separate legacy and Fusion enumeration methods, plus a combined selectable enumeration.
- `Player::initFromDict` reads the optional `playerFusionSource` dictionary key and uses the source-qualified template lookup. Its skirmish-side and script-owner matching also compare source identity, avoiding same-side-name matches across different sources.

This is meaningful faction-selection groundwork, but it does **not** establish complete faction isolation. The audit still needs to trace where the Skirmish UI writes `playerFusionSource` into every selected side dictionary, how AI/player creation preserves it through all paths, and whether every faction-owned reference (starting units/buildings, intrinsic sciences, command sets, special-power shortcuts, icons/load screens/audio) resolves against the same source. The wider runtime registries (ThingTemplate, Weapon, Armor, Locomotor, FX/OCL/ParticleSystem, command UI, upgrades/sciences/special powers, audio and asset paths) remain separate isolation work.

No native compile, Android build, automated test, or device test was run as part of this source audit. Do not treat the existing PlayerTemplate source tag as proof that mixed-mod Skirmish is playable.


### Skirmish selector hand-off audit (14 November 2026)

The Skirmish options menu enumerates source-tagged templates through `getSelectablePlayerTemplateCount()` / `getNthSelectablePlayerTemplate()`, and its saved preference stores a stable faction-name plus source-ID pair. However, the actual slot-selection handler still reads the combo-box item data as a single integer and calls `GameSlot::setPlayerTemplate(index)`. The slot model therefore remains index-based at this point in the inspected path.

The inspected `SkirmishGameOptionsMenu.cpp` does not itself attach a `playerFusionSource` value to the selected slot dictionary. Meanwhile, `Player::initFromDict` depends on that key to select the source-qualified template. This creates a critical hand-off that must be traced through combo population, GameInfo serialization/slot-to-Dict conversion, and multiplayer/skirmish player creation before claiming faction source identity reaches runtime. Do not patch only the menu handler: the stable source/name pair must survive slot storage, save/network serialization where applicable, and Dict creation for every AI and human player.

Next implementation target: replace or augment the bare template index in the Skirmish slot pipeline with a stable faction identity (template name + source ID), while preserving old numeric preference/map compatibility. Add tests for two templates with the same faction name from different sources, slot save/load, and AI/human player initialization.


### Fusion faction index handling fix (14 November 2026)

Updated `Core/GameEngine/Source/GameNetwork/GameInfo.cpp`:

- Slot display-name lookup now uses `getNthSelectablePlayerTemplate()` instead of the legacy-only `getNthPlayerTemplate()`, and safely returns an empty display string for an invalid index.
- Both branches of the ASCII GameInfo slot parser now validate faction indices against `getSelectablePlayerTemplateCount()`, allowing indices belonging to source-tagged Fusion templates instead of rejecting them at the legacy-template boundary.

This fixes a concrete mismatch introduced by the combined legacy + Fusion selector: a Fusion faction could appear in the selector but be rejected by one parser branch or displayed using the wrong legacy index. It does not add a source ID to GameSlot, change the wire format, or guarantee that player creation receives `playerFusionSource`. The source/name identity hand-off described above remains unresolved. No build or test was run.


### Runtime slot-to-player path audit and starting-building lookup fix (14 November 2026)

A deeper trace confirmed that the source identity hand-off is partly implemented already: in `GameLogic.cpp`, the selected slot index is resolved through `getNthSelectablePlayerTemplate()`, and the resulting template's name plus `getFusionSourceId()` are written into the side `Dict` as `playerFaction` and `playerFusionSource`. `PlayerList::newGame()` passes that Dict to `Player::initFromDict()`. `Player::initFromDict()` then performs the source-qualified template lookup. Thus, for the inspected local Skirmish player-creation path, source identity is already propagated; earlier notes about this being unconfirmed should be read as an audit question, not as a claim that the path is definitely missing.

The audit found and fixed a concrete remaining legacy-index lookup in the post-load network-building pass: `GameLogic.cpp` previously passed the combined selectable slot index to `getNthPlayerTemplate()` (legacy-only). It now uses `getNthSelectablePlayerTemplate()` before placing each player's starting network buildings. This prevents Fusion faction slot indices from being interpreted as legacy faction indices during starting-building placement.

Together with the preceding `GameInfo.cpp` changes, the inspected code now consistently uses combined selectable indexing in the slot display, both ASCII slot parser branches, random-faction selection, side Dict creation, and starting-building placement. The observer fallback intentionally remains on the legacy lookup path.

Still unresolved: the selected source-qualified template's *dependent gameplay definitions* must remain isolated when templates create starting units and when those units use global registries (ThingTemplate, Weapon, FX/OCL, command UI, upgrades/sciences/special powers, audio and assets). Network/LAN serialization currently transmits a numeric template index, so all peers must load the same ordered faction catalog; this needs an explicit compatibility decision or a stable name/source serialization extension before multiplayer Fusion can be considered safe. No build or runtime test was run.



### Native runtime identity audit — ThingTemplate registry (14 November 2026)

- Traced the native object-definition path in `ThingFactory.cpp`: `parseObjectDefinition()` calls `findTemplateInternal(name)`, and `findTemplateInternal()` uses a single global `hash_map<AsciiString, ThingTemplate*>`. The key contains no source identity. A second definition with the same object name is therefore treated as a duplicate/override rather than an independent faction-owned definition.
- `ThingFactory::newTemplate()` also copies the globally resolved `DefaultThingTemplate`; `newOverride()` chains overrides onto the globally resolved template. Reskin lookup uses the same global name-only lookup.
- `ThingTemplate::resolveNames()` resolves production prerequisites and mapped-image references after loading. This is a second resolution phase, after INI parsing, and currently has no source context. `ThingTemplate` stores resolved data and pointers (including reskin and module/weapon/armor-related structures); simply tagging the top-level template would not isolate its dependency graph.
- Runtime lookups also occur after object creation. For example, build variations in `ThingFactory::newObject()` call the global name-only `findTemplate()`. A source-aware parser alone would still allow a unit from one mod to select a same-named variation from another mod.
- The current global registry and pointer-based resolution mean a safe implementation must define the complete identity and lookup contract before changing the registry: (1) source-qualified definition identity; (2) explicit base-game/shared-source fallback; (3) source propagation through nested INI parsing and delayed resolution; (4) source-aware resolution for runtime string references such as build variations and reskins; (5) deterministic handling of map.ini overrides and template IDs; and (6) save/network compatibility.
- Do not implement a partial `ThingTemplate::sourceId` field or silently namespace only the top-level object name. Either approach leaves delayed/runtime references able to cross into another mod and can produce mixed dependency graphs.
- Next native milestone: inventory every `findTemplate()`/name-based registry lookup reachable from `ThingTemplate` fields, then implement and test a bounded source-aware vertical slice with explicit shared/base fallback. Until that audit is complete, Fusion must continue to block combinations with unresolved cross-source object dependencies rather than claim they are playable.
- This is a source audit/documentation update only. No native engine change, Android build, or device test is claimed.



### Fusion INI source-context propagation audit (14 November 2026)

- Confirmed the native `INI` loader already has a Fusion-only source context: `loadFromFusionSource(filename, loadType, xfer, sourceId)` sets `m_sourceId` for the duration of parsing, and `getSourceId()` exposes it to field parsers. Legacy `load()` continues with an empty source identity.
- `loadFileDirectory()` and recursive `loadDirectory()` preserve the supplied source ID when opening both the primary INI file and nested directory files. The source context is cleared on normal unprepare and on the exception path.
- Found an existing source-aware typed-reference example in `INI.cpp`: weapon-template parsing reads `ini->getSourceId()` and calls `TheWeaponStore->findWeaponTemplate(name, sourceId)` for Fusion loads, retaining the legacy lookup for ordinary loads. This is evidence that the engine already has a pattern for source-qualified registries; it does not establish that every registry follows it.
- The contrast is important: `ThingFactory::parseObjectDefinition()` still calls the global name-only `findTemplateInternal(name)`, and `ThingTemplate::resolveNames()` later resolves prerequisite names without an explicit source parameter. Thus source context reaches the INI parser but is not consistently carried into object definitions and delayed object-reference resolution.
- Follow-up audit must compare every INI field parser and registry lookup against the weapon-template pattern, including Armor, Locomotor, FX/OCL, CommandSet/CommandButton, Upgrade, Science, SpecialPower, AudioEvent, ParticleSystem, and object references. Any registry that lacks source-qualified lookup remains a potential cross-mod collision.
- No runtime code was changed in this audit. This is an architecture inventory update only; it does not mean the listed registries are source-isolated.



### Native registry collision matrix — typed stores (14 November 2026)

The following source inspection was performed against the current Generals native engine branch. This matrix distinguishes a source-aware INI field parser from a genuinely source-isolated runtime store.

| Registry / path | Current behavior observed | Fusion consequence |
|---|---|---|
| WeaponTemplate definition lookup | Has `m_fusionSourceId` and a source-qualified `findWeaponTemplate(name, sourceId)`; INI typed weapon-reference parsing uses it. | Partial support only. |
| WeaponTemplate delayed references | `WeaponTemplate::postProcessLoad()` resolves projectile names with global `ThingFactory::findTemplate(name)`, and fire/detonation OCL names with global `findObjectCreationList(name)`. | A weapon can still bind to another source's projectile or OCL even when its weapon-template pointer was source-qualified. |
| Armor | `ArmorStore` is keyed only by `NameKeyType`; parsing writes directly to `m_armorTemplates[key]`. No source-qualified key or lookup was found in the inspected declarations/implementation. | Same-name armor definitions collide globally. |
| Locomotor | `LocomotorStore` is keyed by `NameKeyType`; parser resolves existing definitions by name key and builds global override chains. Runtime creation and Xfer restore resolve by name key. | Same-name definitions/overrides are shared globally; save/transfer path also has no source token. |
| Upgrade | `UpgradeCenter` searches a single linked list by name key; parser reuses the first matching entry. | Duplicate names are merged/reused rather than represented as independent source-owned upgrades. |
| Science | `ScienceStore` uses a single vector keyed by `ScienceType` derived from global NameKey; parsing finds existing science by that value and override handling is global. | Same-name science identities and prerequisites cannot currently be independent across mods. |
| SpecialPower | `SpecialPowerStore` searches one global vector by name; definition IDs are allocated from one global counter and overrides chain globally. | Same-name powers collide and their runtime IDs are not source-qualified. |

#### Required implementation order

1. Establish one shared source identity contract for definitions and explicit base/shared-source fallback. Keep empty source IDs on the legacy path.
2. Convert one registry at a time to source-qualified storage and lookup, preserving the original legacy overloads. Add tests for same-name definitions in two sources, same-source override behavior, base fallback, and missing-source failure.
3. Update every INI field parser and every delayed/post-load/runtime lookup to use the owning definition's source identity. The WeaponTemplate audit proves why changing only the initial parser is insufficient: its later projectile/OCL resolution is still global.
4. Update serialization/Xfer for any source-owned object whose templates are reconstructed by name (LocomotorSet is a confirmed example). Define backward compatibility for old saves/network peers before changing wire formats.
5. Run native compilation and targeted tests after each vertical slice; only then connect the registry to faction dependency closure and Fusion preflight.

#### Scope and safety

- The table is a source audit, not a claim that all call sites in the entire codebase have been exhausted.
- No registry code was modified in this step. A blind `sourceId` field addition would not fix parser reuse, override chains, delayed resolution, or name-only Xfer restoration.
- The first safe code slice should be a source-aware store with explicit source-qualified parser and lookup APIs plus tests, then its references should be migrated together. Do not enable mixed-mod Skirmish for a registry until all of its lookup paths are source-aware.


### Weapon delayed-reference scanner slice (14 November 2026)

- Extended `FusionIniReferences` to recognize WeaponTemplate dependency fields that the native engine stores as names and resolves later or at runtime:
  - `ProjectileObject` and `ProjectileStreamName` → `Object` (ThingTemplate lookup).
  - `FireOCL`, `VeterancyFireOCL`, `ProjectileDetonationOCL`, and `VeterancyProjectileDetonationOCL` → `ObjectCreationList`.
  - `ProjectileExhaust` and `VeterancyProjectileExhaust` → `ParticleSystem`.
  - `ProjectileDetonationFX` and `VeterancyProjectileDetonationFX` → `FXList`.
  - `HistoricBonusWeapon` → `Weapon`.
- Added namespace-plan tests for all seven dependency categories and for same-named projectile objects referenced by Hypersonic and TEOD. The tests assert that each mod's reference maps to its own source namespace.
- This closes a concrete blind spot in the static INI dependency inventory. It does not change native pointer resolution, rewrite INI/archive contents, or make these runtime registries source-aware. The mixed-mod launcher must remain blocked until native lookup paths and the full dependency graph are migrated.
- The Android/JUnit test suite has not been executed in this environment; these are committed tests, not a reported passing build.

- Follow-up detail: the native `Veterancy* ` parser functions consume a veterancy enum token before the resource name. The scanner now skips that first token rather than misclassifying it as a resource dependency. The audit also includes `FireFX`, `VeterancyFireFX`, and `FireSound` references. Tests cover ordinary and per-veterancy forms; they remain unexecuted pending an Android/JUnit build.


### Follow-up native parser audit — delayed weapon resources (14 November 2026)

- Confirmed in `INI.cpp` that source context is currently used by `parseWeaponTemplate()` only for the WeaponStore lookup in this inspected cluster. The adjacent `parseFXList()`, `parseParticleSystemTemplate()`, and `parseObjectCreationList()` functions still call global name-only stores and do not consult `getSourceId()`.
- `parseAudioEventRTS()` records a global audio event name and asks the global audio system to resolve it; it does not use Fusion source identity.
- This means the scanner change now detects these cross-source dependencies, but native pointer binding remains global for FXList, ParticleSystem, OCL, and audio. A namespace rewrite would need to rename declarations and all their nested references before native load, or the corresponding native stores and parser functions must be source-aware.
- This finding expands the native migration boundary beyond `WeaponTemplate::postProcessLoad()`: direct typed INI parsing itself binds FX/OCL/particle references globally. Do not treat a source-qualified WeaponTemplate as isolated until those parser paths are migrated too.


### ObjectCreationList nested dependency coverage (14 November 2026)

- Extended the static reference scanner with OCL-owner-specific mappings based on the native nugget parsers:
  - `Transport`, `PutInContainer`, `Payload`, and `ObjectNames` → `Object`.
  - `ParticleSystem` → `ParticleSystem`.
  - `FXFinal` → `FXList`.
  - `FadeSound` and `BounceSound` → `AudioEvent`.
- This follows native runtime lookups observed in `ObjectCreationList.cpp`: delivery transport/container/payload names and debris object names are resolved through `ThingFactory::findTemplate`; particle-system names through `ParticleSystemManager::findTemplate`; final FX is parsed as an FXList pointer; fade/bounce sounds are audio event references.
- Added a scanner test covering the OCL field set, including multi-name debris payloads. The mapping is restricted to ObjectCreationList declaration owners to avoid misclassifying similarly named fields elsewhere.
- Native OCL nuggets still store some references as strings and perform global runtime lookups. This scanner change inventories those dependencies; it does not yet provide source-qualified runtime resolution.

- Corrected nested-block accounting for the native OCL nugget grammar (`CreateObject`, `CreateDebris`, `ApplyRandomForce`, `DeliverPayload`, `FireWeapon`, `Attack`) and FXList nugget blocks. Without these block boundaries, an inner `End` could be mistaken for the end of the owning declaration and later dependencies silently skipped. A regression test now verifies a `FireWeapon` nugget is scanned without closing its parent OCL.


### FXList source-qualified store vertical slice (14 November 2026)

- Added a separate source-qualified FXList map while retaining the legacy untagged map.
- `FXListStore::parseFXListDefinition()` now stores a Fusion-loaded FXList under a composite source/name key. Definitions from different selected mods can therefore share a display name without overwriting one another.
- Added `findFXList(name, sourceId)`: exact source match first, then the untagged legacy/base-game store. It never searches another Fusion mod's namespace.
- Updated `INI::parseFXList()` to pass the active `INI::getSourceId()` into the lookup. This also covers nested `FXListAtBonePos -> FX` pointers because the nested parser shares the active INI source context.
- Extended the read-only dependency scanner to follow FXList nugget blocks and record:
  - `Sound -> Name` as AudioEvent.
  - `ParticleSystem -> Name` as ParticleSystem.
  - `RayEffect -> Name` and `Tracer -> TracerName` as ThingTemplate/Object references.
  - `FXListAtBonePos -> FX` as FXList.
- Added scanner regression coverage for those five dependencies.
- Scope and remaining limitations: this is the first native registry vertical slice, not complete FX isolation. FX nuggets still resolve ParticleSystem and RayEffect/Tracer ThingTemplates through global runtime managers; audio event resolution remains global. Other registries and all dependent runtime lookups still require source-aware migration. No native build or Android test run is reported yet.

- Added a cross-source regression test that scans identically named FXList nuggets in Hypersonic and TEOD, then verifies their AudioEvent and ParticleSystem dependencies map to their own source definitions rather than the other mod's definitions.

### Source-isolated faction dependency closure (14 November 2026)

- Added `FusionFactionDependencyPlan`: each discovered `PlayerTemplate` now gets an independent dependency traversal rooted in its own source mod.
- Source ownership is the selected mod identity, not a single BIG archive, so a faction can resolve its local dependencies across multiple BIG archives belonging to the same installed mod.
- The traversal follows only definitions owned by that faction's source mod. A definition found only in another selected mod is reported as a cross-source blocker instead of being silently imported or rebound.
- Missing definitions are reported separately because they may be supplied by base-game data or loose files that are not part of the selected-mod inventory.
- Added PlayerTemplate dependency mappings for `StartingBuilding`, `StartingUnit0..n`, `IntrinsicSciences`, and `PurchaseScienceCommandSet`.
- The Fusion preflight screen now displays each faction's source mod, local dependency count, and cross-source/unresolved dependency warnings.
- Added unit fixtures for same-mod multi-archive resolution and cross-mod name collisions.
- This is a source-aware analysis/manifest stage only. It does not yet rewrite INI references, build the final package, or change the native runtime lookup tables. Android/JUnit compilation and device validation remain outstanding.

### Native ThingTemplate source registry vertical slice (14 November 2026)

- Added a dedicated Fusion ThingTemplate registry keyed by `sourceId::originalTemplateName`; the legacy name-only registry remains reserved for untagged/base-game templates.
- `ThingTemplate` now retains its owning Fusion source identity. Template copies/reskins preserve the destination template's source identity.
- `ThingFactory::parseObjectDefinition` creates and finds Object templates in the active `INI::getSourceId()` namespace. Same-named objects from two Fusion sources can coexist without replacing one another in the legacy table.
- Added source-aware lookup with strict resolution order: exact selected source first, then untagged/base-game template. It never searches another Fusion source as an implicit fallback.
- Source context is also used for ObjectReskin parents, BuildVariations, and ProductionPrerequisite unit references resolved during ThingTemplate post-processing.
- Applied the native change to both `Generals` and `GeneralsMD` engine trees; Android's build workflow compiles the `GeneralsMD` target, so the mobile target is included. Shared `Core/GameEngine/Source/Common/INI/INI.cpp` now calls the source-aware lookup for ThingTemplate fields.
- Registry cleanup/reset now removes the correct source-qualified key and accounts for both registries when checking template IDs.

**Limitations / not yet complete**
- This is only the ThingTemplate identity layer. Many other named registries remain global (WeaponTemplate, OCL, ParticleSystem, Armor, Locomotor, Upgrade, Science, SpecialPower, AudioEvent, CommandSet/CommandButton, etc.).
- Delayed references outside ThingTemplate's own prerequisites, including weapon projectile/OCL/FX/audio links and nested OCL/FX nuggets, still need source-aware propagation.
- ProductionPrerequisite now resolves its Object names by source, but the full game runtime still needs an audit for every name-to-template lookup (scripts, AI, reskins/overrides from map.ini, save/replay/network template IDs, and any direct `findTemplate(name)` call).
- The top-level game loading path must be verified to pass stable source IDs to `loadFileDirectory` / `loadFromFusionSource` for every selected mod. A source-aware registry does nothing for files loaded through the legacy empty-source path.
- No native Android CMake/NDK build or device test has been run in this session. Treat this as an implementation stage, not a verified playable multi-mod build.

- Follow-up correction in the ThingTemplate vertical slice: definition creation now uses an exact-source-only lookup, so a same-named base-game Object is not mistaken for an already-loaded definition from the selected mod. Base-game fallback is reserved for reference resolution (for example, `parseThingTemplate`, prerequisites, and default-template inheritance), never for choosing which registry owns a new mod definition.
