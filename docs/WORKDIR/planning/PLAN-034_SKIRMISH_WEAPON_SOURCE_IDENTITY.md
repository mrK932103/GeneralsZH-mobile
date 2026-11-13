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
