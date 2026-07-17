# Marker Rule Contract

Markers let Minecraft blocks and entities represent modelling instructions rather than exported render geometry.

## Rule shape

```text
ruleId                         stable namespaced identifier
priority                       explicit integer
match                          block/entity/state/metadata predicate
action                         origin, light, camera, curve point, bone, group, collision, metadata, custom
parameters                     action-specific neutral values
consumeSource                  whether marker geometry is omitted
validation                     constraints and required neighbours
```

Rules are stored in a versioned user-editable configuration and managed through the in-game GUI.

## Determinism

- Rules are evaluated by priority and stable `ruleId` order.
- Ambiguous matches produce diagnostics.
- A rule must declare whether later rules can also consume the same source.
- Marker chains use deterministic neighbour and ordering rules.
- Random identifiers are not generated during interpretation.

## Built-in action families

The architecture permits, but does not require all actions in the MVP:

- scene origin
- point/spot/directional light
- camera
- curve point and curve control metadata
- bone/empty
- object or collection group
- parent-child relationship
- collision classification
- LOD classification
- arbitrary namespaced metadata

## Source consumption

Semantic-only markers default to `consumeSource = true`, so the marker block is not included in render geometry. The GUI preview must show consumed markers distinctly.

Rules may retain source geometry when the user explicitly chooses it.

## Safety and validation

Marker text and metadata are data, not executable code. Implementations must not evaluate arbitrary scripts from signs, names, or NBT.

Names and file-related values are sanitized before export. Invalid parameters produce diagnostics and leave the underlying snapshot unchanged.

## Extensibility

Custom marker actions operate on neutral snapshots and scene builders. They do not receive a live Minecraft world or exporter instance.

New action types define:

- schema version
- required parameters
- generated scene elements
- capability requirements
- preview representation
- round-trip behaviour, if any

## GUI requirements

Users can:

- add, duplicate, reorder, enable, and disable rules
- select matching block identifiers and states
- edit action parameters
- choose whether source geometry is consumed
- inspect conflicts and validation errors
- preview marker interpretation before export

No hard-coded block mapping is required for core functionality.
