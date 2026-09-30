---
name: prose
description: Always use this skill before writing long-form Markdown documentation for LVM-KMP
---

# Prose

## Placement

* `README.md` is the entry point: what LVM contains, getting started, configuration and supported versions. Link to longer guides from it instead of growing it.
* `RELEASING.md` covers publishing.
* Longer guides go in `docs/`, one topic per file, linked from `README.md`.
* Instructions for agents live in `AGENTS.md` and `.agents/skills`.

## Structure

Start a guide with a short summary of what it covers, linking to external resources where they help (for example the Kotlin or Gradle documentation). Introduce basic usage first, then advanced topics.

When another document already explains a topic in detail, mention it briefly and link to it.

## General

* Write in plain English with simple sentences, for a reader who has never seen the repository.
* Describe the current state only.
* Do not list things that change over time and go stale.
* Use only ASCII characters: `->` instead of an arrow, `...` instead of an ellipsis character.
* Avoid em dashes entirely. Use colons and semicolons sparingly.
* Keep each paragraph on a single line.
* Tag code blocks with their language (`kotlin`, `properties`, `bash`).
