# GitForge — repository notes for agents

GitForge is a native Android app (Kotlin, Jetpack Compose, Material 3, MVVM) for
editing GitHub repositories on a phone, with a real in-app shell, Git and Python.

## Build environment

The toolchain is **not** on `PATH` by default in this container. Set it explicitly:

```bash
export JAVA_HOME=/workspace/tools/jdk-17.0.20.1+1
export ANDROID_HOME=/workspace/android-sdk
export PATH=$JAVA_HOME/bin:$PATH
```

`gradlew --no-daemon` is reliable here. A full `testDebugUnitTest` run takes about two
minutes; the first build in a session is much slower than later incremental ones.

Signing uses environment variables, never a committed file:

```bash
export GITFORGE_KEYSTORE_PATH=/workspace/gitforge-release/gitforge-release.jks
export GITFORGE_KEYSTORE_PASSWORD=...
export GITFORGE_KEY_ALIAS=gitforge
export GITFORGE_KEY_PASSWORD=...
./gradlew --no-daemon :app:assembleRelease :app:bundleRelease
```

Outputs: `app/build/outputs/apk/{debug,release}/`, `app/build/outputs/bundle/release/`.

## Verifying an APK without a device

`aapt2 dump badging` gives package, versionCode, versionName, label, min/target SDK,
ABIs and the launchable activity; `apksigner verify --print-certs` gives the signing
certificate. Both are in `$ANDROID_HOME/build-tools/34.0.0/`. There is no emulator or
device in this environment, so an install is never verified — do not claim otherwise.

## Token scopes (this matters)

- `GITHUB_TOKEN` is a **GitHub App integration token** with `repo` only. It cannot
  create pull requests (403 "Resource not accessible by integration") and cannot push
  under `.github/workflows/`.
- The user-supplied PAT authenticates as `rajyv13` with `repo, write:packages`. Use it
  for pull requests and releases. It **lacks `workflow` scope**, so a *classic* token
  push that adds or edits a file under `.github/workflows/` is rejected:

  ```text
  refusing to allow a Personal Access Token to create or update workflow
  `.github/workflows/build-apk.yml` without `workflow` scope
  ```

  and the equivalent `PUT .../contents/.github/workflows/...` call returns 404.

- Workaround that worked: the workflow file was added through the GitHub **web UI** (no
  scope needed) and merged, so `.github/workflows/build-apk.yml` is now on `main`.
  **Do not re-push a branch whose history rewrites that file** without a `workflow`-scoped
  credential; editing its content requires the scope even though the file already exists.

Create PRs and releases through the REST API with that PAT; the `create_pr` tool's
MCP token has no access to this repository.

## Android SDK in CI

`android-actions/setup-android@v3` defaults to `packages: tools platform-tools`. The
legacy `tools` package was removed from Google's SDK repository, so that default makes
`sdkmanager` exit 1 and the job dies before the build:

```text
Warning: Failed to find package 'tools'
Error: The process '.../sdkmanager' failed with exit code 1
```

Always pass explicit packages:

```yaml
packages: 'platform-tools platforms;android-34 build-tools;34.0.0'
```

This was verified: `sdkmanager --list | grep -c '^  tools '` returns 0 on a current SDK,
and a local `./gradlew --no-daemon assembleDebug assembleRelease` with those packages
produces `app-debug.apk` (22.7 MB) and `app-release-unsigned.apk` (15.5 MB).

## Signing-key history (do not repeat this mistake)

The `V1.0.0` release was signed with a keystore that no longer exists — it only lived
in a container that has since been reset. `V1.1.0` is therefore signed with a **new**
key (`/workspace/gitforge-release/gitforge-release.jks`, SHA-256
`5b289f0c27fb56e0fb752eb60ec7f1738bee13ef75ae3e9b5697a01882fb4e8f`), and Android
refuses to install it over V1.0.0.

Consequences to preserve in any future work:

- The debug key (`a04abf7f…`) and release key differ, so a debug APK can never update
  a release install. The in-app updater enforces this by channel and says why.
- From V1.1.0 onward the certificate is stable; keep using **this** keystore.
- Do not regenerate a keystore "to be safe" — it silently breaks every existing
  install. Back it up outside the container.

## GitHub sign-in: do not reintroduce these

Sign-in is the OAuth **device flow** (RFC 8628), opened in the **browser**. Two
approaches were tried and removed; do not bring them back:

- **A floating overlay window** to show the code over the browser. It needed
  `SYSTEM_ALERT_WINDOW`, which Play restricts for untrusted apps and users refuse.
  Removed in 1.1.1. The code is copied to the clipboard and the verification page is
  opened with `ACTION_VIEW` instead.
- **A user-editable client ID in Settings.** It was persisted but could not change which
  OAuth app GitHub had registered, so it only invited users to paste a value with no
  effect. The client ID is now a build-time constant:
  `BuildConfig.DEFAULT_OAUTH_CLIENT_ID`, set with `-PGITFORGE_OAUTH_CLIENT_ID=...` or the
  default in `app/build.gradle.kts`.

**Do not switch to GitHub's browser (web) OAuth flow.** It requires a `client_secret` at
the token exchange step — GitHub marks it required and has no public-client concept — so
using it would mean embedding a secret in the APK. The device flow is the only
secret-free option. This is a hard requirement, not a preference.

## Honesty rules for this repository

The README has an "Unverified work" section and the project has previously overstated
CI and version details. When editing documentation:

- Verify claims against the repository and the live GitHub API before writing them.
- State plainly what was not run — no device, no GitHub account, no CI.
- Report test counts from the JUnit XML in `app/build/test-results/`, not from memory.
