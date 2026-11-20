# Generals Mod Fusion — native runtime call-graph audit

Date: 13 November 2026
Audited source: `main` of `mrK932103/GeneralsZH-mobile`
Fusion work branch: `claude/generals-mod-fusion`

## Result

The Java-side namespace planners are not enough to isolate factions. The native engine has several independent global registries and lookup paths. A correct implementation must carry a source identity from the selected Skirmish faction through its Player, Team and created Objects, then use that identity in every source-sensitive lookup. A patch limited to PlayerTemplate would still allow weapons, sciences, powers, OCLs and assets to resolve to another mod.

## Verified native call sites

### PlayerTemplate and Skirmish

`Generals/Code/GameEngine/Source/Common/RTS/PlayerTemplate.cpp`

- `findPlayerTemplate(NameKeyType)` scans the one global `m_playerTemplates` vector and compares the NameKey (lines 276–317).
- `parsePlayerTemplateDefinition` looks up the same global key. If it exists, the new INI block initializes/overrides the existing object; otherwise it appends a new template (lines 361–379).
- General variants are normalized to base USA/China/GLA templates in this same lookup function (lines 278–303).

`Generals/Code/GameEngine/Source/Common/RTS/Player.cpp`

- `Player::init(const PlayerTemplate*)` copies faction fields and stores the template pointer in `m_playerTemplate` (lines 353–474).
- `Player::initFromDict` resolves the faction string through the global PlayerTemplateStore before initializing the player (lines 744–750).
- Skirmish script selection also searches the global side list and PlayerTemplateStore (around lines 769–843).

`Generals/Code/GameEngine/Source/GameLogic/Map/SidesList.cpp`

- `prepareForMP_or_Skirmish` copies side records into fixed `MAX_PLAYER_COUNT` arrays and prepares global Skirmish scripts (lines 469–556).
- `addSide` refuses additions once `MAX_PLAYER_COUNT` is reached (lines 596–601).
- `addPlayerByTemplate` constructs the player name and faction dictionary entry from one global template name (lines 666–717).

### Weapons and object creation

`Generals/Code/GameEngine/Source/GameLogic/Object/Weapon.cpp`

- `WeaponStore::findWeaponTemplate` converts the requested name to a global NameKey and looks it up in one hash map (lines 1492–1519).
- `newWeaponTemplate` inserts by NameKey into the global map (lines 1524–1538).
- `parseWeaponTemplateDefinition` finds an existing global weapon and either creates an override or a new global entry (lines 1644–1672).
- Weapon post-processing resolves projectile templates through `TheThingFactory` and OCL references through `TheObjectCreationListStore` (lines 406–432).

`Generals/Code/GameEngine/Source/GameLogic/Object/ObjectCreationList.cpp`

- OCL nuggets resolve created things through `TheThingFactory` and particle systems through `TheParticleSystemManager` (e.g. lines 363–435 and 962–969).
- These lookups have no mod-source argument in the inspected call sites.

### Science and special powers

`Generals/Code/GameEngine/Source/Common/RTS/Science.cpp`

- A science name is converted directly to a NameKey and cast to `ScienceType` (lines 84–100 and 155–160).
- The global ScienceStore scans its one vector for that enum (lines 141–152).
- Parsing searches for an existing science with the same enum and applies override behavior (lines 155–195).

`Generals/Code/GameEngine/Source/Common/RTS/SpecialPower.cpp`

- Special powers are found by name in one global template vector (lines 109–115 and 239–245).
- New special powers receive monotonically assigned IDs and are pushed into that global vector (lines 127–151).
- Fields then resolve RequiredScience and sound events through global parsing functions (lines 162–170).

## Consequences for implementation

1. **Faction identity:** add a stable source/mod ID to the selected PlayerTemplate and preserve it on Player. The Skirmish dictionary must carry an unambiguous runtime template ID; relying only on the faction string cannot distinguish source copies.
2. **Object ownership:** source identity must reach Teams and Objects created for that player, including OCL-spawned projectiles, drones, payloads and temporary weapons. Ownership transfer, capture, neutral objects and map-created objects need explicit rules.
3. **Typed registries:** Weapon, Science, SpecialPower, Upgrade, Armor, Locomotor, FXList, ParticleSystem, OCL and ThingTemplate lookups need either a source-aware key or complete pre-parse namespace remapping. Science is especially sensitive because its enum is currently a NameKey cast.
4. **Cross-domain references:** Weapon-to-projectile/OCL, OCL-to-object/particle, SpecialPower-to-Science, Object-to-Weapon/CommandSet and AI build-list references must resolve under the correct source context.
5. **Asset lookup:** a source-aware asset resolver is required. Renaming archive entries alone will not work unless all model, texture, audio, animation, map and script references resolve to the matching rewritten path.
6. **Skirmish/AI:** side arrays and player counts are bounded by `MAX_PLAYER_COUNT`; source-specific Skirmish scripts/build lists need to be associated with the selected faction without losing AI behavior.
7. **Save/replay/network:** if source IDs or remapped identifiers enter serialized state or network checksums, the IDs must be deterministic and included consistently in save/load and multiplayer synchronization.

## Recommended native proof-of-concept

Do not begin by converting every store. First add a source-qualified runtime identity to one test PlayerTemplate and Player, then prove all three of these in one running game:

- Two mods register the same Weapon name with different damage and projectile data; each player's unit fires its own version.
- Both versions reference the same OCL and projectile names but resolve their own definitions.
- Both versions request the same virtual model/texture path and render the correct source asset.

Only after this succeeds should the same pattern be expanded to Science, SpecialPower, upgrades, command UI, AI build lists and the ordinary Skirmish faction list.

## Current implementation status

The Android preflight now exposes definition and asset namespace plans and the Fusion analysis screen displays their counts and unresolved items. These plans are diagnostic only. No native runtime source file was changed in this step, no source-aware registry exists yet, and the package is still not playable as a cross-mod faction match. JUnit tests were added but not executed; no APK/device test was performed.
