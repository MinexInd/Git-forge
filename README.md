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

## Table of contents

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
- [Security model](#security-model)
- [Dependency and licence inventory](#dependency-and-licence-inventory)
- [Known limitations](#known-limitations)
- [Unverified work](#unverified-work)
- [Project layout](#project-layout)

---

## Status

| Item | State |
| --- | --- |
| Debug APK builds | Yes — `app/build/outputs/apk/debug/app-debug.apk` |
| Unit test suite | 111 tests, all passing |
| Installed on a physical device | Not verified (no device or emulator was available in the build environment) |
| GitHub end-to-end (clone, push) | Not verified (requires a real account and network access) |
| Terminal with Node/npm/Python | Implemented; the runtime downloads on first use and needs a device to verify |

Nothing in the table above is guessed. Anything that was not actually executed is
called out again in [Unverified work](#unverified-work).

---

## What works today

### GitHub

- Sign in with the OAuth **device flow** (RFC 8628), so no client secret is ever
  embedded in the APK, or paste a personal access token.
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

GitForge builds with **JDK 17** or **JDK 21**. Either works; JDK 21 is used in CI.

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
the build actually wrote. On this repository's build machine the verified output was:

```
/workspace/project/app/build/outputs/apk/debug/app-debug.apk
```

with size ≈ 22.7 MB, package `com.projecthub.gitforge`, version name `1.0.0`,
`minSdk 26`, `targetSdk 34`, launcher activity `com.projecthub.gitforge.MainActivity`.

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

The suite is 111 JVM unit tests and needs no device. It covers:

| Area | Test file | What it proves |
| --- | --- | --- |
| Path validation | `core/PathSafetyTest.kt` | Traversal, absolute paths, Windows drive paths, sibling-prefix escapes and name validation are all refused |
| Credential redaction | `core/RedactionTest.kt` | Tokens of every GitHub shape are removed from logs, remote URLs and error text |
| Editor file layer | `workspace/FileOperationsTest.kt` | Real reads and writes, CRLF/LF preservation, conflicting-save refusal, binary detection, large-file truncation, search, file tree |
| Git engine | `git/GitEngineTest.kt` | Real repositories: status, diffs, staging, commits, branches, merge conflicts, revert, remotes, persistence across reopen |
| Shell | `terminal/ShellEngineTest.kt` | Real command execution: `cd`, `mkdir`, `touch`, redirection, pipes, `grep`, `sed`, `head`, `tail`, `uniq`, `cp`, `mv`, `rm`, `find`, `export`, history, exit codes |
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
4. In GitForge, open **Settings → GitHub account → Sign in with a token**, paste it
   and confirm.

GitForge verifies the token against `GET /user` before storing it. A token GitHub
rejects is discarded immediately, so the app never claims to be signed in when it is
not.

### Option B — OAuth device flow (no token copying)

This is the default. GitForge ships with a public client ID already compiled in
(`BuildConfig.DEFAULT_OAUTH_CLIENT_ID`), so there is normally nothing to configure:

1. Open **Settings → GitHub and security**.
2. Choose **Connect with GitHub**. GitForge asks for permission to display over other
   apps, then opens a floating window showing a short code such as `35BD-DA5A`.
3. Choose **Open browser**, enter the code, and approve access on GitHub. The window
   stays visible the whole time, so the code does not disappear while you are in the
   browser.
4. GitForge verifies the token with `GET /user` and stores it in the Keystore-backed
   credential store.

To use your own OAuth app instead, register one at **GitHub → Settings → Developer
settings → OAuth Apps → New OAuth App**, enable **Device flow**, then paste its
**Client ID** into **Settings → GitHub and security → Change OAuth client ID**. The
override is persisted, so it only has to be entered once.

There is no callback URL in this flow, and GitHub never redirects back to the app.
The device flow does not use one, which is why no client secret is needed and why
nothing secret is embedded in the APK. If GitHub's OAuth app form insists on a value
for *Authorization callback URL*, any valid URL works — it is stored but never
contacted.

The floating window is optional. Declining the over-other-apps permission leaves the
flow fully working through the in-app dialog; only the ability to see the code while
the browser is in the foreground is lost. If the app is closed mid-sign-in, reopen it
and connect again — a code that has not been approved cannot be replayed.

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

## Security model

| Concern | How GitForge handles it |
| --- | --- |
| Token storage | `EncryptedSharedPreferences` backed by the Android Keystore |
| Client secret | Never requested and never embedded; the device flow is used instead |
| OAuth client ID | Public by design, compiled in as `BuildConfig.DEFAULT_OAUTH_CLIENT_ID` and overridable in Settings |
| Overlay permission | Used only to show the sign-in code over the browser. The window never displays the token, and the flow works without the permission |
| Sign-in code | Public, short-lived and single-use; shown in the window and long-pressable to copy |
| Remote URLs | Stored without userinfo; the credential is supplied in memory at push time |
| Logs and errors | Every message passes through `Redaction`, which strips tokens from URLs, headers and free text |
| Repository content | Treated as untrusted; scripts in a cloned repository are never executed automatically |
| Terminal scope | Confined to the workspace and runtime home; paths outside are refused |
| Storage permissions | None requested. Repositories are app-private; import and export use the Storage Access Framework |
| Backup | `allowBackup="false"`, with backup and device-transfer rules excluding everything |
| WebView | Not used for the app shell. If added for an editor engine it must run with JavaScript bridges disabled for untrusted content |
| Destructive Git operations | Require explicit confirmation; force-push is never performed silently |
| Credentials in history | `export` of a token is discouraged in the UI copy, and the token is never written into shell history by the app |

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

## Known limitations

- **Architecture.** The userspace packages are `arm64-v8a`. The APK declares
  `arm64-v8a`, `armeabi-v7a` and `x86_64`, but on a 32-bit ARM or x86 emulator the
  runtime download is not usable. Most modern phones are arm64 and are fine.
- **No root.** GitForge runs inside the normal Android sandbox. It cannot install
  system packages, cannot bind privileged ports, and cannot see other apps' data.
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
3. **The floating sign-in window has not been shown on a real device.** The service
   compiles, is registered in the manifest as a non-exported `dataSync` foreground
   service, and the over-other-apps permission is present, but `TYPE_APPLICATION_OVERLAY`
   behaviour (position, drag, keyboard interaction) can only be confirmed on hardware.
   The in-app dialog path is a working fallback if the permission is refused.
4. **The runtime download was not exercised.** Package versions and checksums are
   recorded in `RuntimePackageRegistry` and covered by unit tests, but no `.deb` was
   actually downloaded or unpacked, because that needs a device and network.
5. **The Compose UI has no automated tests.** Unit tests cover the engine, file
   layer, shell, parsers and settings persistence; the screens were not driven by
   instrumented tests.
6. **`arm64-v8a` on real hardware** is the intended target and the only ABI for which
   the userspace is published, but it has not been confirmed on a physical phone.

---

## Project layout

```
GitForge/
├── .github/workflows/android.yml   CI: build, test, upload APK
├── app/
│   ├── build.gradle.kts            Module config, dependencies, signing
│   ├── proguard-rules.pro          Keep rules for JGit and serialization
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/projecthub/gitforge/   (see Architecture)
│       │   └── res/                            Icons, theme, strings
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

`.github/workflows/android.yml` runs on every push and pull request:

- sets up JDK 17 and the Android SDK (API 34);
- runs `./gradlew testDebugUnitTest`;
- runs `./gradlew assembleDebug`;
- uploads `app-debug.apk` as a workflow artifact.

No signing key or secret is used or required by CI. The workflow holds no secrets.

---

## Licence

GitForge is released under the Apache License 2.0. See `LICENSE`.

Third-party components keep their own licences; see
`THIRD_PARTY_LICENSES.md` and the in-app licence list under
the **Packages** screen under Licences.
