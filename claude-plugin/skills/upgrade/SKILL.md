---
name: upgrade
description: Moves a Gradle build that uses LVM's plugins to another LVM release with lvm upgrade - the version of the settings plugin, catalogVersion, lvm-bom, the coordinates of LVM's artifacts and LVM's plugins in build logic and in the build's own version catalog - after reading the release notes and comparing the Gradle, Kotlin and AGP minimums. Use when the user asks to upgrade or update LVM, or to move an LVM build to a given release.
argument-hint: "[version]"
allowed-tools:
  - Bash(lvm --version)
  - Bash(lvm report *)
  - Bash(lvm upgrade --to * --dry-run)
  - Bash(lvm check --json *)
  - Bash(gh release list *)
  - Bash(gh release view *)
  - Bash(gh pr view *)
---

# Upgrade LVM

Arguments: `$ARGUMENTS`. They name the LVM version to move to, such as `0.5.0`; with none, move to the latest release. Run every command below in a directory of the build.

## 1. Where the build is

Run `lvm --version`: it must print `lvm` and a version. If the command is missing, or is LVM2's `lvm` on Linux, follow "Read the build with lvm" in the `conventions` skill and stop.

Run `lvm report` and read `lvm`:

* `pinned` with `version`: the build's current release. Go on.
* `fromSource`: the build takes LVM's plugins from an included build, so there is no release to move. Say so and stop.
* `unpinned`: the settings script applies `tech.annexflow.lvm.settings` without a version that lvm can see, for example from a variable. Find the version in the build files and ask the user where it is set before going on; `lvm upgrade` can't change it.
* `none`: the build doesn't use LVM's settings plugin. Stop.

Note `toolchain`: the build's Gradle, Kotlin and AGP versions. `actual` is `null` when the files don't name the version, usually because the root build script takes Kotlin and AGP from `lvmLibs`: then they are the versions that the build's LVM release was tested with, and they move with LVM.

## 2. Pick the release

LVM's releases are the tags `v<version>` of https://github.com/Lavmee/LVM-KMP. Without a version in the arguments, take the latest release from `gh release list --repo Lavmee/LVM-KMP --exclude-pre-releases --limit 1`, or from https://repo1.maven.org/maven2/tech/annexflow/lvm/lvm-versions/maven-metadata.xml when `gh` is missing. Always pass the version to `lvm upgrade` with `--to`: lvm 0.4.0 and older keep the list of releases for a day, so right after a release they may not know the latest one yet, and may say that the version is not on Maven Central when it is.

If the target is older than the current release, confirm with the user that they want to move back.

## 3. Read what changes

Read the notes of every release after the current one, up to the target, with `gh release view v<version> --repo Lavmee/LVM-KMP`, or on https://github.com/Lavmee/LVM-KMP/releases. Older releases have a tag but no GitHub Release; for them, read the changes between the tags at `https://github.com/Lavmee/LVM-KMP/compare/v<current>...v<target>`. Collect breaking changes, marked with `!`, renamed or removed keys, changes the build needs to make, and new minimums of Gradle, Kotlin and AGP. When a note doesn't say how to migrate, read its pull request with `gh pr view <number> --repo Lavmee/LVM-KMP`.

The minimums of a release are in `blueprint/common/lvm-keys.json` at its tag, under `minimums`: `https://raw.githubusercontent.com/Lavmee/LVM-KMP/v<version>/blueprint/common/lvm-keys.json`. When `lvm --version` is the target release, `minimum` in the `toolchain` of `lvm report` already has them.

If `lvm --version` is older than the target, suggest updating lvm first, with `cargo install lvm-tui --version <version>` or the archive of the release: its checks know the keys and minimums of its own release.

## 4. Dry run

Run `lvm upgrade --to <version> --dry-run`. Nothing is written. It prints:

* one row for each library and tool that LVM is tested with, such as Kotlin, AGP or Decompose: its version at the current release, `->`, its version at the target release, and after `build:` the version that the build sets itself, or `-` when the build takes it from `lvmLibs`. The same version on both sides means it doesn't change;
* notes, such as whether Kotlin, AGP and Compose move with LVM;
* the diff that the upgrade writes. It changes `catalogVersion` only where the settings script sets it; otherwise the catalog follows the settings plugin's version.

Tell the user, briefly:

* which files change, and how;
* what the release notes ask of the build;
* whether Gradle, Kotlin or AGP is below the target's minimums. A tool that the build takes from `lvmLibs` moves to the version of the target column, which meets them. A version that the build sets itself, shown after `build:`, is raised in the build's own catalog or script, and Gradle with `./gradlew wrapper --gradle-version <version>`.

Ask whether to apply it.

## 5. Apply and verify

Only after the user agrees, run `lvm upgrade --to <version>`. Then make the other changes that the user agreed to: tool versions, and what the release notes ask for.

Check the build with `lvm check --json` and handle its findings as the `check` skill describes. Then offer to run `./gradlew lvmReport build`, or a narrower task, and fix what fails. A failure in LVM's plugins usually names the key or the version to change.

End with a short summary: the old and the new release, the files that changed and what is left for the user, such as iOS builds that need macOS.
