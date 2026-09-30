---
name: pr
description: Always use this skill before opening a pull request in the LVM-KMP repository
---

# Opening Pull Requests

## Describe the diff, not the latest commit

A branch usually holds several commits: initial work, fixups, review responses. The title and body describe the net change landing on the base branch (usually `main`). Read the full diff first:

```bash
git diff <base>...HEAD
git log <base>..HEAD
```

## Title

Same Conventional Commits format as a commit message; see the [`commit`](../commit/SKILL.md) skill. If the branch holds one logical change, the title matches its commit.

## Body

No template. Keep it short:

- **Summary**: what the change does and why, drawn from the diff.
- **Testing**: how you verified it, meaning which checks you ran; see the [`check`](../check/SKILL.md) skill. Mention anything you could not run, such as iOS tests without Xcode.

Do not add tool attribution. Reviewers already know Kotlin and Gradle: include what they need to evaluate the change, and nothing else.
