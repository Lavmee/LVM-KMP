# Releasing

Every artifact, runtime libraries and blueprint plugins alike, is published to Maven Central under `tech.annexflow.lvm` with one shared version.

## One-time setup

| | |
|---|---|
| Namespace | `tech.annexflow` is verified on the Central Portal. `tech.annexflow.lvm` is covered automatically |
| Publishing plugin | [`vanniktech/gradle-maven-publish-plugin`](https://github.com/vanniktech/gradle-maven-publish-plugin) |
| Signing | GPG key uploaded to [keys.openpgp.org](https://keys.openpgp.org) |
| License | Apache 2.0, declared in the POM of every artifact |

### GitHub secrets

| Secret | Passed to Gradle as |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | `ORG_GRADLE_PROJECT_mavenCentralUsername` |
| `MAVEN_CENTRAL_PASSWORD` | `ORG_GRADLE_PROJECT_mavenCentralPassword` |
| `SIGNING_KEY` | `ORG_GRADLE_PROJECT_signingInMemoryKey` |
| `SIGNING_KEY_ID` | `ORG_GRADLE_PROJECT_signingInMemoryKeyId` |
| `SIGNING_KEY_PASSWORD` | `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` |

The username and password are a Central Portal user token, not your account credentials.

## Release steps

1. Set the new version in `gradle.properties` (`VERSION_NAME`). Blueprint reads the same value, so there is only one place to change.
2. Check the public API:
   ```bash
   ./gradlew checkKotlinAbi
   ```
   If the API change is intentional, run `./gradlew updateKotlinAbi` and commit the updated dumps.
3. Tag the release and push the tag:
   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```
4. The release workflow runs on the `v*` tag and publishes both builds. Blueprint is an included build, so the root publishing task doesn't reach it and needs its own invocation:
   ```bash
   ./gradlew publishAndReleaseToMavenCentral
   ./gradlew -p blueprint publishAndReleaseToMavenCentral
   ```

## CI runner

The release workflow runs on `macos-latest` because the iOS klibs are built there. Kotlin can cross-compile klibs for Apple targets on Linux, with limitations, and not when a module uses its own cinterop. Moving to Linux is possible later if no module needs cinterop.

## Plugin markers

`java-gradle-plugin` publishes a marker artifact for every plugin ID, so consumers can resolve plugins from Maven Central with `mavenCentral()` in `pluginManagement`. The default Android Studio project template already includes it.

## Gradle Plugin Portal

LVM is not published to the Gradle Plugin Portal. If that changes: the Portal has no way to reserve a prefix in advance. The first publication is reviewed manually, and for a domain-based ID the Portal asks for a DNS TXT record on `annexflow.tech`.
