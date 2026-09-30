# Agent instructions

LVM-KMP holds Gradle convention plugins and Kotlin Multiplatform libraries for Android, iOS and JVM. Read [`README.md`](README.md) for what the plugins configure and how projects apply them, and [`RELEASING.md`](RELEASING.md) before changing versions or publishing.

- `blueprint/` is an included build with the plugins: `settings`, `common`, `multiplatform` and `quality`. Its `buildSrc` holds build logic for blueprint itself. The root build applies the plugins from these sources.
- `samples/` holds modules that apply the plugins the way a consumer would. CI builds them for every target.
- `gradle/libs.versions.toml` is published as the `lvm-versions` catalog, so it lists only what consumers need.
- The repository is public. Write in English, and never commit `local.properties`, keys or other secrets.

Use the skills in [`.agents/skills`](.agents/skills):

- `check` before committing or opening a pull request.
- `commit` before writing a commit message.
- `pr` before opening a pull request.
- `style` before writing or editing Kotlin or Gradle code.
- `prose` before writing documentation.

Update this file and the skills when a change affects what they say.
