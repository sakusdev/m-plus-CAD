# m+CAD Roadmap

このロードマップは依存順を示す。日付やリリース保証ではない。

## Wave 0 — Architecture foundation

- [x] Product boundary and dependency direction
- [x] Parallel-agent contribution rules
- [x] Neutral coordinate-system contract
- [x] Snapshot and generated-scene contracts
- [x] Material and marker policy
- [x] Select initial Minecraft version and loader through an ADR
- [x] Add multi-module build skeleton
- [x] Add immutable `mcad-api` types and contract tests
- [x] Create integration branch after architecture approval

## Wave 1 — Minimal geometry pipeline

- [x] In-game two-point selection and wireframe preview
- [x] Immutable world-to-`StructureSnapshot` adapter
- [x] Full-cube geometry generation
- [x] Hidden-face removal
- [x] Default grouping by block identifier
- [x] Deterministic mesh IDs and diagnostics
- [x] GUI shell with persisted project settings

MVP acceptance:

1. A selected volume can be snapshotted without Minecraft objects escaping the adapter.
2. Adjacent full blocks do not generate internal faces.
3. Different block identifiers produce distinct mesh groups by default.
4. All MVP settings are visible in the GUI.

## Wave 2 — Editable export

- [x] OBJ/MTL exporter
- [x] glTF/GLB exporter
- [x] Explicit coordinate, scale, and origin controls
- [x] Original single-colour material table
- [x] User-defined material mapping
- [x] Vertex-colour mode
- [x] Transparent and emissive material properties
- [x] Export capability warnings
- [x] Progress, cancellation, and safe file replacement

No Minecraft texture extraction or bundling is permitted.

## Wave 3 — Modelling semantics

- [ ] Configurable marker-rule GUI
- [x] Origin, light, camera, curve, bone, collision, and metadata markers
- [x] Object hierarchy and user groups
- [ ] Connected-component separation
- [ ] Block-state separation
- [ ] Geometry replacement providers
- [x] Curves from ordered marker chains
- [x] Blender add-on for scene organization and neutral material linking

## Wave 4 — Optimization and large structures

- [ ] Greedy meshing with material-boundary preservation
- [ ] Chunk/tile partitioning
- [ ] Instancing
- [ ] Incremental/differential file export
- [ ] Memory-bounded snapshot and generation pipeline
- [ ] LOD scene generation
- [ ] Collision and occluder meshes
- [ ] 3D-print diagnostics and printable-mesh transformations

## Wave 5 — Live and round-trip workflows

- [x] Minecraft-to-Blender live link
- [x] Differential scene updates
- [ ] Blender-to-Minecraft voxelization
- [ ] Snapshot animation and redstone-state recording
- [ ] Display entity and armour-stand rig metadata
- [x] Versioned live-link protocol

Live Link v1 is intentionally one-way: Minecraft → Blender. It uses an authenticated loopback-only WebSocket and stable-ID upsert/remove operations. Blender-to-Minecraft edits require a future protocol version and explicit safety design.

## Deliberately excluded

- Minecraft texture/model extraction
- Bundling Mojang/Microsoft assets
- Hidden export presets that rewrite settings
- Exporters reading live Minecraft worlds directly
- Automatic ownership claims over generated user models
- Direct `.blend` serialization from Java without Blender

## Quality gates for every wave

- deterministic tests
- no cross-module dependency inversion
- documented schema changes
- bounded error messages and diagnostics
- GUI exposure for user-facing settings
- cancellation for potentially long client operations
- no silent loss of unsupported scene data
