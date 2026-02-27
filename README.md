# PhysicsScene

PhysicsScene is a physics-first Jetpack Compose library powered by Box2D.

It lets you register existing composables as physics bodies inside a scene and drive rich UI interactions (falling, shattering, recall, impulse interactions) without replacing your layout system.

<a href="https://box2d.org/">
  <img src="https://box2d.org/images/logo.svg" alt="Box2D logo" width="240" />
</a>

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

## Box2D Attribution

PhysicsScene uses Box2D as the underlying physics engine via `com.badlogicgames.gdx:gdx-box2d`.

Box2D is created by Erin Catto and is licensed under MIT. Per the official FAQ, credit is appreciated and logo use is allowed.

- Box2D website: [box2d.org](https://box2d.org/)
- Box2D FAQ: [What is Box2D?](https://box2d.org/documentation/md_faq.html)
- Box2D license: [MIT](https://github.com/erincatto/box2d/blob/main/LICENSE)

## Versioning

PhysicsScene follows SemVer.

- Current baseline: `0.1.0`
- Breaking API changes are possible before `1.0.0`

## License

PhysicsScene source code is licensed under Apache License 2.0. See [LICENSE](LICENSE).

Third-party dependencies, including Box2D, are licensed under their own terms.
