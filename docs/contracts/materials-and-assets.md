# Materials and Asset Policy

## Core rule

m+CAD does not bundle, extract, copy, reconstruct, or export Minecraft textures, block-model JSON, sounds, or other Mojang/Microsoft assets.

The default export remains useful through neutral project-owned material data and user-controlled mappings.

## Neutral material fields

```text
stableId
name
baseColourRGBA                linear or explicitly tagged colour space
metallic                      0..1
roughness                     0..1
emissiveColourRGB
emissiveStrength              >= 0
alphaMode                     OPAQUE | MASK | BLEND
alphaCutoff                   optional
externalUserAssetReferences   optional
customProperties
```

All numeric fields are finite and validated.

## Built-in colour table

A built-in colour table may map canonical block identifiers to original m+CAD colours and generic PBR values.

Requirements:

- values are authored independently by the project
- values are not sampled from Minecraft textures
- the table contains no copied texture files
- every entry has a documented fallback
- missing blocks use a stable generated identification colour or user-selected fallback

## Material modes exposed in GUI

- no materials
- original single-colour table
- vertex colours
- deterministic identification colours
- user-defined material mapping

The mode is an explicit project setting. Export format selection does not silently change it.

## User-provided assets

Users may reference their own texture or material assets. m+CAD must:

- store references separately from bundled project files
- avoid committing or uploading user assets automatically
- resolve paths safely
- warn about missing files
- never claim ownership
- copy assets into an export only when the user explicitly enables that behaviour

External references should support portable project-relative paths where possible.

## Blender material linking

The Blender add-on may map a stable m+CAD material ID to an existing Blender material. This mapping belongs to the user’s Blender/project configuration, not to the Minecraft snapshot.

## Transparency and emission

Transparency and emission are neutral properties, not evidence that Minecraft textures or rendering code were copied. The GUI allows users to edit generated defaults.

## Output rights notice

Generated models and material definitions are not considered derivative works of m+CAD solely because the software generated them. Third-party content supplied by the user remains subject to its own terms.
