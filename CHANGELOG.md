# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Breaking
- Replaced `PhysicsItemEventType` + `PhysicsItemEvent` with typed `PhysicsSceneEvent` hierarchy:
  - `BodyActivated`
  - `BodyShatteringStarted`
  - `BodyRemoved`
  - `ShardHit(ownerId, hitterId, shardId)`
  - `ShardDropped(ownerId, shardId)`
- Renamed `PhysicsScene` callback from `onItemEvent` to `onEvent`.

### Added
- Reactive snapshot streams in `PhysicsSceneState`:
  - `bodySnapshots: StateFlow<Map<PhysicsId, PhysicsBodySnapshot>>`
  - `shardSnapshots: StateFlow<List<PhysicsShardSnapshot>>`
- `respawnBody(id)` command for per-body recovery without full `resetScene()`.
- Extended smoke coverage in `:app-demo` for all curated demo screens.

### Changed
- Emoji Cannon user-facing copy is now fully English.
- Public API KDoc coverage expanded across `:physics-scene` symbols.
- Docs expanded with events/snapshots and reset/respawn behavior.

### Migration
- Replace `onItemEvent = { ... }` with `onEvent = { ... }`.
- Update event handling to the new typed events.
- Replace any `PhysicsItemEventType` usage with `when (event)` over `PhysicsSceneEvent`.

## [0.1.0] - 2026-02-27

### Added
- Rebranded library API to `PhysicsScene` with package `dev.damianpetla.physicsscene`.
- Curated demo baseline with 4 demos: Falling Shatter, Center Burst, Shard Recall, Emoji Cannon.
- Maven Central publishing foundation (Vanniktech + Dokka + signing-ready metadata).
- GitHub Actions workflows for PR checks, release, and docs pages.
- Astro + Starlight docs scaffold.

### Changed
- Removed `:app` module from the project.
- Removed AGSL-based demo code paths to keep repository physics-first.
