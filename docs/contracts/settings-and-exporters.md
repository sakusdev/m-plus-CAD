# Settings and Exporter Contract

## ProjectSettings

All user-facing behaviour is represented by a versioned immutable settings snapshot. The in-game GUI is the primary editor for this model.

Top-level areas:

```text
schemaVersion
selection
geometry
meshSeparation
materials
transform
markers
optimization
animation
collision
output
preview
advanced
```

Commands, automation, and tests may create the same model programmatically, but must not introduce settings unavailable to the GUI without a documented reason.

## No export presets

m+CAD does not provide opaque target presets such as “Unity”, “Blender”, or “VRChat” that silently modify multiple settings.

The GUI may:

- show exporter capabilities
- disable impossible controls
- explain incompatible combinations
- display recommended values as non-destructive hints

The GUI may not rewrite unrelated settings merely because an output format changes.

## Exporter interface

A `ModelExporter` conceptually provides:

```text
formatId()
displayName()
fileExtensions()
capabilities()
preflight(scene, options)
export(scene, destination, options, progress, cancellation)
```

`preflight` is side-effect free and returns diagnostics.

## Capabilities

Capabilities are explicit flags or structured limits, including:

- hierarchy
- multiple meshes
- multiple materials
- vertex colours
- alpha modes
- custom properties
- lights
- cameras
- curves
- bones/animation
- collision metadata
- embedded binary assets
- external file sets

A missing capability results in a preflight warning or error according to user-selected loss policy.

## File safety

Exporters must:

- validate destination paths
- write to temporary files or directories when practical
- replace final outputs only after successful completion
- support cancellation
- clean up temporary outputs
- avoid writing outside the selected destination through crafted names or paths
- return the complete list of produced files

## Naming

Names are sanitized per target format, while stable IDs remain unchanged. The exporter reports name substitutions when they could affect external workflows.

Collisions are resolved deterministically, for example:

```text
stone
stone_2
stone_3
```

## Coordinate conversion

Exporters consume internal Y-up right-handed coordinates and apply the configured transform at the boundary. They do not reinterpret snapshot coordinates.

## Error policy

Errors use stable categories:

- invalid settings
- unsupported capability
- invalid scene data
- destination conflict
- I/O failure
- cancellation
- internal exporter failure

Expected invalid input must not be reported as a generic internal exception.

## Determinism

When format metadata permits it, the same scene and options produce stable file ordering and serialized element ordering. Timestamps and generator-version strings must be controllable or excluded from golden-file comparisons.
