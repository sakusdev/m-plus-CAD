# GeneratedScene Contract

`GeneratedScene` is the neutral output of marker interpretation, mesh generation, and optimization. Every exporter and live-link transport consumes this model.

## Top-level contents

```text
schemaVersion
sceneId
rootNodes[]
meshes[]
materials[]
lights[]
cameras[]
curves[]
bones[]
collisions[]
customProperties
diagnostics[]
statistics
```

Collections are immutable and deterministically ordered.

## Nodes

A scene node contains:

```text
stableId
name
parentId                      optional
localTransform
meshIds[]
childIds[]
customProperties
sourceReferences[]
```

`stableId` is machine-oriented and does not change when a user-facing name is edited. Exporters use stable IDs to preserve live-link and incremental-update identity.

## Meshes

A mesh contains one or more primitives. A primitive contains:

```text
positions
normals
indices                       triangles
materialId                    optional
vertexColours                 optional
customAttributes              optional, namespaced
```

UV coordinates are optional and are not generated merely to reproduce Minecraft textures. They may be used for user-provided materials or future neutral workflows.

Indices must be valid, triangle counts must be integral, and positions/normals must contain only finite values.

## Default mesh separation

The default generation mode creates a distinct mesh group for each canonical block identifier. Grouping is performed before exporter serialization.

Optional ordered separation keys include:

- user group
- block identifier
- block state
- connected component
- material
- chunk/tile

Exporters must not collapse or split these groups silently.

## Scene elements

Lights, cameras, curves, bones, collision meshes, and metadata originate from explicit settings, marker rules, or neutral processors. Marker source blocks are omitted from render geometry by default when the rule declares them semantic-only.

Every scene element carries stable identity and optional source references.

## Source references

Source references provide traceability without retaining a live world:

```text
snapshotId
relativeBlockPosition         optional
blockId                       optional
markerRuleId                  optional
```

They support diagnostics, GUI previews, and incremental updates.

## Diagnostics

Diagnostics use stable codes and severity:

```text
INFO
WARNING
ERROR
```

Examples:

- unsupported format capability
- invalid marker chain
- material mapping missing
- geometry provider fallback
- degenerate triangle removed

An exporter must report loss of scene data. Silent dropping is forbidden.

## Statistics

Statistics may include:

- source block count
- visible face count
- removed hidden faces
- vertices and triangles
- mesh/material counts
- elapsed phase durations
- estimated output size

Timing values are informational and excluded from deterministic scene identity.

## Capability negotiation

Exporters declare capabilities separately from the scene. A preflight step compares scene features and chosen options with exporter capabilities. It returns diagnostics before writing any file.

The output format must not mutate the shared scene to hide unsupported data.
