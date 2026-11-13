# PLAN-034 — Fusion source identity through Skirmish and weapon parsing

Date: 13 November 2026

## Changes committed

This increment adds a first source-identity path in native code:

1. `INI::loadFromFusionSource(filename, loadType, xfer, sourceId)` tags the INI instance while parsing. `INI::unPrepFile()` clears the tag on normal completion and parser exceptions.
2. `PlayerTemplate` stores a Fusion source ID. The source-aware PlayerTemplateStore lookup searches by both NameKey and source ID, while the legacy lookup remains unchanged.
3. `Player::initFromDict(dict, sourceId)` resolves the faction in the specified source. The legacy overload reads optional `playerFusionSource` from the side dictionary; legacy dictionaries have no such key and continue using the old lookup.
4. `SidesList::addPlayerByTemplate(name, sourceId)` records `playerFusionSource` in the generated side dictionary and adds the source to the internal player name to avoid same-name collisions. The original overload delegates with an empty source ID.
5. WeaponTemplate stores its Fusion source ID. WeaponStore has source-aware lookup and registration, and Weapon.ini definitions use the INI source tag.
6. The generic INI weapon-reference parser now resolves an object's weapon reference in the same source namespace.
7. Weapon snapshot transfer version 4 stores the source ID alongside the weapon template name; CRC includes the source ID for Fusion weapons while leaving legacy CRC byte streams unchanged.

## Important boundary

This is not yet end-to-end source-isolated gameplay. The new source-aware side overload is not yet connected to the standard Skirmish faction selector; current Skirmish preferences still enumerate only legacy PlayerTemplates. The mod/archive loading path also has not yet been changed to call `loadFromFusionSource` for selected mod archives. Other registries and references (ThingTemplate, projectile/particle/OCL, Armor, Science, SpecialPower, Upgrade, FX, command UI, AI build lists, assets and scripts) remain global or unresolved. The runtime source identity is not yet propagated to all created objects and owner-dependent lookups.

## Validation

No native compilation, unit test, APK build, device test or in-game match was run for this increment. The changes are committed as a staged native prototype and must be compile-checked before they are treated as stable.


## Follow-up in the same increment

- The standard `PlayerList::newGame()` path already passes each side Dict to `Player::initFromDict`; the legacy overload now reads the optional `playerFusionSource` field. This means a side created with the new SidesList overload reaches the source-aware PlayerTemplate lookup without changing legacy callers.
- The Skirmish-side validation and the human/AI Skirmish script matching loops inside `Player::initFromDict` now resolve each side's PlayerTemplate using that side's own source ID. This prevents a same-named side from another selected mod being mistaken for the current faction when matching sides and selecting source-specific Skirmish scripts.
- The standard faction combo box/preferences still do not expose source-tagged templates. The new SidesList overload is an engine entry point for the eventual selector integration, not proof that the UI can currently select Fusion factions.

- Player now exposes its source identity through `getFusionSourceId()`, derived from its selected PlayerTemplate. This gives later object/weapon/projectile code a direct ownership-context accessor without duplicating a separate source string on every Player.

- Added `getSelectablePlayerTemplateCount()` and `getNthSelectablePlayerTemplate()` to `PlayerTemplateStore`. They expose one deterministic combined view (legacy factions first, then source-tagged Fusion factions) while preserving the old legacy-only enumeration API and its indices. The Skirmish UI still needs to be switched to this view and persist both faction name and source ID; this accessor alone does not enable faction selection.


## Normal Skirmish selector integration (in progress)

- `PopulatePlayerTemplateComboBox` now has an opt-in `allowFusionFactions` parameter. The ordinary LAN/online callers keep the legacy-only list; the local Skirmish menu opts into the combined list.
- Fusion entries are displayed with their source ID and are not collapsed against same-named base-game sides. Legacy faction gating and Challenge General lock filtering remain unchanged for ordinary factions; source-tagged entries bypass those base-game-only restrictions.
- Skirmish preferences now save a stable `PlayerTemplateName` + `PlayerTemplateSource` pair and resolve that pair on load, retaining the old numeric preference as a compatibility fallback.
- The local Skirmish `GameLogic` path now resolves combined faction indices and writes `playerFusionSource` into each side Dict. `Player::initFromDict` already consumes this field to resolve the matching source-tagged PlayerTemplate.
- This connects faction selection identity through player creation, but it does **not** make mixed-mod gameplay safe yet: ThingTemplate, object dependencies, weapons beyond the currently covered references, AI/build scripts, assets and other registries still need source isolation. No compile/APK/device test has been run.

- Extended `INI::loadFileDirectory` and `INI::loadDirectory` with an optional source ID. When supplied, both the single-file form and every file discovered in the directory tree are parsed through `loadFromFusionSource`; the default empty ID preserves existing callers. This is a loader API only: `ArchiveFileSystem` still mounts managed mod archives into one merged virtual filesystem, and the launcher does not yet call this API per original mod archive.
