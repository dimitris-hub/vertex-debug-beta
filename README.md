# Vertex Debug

A Meteor Client addon. Includes **Chunk Finder** — scans loaded chunks for
user-selected block types and highlights the surrounding 2×2 chunk area
(4 chunks total), rendered below Y=0 so it stays visible underground.

## Important — read before building

I generated this project without network/build access in my sandbox, so I
could **not** actually run Gradle against the real Fabric/Meteor Client
dependencies or compile-check it end to end. Treat this as a strong
starting scaffold, not a verified-working jar. Concretely:

1. **Version numbers in `gradle.properties`** (Minecraft/Yarn/Fabric
   Loader/Fabric API/Meteor Client) are placeholders for a recent 1.21.1
   setup. You must match these to whatever Meteor Client build you're
   actually running — check https://github.com/MeteorDevelopment/meteor-client
   and use their `gradle.properties` as the source of truth for your target version.
2. **`Modules.registerCategory`** and a couple of other Meteor internals
   are referenced by best-known API shape from the public addon template
   (https://github.com/MeteorDevelopment/meteor-addon-template). Meteor's
   internal APIs do change between versions — if a method doesn't resolve,
   check that template repo at the tag matching your Meteor version and
   adjust imports/method names accordingly.
3. **Chunk/section scanning** (`WorldChunk.getSection`, `getBlockState`,
   `getSectionIndex`) uses standard Yarn-mapped names for 1.21.x, but you
   should verify against your exact mappings version — this is the part
   most likely to need small signature tweaks.
4. I removed a fabricated palette-introspection shortcut that would not
   have compiled; the current code does a direct per-block scan of loaded
   chunk sections instead. It's correct but not maximally optimized —
   fine at typical render distances with a 20-tick scan interval, but if
   you push render distance very high you may want to raise
   `scan-interval` or add real palette-level short-circuiting yourself.

## Build

```
./gradlew build
```

Output jar: `build/libs/vertex-debug-1.0.0.jar` — drop into your `mods`
folder alongside `fabric-api.jar` and `meteor-client.jar`.

## Project layout

```
src/main/resources/fabric.mod.json      — mod metadata, meteor+client entrypoints
src/main/java/com/vertexdebug/
  VertexDebugAddon.java                 — MeteorAddon entrypoint, registers the module
  modules/ChunkFinder.java              — the Chunk Finder module (settings + logic + render)
```

## Chunk Finder settings

- **tracked-blocks** — add/remove blocks via the in-game GUI (defaults:
  Piston, Sticky Piston, Hopper)
- **scan-interval** — ticks between chunk scans
- **max-highlights** — cap on simultaneous highlight groups
- **side-color / line-color / fill** — standard Meteor render customization
- **below-y-extent / above-y-extent** — how far the highlight box extends
  past Y=0 in each direction (default 64 below, 0 above)
