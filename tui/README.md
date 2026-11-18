# lvm-tui

The crate of `lvm`, a terminal app for Gradle builds that use the convention plugins of [LVM-KMP](https://github.com/Lavmee/LVM-KMP). It shows a build's modules and the values of `lvm { }` with where each one comes from, checks the build and fixes what it finds, runs Gradle tasks and apps on devices, moves the build to a new LVM release, and creates builds and modules.

```bash
cargo install lvm-tui
lvm path/to/build
```

Each version of the crate comes with the LVM release of the same version, and binaries for macOS, Linux and Windows are in the [GitHub Releases](https://github.com/Lavmee/LVM-KMP/releases). The [guide](https://github.com/Lavmee/LVM-KMP/blob/main/docs/tui.md) covers the tabs, the keys and the commands for scripts and CI.

On Linux, `lvm` is also the command of LVM2, which manages disk volumes: the shell runs the one that comes first in your `PATH`.
