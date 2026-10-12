# Third-party licences

GitForge itself is Apache-2.0 (see `LICENSE`). It depends on the components below.
Each is used under its own licence, and each licence is reproduced or referenced
here. The runtime package list is also shown in the app on the **Packages** screen under
Licences.

Nothing in this list is a copyleft obligation on GitForge's own source, because the
GPL and MPL components are either downloaded onto the device at the user's request
rather than distributed in the APK, or are not linked into GitForge at all.

---

## Bundled in the APK

These are compiled into the application and shipped in `app-debug.apk` /
`app-release.apk`.

### Apache License 2.0

The full text is in `LICENSE`.

| Component | Version | Copyright |
| --- | --- | --- |
| Kotlin standard library | 1.9.24 | JetBrains s.r.o. and Kotlin contributors |
| kotlinx.coroutines | 1.8.1 | JetBrains s.r.o. and contributors |
| kotlinx.serialization | 1.6.3 | JetBrains s.r.o. and contributors |
| AndroidX core-ktx | 1.13.1 | The Android Open Source Project |
| AndroidX core-splashscreen | 1.0.1 | The Android Open Source Project |
| AndroidX activity-compose | 1.9.2 | The Android Open Source Project |
| AndroidX lifecycle | 2.8.6 | The Android Open Source Project |
| AndroidX navigation-compose | 2.8.1 | The Android Open Source Project |
| AndroidX Compose UI, Foundation, Material 3, material-icons-extended | BOM 2024.09.03 | The Android Open Source Project |
| AndroidX Room | 2.6.1 | The Android Open Source Project |
| AndroidX DataStore | 1.1.1 | The Android Open Source Project |
| AndroidX security-crypto | 1.1.0-alpha06 | The Android Open Source Project |
| AndroidX WorkManager | 2.9.1 | The Android Open Source Project |
| AndroidX documentfile | 1.0.1 | The Android Open Source Project |
| OkHttp | 4.12.0 | Square, Inc. |
| Okio | 3.x (transitive) | Square, Inc. |
| Apache Commons Compress | 1.27.1 | The Apache Software Foundation |

### Eclipse Distribution License 1.0 / BSD-3-Clause (dual)

| Component | Version | Copyright |
| --- | --- | --- |
| Eclipse JGit (`org.eclipse.jgit`) | 6.7.0.202309050840-r | Eclipse Foundation and contributors |

JGit is distributed under the Eclipse Distribution License v1.0, which is the
three-clause BSD licence. The EDL text is reproduced at the end of this file.

### MIT

| Component | Version | Copyright |
| --- | --- | --- |
| SLF4J Android binding (`slf4j-android`) | 1.7.36 | QOS.ch |

### Public domain

| Component | Version | Copyright |
| --- | --- | --- |
| XZ for Java (`org.tukaani:xz`) | 1.10 | Lasse Collin |

The XZ for Java library is released into the public domain.

### Test-only dependencies

Not shipped in the APK; used to run the JVM test suite.

| Component | Version | Licence |
| --- | --- | --- |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 |
| Robolectric | 4.13 | MIT |
| MockWebServer | 4.12.0 | Apache-2.0 |
| AndroidX test core-ktx | 1.6.1 | Apache-2.0 |
| AndroidX Room testing | 2.6.1 | Apache-2.0 |
| kotlinx-coroutines-test | 1.8.1 | Apache-2.0 |

---

## Downloaded onto the device at the user's request

These are **not** part of the APK and are not distributed by this project. The
**Packages** screen fetches them from the Termux main package repository over HTTPS,
verifies each archive against the SHA-256 checksum recorded in
`RuntimePackageRegistry.kt`, and unpacks it into the app's private storage. The user
initiates each download.

Because they are fetched rather than redistributed, their licences do not attach to
GitForge's own source. They are listed so users know what they are installing.

| Package | Version | Licence |
| --- | --- | --- |
| bash | 5.3.0 | GPL-3.0-or-later |
| coreutils | 9.6 | GPL-3.0-or-later |
| git | 2.51.0 | GPL-2.0-only |
| nodejs | 26.4.0 | MIT |
| npm | 11.20.0 | Artistic License 2.0 |
| python (CPython) | 3.14.6 | PSF-2.0 |
| python-pip | 26.2.1 | MIT |
| openssl | 3.6.5 | Apache-2.0 |
| libc++ | 30 | Apache-2.0 with LLVM exception |
| ca-certificates | 2026.09.25 | MPL-2.0 |
| ncurses | 6.5 | MIT-style (X11) |
| readline | 8.3.6 | GPL-3.0-or-later |
| gdbm | 1.26 | GPL-3.0-or-later |
| zlib | 1.3.2 | zlib licence |
| libiconv | 1.18 | LGPL-2.1-or-later |
| libandroid-support | 29 | Apache-2.0 |
| dash | 0.5.12 | BSD-3-Clause |
| termux-am | current | Apache-2.0 |

If you install a GPL-licensed package (bash, git, coreutils, readline, gdbm), the
corresponding source is available from the package repository and from the upstream
projects. GitForge links to none of them statically; it launches them as separate
processes, so GitForge's own licence is unaffected.

---

## Eclipse Distribution License — Version 1.0

```
Copyright (c) 2000-2023 Eclipse Foundation, Inc. and others.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright notice, this
    list of conditions and the following disclaimer in the documentation and/or
    other materials provided with the distribution.

  * Neither the name of the Eclipse Foundation, Inc. nor the names of its
    contributors may be used to endorse or promote products derived from this
    software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
DAMAGE.
```
