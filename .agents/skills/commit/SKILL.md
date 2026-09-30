---
name: commit
description: Always use this skill before authoring a commit message in the LVM-KMP repository
---

# Authoring Commit Messages

LVM-KMP follows [Conventional Commits](https://www.conventionalcommits.org/). Pull request titles use the same format; see the [`pr`](../pr/SKILL.md) skill.

## Format

```text
<type>(<scope>): <subject>

<body>

<footer>
```

Only the header is required.

**Type**: one of `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`. Use `feat` and `fix` only for changes that users of the published plugins and libraries notice. Changes to how LVM itself is built, such as the Gradle wrapper, `blueprint/buildSrc` or test wiring, are `build`.

**Scope**: optional. The area touched: `blueprint` for the plugins, or a library name with the `lvm-` prefix dropped (`core`, `logger`, `decompose-compose`). Omit it when the change spans areas or the type says enough (`build: update Gradle to 9.8.0`).

**Subject**: imperative present tense ("add", not "added" or "adds"), lowercase first letter, no trailing period, short.

**Body**: optional. Add one when the "what" or the "why" is not obvious from the subject. State the motivation and contrast it with the previous behavior.

**Footer**: reference closed issues (`Closes #123`). For a breaking change, add `!` after the type or scope (`feat(blueprint)!: ...`) and end with:

```text
BREAKING CHANGE: <what breaks and how to migrate>
```

Do not add `Co-authored-by`, tool attribution or any other trailers.

## Characters

Plain ASCII only, per the [`style`](../style/SKILL.md) skill: `-` instead of an em dash, `->` instead of an arrow, `...` instead of an ellipsis character.

## Be succinct

Readers already know Kotlin and Gradle. State what changed and why; skip restated context.
