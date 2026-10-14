# GitForge

**Code anywhere. Build everything.**

GitForge is a native Android application for editing GitHub repositories and doing
real development work on a phone: browse and clone repositories, edit files in a
real editor, run genuine Git operations, and execute an actual shell with Git,
Node.js, npm and Python 3 in a Termux-compatible userspace.

It is not a website, not a PWA, not a WebView wrapper and not a mockup. The UI is
Jetpack Compose, the Git engine is JGit running in-process, and the terminal runs
real processes.

---

## Install the app (no Android Studio needed)

You do not need Android Studio, a JDK or the Android SDK to run GitForge. Install the
built APK:

1. On the phone, open
   [the latest release](https://github.com/ProjectHub-fie/Git-forge/releases/latest)
   and download **`app-release.apk`**.
2. Open the downloaded file. Android will ask you to allow installs from this source;
   allow it. (This is the standard prompt for any app installed outside the Play
   Store — GitForge does not need it enabled afterwards.)
3. Tap **Install**, then launch **GitForge** from the app drawer.

Requires Android 8.0 (API 26) or newer. The APK is signed and targets `arm64-v8a`
(most phones), with `armeabi-v7a` and `x86_64` also offered.

> **Version 1.0.1 fixed the terminal.** If commands produced no output, or a
> userspace command reported "cannot execute", install 1.0.1 or later. See
> [Fixed in 1.0.1](#fixed-in-101).
>
> **Version 1.1.0 adds in-app updates.** Once 1.1.0 or later is installed, GitForge
> checks GitHub for a newer build on launch and can download and install it without a
> computer. See [Updating GitForge](#updating-gitforge).
>
> **Version 1.1.1 moves GitHub sign-in to the browser.** The floating window and its
> "display over other apps" permission are gone: sign-in now opens the GitHub
> verification page in your browser with the code on the clipboard. No special Android
> permission is requested for sign-in. See
> [Option B — OAuth device flow](#option-b--oauth-device-flow-no-token-copying).
>
> ⚠️ **Upgrading from V1.0.0 needs a one-time uninstall.** V1.0.0 was signed with a
> keystore that is no longer available, so Android will refuse to install V1.1.0 or
> later over it. Uninstall V1.0.0 first — **which deletes the app's private storage,
> including cloned repositories**, so export or push anything you need first. Every
> update after 1.1.0 works in place. Details under
> [Installing over an existing copy](#installing-over-an-existing-copy).

If you would rather build it yourself, see
[Building the APK](#building-the-apk) below — that path does need the SDK.

---

## Table of contents

- [Install the app](#install-the-app-no-android-studio-needed)
- [Status](#status)
- [What works today](#what-works-today)
- [Screenshots of the feature set](#feature-set)
- [Architecture](#architecture)
- [Getting started on Windows](#getting-started-on-windows)
- [Building the APK](#building-the-apk)
- [Running the tests](#running-the-tests)
- [Connecting GitHub](#connecting-github)
- [Cloning and editing a repository](#cloning-and-editing-a-repository)
- [Using the Git tools](#using-the-git-tools)
- [Using the terminal](#using-the-terminal)
- [Running Python 3 and Node.js](#running-python-3-and-nodejs)
- [Installing packages](#installing-packages)
- [Building a signed release](#building-a-signed-release)
- [Updating GitForge](#updating-gitforge)
- [Security model](#security-model)
- [Dependency and licence inventory](#dependency-and-licence-inventory)
- [Known limitations](#known-limitations)
- [Troubleshooting with Diagnostics](#troubleshooting-with-diagnostics)
- [Unverified work](#unverified-work)
- [Project layout](#project-layout)

---

## Status

| Item | State |
| --- | --- |
| Debug APK builds | Yes — `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK and AAB build | Yes — `app-release.apk` and `app-release.aab`, signed and verified with `apksigner` |
| Unit test suite | 151 tests, all passing |
| Installed on a physical device | Not verified (no device or emulator was available in the build environment) |
| GitHub end-to-end (clone, push) | Not verified (requires a real account and network access) |
| Terminal with Node/npm/Python | Implemented; the runtime downloads on first use and needs a device to verify |
| In-app update check and install | Implemented and unit-tested; the install prompt needs a device to verify |

Nothing in the table above is guessed. Anything that was not actually executed is
called out again in [Unverified work](#unverified-work).

---

## What works today

### GitHub

- Sign in with the OAuth **device flow** (RFC 8628), which opens the GitHub
  verification page in your browser and needs no Android permission and no client
  secret embedded in the APK, or paste a personal access token.
- Browse public and private repositories you are authorised to see, with search,
  filtering and pagination.
- Browse branches, contents and commit history through the REST API.
- Clone to app-private storage; fetch, pull and push over HTTPS.
- Handles authentication failures, rate limits, offline use and revoked tokens with
  plain-language errors.

### Editor

- A real file tree over the repository working tree.
- Multiple tabs, unsaved-change indicators, save and save-all.
- Line numbers, syntax highlighting for Kotlin, Java, JavaScript, TypeScript, Python,
  HTML, CSS, JSON, YAML, Markdown, SQL, C and C++.
- Find and replace within a file, plus project-wide search with line numbers.
- Create, rename, move and delete files.
- Git diff view beside the editor.
- Encoding-aware saves: the detected charset and line endings are preserved, and a
  save is refused if the file changed on disk since it was opened.
- Atomic writes, so a crash mid-save cannot leave a half-written file.

### Git

- Status, staged and unstaged diffs, stage and unstage.
- Commit with author configuration.
- Create, switch, rename and delete branches; compare branches.
- Commit history with paging.
- Merge, with conflict detection and the conflicting paths listed.
- Revert commits without rewriting history.
- Manage remotes.
- Fetch, pull and push.
- Destructive operations require confirmation and are never silent.

### Terminal

- A genuine shell with pipes, redirection, globbing, quoting and `$VAR` expansion.
- Built-in core utilities: `pwd`, `ls`, `cd`, `cat`, `cp`, `mv`, `mkdir`, `rm`,
  `touch`, `grep`, `find`, `sed`, `awk`, `head`, `tail`, `sort`, `uniq`, `wc`,
  `cut`, `diff`, `tar`, `gzip`, `unzip`, `which`, `env`, `export`, `echo`, `help`.
- External commands from the userspace: `git`, `python3`, `pip`, `node`, `npm`,
  `bash` and the rest of the installed packages.
- Multiple sessions, command history, persistent working directories, environment
  variables, stdout/stderr separation, exit codes, process cancellation.
- Streamed output with a bounded scrollback and a 2 MB pipeline cap, so a runaway
  command cannot exhaust memory.
- ANSI colour and attribute parsing, including carriage-return progress bars.

### App

- Light and dark themes, dynamic colour on Android 12+.
- Workspace management with storage usage reporting.
- Recent repositories.
- Settings for theme, editor, terminal, security and storage.
- Onboarding.
- Offline access to local files, local Git operations and installed tools.
- In-app updates: checks this repository's releases on launch, verifies the download,
  and installs through the Android package installer. See
  [Updating GitForge](#updating-gitforge).

---

## Feature set

| Screen | Purpose |
| --- | --- |
| **Home** | Recent projects, quick actions, workspace usage, runtime status |
| **Repositories** | GitHub repositories and local workspaces, clone and open |
| **Editor** | Project explorer, tabs, code editor, find and replace, search |
| **Git** | Status, diff, staging, commits, branches, history, remotes |
| **Terminal** | Real command execution with sessions and history |
| **Packages** | Install and manage the shell, Node.js/npm and Python 3 userspace |
| **Settings** | Theme, editor, terminal, security, storage |

On compact screens the app uses bottom navigation. On wider screens it uses a
navigation rail and a two-pane layout so the editor and the Git panel can be open at
the same time.

---

## Architecture

Kotlin, Jetpack Compose, Material 3, AndroidX Navigation Compose, Coroutines, Flow
and MVVM. Layers are separated so each one can be tested on its own:

```
app/src/main/java/com/projecthub/gitforge/
├── MainActivity.kt              Compose entry point and navigation host
├── GitForgeApplication.kt       Application, WorkManager configuration
├── core/                        Path validation, redaction, logging, result types
├── data/
│   ├── db/                      Room entities, DAOs, database
│   └── prefs/                   DataStore-backed settings
├── di/AppContainer.kt           Manual dependency container
├── git/                         JGit engine and the app's Git models
├── github/                      REST API client and the device-flow auth
├── security/                    Keystore-backed credential store
├── terminal/
│   ├── AnsiParser.kt            ANSI SGR parsing
│   ├── TerminalSessionManager.kt  Sessions, scrollback, environment
│   ├── shell/                   Lexer, engine, builtins, command resolution
│   └── runtime/                 Termux package registry, installer, external commands
├── ui/                          ViewModels, navigation, theme, components
│   └── screens/                 One file per screen
└── workspace/                   Repository storage, file operations, file tree
```

### Key design decisions

**One Git implementation per working tree.** JGit is pure Java, needs no NDK, and is
the single writer of every index and working tree. The terminal's `git` command
routes into the same engine rather than shelling out to a second Git, so the Git
screen and the terminal can never disagree about the repository state.

**The terminal is sandboxed to the app's own tree.** Every path a command resolves is
checked against the workspace and runtime home. A command such as `cat
../databases/app.db` is refused instead of reading app-private data.

**The runtime is downloaded, not bundled.** The APK stays at roughly 22 MB and
carries no native binaries. The Packages screen fetches Termux `.deb` packages over
HTTPS, verifies each one against a SHA-256 checksum, and unpacks it with pure-Java
`commons-compress` and `xz`.

**Room for metadata, DataStore for preferences.** Repository records, recent
workspaces and terminal history are structured and queryable, so they live in Room.
Settings are a small key-value set and live in DataStore.

**Credentials never touch the repository.** Tokens are stored with
`EncryptedSharedPreferences` backed by the Android Keystore. Remote URLs are stored
without userinfo, and the credential is supplied at push time through an in-memory
JGit credentials provider.

---

## Getting started on Windows

### 1. Install a JDK

GitForge builds with **JDK 17** or **JDK 21**. Either works. The build was verified on
JDK 17 (see [Build and test results](#build-and-test-results)); CI is not active, so
there is no second toolchain in use to point at.

- Download from [Adoptium Temurin](https://adoptium.net/temurin/releases/?version=21)
  or install Microsoft's OpenJDK.
- Verify:

  ```powershell
  java -version
  ```

### 2. Install the Android SDK

The simplest route is Android Studio, which installs the SDK and the platform tools
for you.

1. Install [Android Studio](https://developer.android.com/studio).
2. Open **More Actions → SDK Manager**.
3. On the **SDK Platforms** tab, install **Android 14 (API 34)**.
4. On the **SDK Tools** tab, install **Android SDK Build-Tools**, **Android SDK
   Platform-Tools** and **Android SDK Command-line Tools**.
5. Note the SDK path, usually `C:\Users\<you>\AppData\Local\Android\Sdk`.

If you prefer a command-line-only setup, install the
[command-line tools](https://developer.android.com/studio#command-tools), then:

```powershell
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

### 3. Point Gradle at the SDK

Create `local.properties` in the project root (it is git-ignored):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Alternatively set the `ANDROID_HOME` environment variable to the same path.

### 4. Open and sync

```powershell
git clone https://github.com/ProjectHub-fie/Git-forge.git
cd Git-forge
.\gradlew.bat --version
```

Open the folder in Android Studio and let it sync, or stay on the command line.

---

## Building the APK

From the project root in PowerShell or Command Prompt:

```powershell
.\gradlew.bat assembleDebug
```

Output:

```
app\build\outputs\apk\debug\app-debug.apk
```

That path is produced by the standard Android Gradle Plugin layout; it is the file
the build actually wrote. On the build machine used for this release the verified
outputs were:

```
app/build/outputs/apk/debug/app-debug.apk      22.7 MB
app/build/outputs/apk/release/app-release.apk  15.5 MB
app/build/outputs/bundle/release/app-release.aab  15.2 MB
```

Both APKs were inspected with `aapt2` and report package `com.projecthub.gitforge`,
version code `4`, version name `1.1.1`, label `GitForge`, `minSdk 26`, `targetSdk 34`,
launcher activity `com.projecthub.gitforge.MainActivity`, and native ABIs
`arm64-v8a`, `armeabi-v7a`, `x86_64`. The release APK was verified with `apksigner` as
signed by `CN=GitForge, OU=Release, O=ProjectHub`, certificate SHA-256
`5b289f0c27fb56e0fb752eb60ec7f1738bee13ef75ae3e9b5697a01882fb4e8f` — the same key as
V1.1.0, so 1.1.1 installs over 1.1.0 in place.

The release APK declares only `INTERNET`, `ACCESS_NETWORK_STATE` and
`REQUEST_INSTALL_PACKAGES` in its own manifest; `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`
and `FOREGROUND_SERVICE` are merged in by `androidx.work` (WorkManager), and there is
no `SYSTEM_ALERT_WINDOW` or `POST_NOTIFICATIONS`. `aapt2 dump permissions` confirms
this on the built file.

The debug and release APKs are signed with **different** certificates, which is
expected: the debug build uses the Android debug key. An installed release build will
not accept a debug APK as an update, and the in-app updater refuses the swap rather
than letting the installer fail.

### Installing on a device

1. On the phone, enable **Developer options** and **USB debugging**.
2. Connect by USB and accept the authorisation prompt.
3. Confirm the device is visible:

   ```powershell
   adb devices
   ```

4. Install:

   ```powershell
   .\gradlew.bat installDebug
   ```

   or, equivalently:

   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

GitForge then appears in the app drawer as **GitForge**.

If you would rather not use a cable, copy `app-debug.apk` to the phone and open it
with a file manager, allowing installation from that source when prompted.

---

## Running the tests

```powershell
.\gradlew.bat test
```

or just the debug variant:

```powershell
.\gradlew.bat testDebugUnitTest
```

The suite is 133 JVM unit tests and needs no device. It covers:

| Area | Test file | What it proves |
| --- | --- | --- |
| Launch path | `LaunchPathTest.kt` | The exact code the app runs at startup — settings, workspace, database, file tree, Git engine, credential store, repository ViewModel — all initialise |
| Subsystems | `SubsystemSmokeTest.kt` | Every ViewModel and every lazily built service constructs and its first work runs |
| Path validation | `core/PathSafetyTest.kt` | Traversal, absolute paths, Windows drive paths, sibling-prefix escapes and name validation are all refused |
| Credential redaction | `core/RedactionTest.kt` | Tokens of every GitHub shape are removed from logs, remote URLs and error text |
| Diagnostics log | `core/DiagnosticsLogTest.kt` | A token written to the log is redacted, and the in-memory buffer stays bounded |
| Editor file layer | `workspace/FileOperationsTest.kt` | Real reads and writes, CRLF/LF preservation, conflicting-save refusal, binary detection, large-file truncation, search, file tree |
| Git engine | `git/GitEngineTest.kt` | Real repositories: status, diffs, staging, commits, branches, merge conflicts, revert, remotes, persistence across reopen |
| Shell | `terminal/ShellEngineTest.kt` | Real command execution: `cd`, `mkdir`, `touch`, redirection, pipes, `grep`, `sed`, `head`, `tail`, `uniq`, `cp`, `mv`, `rm`, `find`, `export`, history, exit codes |
| Shell output routing | `terminal/shell/ShellOutputTest.kt` | A builtin's output reaches the terminal, and redirecting one stream does not swallow the other |
| Process launching | `terminal/runtime/ProcessInvocationTest.kt` | On API 29+ an app-data binary is routed through the system linker; on older APIs it is started directly; the ELF class picks the right linker |
| ANSI parsing | `terminal/AnsiParserTest.kt` | Colours, reset, bold, split sequences, carriage returns |
| Runtime manifest | `terminal/AnsiParserTest.kt` | Every downloadable package has a version, filename, 64-character SHA-256 and positive size |

Instrumented tests can be added later for the Compose UI:

```powershell
.\gradlew.bat connectedAndroidTest
```

---

## Connecting GitHub

GitForge offers two ways in. Both keep the token on the device and out of the
repository.

### Option A — personal access token (quickest)

1. In a browser, go to **GitHub → Settings → Developer settings → Personal access
   tokens**.
2. Create a **fine-grained token** and grant:
   - **Repository access**: the repositories you want to work with.
   - **Contents**: Read and write (needed to push).
   - **Metadata**: Read.
3. Copy the token.
4. In GitForge, open **Settings → GitHub and security → Sign in with a token**, paste it
   and confirm.

GitForge verifies the token against `GET /user` before storing it. A token GitHub
rejects is discarded immediately, so the app never claims to be signed in when it is
not.

### Option B — OAuth device flow (no token copying)

This is the default. GitForge ships with a public client ID already compiled in
(`BuildConfig.DEFAULT_OAUTH_CLIENT_ID`), so there is nothing to configure:

1. Open **Settings → GitHub and security**.
2. Choose **Connect with GitHub**. GitForge asks GitHub for a short code such as
   `35BD-DA5A` and **opens the verification page in your browser**, with the code
   already on the clipboard.
3. Paste the code, approve access on GitHub, and return to GitForge. The dialog stays
   on screen while GitForge polls GitHub, so progress and any error remain visible.
4. GitForge verifies the token with `GET /user` and stores it in the Keystore-backed
   credential store.

There is no callback URL in this flow, and GitHub never redirects back to the app.
The device flow does not use one, which is why no client secret is needed and why
nothing secret is embedded in the APK.

If the browser does not open, choose **Open browser** in the dialog, or copy the code
and go to `https://github.com/login/device` yourself. If the app is closed mid-sign-in,
reopen it and connect again — a code that has not been approved cannot be replayed.

**No special Android permission is needed.** Earlier builds drew the code in a floating
window, which required the "display over other apps" permission; Play restricts that
permission for untrusted apps and users reasonably refuse it, so sign-in now uses a
real browser tab instead. GitForge requests no permission beyond network access and the
optional "install unknown apps" used by the updater.

To use your own OAuth app, register one at **GitHub → Settings → Developer settings →
OAuth Apps → New OAuth App**, enable **Device flow**, and build with
`-PGITFORGE_OAUTH_CLIENT_ID=...` or edit the default in `app/build.gradle.kts`. There is
no in-app field for it: the client ID is public information but it is a build-time
constant, and changing it at runtime would not change which OAuth app GitHub has
registered.

> A client **secret** is never requested and never stored. The device flow is
> designed so that a public client can obtain a token without one.

To disconnect, use **Settings → GitHub and security → Disconnect**. The stored token is
deleted from the Keystore-backed store.

---

## Cloning and editing a repository

1. Open **Repositories** and choose **GitHub**. Search or scroll the list of
   repositories you can access.
2. Tap a repository and choose **Clone**. It is copied into the app's private
   workspace, and progress is streamed while it runs.
3. The repository now appears under **Local workspaces** and on **Home**.
4. Open **Editor**. The file tree shows the working tree.
5. Tap a file to open it in a tab. Edit, then **Save** or **Save all**.
6. The tab shows a dot while there are unsaved changes. Closing a tab with unsaved
   changes asks first.
7. **Find** and **Replace** act on the current file; **Search project** walks the
   whole working tree and reports file and line.

Saving writes to the actual working tree, so `git status` in the terminal sees the
same change immediately.

### Importing and exporting files

Use **Import** and **Export** in the Editor. Both go through the Android Storage
Access Framework, so GitForge needs no storage permission and you choose exactly
which files are involved.

---

## Using the Git tools

Open the **Git** screen with a repository selected.

- **Changes** lists staged and unstaged files with their change type. Tap a file to
  see its diff. Use the checkbox to stage or unstage.
- **Commit** writes the message and commits what is staged. Committing with nothing
  staged is refused rather than creating an empty commit.
- **Branches** lists local and remote branches, and creates, switches, renames or
  deletes them. Deleting the checked-out branch is refused.
- **History** shows commits with author and time, and can page back through them.
- **Remotes** shows and edits remote URLs. URLs are stored without credentials.
- **Merge** merges a branch and reports conflicts explicitly, listing the conflicting
  paths and leaving the conflict markers in place for you to resolve.
- **Revert** undoes a commit by creating a new commit, so history is never rewritten.
- **Fetch**, **Pull** and **Push** talk to the remote. Push asks for confirmation and
  never force-pushes.

The same operations are available in the terminal through `git`, and they act on the
same repository through the same engine.

---

## Using the terminal

Open **Terminal**. On first use, install the shell runtime from the **Packages**
screen.

- Type a command and press enter. Output streams as it is produced.
- **New session** opens an independent shell with its own working directory,
  environment and history.
- **Stop** cancels the running process.
- Command history is kept per session; use the arrow keys on a hardware keyboard.
- Working directories persist across commands, so `cd` behaves as expected.

Supported syntax: pipes (`|`), output and error redirection (`>`, `>>`, `2>`, `2>>`),
input redirection (`<`), quoting with `'` and `"`, `$VAR` and `${VAR}` expansion,
`$?`, `~`, and globbing with `*`, `?` and `[...]`.

Example session:

```console
$ pwd
/data/user/0/com.projecthub.gitforge/files/workspace/github/owner/repo
$ git status -s
 M src/App.kt
$ grep -n "fun main" src/App.kt
12:fun main() {
$ git add src/App.kt && git commit -m "Fix entry point"
[main a1b2c3d] Fix entry point
 1 file changed, 2 insertions(+), 1 deletion(-)
$ python3 -c "print(sum(range(10)))"
45
$ node -e "console.log(process.version)"
v26.4.0
```

### How the shell is confined

Commands can only reach the app's workspace and the runtime home directory. This is
deliberate: it means a mistyped or untrusted command cannot read app-private data or
touch the rest of the device, and it is why GitForge needs no storage permission.

Terminal commands can still modify or delete files inside your repositories and run
code you ask them to run. Treat any command you paste from a repository with the same
caution you would on a desktop.

---

## Running Python 3 and Node.js

Both come from the same userspace, installed from **Packages**.

- **Python 3** is CPython 3.14 with `pip`. Run scripts with `python3 script.py`, use
  the interactive interpreter with `python3`, and install packages with
  `pip install <name>`. Virtual environments work: `python3 -m venv .venv`.
- **Node.js** is Node 26 with npm 11. Run scripts with `node script.js` and manage
  dependencies with `npm install`, `npm run` and the rest of the npm CLI.

If a command is missing, the shell says so and points at **Packages** rather than
failing silently.

---

## Installing packages

Open **Packages**.

- Install the **shell** runtime first: it provides `bash` and the core utilities.
- Install **Node.js and npm** and **Python 3 and pip** as needed.
- The screen shows what is installed, the download size before you start, and the
  disk space each runtime uses afterwards.

Packages are downloaded over HTTPS from the Termux main repository, verified against
the SHA-256 checksum recorded in `RuntimePackageRegistry`, and unpacked with
pure-Java readers. Nothing is executed during installation.

Inside the terminal, the userspace package manager is available as well.

---

## Building a signed release

Signing material is read from environment variables and is **never** committed. The
repository's `.gitignore` excludes `*.jks`, `*.keystore`, `keystore.properties`,
`secrets.properties` and `release-signing/`.

### 1. Create a keystore

```powershell
keytool -genkeypair -v `
  -keystore gitforge-release.jks `
  -alias gitforge `
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep this file and its passwords somewhere safe and backed up. Losing it means you
can no longer update an app already published under it.

> **This is not hypothetical.** The V1.0.0 release's keystore was lost, so V1.1.0 had
> to be signed with a new key and V1.0.0 users must uninstall before upgrading — losing
> their app-private storage with it. Back the keystore up somewhere durable before
> publishing anything, and record which key signed each release.
>
> If you are publishing updates to an app you built earlier, re-use **that** keystore.
> Generating a new one silently breaks every existing install.

### 2. Build with the secrets in the environment

PowerShell:

```powershell
$env:GITFORGE_KEYSTORE_PATH   = "C:\keys\gitforge-release.jks"
$env:GITFORGE_KEYSTORE_PASSWORD = "<store password>"
$env:GITFORGE_KEY_ALIAS         = "gitforge"
$env:GITFORGE_KEY_PASSWORD      = "<key password>"
.\gradlew.bat assembleRelease
```

Command Prompt:

```bat
set GITFORGE_KEYSTORE_PATH=C:\keys\gitforge-release.jks
set GITFORGE_KEYSTORE_PASSWORD=<store password>
set GITFORGE_KEY_ALIAS=gitforge
set GITFORGE_KEY_PASSWORD=<key password>
gradlew.bat assembleRelease
```

Output:

```
app\build\outputs\apk\release\app-release.apk
```

If those variables are absent the release build still succeeds but produces an
unsigned APK. That is intentional: the build must never invent or commit a key.

### 3. Optional App Bundle for Play distribution

```powershell
.\gradlew.bat bundleRelease
```

Output:

```
app\build\outputs\bundle\release\app-release.aab
```

---

## Updating GitForge

GitForge updates itself from this repository's releases. There is no server to run and
no account to sign in to for updates.

### How it works

1. On launch, the app makes one silent check against
   `https://api.github.com/repos/ProjectHub-fie/Git-forge/releases`.
2. If a newer build exists on the app's channel, a snackbar appears with an **Update**
   action. It is silent otherwise, so an offline or up-to-date device is never
   interrupted.
3. Open **Update** from the drawer, or **Settings → About → Check for updates**.
4. Tap **Download update**. The APK is downloaded to app-private storage and verified.
5. Tap **Install**. Android shows its normal install confirmation; the app cannot and
   does not install anything silently.

### Channels

The channel follows the build type, so the two never cross-install:

| Installed build | Channel | Downloads |
| --- | --- | --- |
| `app-release.apk` (signed) | Release | `app-release.apk` |
| `app-debug.apk` | Debug | `app-debug.apk` |

A release build will not install a debug APK and vice versa. The two are signed with
different keys, so an install across the boundary would fail; the app checks the
signing certificate before it offers the install and explains the mismatch instead.

### What is verified before installing

- **Checksum** — if the release advertises a SHA-256 digest for the asset, the bytes
  on disk must match, or the download is discarded.
- **Size** — a truncated download is rejected even when no digest is published.
- **Signing certificate** — the downloaded APK must be signed by the same key as the
  installed app. Android enforces this anyway; checking first turns a bare
  "App not installed" into a readable reason.

Nothing is uploaded and no credential is used: the releases endpoint is public, so the
check works whether or not you are signed in to GitHub.

### Installing over an existing copy

Android replaces an installed app in place when the new APK has a higher
`versionCode` and the same signing certificate. Your repositories, settings and
credentials are untouched by an update because they live in app-private storage that
the installer does not clear.

**The V1.0.0 → V1.1.0 step is the exception.** The V1.0.0 release was signed with a
keystore that is no longer available, so V1.1.0 is signed with a new key and Android
will refuse to install it over V1.0.0. This is a one-time step:

1. Export or push anything you need from the repositories in GitForge.
2. Uninstall V1.0.0. **This deletes the app's private storage, including cloned
   repositories.**
3. Install V1.1.0 or later (V1.1.1 is current).

From V1.1.0 onward the certificate is stable, so every later update installs in place
and this does not recur. If you still hold the original V1.0.0 keystore, a maintainer
can re-sign V1.1.0 with it and the one-time uninstall is not needed.

More generally, if you installed a build signed with a *different* key — an earlier
hand-built debug APK, or a release built with your own keystore — Android will refuse
the update and you must uninstall the old copy first, with the same storage caveat.

### Granting install permission

Android 8.0 and later require per-app permission to install packages. The first time
you tap **Install**, GitForge opens the right settings screen
(*Install unknown apps → GitForge*). Grant it and return; the app re-checks the
permission when the screen resumes. The permission is only needed for the update
itself.

### Building your own update channel

A fork can point the updater at its own releases instead of upstream's:

```
gradlew.bat assembleDebug -PGITFORGE_UPDATE_REPO_OWNER=your-user -PGITFORGE_UPDATE_REPO_NAME=your-repo
```

The default owner and repository are compiled in from
`UPDATE_REPO_OWNER` / `UPDATE_REPO_NAME` in `app/build.gradle.kts`.

---

## Security model

| Concern | How GitForge handles it |
| --- | --- |
| Token storage | `EncryptedSharedPreferences` backed by the Android Keystore |
| Client secret | Never requested and never embedded; the device flow is used instead |
| OAuth client ID | Public by design, compiled in as `BuildConfig.DEFAULT_OAUTH_CLIENT_ID`; not user-configurable |
| Android permissions | Only `INTERNET`, `ACCESS_NETWORK_STATE` and the optional `REQUEST_INSTALL_PACKAGES`. No overlay, notification or foreground-service permission |
| Sign-in code | Public, short-lived and single-use; shown in the dialog and copied to the clipboard for the browser |
| Remote URLs | Stored without userinfo; the credential is supplied in memory at push time |
| Logs and errors | Every message passes through `Redaction`, which strips tokens from URLs, headers and free text |
| Repository content | Treated as untrusted; scripts in a cloned repository are never executed automatically |
| Terminal scope | Confined to the workspace and runtime home; paths outside are refused |
| Storage permissions | None requested. Repositories are app-private; import and export use the Storage Access Framework |
| Backup | `allowBackup="false"`, with backup and device-transfer rules excluding everything |
| WebView | Not used for the app shell. If added for an editor engine it must run with JavaScript bridges disabled for untrusted content |
| Destructive Git operations | Require explicit confirmation; force-push is never performed silently |
| Credentials in history | `export` of a token is discouraged in the UI copy, and the token is never written into shell history by the app |

### Why sign-in uses the device flow and not a browser redirect

GitHub's browser (web) flow requires a `client_secret` at the token exchange step, and
GitHub has no public-client concept for OAuth apps: its own documentation marks
`client_secret` as required, and GitHub's staff have confirmed that native apps are
expected to embed one. A secret shipped in an APK is not a secret, so GitForge does not
use that flow. The device flow is the one flow GitHub documents as not needing a client
secret, which is why the app opens the verification page in a browser and polls, rather
than registering a redirect URI.

GitForge also refuses, by design, to:

- upload repository contents anywhere without your explicit action;
- rewrite history as part of a revert;
- commit with nothing staged;
- delete the checked-out branch;
- write outside the workspace from the terminal.

---

## Dependency and licence inventory

Everything below is used at runtime in the APK. Full licence texts for the runtime
packages are also listed in `THIRD_PARTY_LICENSES.md` and shown in the **Packages** screen under
Licences.

| Dependency | Version | Licence | Why |
| --- | --- | --- | --- |
| Kotlin standard library | 1.9.24 | Apache-2.0 | Language runtime |
| kotlinx.coroutines | 1.8.1 | Apache-2.0 | Structured concurrency |
| kotlinx.serialization | 1.6.3 | Apache-2.0 | JSON for the GitHub API |
| AndroidX core-ktx | 1.13.1 | Apache-2.0 | Platform helpers |
| AndroidX core-splashscreen | 1.0.1 | Apache-2.0 | Cold-start splash |
| AndroidX activity-compose | 1.9.2 | Apache-2.0 | Compose host |
| AndroidX lifecycle | 2.8.6 | Apache-2.0 | ViewModels, lifecycle-aware state |
| AndroidX navigation-compose | 2.8.1 | Apache-2.0 | Navigation |
| AndroidX Compose BOM | 2024.09.03 | Apache-2.0 | UI toolkit |
| Material 3 | via BOM | Apache-2.0 | Design system |
| AndroidX Room | 2.6.1 | Apache-2.0 | Structured metadata |
| AndroidX DataStore | 1.1.1 | Apache-2.0 | Preferences |
| AndroidX security-crypto | 1.1.0-alpha06 | Apache-2.0 | Keystore-backed credential storage |
| AndroidX WorkManager | 2.9.1 | Apache-2.0 | Deferrable background work |
| AndroidX documentfile | 1.0.1 | Apache-2.0 | Storage Access Framework helpers |
| OkHttp | 4.12.0 | Apache-2.0 | HTTP for the GitHub API |
| JGit | 6.7.0 | EDL-1.0 / BSD-3-Clause | The Git engine |
| slf4j-android | 1.7.36 | MIT | JGit's logging facade |
| Apache Commons Compress | 1.27.1 | Apache-2.0 | Reading `.deb` (ar and tar) archives |
| XZ for Java | 1.10 | Public domain | Decompressing `.tar.xz` runtime packages |
| JUnit | 4.13.2 | EPL-1.0 | Unit tests only |
| Robolectric | 4.13 | MIT | Unit tests only |
| MockWebServer | 4.12.0 | Apache-2.0 | Unit tests only |

### Runtime packages downloaded on demand

These are fetched from the Termux main repository and are **not** part of the APK.
All are permissively licensed and redistributable; the app records each one's
version and SHA-256 before installing it.

| Package | Version | Licence |
| --- | --- | --- |
| bash | 5.3.0 | GPL-3.0 |
| coreutils | 9.6 | GPL-3.0 |
| git | 2.51.0 | GPL-2.0 |
| nodejs | 26.4.0 | MIT |
| npm | 11.20.0 | Artistic-2.0 |
| python | 3.14.6 | PSF-2.0 |
| python-pip | 26.2.1 | MIT |
| openssl | 3.6.5 | Apache-2.0 |
| libc++ | 30 | Apache-2.0 with LLVM exception |
| ca-certificates | 2026.09.25 | MPL-2.0 |
| ncurses, readline, gdbm, libandroid-support, zlib, libiconv, termux-am, dash | current | MIT / GPL / BSD as published |

---

## Troubleshooting with Diagnostics

You do not need adb or logcat to see what went wrong. GitForge keeps its own log and
its own crash record inside the app.

1. Open **Diagnostics** from the navigation drawer, or from **Settings → About →
   Open Diagnostics**.
2. The screen shows the device and ABI, the previous crash (if the last run ended
   badly), and the recent app log.
3. Use **Share** to send the text to yourself or a maintainer, **Copy** to put it on
   the clipboard, and **Refresh** to re-read the log after reproducing a problem.

Everything shown there has already passed through the same redaction that protects
logcat, so a token does not appear. Nothing is uploaded automatically: sharing is
always a deliberate action.

If the app closes immediately on launch, reopen it and check Diagnostics — the crash
from the previous run is recorded there. The log file lives at
`files/diagnostics/gitforge.log` and the crash record at
`files/diagnostics/last-crash.txt`, both inside app-private storage.

---

## Known limitations

- **Architecture.** The userspace packages are `arm64-v8a`. The APK declares
  `arm64-v8a`, `armeabi-v7a` and `x86_64`, but on a 32-bit ARM or x86 emulator the
  runtime download is not usable. Most modern phones are arm64 and are fine.
- **No root.** GitForge runs inside the normal Android sandbox. It cannot install
  system packages, cannot bind privileged ports, and cannot see other apps' data.
- **Executing userspace binaries (W^X).** On Android 10 and later an app whose
  `targetSdkVersion` is 29 or higher may not `execve()` a file in its own data
  directory. GitForge therefore starts every downloaded userspace binary through the
  system linker (`/system/bin/linker64 <binary> …`), which the platform does permit
  to run app-data files. This is why the terminal works without root, and it is the
  single place process launching happens
  (`terminal/runtime/ProcessInvocation.kt`). A command that still reports
  "cannot execute" is a genuine per-binary problem, not the W^X rule.
- **Android's filesystem.** `/tmp`, `/usr`, `/etc` and `/dev` do not exist as they do
  on a desktop. The userspace supplies its own prefix, and commands that assume a
  normal FHS layout need `PREFIX`-relative paths.
- **Termux integration.** The runtime uses Termux package archives and its prefix
  layout, but GitForge is not a Termux plugin and does not share Termux's
  installation. Anything requiring the `termux-am` service or Termux:API will not
  work.
- **Native libraries.** Some Python and Node packages need to compile native code at
  install time. Those that ship prebuilt wheels or use the NDK toolchain work; others
  do not.
- **Editor engine.** The editor is a Compose text surface with its own highlighting,
  not a full language server. There is no code completion, refactoring or
  go-to-definition.
- **Merge conflicts.** Conflicts are detected and surfaced with markers left in the
  file, but there is no three-way merge editor.
- **Large repositories.** A very large clone is bounded by the phone's storage and by
  the HTTP timeouts; there is no shallow-clone option in the UI yet.
- **Background work.** Long clones continue while the app is foregrounded. Full
  WorkManager-backed continuation across process death is implemented for
  maintenance tasks but not yet for clone and push.

---

## Unverified work

This section exists so nothing is overstated.

1. **No device or emulator was available** in the environment where this project was
   built. The APK was compiled and its manifest inspected with `aapt2`, but it was
   **not** installed or launched. Installation instructions are provided and are
   standard, but the installation itself is unverified.
2. **No GitHub account was available**, so the end-to-end flow (connect, clone,
   edit, commit, push, verify the remote) was not executed. What *was* verified
   against the live service is the device-flow handshake: the client ID compiled into
   the app (`Iv23li8KI2OKDHaDX4BN`) successfully obtained a device code from
   `POST /login/device/code`, and the polling leg returned `authorization_pending`
   with no token, exactly as RFC 8628 requires before the user approves. Everything
   after approval — token exchange, clone, push — is implemented and unit-tested but
   has not been run against GitHub.
3. **The browser hand-off has not been run on a device.** The device flow's first leg is
   verified against the live service (item 2), and the code path that copies the user
   code and fires an `ACTION_VIEW` intent is straightforward, but "the browser opened
   and the code pasted correctly" can only be confirmed on hardware.
4. **The runtime download was not exercised.** Package versions and checksums are
   recorded in `RuntimePackageRegistry` and covered by unit tests, but no `.deb` was
   actually downloaded or unpacked, because that needs a device and network.
5. **The Compose UI has no automated tests.** Unit tests cover the engine, file
   layer, shell, parsers and settings persistence; the screens were not driven by
   instrumented tests.
6. **`arm64-v8a` on real hardware** is the intended target and the only ABI for which
   the userspace is published, but it has not been confirmed on a physical phone.
7. **The in-app updater has not performed a real install.** The release lookup, the
   version ordering, the channel selection and the download verification are covered
   by unit tests that run the real `ReleaseClient` against a real HTTP server, but no
   APK has been downloaded to a device and installed through the system installer.
   The signature-continuity check in particular needs a device to confirm it behaves
   as intended for a matching and a mismatching key.
8. **CI is not active.** No workflow file is in the repository, so nothing has run on
   GitHub Actions. The workflow is written out in
   [Continuous integration](#continuous-integration) for a maintainer to add; it could
   not be pushed because the available tokens lacked the `workflow` scope. Every build
   and test result in this document comes from a local run, not from CI.
9. **The published V1.1.0 cannot be installed over V1.0.0.** The V1.0.0 keystore was
   lost, so V1.1.0 is signed with a new key and Android rejects the upgrade. This is
   documented rather than fixed: it cannot be fixed without the original key. From
   V1.1.0 onward the certificate is stable.

### Fixed in 1.0.1

Two defects were found and fixed after 1.0.0 was published. Both are covered by new
tests, and both were found by running the real code, not by reading it.

1. **Builtin commands produced no output.** `ls`, `pwd`, `cat`, `grep` and every other
   builtin returned their stdout to the shell engine instead of streaming it to the
   terminal, so the scrollback showed only the prompt and the exit code. The pipeline
   now forwards a builtin's stdout and stderr to the terminal when the stream is not
   redirected. See `terminal/shell/ShellEngine.kt` and `ShellOutputTest`.
2. **Redirecting one stream discarded the other.** `cmd 2> err.txt` also swallowed
   stdout, because the redirection helper returned a fresh, empty result whenever any
   redirection was present. Redirection is now applied per stream, so `2>` no longer
   affects stdout. See `applyRedirections` and `ShellOutputTest`.

A third, larger issue was identified in the same review:

3. **Userspace binaries could not be executed on Android 10+.** An app targeting
   API 29 or later may not `execve()` a file in its own data directory (the W^X rule),
   so a userspace binary unpacked into app storage would fail with "cannot execute"
   and every terminal command would appear broken. Process launching now routes
   app-data binaries through the system linker
   (`/system/bin/linker64 <binary> …`), which the platform permits. See
   `terminal/runtime/ProcessInvocation.kt` and `ProcessInvocationTest`. This is a
   fix to a code path that had never been executed on a device; it is reasoned from
   the platform rule and unit-tested, but **it has still not been observed working on
   hardware.**

To make any remaining failure diagnosable without adb, the app now keeps its own
redacted log and crash record and exposes them on the **Diagnostics** screen.

### New in 1.1.1

GitHub sign-in now uses the browser instead of a floating window. The "display over
other apps" permission is gone from the manifest, along with the overlay service and
the notification permission it needed:

- the device flow asks GitHub for a code and opens the verification page with
  `ACTION_VIEW`, putting the code on the clipboard first;
- the in-app dialog stays visible while polling continues, so the waiting state and
  any error are still shown;
- the build-time client ID is no longer editable in Settings, because a runtime field
  could not change which OAuth app GitHub has registered and invited users to paste a
  value with no effect;
- the app now requests no permission for sign-in at all.

The release is signed with the same certificate as V1.1.0 (SHA-256
`5b289f0c27fb56e0fb752eb60ec7f1738bee13ef75ae3e9b5697a01882fb4e8f`), so it installs
over 1.1.0 in place.

### New in 1.1.0

In-app updates. GitForge now checks this repository's releases on launch, and can
download, verify and install a newer build without a computer. The updater:

- follows the build type's channel, so a release build never installs a debug APK;
- verifies the download's size, and its SHA-256 when the release advertises one;
- checks the downloaded APK's signing certificate against the installed app's before
  offering the install, so a cross-key install is explained rather than silently
  failing;
- stages the APK in app-private storage and hands it to the Android package
  installer, which still shows its own confirmation.

See [Updating GitForge](#updating-gitforge). New tests cover version ordering
(including the `1.10.0` vs `1.9.0` case), release parsing, channel and asset
selection, rate-limit and 404 handling, and the rule that no token appears in a URL.

One design defect was caught by these tests before release: the first version of the
asset selector fell back to *any* APK, so a release shipping only a debug APK would
have been offered to a release build. That build is signed with a different key and
could never install. The selector now refuses to cross the release/debug boundary,
and a regression test pins that behaviour.

---

## Project layout

```
GitForge/
├── .github/                        absent: the CI workflow is documented but not
│                                    yet pushed, see "Continuous integration"
├── app/
│   ├── build.gradle.kts            Module config, dependencies, signing
│   ├── proguard-rules.pro          Keep rules for JGit and serialization
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/projecthub/gitforge/   (see Architecture)
│       │   │   └── update/                      In-app updater (release lookup,
│       │   │                                     download, verify, install)
│       │   └── res/                            Icons, theme, strings, FileProvider
│       └── test/java/com/projecthub/gitforge/  JVM unit tests
├── gradle/wrapper/                 Gradle 8.9 wrapper
├── build.gradle.kts                Plugin versions
├── settings.gradle.kts             Repositories and modules
├── gradle.properties               JVM args, AndroidX flags
├── README.md                       This file
├── THIRD_PARTY_LICENSES.md         Licence texts
└── LICENSE                         Apache-2.0
```

---

## Continuous integration

**GitForge does not currently run CI.** There is no workflow file in the repository.
One was written and reviewed, but pushing anything under `.github/workflows/` requires
a token with the `workflow` scope, and the credentials available during development
had only `repo`. GitHub rejected the push, so the file was left out rather than the
documentation claiming a pipeline that does not exist.

To enable it, a maintainer with `workflow` scope can save the following as
`.github/workflows/android.yml`, or add it through the GitHub web UI. It contains no
secrets and no signing material, and it uses the same toolchain the build was verified
against locally — JDK 17, Android SDK API 34, build-tools 34.0.0.

```yaml
name: Android CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Install SDK packages
        run: sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Unit tests
        run: ./gradlew testDebugUnitTest --no-daemon

      - name: Assemble debug APK
        run: ./gradlew assembleDebug --no-daemon

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: gitforge-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: app/build/test-results/testDebugUnitTest/*.xml
          if-no-files-found: ignore
```

Until that file is added, run the same commands locally; the results are in
[Build and test results](#build-and-test-results). No signing key or secret is used or
required, by CI or by the build.

---

## Licence

GitForge is released under the Apache License 2.0. See `LICENSE`.

Third-party components keep their own licences; see
`THIRD_PARTY_LICENSES.md` and the in-app licence list under
the **Packages** screen under Licences.
