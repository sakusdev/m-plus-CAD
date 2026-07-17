# m+CAD Architecture

## 1. Product boundary

m+CAD turns Minecraft into a block-based modelling front end. The project exports editable geometry and scene metadata; it is not a Minecraft asset extractor.

Non-negotiable product rules:

1. Minecraft textures, models, sounds, or other Mojang/Microsoft assets are not bundled, extracted, or copied into exports.
2. Every user-facing export setting is configurable through an in-game GUI. Command-line commands may automate the same settings but must not become the only interface.
3. Mesh separation by block identifier is the default. Additional separation rules are optional transformations over the same scene model.
4. Exporters consume a neutral generated-scene model. They never read a Minecraft world directly.
5. Minecraft classes may exist only in the Minecraft adapter module.
6. User-generated output is not claimed by the project merely because m+CAD generated it.
7. There are no opaque “Unity”, “Blender”, or similar export presets. Capabilities and individual settings are exposed explicitly.

## 2. Pipeline

```text
Minecraft world / structure
        │
        ▼
Minecraft adapter
        │  immutable StructureSnapshot
        ▼
Marker interpreter
        │  geometry blocks + scene instructions
        ▼
Mesh generator / optimizer
        │  GeneratedScene
        ├───────────────┬────────────────┬───────────────┐
        ▼               ▼                ▼               ▼
   OBJ exporter     glTF/GLB exporter  future formats  live-link transport
```

The two stable boundaries are `StructureSnapshot` and `GeneratedScene`. Parallel implementations must communicate through these contracts instead of importing one another’s internals.

## 3. Planned modules

```text
mcad-api/            Neutral immutable data contracts and extension interfaces
mcad-core/           Mesh generation, culling, grouping, optimization
mcad-materials/      Independent colour/PBR definitions and user mappings
mcad-markers/        Configurable marker-rule interpretation
mcad-export-obj/     OBJ/MTL writer
mcad-export-gltf/    glTF/GLB writer
mcad-minecraft/      Selection, world snapshot, GUI, persistence, renderer hooks
blender-addon/       Optional Blender-side import and live-link helpers
```

Dependency direction:

```text
api <- core
api <- materials
api <- markers
api + core <- exporters
api + core + materials + markers <- minecraft adapter
Generated files <- blender addon
```

Forbidden dependencies:

- `mcad-api` must not depend on Minecraft, a loader, Blender, or an exporter.
- Exporters must not depend on each other.
- `mcad-core` must not access the Minecraft client, world, registries, or GUI.
- Blender code must not be required to build or run the Minecraft mod.

## 4. Stable data contracts

### StructureSnapshot

An immutable, deterministic snapshot of a selected block volume. It contains dimensions, relative block positions, canonical block identifiers, sorted block-state properties, and optional namespaced metadata.

The snapshot is detached from the live world so mesh generation can run on worker threads and in unit tests.

### GeneratedScene

A neutral result containing:

- mesh groups and triangle primitives
- material definitions without Minecraft textures
- hierarchy and object names
- origin and transform metadata
- lights, cameras, curves, bones, collision meshes, and custom properties
- diagnostics and generation statistics

Export-format limitations are reported through capabilities and diagnostics rather than silently changing settings.

## 5. Coordinate system

The internal coordinate system is fixed and exporter-independent:

- right-handed
- Y-up
- one Minecraft block equals `1.0` internal unit
- block boundaries lie on integer coordinates
- block `(x, y, z)` occupies `[x,x+1] × [y,y+1] × [z,z+1]`
- snapshots use coordinates relative to the selected minimum corner

Axis conversion, unit scaling, rotation, and origin relocation happen at the export boundary. See `docs/contracts/coordinate-system.md`.

## 6. Determinism

Given the same snapshot and settings, generation must produce byte-for-byte stable logical data before format serialization.

Required practices:

- sort block-state properties and material identifiers
- do not rely on hash-map iteration order
- use stable mesh-group identifiers separate from display names
- define floating-point tolerances in tests
- make optimization passes explicit and ordered

## 7. Geometry policy

The MVP supports full-cube block geometry with hidden-face removal. Later geometry providers may support stairs, slabs, fences, user replacements, curves, smoothing, and other shapes.

Default grouping key:

```text
namespace:block_id
```

Optional ordered keys:

1. user group
2. block identifier
3. block state
4. connected component
5. material identifier
6. chunk/tile identifier

No exporter may independently regroup geometry without an explicit export option.

## 8. Materials policy

Minecraft textures are not part of the pipeline. The neutral material model supports:

- base colour
- metallic
- roughness
- alpha and alpha mode
- emissive colour and strength
- optional references to user-provided external assets
- vertex colours

Built-in colours are original project data, not sampled from Minecraft textures. User assets remain user-controlled and are never committed automatically.

## 9. GUI and settings

The GUI edits a versioned `ProjectSettings` model. Core and exporter code receives immutable settings snapshots.

Settings areas:

- selection
- geometry and hidden-face removal
- mesh separation
- materials
- transforms and origin
- markers
- optimization
- animation and hierarchy
- collision
- output format and path
- diagnostics and preview

Unsupported combinations must be disabled or explained before export. Settings must not be silently rewritten by output format selection.

## 10. Extension points

Planned interfaces:

- `GeometryProvider`
- `MeshGenerator`
- `MarkerInterpreter`
- `MaterialResolver`
- `SceneOptimizer`
- `ModelExporter`
- `LiveLinkTransport`

New extension points require an architecture decision record when they alter a stable contract.

## 11. Versioning

Serialized settings, snapshots, marker mappings, and live-link messages include an explicit schema version.

Compatibility rules:

- additive optional fields are backward-compatible
- removed or reinterpreted fields require a schema migration
- exporters declare supported scene-model versions
- unknown namespaced metadata must be preserved when practical

## 12. Loader and Minecraft version strategy

The initial Minecraft loader and target game version are intentionally not fixed in this architecture PR. They must be selected in a dedicated decision record after checking current modding support.

Loader-specific code stays behind the Minecraft adapter. Core contracts must remain usable without launching Minecraft.

## 13. Parallel development rule

Each implementation agent owns one module or narrowly defined path. Shared contracts, root build files, dependency versions, registries, and integration wiring are changed only by the architecture/integration owner.

See `CONTRIBUTING_AGENTS.md` for the operational rules.
