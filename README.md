# PhysicsScene

PhysicsScene is a physics-first Jetpack Compose library powered by Box2D.

It lets you register existing composables as physics bodies inside a scene and drive rich UI interactions (falling, shattering, recall, impulse interactions) without replacing your layout system.

## Installation

```kotlin
dependencies {
    implementation("io.github.damianpetla:physics-scene:0.1.0")
}
```

## Quick start

```kotlin
val sceneState = rememberPhysicsSceneState()

PhysicsScene(
    state = sceneState,
) {
    Button(
        onClick = { sceneState.activateBody("cta") },
        modifier = Modifier.physicsBody(
            id = "cta",
            effect = FallingShatterEffect(),
        ),
    ) {
        Text("Drop me")
    }
}
```

## Demo app

This repository includes `:app-demo` as a showcase app.

Release builds of the demo are published as signed APK assets in GitHub Releases.

## Compatibility

- Android only
- Jetpack Compose UI
- Box2D runtime included via native dependencies

## Versioning

PhysicsScene follows SemVer.

- Current baseline: `0.1.0`
- Breaking API changes are possible before `1.0.0`

## License

Apache License 2.0. See [LICENSE](LICENSE).
