# mcad-api contract fixtures

This directory documents canonical scenarios used by contract tests. It intentionally contains no
Minecraft assets or serialized public interchange format.

Canonical ordering rules:

- block entries: `(y, z, x)`
- canonical identifiers: `namespace`, then `path`
- scene objects: `stableId`
- diagnostics: severity, code, message, source key
- produced files: portable relative path

The Java unit tests construct these scenarios directly until a versioned debug fixture format is
approved by a later ADR.
