# Versioning and compatibility

Transerver uses semantic versions. Before `1.0.0`, a minor release may adjust public Java APIs; patch releases remain source-compatible. After `1.0.0`, incompatible API changes require a major release.

Four versions are intentionally independent:

- library version: published artifact and Java API;
- wire protocol version: encoded in every Transerver envelope;
- storage format version: owned by each `MessageStore` implementation;
- channel schema version: owned by the application, such as `distantstock:v1/package.dispatch`.

An application must not infer one version from another. A new Distant Stock payload schema does not require replacing the transport protocol, and a WebSocket transport does not change stored packages.

## Compatibility rules

- Unknown wire versions are rejected before allocating payload memory.
- A message ID keeps the same bytes, source and destination for every retry.
- A `MessageStore` upgrade must either read the previous format or migrate atomically before starting.
- Public extension interfaces receive additive methods only through a new compatible sub-interface or a default method.
- Deprecated API remains for at least one minor release before removal.
- Tagged builds use `-PreleaseVersion`; untagged builds remain `SNAPSHOT` builds.

GitHub Actions builds every push and pull request on Java 21. Tags matching `v*` build the tag, rerun all tests and publish the distribution archives and JARs as a GitHub release.
