# StructureSnapshot Contract

`StructureSnapshot` is the only supported input from a Minecraft adapter into neutral processing code.

## Required fields

```text
schemaVersion
snapshotId
size                         integer width/height/depth
sourceWorldOrigin            optional absolute world position
blocks[]
metadata                     namespaced optional values
```

Each block entry contains:

```text
relativePosition             integer x/y/z
blockId                      canonical namespace:path
stateProperties              sorted string-to-string map
metadata                     namespaced optional values
```

Air is omitted by default. An explicit setting may preserve selected empty cells for algorithms that require them, but this must be represented as snapshot metadata rather than fake block identifiers.

## Invariants

- The snapshot is immutable after construction.
- Every relative position is within `[0,size)`.
- At most one block entry exists at each relative position.
- `blockId` is canonical and lowercase where the source registry is case-insensitive.
- State-property ordering is deterministic.
- Snapshot iteration order is lexicographic `(y, z, x)` unless a later ADR changes it.
- Minecraft API objects, registry handles, NBT classes, text objects, or mutable collections do not cross this boundary.

## Metadata

Metadata keys are namespaced, for example:

```text
mcad:block_entity
mcad:biome
mcad:block_light
mcad:sky_light
example-extension:custom-value
```

Unknown metadata should be preserved when practical. Core algorithms must ignore unknown keys safely.

Nested metadata requires a versioned neutral value model; adapters must not expose raw platform-specific objects.

## Threading

World reads happen according to the Minecraft loader’s threading rules. Once built, a snapshot can be processed off-thread because it has no live-world references.

Snapshot generation must support progress and cancellation for large selections. Cancellation produces no partial snapshot unless an explicit streaming API is used.

## Deterministic identity

`snapshotId` identifies snapshot content and settings, not the wall-clock operation. The implementation may use a digest over canonical serialized content. Timestamps, random UUIDs, iteration-order-dependent maps, and absolute file paths must not affect content identity.

## Block entities and entities

Block entities and ordinary entities are optional extensions. They are excluded from the MVP unless required by a marker rule.

When included:

- data is converted to neutral versioned metadata
- sensitive or irrelevant fields may be filtered
- Minecraft classes never leave the adapter
- unsupported values generate diagnostics rather than crashing the export

## Serialization

A debug serialization format may be added for fixtures and bug reports. It is not automatically a stable public interchange format. Any persisted format includes `schemaVersion` and validates size limits before allocation.
