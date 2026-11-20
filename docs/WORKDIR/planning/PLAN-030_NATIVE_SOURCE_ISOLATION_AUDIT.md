# Generals Mod Fusion — native engine audit notes

Audit date: 13 November 2026
Repository branch reviewed: `main` engine sources (read-only audit).
This document records verified integration constraints; it does not claim source-isolated loading is implemented.

## Verified findings

### PlayerTemplate registration is global and name-keyed

File: `Generals/Code/GameEngine/Source/Common/RTS/PlayerTemplate.cpp`

- `PlayerTemplateStore::parsePlayerTemplateDefinition` reads the template name, converts it to a global `NameKeyType`, and calls `findPlayerTemplate(namekey)`.
- If the key already exists, the parser calls `ini->initFromINI(pt, ...)` on the existing record. The second declaration therefore updates the existing global PlayerTemplate instead of registering a separate source-qualified faction.
- If the key is new, it appends a new record to `m_playerTemplates`.
- `findPlayerTemplate` linearly scans the one global vector by `NameKeyType`; it has no source/mod argument.
- Legacy general variants are deliberately normalized to their base faction key (America/China/GLA), another reason not to assume all templates are independent slots.

Implication: source isolation cannot be achieved by mounting multiple BIG archives or merely adding more PlayerTemplate declarations. A source-qualified registry or a proven in-memory unique-key rewrite is required.

### Skirmish side records are a separate registry

File: `Generals/Code/GameEngine/Source/GameLogic/Map/SidesList.cpp`

- `SidesList::addPlayerByTemplate` creates a player name from the PlayerTemplate name, creates a side Dict containing `TheKey_playerFaction`, and creates a singleton team.
- `prepareForMP_or_Skirmish` copies map side/team data into separate Skirmish side/team records, clears/rebuilds portions of the side list, and loads default scripts from `Data\\Scripts\\SkirmishScripts.scb` when no side scripts are present.
- Side data is stored in arrays sized by `MAX_PLAYER_COUNT`; map side parsing also stops at that bound.

Implication: registering a PlayerTemplate alone does not establish a complete playable Skirmish faction. Side identity, teams, AI scripts/build lists and capacity must be handled together.

### Skirmish preferences serialize faction selection as an integer index

File: `Generals/Code/GameEngine/Source/GameClient/GUI/GUICallbacks/Menus/SkirmishGameOptionsMenu.cpp`

- `SkirmishPreferences::getPlayerTemplate` reads an integer from the saved preference.
- It validates that integer against `ThePlayerTemplateStore->getPlayerTemplateCount()`, then resolves it with `getNthPlayerTemplate(index)`.
- It also rejects a template without a starting building.

Implication: adding/remapping faction slots affects saved preferences and requires a stable index/ID strategy. Preserve vanilla indices and define behavior for stale Fusion profiles.

### BIG loading is an ordered global asset mount

File: `Core/GameEngineDevice/Source/StdDevice/Common/StdBIGFileSystem.cpp`

- The filesystem loads BIG files from configured asset directories through `loadBigFilesFromDirectory(..., "*.big", overwrite)`.
- This mechanism is directory/archive-oriented and does not receive an owning faction or definition context.

Implication: it cannot select between two different files at the same virtual path based on which faction's unit requested the asset. Asset isolation requires source context in the asset resolver or collision-free virtual asset names with all references updated.

## Immediate engineering consequence

The first native proof-of-concept should not start by changing the Skirmish UI. It should prove a single colliding definition and one same-path asset can be resolved from two independent source IDs during one running match. After that, wire source identity through faction registration, object creation, weapon/FX/power references and Skirmish selection.

The audit has not yet proven that every object/weapon/UI/asset lookup can carry a source context. That requires a call-graph audit of the INI definition registries and runtime creation/lookup paths. Any domain that cannot carry context must be safely namespaced in memory or explicitly rejected by preflight.
