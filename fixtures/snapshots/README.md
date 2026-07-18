# Snapshot fixtures

These fixtures describe neutral logical snapshot scenarios only. They contain no Minecraft textures,
model JSON, registry dumps, NBT payloads, or other Mojang/Microsoft assets.

`deterministic-basic.snapshot` pins the content-derived identity for a selection containing two neutral
block entries and ten omitted air cells. The corresponding JUnit test constructs the same logical input
with intentionally non-canonical identifier casing and state-property ordering.
