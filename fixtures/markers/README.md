# Marker interpretation fixtures

This directory documents the canonical scenarios covered by `mcad-markers` tests. It contains no
Minecraft textures, models, NBT objects, block-model JSON, or executable marker content.

Deterministic conventions:

- rules are evaluated by descending priority, then ascending namespaced rule ID
- snapshot traversal follows the API `(y, z, x)` order
- spatial marker positions default to the centre of the source block, plus explicit neutral offsets
- curve points are ordered by configured integer order, then source position, then rule ID
- same-priority matches and duplicate curve orders produce diagnostics instead of nondeterminism
- semantic source blocks are removed only after a rule action validates successfully
- marker names and path-like identifiers are sanitized; marker text is never evaluated as code

Required scenarios are implemented in `ConfigurableMarkerInterpreterTest`: origin, conflicts,
consumed/retained sources, invalid parameters, deterministic curve chains, unknown custom actions,
unsafe text/path input, and cancellation.
