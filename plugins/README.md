# plugins/

This directory is the source tree for the community screen marketplace.

## Structure

```
plugins/
  _template/          ← copy this to start a new plugin
  <alias>/            ← one folder per developer, named after their alias
    <plugin-name>/    ← one Gradle project per plugin
```

Each `<plugin-name>/` directory is a self-contained Android Gradle project that builds a single APK. The APK is loaded at runtime by the host app — it is never installed on the device.

## Getting started

See [`docs/marketplace/`](../reference-docs/marketplace/README.md) for:

- [`README.md`](../reference-docs/marketplace/README.md) — what plugins are and what they can do
- [`api-reference.md`](../reference-docs/marketplace/api-reference.md) — ClusterPlugin, VehicleSnapshot, PluginContext
- [`submission.md`](../reference-docs/marketplace/submission.md) — step-by-step build and PR guide

## CI / publishing

GitHub Actions will be configured to build each plugin on PR and, after merge, produce a signed release APK, compute its SHA-256, and update `index.json` automatically. This is not yet implemented.
