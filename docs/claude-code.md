# The Claude Code plugin

`lvm` is a [Claude Code plugin](https://code.claude.com/docs/en/plugins) for Gradle builds that use LVM's plugins. It teaches Claude how such a build is set up and how to work on it with the [`lvm` command](tui.md): Claude reads the build's modules and values of `lvm { }` with `lvm report` instead of guessing, checks changes with `lvm check`, moves the build to a new LVM release with a dry run first, and creates builds and modules the way LVM sets them up.

## Install

The plugin needs the `lvm` command on your `PATH`; [Install](tui.md#install) describes how to get it. Then add this repository as a plugin marketplace in Claude Code and install the plugin from it:

```text
/plugin marketplace add Lavmee/LVM-KMP
/plugin install lvm@lvm-kmp
```

The plugin is available from LVM 0.5.0. Each LVM release comes with the plugin of the same version, so the plugin describes the `lvm` command and the plugins of a release, not the changes on `main` that are still unreleased. Claude Code updates plugins only from its own marketplace by default. Turn on auto-update for `lvm-kmp` in `/plugin`, under Marketplaces, or update the plugin after a release:

```bash
claude plugin marketplace update lvm-kmp
claude plugin update lvm@lvm-kmp
```

## Skills

Claude uses the plugin's skills when a request needs them. Each one also runs as a command:

| Command | What Claude does |
|---|---|
| `/lvm:conventions` | Explains how a build with LVM works: where each value of `lvm { }` comes from, the plugins and their declarations in the root project, the `lvmLibs` catalog, the flat source layout and where secrets belong. Claude uses it whenever it works in such a build |
| `/lvm:check [directory]` | Runs `lvm check`, reports the errors and warnings with their files, and applies the fixes you agree to |
| `/lvm:upgrade [version]` | Reads the release notes up to the version, by default the latest release, runs `lvm upgrade --dry-run`, and applies the upgrade once you agree, then checks and builds the build |
| `/lvm:new [directory or module]` | Creates a build with `lvm new` after agreeing on its options with you, or adds a module set up like the build's other modules |

The skills let Claude run `lvm --version`, `lvm report`, `lvm check --json` and `lvm upgrade --dry-run` without asking, since they only read the build. Every command that writes files, such as `lvm check --fix` or `lvm upgrade`, still asks for your permission, and Claude asks before it makes such a change.

## Develop the plugin

The marketplace is [`.claude-plugin/marketplace.json`](../.claude-plugin/marketplace.json), and the plugin is in [`claude-plugin/`](../claude-plugin). The marketplace installs the plugin from `claude-plugin/` at the tag of the release that its `ref` names. To try a change to the skills, start Claude Code in a build with LVM and load the plugin from your checkout:

```bash
claude --plugin-dir path/to/LVM-KMP/claude-plugin
```

`claude plugin validate --strict .` and `claude plugin validate --strict claude-plugin` check the marketplace and the plugin, as the [Claude Code plugin workflow](../.github/workflows/plugin.yml) does for every change to them. [Releasing](../RELEASING.md) describes how the plugin's version and the marketplace's `ref` change with a release.
