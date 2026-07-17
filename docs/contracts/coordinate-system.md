# Coordinate System Contract

Status: architecture baseline. Changes require an ADR and migration note.

## Internal space

m+CAD uses one exporter-independent coordinate space:

- right-handed
- Y-up
- `1.0` unit per Minecraft block
- integer block boundaries
- relative coordinates inside a snapshot

A block at integer position `(x, y, z)` occupies:

```text
[x, x + 1] × [y, y + 1] × [z, z + 1]
```

Its centre is `(x + 0.5, y + 0.5, z + 0.5)`.

The minimum selected corner maps to snapshot coordinate `(0, 0, 0)`. Absolute world coordinates are metadata and must not be baked into mesh vertices unless the user selects a world-origin export mode.

## Winding and normals

Triangles use counter-clockwise winding when viewed from the front in internal space. Normals point outwards.

Full-cube face order and corner order must be defined once in `mcad-core` and covered by tests. Exporters may transform coordinates but must preserve front-face orientation, including when an axis transformation changes handedness.

## Origin

The generated scene stores an explicit origin transform. Supported origin modes are data, not exporter behaviour:

- selection minimum corner
- selection centre
- bottom centre
- player position captured at snapshot time
- marker-defined origin
- preserved world origin
- explicit numeric offset

Changing origin must not mutate the underlying snapshot.

## Export transformation order

Unless a format contract explicitly requires a different serialization order, the logical transformation is:

```text
relative vertex
→ origin relocation
→ user rotation
→ unit scale
→ target-axis conversion
```

Exporters must expose the effective transform in diagnostics or metadata.

## Numeric rules

- Geometry generation uses double precision internally where practical.
- Serialized formats may use float precision when required.
- Equality tests use a documented epsilon; exact floating-point equality is not assumed after transforms.
- Integer block positions remain integers in snapshots.
- NaN and infinity are invalid in public scene data.

## Examples

Two adjacent blocks at `(0,0,0)` and `(1,0,0)` share the plane `x = 1`. Hidden-face removal eliminates both coincident internal faces.

A 2×1×1 selection occupies internal bounds:

```text
min = (0,0,0)
maxExclusive = (2,1,1)
```

Bounds are minimum-inclusive and maximum-exclusive.
