# Contributing

## Development workflow

1. Create a branch from `main`.
2. Implement your change.
3. Run:
   - `./gradlew :physics-scene:testDebugUnitTest`
   - `./gradlew :app-demo:compileDebugKotlin`
   - `./gradlew :physics-scene:lintDebug :app-demo:lintDebug`
4. Open a pull request.

## Release workflow

1. Update `CHANGELOG.md`.
2. Ensure `VERSION_NAME` in `gradle.properties` matches the target release.
3. Create and push tag: `vX.Y.Z`.
4. GitHub Actions release workflow publishes:
   - Maven Central artifact for `:physics-scene`
   - Signed demo APK asset in GitHub Release

## Repository policy

- Do not store planning documents or internal planning notes in repository files.
- Keep public API changes deliberate and documented.
