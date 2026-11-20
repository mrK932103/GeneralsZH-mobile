# Generals Mod Fusion — source-isolated faction runtime design

Status: architecture specification; implementation not yet complete.
Target branch: `claude/generals-mod-fusion`.
Scope: Zero Hour Android port. Original mod files are read-only inputs.

## Product requirement

A selected faction must use the gameplay definitions and assets from the mod that supplied that faction, while factions from other selected mods remain available in the same ordinary Zero Hour Skirmish match.

Example: an Iran faction rooted in Hypersonic, a Russia faction rooted in TEOD, and a USA faction rooted in ROTR may be selected as separate sides in one match. The example is illustrative only; support is determined by compatibility analysis, not hard-coded mod names.

The user-visible contract is:
- Never edit, rename, repack in place, or overwrite a source mod's folders, INI files, or BIG archives.
- Keep a persistent source identity for every discovered faction and every imported definition/asset.
- Do not silently let one mod's same-named definition or asset replace another's.
- Add supported factions to the existing Skirmish faction selection flow; do not create a replacement in-game faction picker.
- Support N selected mods, not a fixed pair.
- Refuse unsupported combinations with an actionable report rather than producing a misleading "playable" package.

## Important engine finding

The current managed-mod mechanism reads `mods_active.txt` and mounts mod directories as ordered overlays. It is a global virtual filesystem: it does not select a different source archive based on the owner faction of a unit. The existing BIG filesystem loads archives into the shared asset namespace.

The current engine's `PlayerTemplateStore` stores a global vector of `PlayerTemplate` records. `PlayerTemplate` fields such as starting building/units, science command sets, shortcut command sets, images, and beacon template are string names resolved through other global systems. The current `SidesList` stores Skirmish side records in fixed `MAX_PLAYER_COUNT` arrays. Consequently, simply mounting several source mod folders cannot isolate definitions. A per-faction source resolver must be added to the engine or the build pipeline must safely namespace all colliding identifiers before registration.

## Required architecture

### 1. Immutable source registry

Create a manifest for each Fusion profile. Each source record contains:
- stable mod ID (derived from canonical source identity, not display name);
- source mod directory and archive list;
- archive/file entry identity, normalized virtual path, content hash and source provenance;
- discovered faction roots (PlayerTemplate and any companion side/AI/UI declarations);
- game version/target and detected global assumptions.

The manifest points to original files. Source archives remain untouched. Paths must be canonicalized and constrained to approved mod roots; reject traversal and symlink escapes.

### 2. Faction-scoped dependency graph

For every playable faction root, calculate a typed transitive dependency closure. Roots include at least:
- PlayerTemplate fields: starting building/units, intrinsic sciences, science purchase command sets, special-power shortcut command sets, beacon template, side identity and presentation assets;
- faction command sets/buttons and command-bar/UI declarations;
- AI side/build-list/script references needed by Skirmish;
- Object/ChildObject, Weapon, Armor, Locomotor, FXList, ParticleSystem, OCL, Upgrade, Science, SpecialPower, AudioEvent, MappedImage and other supported definition domains;
- W3D/model, texture, icon, sound, animation and other referenced assets.

A dependency edge must record its field/domain, source definition, target name, and source mod. Unknown/dynamic references are unresolved diagnostics, not guessed dependencies. A faction is not "ready" merely because a PlayerTemplate was found.

### 3. Runtime namespace strategy

The engine currently has global name registries, so source identity must survive name resolution. Two implementation paths are allowed only after a proof-of-concept demonstrates correctness:

**Preferred path — source-qualified runtime registry**
- Extend engine definition lookup to accept a source namespace/context for references originating from faction-owned definitions and runtime objects.
- Store source-qualified keys for definitions; preserve original display names for UI.
- Propagate the owning source context through object creation, weapon firing, upgrades, sciences, special powers, command buttons/sets, OCL/FX creation and asset lookup.
- Add source-aware asset resolution so identical virtual paths from different mods can resolve to the correct source at runtime.
- Keep base-game lookups as the fallback only when a source-scoped lookup has no entry and the dependency policy explicitly allows fallback.

**Fallback path — deterministic in-memory namespacing**
- Build a source-qualified rename map for every colliding definition and every reference type proven safe to rewrite.
- Rewrite parsed INI streams in memory (or into a generated cache outside source mod folders); never edit originals.
- Preserve a reverse map for diagnostics and original display labels.
- Rewrite all supported references consistently, including inherited/child objects, command/UI references, AI scripts and dynamic OCL/FX references.
- Reject any domain with unrecognized or dynamic name usage that cannot be proven safe.
- Asset path collisions require an equivalent virtual-path remap or source-aware asset resolver; renaming INI identifiers alone is insufficient.

Do not combine these approaches casually. The proof-of-concept must establish whether source context can be propagated through the actual engine call graph. A mixed strategy must document which domains use which resolver.

### 4. Skirmish integration

- Register each compatible faction as a distinct playable PlayerTemplate entry with a stable runtime ID.
- Preserve the original faction display name and presentation images.
- Ensure the side registry, Skirmish menu enumeration, player creation, AI side selection, default AI scripts, team setup, save/load and replay serialization all use the same stable faction mapping.
- Audit `MAX_PLAYER_COUNT`, `MAX_PLAYER_COUNT`-sized side arrays, template index serialization, map-side records and any UI assumptions before increasing the number of selectable factions.
- Do not treat a PlayerTemplate alone as a complete Skirmish side. Side/AI registry records and faction-specific command UI are separate dependencies.
- Preserve vanilla faction IDs and save compatibility.

### 5. Asset resolver and source lifetime

- Source files are opened read-only from original BIG/loose-file sources.
- Resolve a requested asset using the source context of the owning definition/object.
- Define deterministic fallback rules for shared base-game assets.
- Keep file handles/cache entries valid for the entire match; handle archive disposal and profile switching safely.
- Detect duplicate asset paths with different bytes. Never pick a winner by archive load order for a faction-scoped asset.

### 6. Compatibility gates

A combination is playable only when all selected factions pass:
- archive and loose-file inventory;
- structural INI parse and typed dependency closure;
- all required definitions resolved in the correct source namespace;
- all required assets resolved with no ambiguous source;
- faction/UI/side/AI registry compatibility;
- engine limits and Skirmish/save-load mapping;
- no unsupported global engine patches or incompatible singleton configuration.

Mods that replace global control-bar schemes, global game rules, common object definitions, shared AI systems, or engine-level behavior may require explicit adapters. The app must show which factions were accepted, rejected, and why. "Any mod" means the scanner is generic and N-way; it does not mean every arbitrary total conversion is automatically compatible.

## Implementation sequence

1. **Engine call-graph audit:** inspect PlayerTemplateStore, SidesList, SkirmishGameOptionsMenu, Player creation, INI registries, object/weapon/special-power lookup, archive VFS and asset loading. Record every source-context boundary and fixed limit.
2. **Minimal source-context prototype:** load two deliberately colliding fixture definitions from two source archives and prove both can be instantiated and used in one process without one replacing the other. Include a weapon and an asset, not only a unit name.
3. **Faction closure engine:** extend the Java analyzer into a typed, conservative dependency model with fixture-driven tests for nested INI blocks, inheritance, references and unresolved dynamic names.
4. **Runtime resolver:** implement the proven source-qualified registry or in-memory remapping in native code; no UI claim before this works.
5. **Skirmish registry:** register source-qualified factions and AI side records in the existing Skirmish menu.
6. **Asset source resolver:** preserve source identity for W3D, textures, images, sounds and other assets.
7. **Android integration:** store profile manifest/cache in app-private storage; pass only the profile ID/manifest to native engine. Keep normal multi-mod overlay mode separate from Fusion.
8. **Validation:** run automated fixtures, native build, Android build, and device Skirmish tests. Do not call a profile playable until cross-faction combat, AI, save/load and resource resolution pass.

## Acceptance test

A test profile with at least three independent mod sources must expose at least one faction from each source in the ordinary Skirmish picker. Spawn each faction's signature unit; verify its model, weapon, projectile, FX, command buttons, upgrades, powers and sounds originate from its own source. Make the factions fight each other and AI. Include deliberate collisions where two sources define the same object/weapon/asset path with different content and verify correct source selection during the same match. Verify original source-file hashes remain unchanged.

## Current implementation boundary

The existing Fusion code provides archive inventory, lexical INI indexing, candidate PlayerTemplate discovery, dependency heuristics and a strict non-overlapping BIGF archive writer. It does **not** yet provide source-qualified runtime registries, faction dependency closure sufficient for safe extraction, asset isolation, Skirmish registration, or a playable cross-mod faction match. The existing safe BIG overlay must not be presented as the requested faction-isolation feature.
