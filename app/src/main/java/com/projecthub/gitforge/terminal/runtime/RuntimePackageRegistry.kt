package com.projecthub.gitforge.terminal.runtime

/**
 * Which installable group a package belongs to. The user chooses what to install,
 * so a Git-only workflow never pays for the 10 MB ICU library that Node needs.
 */
enum class RuntimeGroup {
    /** Node.js plus npm and its shared libraries. */
    NODE,

    /** CPython 3 plus pip. */
    PYTHON,

    /** Certificates and resolver configuration shared by both. */
    COMMON,
}

/**
 * A single Termux `.deb` from the official Termux repository.
 *
 * Pinning the version *and* the SHA-256 means the installer verifies the exact
 * bytes before they are unpacked into app-private storage. A changed upstream
 * package fails verification instead of silently installing.
 */
data class TermuxPackage(
    val name: String,
    val version: String,
    val filename: String,
    val sizeBytes: Long,
    val sha256: String,
    val group: RuntimeGroup,
)

/**
 * The verified Termux userspace that GitForge installs on device.
 *
 * ## Why Termux packages
 *
 * Android has no `/usr/bin` and ships neither a shell interpreter nor Git, Python
 * or Node. Termux is the only maintained project that builds a real GNU userspace
 * for Android against bionic: its binaries are ordinary PIE executables linked
 * against `/system/bin/linker64`, with a `DT_NEEDED` list of libraries the package
 * manager also ships. That makes them relocatable into any app-private directory,
 * which is exactly what this app needs, and it means no root and no second copy of
 * libc.
 *
 * ## Licensing and redistribution
 *
 * Termux packages are downloaded at the user's request from the official mirror;
 * they are not redistributed inside the APK. Licences are mixed and per-package:
 * Node.js and npm are MIT, CPython is PSF-2.0, OpenSSL is Apache-2.0, libc++ is
 * Apache-2.0 with the LLVM exception, ICU is Unicode-3.0, and the rest are
 * BSD/MIT/zlib-style. `RuntimePackageRegistry.licenceSummary` surfaces this in the
 * Packages screen so the user sees what they are installing.
 *
 * ## Architecture support
 *
 * The manifest below is for **arm64-v8a**, which covers essentially every Android
 * phone and tablet that can run a modern app. `RuntimePackageRegistry.supported`
 * reports honestly whether the current device matches, and the Packages screen
 * explains the limitation on other ABIs instead of failing mysteriously.
 */
object RuntimePackageRegistry {

    /** Official Termux mirror. Overridable in Settings for users behind a proxy. */
    const val DEFAULT_MIRROR = "https://packages.termux.dev/apt/termux-main"

    const val SUPPORTED_ABI = "arm64-v8a"

    val nodePackages: List<TermuxPackage> = listOf(
        TermuxPackage(
            name = "nodejs",
            version = "26.4.0-1",
            filename = "pool/main/n/nodejs/nodejs_26.4.0-1_aarch64.deb",
            sizeBytes = 10349280L,
            sha256 = "eaf3ed8a6e4b72ebaa8c2cb3bad778c577cdf9ea87ca91761213d8a3940fc090",
            group = RuntimeGroup.NODE,
        ),
        TermuxPackage(
            name = "npm",
            version = "11.20.0",
            filename = "pool/main/n/npm/npm_11.20.0_all.deb",
            sizeBytes = 2025572L,
            sha256 = "a2775653a0626749138ccd6e37a7b63239f36a9e3c62a9e6740818ca4b2d088b",
            group = RuntimeGroup.NODE,
        ),
        TermuxPackage(
            name = "libc++",
            version = "30",
            filename = "pool/main/libc/libc++/libc++_30_aarch64.deb",
            sizeBytes = 341604L,
            sha256 = "53d0b84a7ba7459024257cb94d5b136fe13ef858567f65a8064b35950799f2ca",
            group = RuntimeGroup.NODE,
        ),
        TermuxPackage(
            name = "openssl",
            version = "1:3.6.5",
            filename = "pool/main/o/openssl/openssl_1:3.6.5_aarch64.deb",
            sizeBytes = 2482764L,
            sha256 = "a2fb7856875e63ef6b90442752e05974813372c3ca86d2513a78ea43c67a0f8b",
            group = RuntimeGroup.NODE,
        ),
        TermuxPackage(
            name = "c-ares",
            version = "1.34.8",
            filename = "pool/main/c/c-ares/c-ares_1.34.8_aarch64.deb",
            sizeBytes = 194924L,
            sha256 = "7681fc23e822d7988ba8b2adf3468f93ae68f724dda365cff1385096a9fa87e6",
            group = RuntimeGroup.NODE,
        ),
        TermuxPackage(
            name = "libicu",
            version = "78.3",
            filename = "pool/main/libi/libicu/libicu_78.3_aarch64.deb",
            sizeBytes = 10210396L,
            sha256 = "f536403f65a08fe0df6e7304184e902d54def77d5c3bd5edfd9109d57601d276",
            group = RuntimeGroup.NODE,
        ),
        TermuxPackage(
            name = "libsqlite",
            version = "3.54.0",
            filename = "pool/main/libs/libsqlite/libsqlite_3.54.0_aarch64.deb",
            sizeBytes = 768760L,
            sha256 = "9adf91e118fcf4d449f0fb9668485d895916c4f76c2407da17ba114406f43a58",
            group = RuntimeGroup.NODE,
        ),
        TermuxPackage(
            name = "zlib",
            version = "1.3.2",
            filename = "pool/main/z/zlib/zlib_1.3.2_aarch64.deb",
            sizeBytes = 62840L,
            sha256 = "75e7d0af17fcc3b40004309fdc00a1ddb9ae08346dce5e269902c34ac3966ac9",
            group = RuntimeGroup.NODE,
        ),
        TermuxPackage(
            name = "libffi",
            version = "3.8.0",
            filename = "pool/main/libf/libffi/libffi_3.8.0_aarch64.deb",
            sizeBytes = 37592L,
            sha256 = "4f255badf74cd31f6a2801c17fa1444199c84c834b517b7c843e2fe9ebe91d77",
            group = RuntimeGroup.NODE,
        ),
    )

    val pythonPackages: List<TermuxPackage> = listOf(
        TermuxPackage(
            name = "python",
            version = "3.14.6-1",
            filename = "pool/main/p/python/python_3.14.6-1_aarch64.deb",
            sizeBytes = 4816756L,
            sha256 = "3166e56c2b6c03fff41191fbb9d736302978e7c484702814d9f6dc99dd6006bd",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "python-pip",
            version = "26.2.1",
            filename = "pool/main/p/python-pip/python-pip_26.2.1_all.deb",
            sizeBytes = 1190440L,
            sha256 = "92a9cb4b3c99dd20086f90fedc95c604eefc5bd0f88d1862690884474f82de44",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "gdbm",
            version = "1.26-1",
            filename = "pool/main/g/gdbm/gdbm_1.26-1_aarch64.deb",
            sizeBytes = 150284L,
            sha256 = "c378de833aae3cf41f3028491ea11836479d9833ab653edc27d0c0e0e691d5c5",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "readline",
            version = "8.3.6",
            filename = "pool/main/r/readline/readline_8.3.6_aarch64.deb",
            sizeBytes = 294164L,
            sha256 = "616cc73009bc0223f61caed8dd09455714455aa5ea5e180126e2f69c4c775136",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "libandroid-support",
            version = "29-1",
            filename = "pool/main/liba/libandroid-support/libandroid-support_29-1_aarch64.deb",
            sizeBytes = 10880L,
            sha256 = "f2f145d6135ad4843ac9670153be3e3944dc1e6f1736d46d2306c28f2b86f517",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "ncurses",
            version = "6.6.20260307+really6.5.20250830",
            filename = "pool/main/n/ncurses/ncurses_6.6.20260307+really6.5.20250830_aarch64.deb",
            sizeBytes = 557792L,
            sha256 = "f44bbfdc3d42ec0217bffa978309390e59cea5a48a9a83226d4a496c42ad0b99",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "libandroid-posix-semaphore",
            version = "0.1-4",
            filename = "pool/main/liba/libandroid-posix-semaphore/libandroid-posix-semaphore_0.1-4_aarch64.deb",
            sizeBytes = 4076L,
            sha256 = "0efa8677a0166315ba4e685863712eba0ca0a1732827492f38226e2723730c7a",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "libbz2",
            version = "1.0.8-8",
            filename = "pool/main/libb/libbz2/libbz2_1.0.8-8_aarch64.deb",
            sizeBytes = 26116L,
            sha256 = "4335d7f060650b0aabef545d1334c2f9f280223d5962e13c24a00ec934b794ba",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "libcrypt",
            version = "0.2-6",
            filename = "pool/main/libc/libcrypt/libcrypt_0.2-6_aarch64.deb",
            sizeBytes = 8880L,
            sha256 = "6c283eed576b98cc3568f99638156f8588f77d979579d03bef8683d6eb8601e1",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "libexpat",
            version = "2.9.0",
            filename = "pool/main/libe/libexpat/libexpat_2.9.0_aarch64.deb",
            sizeBytes = 100256L,
            sha256 = "f8a104e8227c9c181f6818658a3ee2fa4c107fac94219b068144fc6591086cdb",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "liblzma",
            version = "5.8.4",
            filename = "pool/main/libl/liblzma/liblzma_5.8.4_aarch64.deb",
            sizeBytes = 194796L,
            sha256 = "44e95e6e60dddb3705e60a344a4f009a1085a210797342b721eaff41b44033c0",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "ncurses-ui-libs",
            version = "6.6.20260307+really6.5.20250830",
            filename = "pool/main/n/ncurses-ui-libs/ncurses-ui-libs_6.6.20260307+really6.5.20250830_aarch64.deb",
            sizeBytes = 32940L,
            sha256 = "7393f369009be189b3d4ec1f9b16ebd57621d6a1b22949ae07685573950d1f37",
            group = RuntimeGroup.PYTHON,
        ),
        TermuxPackage(
            name = "zstd",
            version = "1.5.7-1",
            filename = "pool/main/z/zstd/zstd_1.5.7-1_aarch64.deb",
            sizeBytes = 360488L,
            sha256 = "e1b4a5113648da8de189620ba1fce74c48b2d0833d9043391b9a1c91fb606fd3",
            group = RuntimeGroup.PYTHON,
        ),
    )

    val commonPackages: List<TermuxPackage> = listOf(
        TermuxPackage(
            name = "ca-certificates",
            version = "1:2026.09.25",
            filename = "pool/main/c/ca-certificates/ca-certificates_1:2026.09.25_all.deb",
            sizeBytes = 105404L,
            sha256 = "57fab24bf4561bfdd35afe71bc1929914e51c35458cfd1fca2b15e54bc8df7af",
            group = RuntimeGroup.COMMON,
        ),
        TermuxPackage(
            name = "resolv-conf",
            version = "1.3",
            filename = "pool/main/r/resolv-conf/resolv-conf_1.3_aarch64.deb",
            sizeBytes = 976L,
            sha256 = "ab541abac8e0c81709cd7ca4a02bcfa0d60ba1f4bfe7fd6dce4a694a4a9dfffa",
            group = RuntimeGroup.COMMON,
        ),
    )

    fun packagesFor(group: RuntimeGroup): List<TermuxPackage> = when (group) {
        RuntimeGroup.NODE -> nodePackages
        RuntimeGroup.PYTHON -> pythonPackages
        RuntimeGroup.COMMON -> commonPackages
    }

    /** Everything needed to run both Node and Python. */
    fun fullUserspace(): List<TermuxPackage> = commonPackages + nodePackages + pythonPackages

    fun totalDownloadBytes(packages: List<TermuxPackage>): Long = packages.sumOf { it.sizeBytes }

    fun licenceSummary(): List<Pair<String, String>> = listOf(
        "Node.js" to "MIT",
        "npm" to "Artistic-2.0",
        "CPython" to "PSF-2.0",
        "OpenSSL" to "Apache-2.0",
        "libc++" to "Apache-2.0 WITH LLVM-exception",
        "ICU" to "Unicode-3.0",
        "zlib" to "Zlib",
        "libffi" to "MIT",
        "c-ares" to "MIT",
        "SQLite" to "Public Domain",
        "ncurses" to "MIT-style",
        "readline" to "GPL-3.0-or-later",
        "Termux packages" to "Downloaded from packages.termux.dev at your request; not bundled in the APK",
    )
}
