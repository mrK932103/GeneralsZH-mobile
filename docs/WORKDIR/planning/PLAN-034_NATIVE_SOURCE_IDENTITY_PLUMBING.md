# Generals Mod Fusion — native source identity plumbing

Date: 13 November 2026
Branch: `claude/generals-mod-fusion`

## Native code changed

### INI loader source context

- `Core/GameEngine/Include/Common/INI.h`
- `Core/GameEngine/Source/Common/INI/INI.cpp`

Added `INI::loadFromFusionSource(filename, loadType, xfer, sourceId)` and `INI::getSourceId()`.

The Fusion entry point requires a non-empty caller-supplied stable source identity. It is visible to block parsers only while that file is being parsed and is cleared on normal completion or exception. Existing `load()`, `loadDirectory()` and `loadFileDirectory()` retain an empty source identity and their previous behavior.

The source ID must represent the selected mod, not an individual archive filename: the launcher/native loader must pass the same ID for every BIG archive and loose INI file belonging to one selected mod.

### PlayerTemplate registration and enumeration

- `Generals/Code/GameEngine/Include/Common/PlayerTemplate.h`
- `Generals/Code/GameEngine/Source/Common/RTS/PlayerTemplate.cpp`

Each PlayerTemplate now stores its owning Fusion source ID. The store has a source-qualified `findPlayerTemplate(nameKey, sourceId)` overload. When the INI parser is called through `loadFromFusionSource`, a same-named template from another source is not treated as an override; the parser registers a separate template under the owning source.

Legacy lookup, count and index enumeration intentionally ignore source-tagged Fusion templates. Fusion templates have their own `getFusionPlayerTemplateCount()` and `getNthFusionPlayerTemplate()` enumeration API. This prevents the ordinary Skirmish menu and legacy Player initialization from accidentally selecting a source-tagged duplicate without carrying its source identity.

### Player initialization

- `Generals/Code/GameEngine/Include/Common/Player.h`
- `Generals/Code/GameEngine/Source/Common/RTS/Player.cpp`

Added `Player::initFromDict(dict, fusionSourceId)`. It resolves the faction within the supplied source namespace. The old one-argument overload delegates with an empty ID and keeps the old behavior.

The caller that builds the Fusion Skirmish slot must pass the selected source ID. That Skirmish/UI/PlayerList handoff is not yet implemented.

### Weapon registration and lookup

- `Generals/Code/GameEngine/Include/GameLogic/Weapon.h`
- `Generals/Code/GameEngine/Source/GameLogic/Object/Weapon.cpp`

WeaponTemplate now retains a source ID. WeaponStore has a source-qualified lookup that scans owned templates. A tagged Fusion INI load can register a same-named weapon separately instead of overwriting the legacy global NameKey map. Legacy global lookup continues to use only the legacy hash map.

Weapon snapshot transfer version is now 4 and stores the source ID for Fusion-owned templates. Loading older versions continues to use the legacy unqualified lookup. Weapon CRC includes the source ID only for Fusion-owned weapons, so same-named weapons from different sources remain distinguishable without changing the legacy CRC stream for untagged weapons.

## Safety properties

- Existing untagged INI loads use the original PlayerTemplate and Weapon lookup behavior.
- Source-tagged PlayerTemplates are hidden from the ordinary Skirmish enumeration until the Fusion menu can pass source identity.
- Source-tagged weapons do not replace entries in the legacy global WeaponStore NameKey map.
- The source identity is explicitly supplied by the Fusion loader; this code does not guess it from a filename.

## Not yet implemented — do not call this playable

1. No JNI/launcher path calls `loadFromFusionSource` yet.
2. No Fusion Skirmish slot stores and passes the source ID to `Player::initFromDict(dict, sourceId)`.
3. ThingTemplate, Armor, Locomotor, Upgrade, Science, SpecialPower, CommandSet, CommandButton, FXList, ParticleSystem and OCL still need source-aware registration and lookup.
4. Weapon post-processing still resolves projectile, OCL, particle, FX and audio references through existing global paths. The new source-aware WeaponStore lookup is not yet wired into unit WeaponSet construction or all runtime callers.
5. The native asset filesystem still has global virtual-path lookup; model, texture, animation, sound, map and script references are not isolated.
6. AI build lists, side scripts, Skirmish UI, save/load, replay and multiplayer synchronization need full source identity propagation.
7. No native build, JUnit run, Android APK build, device test or cross-mod match was performed in this step.

## Next proof milestone

Connect the launcher to the tagged INI load path, load two fixture sources that intentionally define the same PlayerTemplate and Weapon names, enumerate them through the Fusion-only API, and resolve both with explicit source IDs. Then add the source ID to the Skirmish slot/Dict and prove Player initialization. Do not enable the Fusion Skirmish button until all source-sensitive weapon dependencies and assets resolve to their owning mod.
