# Generals Mod Fusion — source namespace and asset-path planning

Date: 13 November 2026
Branch: `claude/generals-mod-fusion`

## Implemented in this step

Added a second planning layer for virtual asset paths:

- `android/app/src/main/java/com/generalsx/zerohour/FusionAssetNamespacePlan.java`
- `android/app/src/test/java/com/generalsx/zerohour/FusionAssetNamespacePlanTest.java`

The plan consumes the existing BIGF preflight path inventory. A virtual path that occurs in more than one selected mod receives a deterministic source-qualified target such as:

`__fusion/<source-hash>/art/textures/shared.tga`

Unique paths remain unchanged. Absolute paths, traversal paths, missing owners, duplicate entries within one source, and generated path collisions are reported as unresolved.

The definition planner and asset planner are now exposed from `FusionPreflight.Result` through:

- `assessDefinitionNamespaces()`
- `assessAssetNamespaces()`

## Source identity correction

The preflight inventory labels archive owners as `ModName/Archive.big` and INI sources as `ModName/Archive.big!Data/INI/...`.

Both namespace planners now normalize these to the selected mod folder identity. This is important: a mod can distribute its INI definitions and art assets across multiple BIG archives. Those archives must share one faction namespace; treating every archive as a separate mod would incorrectly split references.

Regression tests cover:
- A definition in one BIG archive referenced from another archive belonging to the same mod.
- A duplicate virtual path repeated across different mods (each gets a distinct planned target).
- A duplicate virtual path repeated across two archives of the same mod (reported as an invalid duplicate within one source).
- Unique assets retaining their original normalized path.
- Traversal/absolute paths being rejected.
- Same-named INI definitions receiving stable source-qualified names.
- Unique cross-source reference fallback using the normalized source key.
- Ambiguous and missing references being rejected.

## Corrected planner defect

The definition planner's unique-global-reference fallback previously looked up the target using the raw INI member source string even though its map was keyed by normalized source identity. The fallback now uses the same normalized source key. A regression test covers this case.

## What this still does NOT do

These are **plans**, not a runtime asset resolver or an archive rewriter. No original BIG archive or INI file is changed.

The engine still needs a mechanism to resolve a runtime virtual path to the correct selected mod's source archive. In addition, the project must find and safely rewrite every asset reference in INI files, scripts, W3D/model data, textures, audio, maps, and other resources. Binary formats and dynamically constructed paths cannot be assumed safe.

The next native milestone remains a proof-of-concept in the game engine:
1. Register two same-named definitions from separate mod sources under distinct runtime identifiers.
2. Resolve each definition's same-path model/texture from its own source during one running match.
3. Verify weapon, OCL/FX, command UI, and AI references do not cross source boundaries.
4. Only then integrate the factions into the existing Skirmish selection and side/AI systems.

## Validation status

JUnit test source files were added/updated but were not executed in this environment. No native runtime patch was made, no APK was built, and no real match was run. The playable-package gate remains disabled.
