# m+CAD Roadmap

このロードマップは依存順を示す。日付やリリース保証ではない。

## Wave 0 — Architecture foundation

- [x] Product boundary and dependency direction
- [x] Parallel-agent contribution rules
- [x] Neutral coordinate-system contract
- [x] Snapshot and generated-scene contract drafts
- [x] Material and marker policy
- [ ] Select initial Minecraft version and loader through an ADR
- [ ] Add multi-module build skeleton
- [ ] Add immutable `mcad-api` types and contract tests
- [ ] Create integration branch after architecture approval

## Wave 1 — Minimal geometry pipeline

Parallelizable after API contracts are merged:

- [ ] In-game two-point selection and wireframe preview
- [ ] Immutable world-to-`StructureSnapshot` adapter
- [ ] Full-cube geometry generation
- [ ] Hidden-face removal
- [ ] Default grouping by block identifier
- [ ] Deterministic mesh IDs and diagnostics
- [ ] GUI shell with persisted project settings

MVP acceptance:

1. A selected volume can be snapshotted without Minecraft objects escaping the adapter.
2. Adjacent full blocks do not generate internal faces.
3. Different block identifiers produce distinct mesh groups by default.
4. All MVP settings are visible in the GUI.

## Wave 2 — Editable export

- [ ] OBJ/MTL exporter
- [ ] glTF/GLB exporter
- [ ] Explicit coordinate, scale, and origin controls
- [ ] Original single-colour material table
- [ ] User-defined material mapping
- [ ] Vertex-colour mode
- [ ] Transparent and emissive material properties
- [ ] Export capability warnings
- [ ] Progress, cancellation, and safe file replacement

No Minecraft texture extraction or bundling is permitted.

## Wave 3 — Modelling semantics

- [ ] Configurable marker-rule GUI
- [ ] Origin, light, camera, curve, bone, collision, and metadata markers
- [ ] Object hierarchy and user groups
- [ ] Connected-component separation
- [ ] Block-state separation
- [ ] Geometry replacement providers
- [ ] Curves from ordered marker chains
- [ ] Blender add-on for scene organization and user material linking

## Wave 4 — Optimization and large structures

- [ ] Greedy meshing with material-boundary preservation
- [ ] Chunk/tile partitioning
- [ ] Instancing
- [ ] Incremental/differential export
- [ ] Memory-bounded snapshot and generation pipeline
- [ ] LOD scene generation
- [ ] Collision and occluder meshes
- [ ] 3D-print diagnostics and printable-mesh transformations

## Wave 5 — Live and round-trip workflows

- [ ] Minecraft-to-Blender live link
- [ ] Differential scene updates
- [ ] Blender-to-Minecraft voxelization
- [ ] Snapshot animation and redstone-state recording
- [ ] Display entity and armour-stand rig metadata
- [ ] Versioned live-link protocol

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
