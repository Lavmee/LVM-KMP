# Continuous integration

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs on every push to `main` and on every pull request. Its `blueprint` job runs blueprint's tests, including the TestKit fixture builds. Its `build` job builds and tests every module on every target, which needs macOS and Xcode. Code from this repository runs both jobs on a self-hosted Mac. Code from forks runs `blueprint` on GitHub's Linux runners and `build` on GitHub's macOS runners. [`.github/workflows/tui.yml`](../.github/workflows/tui.yml) checks the `lvm` command in `tui/` on GitHub's Linux, macOS and Windows runners only, and `ci.yml` skips changes that touch only `tui/`. [`.github/workflows/plugin.yml`](../.github/workflows/plugin.yml) validates the Claude Code plugin in `claude-plugin/` on GitHub's Linux runners. This guide covers the self-hosted runner and what keeps it safe in a public repository. GitHub's [hardening guide for self-hosted runners](https://docs.github.com/en/actions/reference/security/secure-use#hardening-for-self-hosted-runners) explains the risks.

## Which code runs on the runner

Both jobs run on the self-hosted runner for pushes to `main` and for pull requests from branches of this repository. Only people with write access can push those branches. A pull request from a fork runs the jobs on GitHub's runners. If any other event reaches the self-hosted runner, the first step of the job fails before the code is checked out.

A pull request runs the workflow files of its own branch, so a fork could edit `ci.yml` to reach the runner. The repository setting "Require approval for all external contributors" prevents that: with it, a fork's workflows run only after a maintainer approves them. Before you approve a run, check that the pull request doesn't change `.github/workflows`. GitHub describes the setting in [Controlling changes from forks to workflows in public repositories](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository#controlling-changes-from-forks-to-workflows-in-public-repositories).

## What a job can do there

- A job gets no secrets, and its `GITHUB_TOKEN` can only read the repository. `actions/checkout` doesn't keep the token in the checkout.
- Actions from outside GitHub are pinned to a commit SHA, so moving a tag can't change the code that runs on the runner.
- Gradle, Kotlin/Native and the blueprint fixtures keep their state in the runner's tool cache, through `GRADLE_USER_HOME`, `KONAN_DATA_DIR` and `LVM_BLUEPRINT_FIXTURE_DIR`. The build reads neither `~/.gradle` nor `~/.konan` of the account the runner runs as. The state stays between jobs, so later builds are fast.
- The last step stops the Gradle and Kotlin daemons of the job, also after a failure or a cancelled run. The next job starts without them.

The release workflow never runs on the self-hosted runner, because it uses the Maven Central and signing secrets. [Releasing](../RELEASING.md) describes it.

## Setting up the runner

1. Create a standard macOS user for the runner. It holds none of the owner's SSH keys, GitHub tokens or `~/.gradle/gradle.properties` secrets.
2. As that user, install Xcode and the Android SDK. The jobs find the SDK in `~/Library/Android/sdk`, where Android Studio installs it. For another location, set `ANDROID_HOME` in the `.env` file of the runner directory.
3. Register the runner for this repository, as [Adding self-hosted runners](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners) describes. It needs the labels `self-hosted`, `macOS` and `ARM64`, which a runner on an Apple silicon Mac gets by default.
4. Install the runner as a service of that user, as [Configuring the self-hosted runner application as a service](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/configure-the-application) describes:
   ```bash
   ./svc.sh install
   ./svc.sh start
   ```
5. In the repository settings, under Actions and General, choose "Require approval for all external contributors".

A runner runs one job at a time, so the two jobs of a run go one after the other. While the runner is busy or offline, jobs that need it wait in the queue.
