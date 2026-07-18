# m+CAD OBJ/MTL exporter

This module serializes neutral `GeneratedScene` values to deterministic Wavefront OBJ and MTL files.
It never reads a Minecraft world and never generates, copies, or references Minecraft textures.

## Output

A destination named `model.obj` produces the following sibling files only after both temporary files
have been written successfully:

- `model.obj`
- `model.mtl`

`MeshGroup` boundaries remain separate OBJ `o` and `g` records. Node transforms are applied to mesh
instances at the export boundary. A handedness-changing transform reverses triangle winding.

## Export options

Options use the existing immutable `ExportOptions` metadata map:

| Identifier | Type | Default | Meaning |
| --- | --- | --- | --- |
| `mcad:transform/origin_offset` | three-number list | `[0, 0, 0]` | Point subtracted before rotation |
| `mcad:transform/rotation_degrees` | three-number list | `[0, 0, 0]` | X, then Y, then Z rotation |
| `mcad:transform/unit_scale` | positive number | `1` | Uniform output scale |
| `mcad:transform/target_axis` | string | `internal_right_handed_y_up` | Internal Y-up, right-handed Z-up, or left-handed Y-up |
| `mcad:loss_policy` | string | `warn_and_continue` | `fail` or `warn_and_continue` for unsupported scene data |

The logical order is origin relocation, XYZ rotation, unit scale, then target-axis conversion.
Format selection does not mutate these values.

## Material policy

Neutral base colour and alpha are written to MTL. Metallic, roughness, emission, mask cut-off, and
blend semantics are represented as deterministic comments or legacy approximations and produce
preflight diagnostics where information may be lost. External user assets are not copied or written
as texture paths by this exporter.
