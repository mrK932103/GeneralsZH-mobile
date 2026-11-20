# Generals Mod Fusion — namespace planner prototype

Date: 13 November 2026
Branch: `claude/generals-mod-fusion`

## Change

Added:

- `android/app/src/main/java/com/generalsx/zerohour/FusionNamespacePlan.java`
- `android/app/src/test/java/com/generalsx/zerohour/FusionNamespacePlanTest.java`

This is the first executable namespace-planning primitive in the Android project. It consumes the existing declaration index and typed-reference scanner and creates a deterministic mapping from:

`(owning mod source, declaration type, original name)`

to a generated runtime identifier.

The owner is normalized to the containing BIG archive or loose mod root, so definitions in multiple INI members of one archive share a namespace. Different mods with the same declaration name receive different generated identifiers. Hash input includes source, type, and original name; generated identifiers are conservative ASCII names with a bounded length.

Typed references resolve in this order:

1. A matching declaration from the same owning mod source.
2. A globally unique declaration of that type/name.
3. Otherwise, the reference is marked unresolved (missing or ambiguous).

Duplicate declarations within one source and generated-name collisions are also reported, never silently guessed.

## Test cases added

- Same typed definition in two mods gets distinct, stable identifiers.
- A reference in a mod resolves to that mod's own same-named definition.
- INI files inside one BIG archive share one source namespace.
- A reference outside two competing sources is rejected as ambiguous.
- A missing reference is rejected.

## Important limits — not a playable Fusion runtime

This class only creates a plan. It does **not** rewrite INI bytes, BIG archives, model/texture/audio paths, script strings, dynamic names, or native runtime lookups. It is not yet connected to the game engine or the Skirmish faction registry. The scanner also does not prove that every reference-bearing field in every total-conversion mod is covered.

In particular, source-qualified names alone cannot isolate same-path assets. The native engine still uses global definition registries and global asset lookup, as recorded in PLAN-029 and PLAN-030. Before this plan can be applied to a playable package, the project still needs:

1. A byte-preserving INI rewrite pass with exact token spans and a complete typed-reference audit.
2. Asset virtual-path remapping or a source-aware native asset resolver.
3. Native registration/lookup changes for all relevant definition domains, or proof that every lookup is safely remapped.
4. Skirmish template registration and side/AI integration.
5. Native build, unit/integration tests, and a real match with two mods that deliberately reuse a definition name and asset path.

The planner is intentionally not allowed to turn an incomplete scan into a package that claims to be playable.

## Validation status

The new JUnit tests are committed but were not executed in this environment. No Android APK was built and no device test was performed. The existing safe BIGF overlay remains separate from source-isolated faction loading.
