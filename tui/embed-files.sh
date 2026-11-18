#!/bin/sh
# Copies the files from outside tui/ that the crate embeds, and LVM's license, into tui/ for its package on crates.io,
# which holds only tui/. build.rs lists the same files; inside the repository it reads the originals.
set -eu
cd "$(dirname "$0")"
mkdir -p embedded
cp ../gradle.properties ../settings.gradle.kts embedded/
cp ../gradle/wrapper/gradle-wrapper.properties ../gradle/wrapper/gradle-wrapper.jar embedded/
cp ../gradlew ../gradlew.bat embedded/
cp ../blueprint/common/lvm-keys.json embedded/
cp ../LICENSE LICENSE
