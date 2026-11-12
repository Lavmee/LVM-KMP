# Agent instructions

LVM-KMP holds Gradle convention plugins and Kotlin Multiplatform libraries for Android, iOS and JVM. Read [`README.md`](README.md) for what the plugins configure and how projects apply them, and [`RELEASING.md`](RELEASING.md) before changing versions or publishing.

- `blueprint/` is an included build with the plugins: `settings`, `common`, `multiplatform`, `quality`, `main-context` and `compose`. Its `buildSrc` holds build logic for blueprint itself. The root build applies the plugins from these sources.
- `runtime/` holds the libraries, one module per artifact: `runtime/core` is `lvm-core`, with the Gradle path `:runtime:core`. Folder names drop the `lvm-` prefix, as in `blueprint/`. Folders are flat with hyphens (`runtime/logger-console`), never nested: the klib unique name is `<group>:<project name>`, so a project name must not repeat in the build. The klib of common code is named `<archivesName>_commonMain`, without the group, so a module whose project name is also the name of a library it uses, as `runtime/decompose` is, sets `base { archivesName = "lvm-<name>" }`. Modules use the flat source layout: `src/` and `test/` for common code and its tests, `src@<name>/` and `test@<name>/` for the rest, without the root package in the path.
- `compiler/` holds Kotlin compiler plugins, one Kotlin/JVM module per artifact: `compiler/main-context-compiler` is `lvm-main-context-compiler`, with the Gradle path `:compiler:main-context-compiler`. A compiler plugin compiles against `kotlin-compiler-embeddable` of the Kotlin version in `gradle/libs.versions.toml` and works only with that version. Its build script sets up the flat layout: `src/`, `test/` and `resources/`.
- `samples/` holds modules that apply the plugins the way a consumer would. CI builds them for every target.
- A module of this build that applies `tech.annexflow.lvm.main-context` substitutes `lvm-main-context` and `lvm-main-context-compiler` with their projects, as `samples/main-context-sample/build.gradle.kts` does: the plugin adds the published artifacts.
- `gradle/libs.versions.toml` is published as the `lvm-versions` catalog, so it lists only what consumers need.
- The repository is public. Write in English, and never commit `local.properties`, keys or other secrets.

Use the skills in [`.agents/skills`](.agents/skills):

- `check` before committing or opening a pull request.
- `commit` before writing a commit message.
- `pr` before opening a pull request.
- `style` before writing or editing Kotlin or Gradle code.
- `prose` before writing documentation.

Update this file and the skills when a change affects what they say.
