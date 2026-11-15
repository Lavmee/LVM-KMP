# Releasing

Every artifact, runtime libraries and blueprint plugins alike, is published to Maven Central under `tech.annexflow.lvm` with one shared version.

## One-time setup

| | |
|---|---|
| Namespace | `tech.annexflow` is verified on the Central Portal. `tech.annexflow.lvm` is covered automatically |
| Publishing plugin | [`vanniktech/gradle-maven-publish-plugin`](https://github.com/vanniktech/gradle-maven-publish-plugin) |
| Signing | GPG key uploaded to [keys.openpgp.org](https://keys.openpgp.org) |
| License | Apache 2.0, declared in the POM of every artifact |
| POM | The `lvm.publish.*` values in `gradle.properties`: the artifact ID prefix `lvm-`, the description, URL, license and developer of every artifact of both builds |

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

1. Set the release version in `gradle.properties` (`VERSION_NAME`, without `-SNAPSHOT`) and in the status line of `README.md`. Blueprint reads the same `gradle.properties`, so there is only one version.
2. Check the public API:
   ```bash
   ./gradlew checkKotlinAbi
   ```
   If the API change is intentional, run `./gradlew updateKotlinAbi` and commit the updated dumps.
3. Commit, push `main` and wait for CI to pass on that commit: the release workflow doesn't run the tests.
4. Tag the commit and push the tag. This publishes to Maven Central, which can't be undone:
   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```
5. The release workflow, `.github/workflows/release.yml`, runs on the `v*` tag. It checks that the tag is `v` followed by `VERSION_NAME` and not a snapshot, checks the ABI dumps, and publishes and releases both builds, with the secrets above as Gradle properties:
   ```bash
   ./gradlew publishAndReleaseToMavenCentral
   ./gradlew -p blueprint publishAndReleaseToMavenCentral
   ```
   Blueprint is an included build, so the root publishing task doesn't reach it: it has its own job, which starts after the libraries are out, since the settings plugin imports `lvm-versions` of its own version. If the blueprint job fails, re-run that job alone: Maven Central refuses to take the libraries' version a second time.
6. Once the artifacts are on Maven Central, set `VERSION_NAME` to the next snapshot version, such as `0.2.0-SNAPSHOT`.

## What is published

- The libraries in `runtime/` and the compiler plugin apply `tech.annexflow.lvm.publish`, which publishes each as `lvm-<project name>` with the POM from `gradle.properties`, sources and javadoc jars, signed.
- The blueprint modules apply `lvm.blueprint-publish` from `blueprint/buildSrc`, which does the same with vanniktech's plugin as `lvm-blueprint-<name>`, plus a marker for every plugin ID. Blueprint can't apply its own publish plugin from its sources.
- `bom/` publishes `lvm-bom`, a Gradle platform with every library and the compiler plugin; `versions/` publishes `lvm-versions`, the catalog of `gradle/libs.versions.toml` plus LVM's libraries, BOM and blueprint plugins.
- The samples are not published.

To check a release locally, publish both builds into a throwaway repository instead of `~/.m2`. Versions ending in `-SNAPSHOT` are not signed; a release version needs `-Plvm.publish.signing=false` without a key.

```bash
./gradlew publishToMavenLocal -Dmaven.repo.local=/tmp/lvm-repository
./gradlew -p blueprint publishToMavenLocal -Dmaven.repo.local=/tmp/lvm-repository
```

## CI runner

The libraries job of the release workflow runs on `macos-latest` because the iOS klibs are built there; the blueprint job has only JVM code and runs on `ubuntu-latest`. Kotlin can cross-compile klibs for Apple targets on Linux, with limitations, and not when a module uses its own cinterop. Moving to Linux is possible later if no module needs cinterop.

## Plugin markers

`java-gradle-plugin` publishes a marker artifact for every plugin ID, so consumers can resolve plugins from Maven Central with `mavenCentral()` in `pluginManagement`. The default Android Studio project template already includes it.

## Gradle Plugin Portal

LVM is not published to the Gradle Plugin Portal. If that changes: the Portal has no way to reserve a prefix in advance. The first publication is reviewed manually, and for a domain-based ID the Portal asks for a DNS TXT record on `annexflow.tech`.
