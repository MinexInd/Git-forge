package com.projecthub.gitforge.workspace

import com.projecthub.gitforge.core.PathSafety
import java.io.File
import java.io.IOException

/**
 * Owns the app-private directory tree.
 *
 * Layout:
 * ```
 * files/workspace/<owner>/<name>/     cloned GitHub repositories
 * files/workspace/local/<name>/       repositories created on device
 * files/tmp/                          scratch space, safe to delete
 * files/export/                       staging area for Storage Access Framework exports
 * ```
 *
 * Everything lives under `context.filesDir`, so it needs no storage permission,
 * survives process death and app restarts, and is removed when the user
 * uninstalls the app.
 */
class WorkspaceManager(private val filesDir: File) {

    val root: File = File(filesDir, "workspace")
    val tempDir: File = File(filesDir, "tmp")
    val exportDir: File = File(filesDir, "export")

    init {
        listOf(root, tempDir, exportDir).forEach { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Could not create workspace directory: ${dir.name}")
            }
        }
    }

    fun githubRoot(): File = ensureDir(File(root, "github"))

    fun localRoot(): File = ensureDir(File(root, "local"))

    /**
     * Folder for a cloned GitHub repository: `github/<owner>/<name>`.
     * Both segments are sanitised so a hostile repository name cannot escape.
     */
    fun githubRepositoryDir(owner: String, name: String): File {
        val safeOwner = sanitizeSegment(owner)
        val safeName = sanitizeSegment(name)
        return File(File(githubRoot(), safeOwner), safeName)
    }

    /** Folder for a repository created on device: `local/<name>`. */
    fun localRepositoryDir(name: String): File = File(localRoot(), sanitizeSegment(name))

    /** Relative folder name persisted in the database. */
    fun relativePathOf(directory: File): String = PathSafety.relativize(root, directory)

    /** Resolves a relative folder name from the database back to an absolute path. */
    fun resolveRelative(relative: String): File = PathSafety.resolveInside(root, relative)

    fun ensureDir(dir: File): File {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create ${dir.name}")
        return dir
    }

    /** Total bytes used by the workspace, plus a per-repository breakdown. */
    fun usage(): WorkspaceUsage {
        val perRepo = mutableMapOf<String, Long>()
        val rootDirs = listOf(githubRoot(), localRoot())
        for (base in rootDirs) {
            base.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    // github/<owner>/<name> needs one more level of nesting.
                    val repos = if (child.name == "github" || child.listFiles()?.any { it.isDirectory } == true) {
                        child.listFiles()?.filter { it.isDirectory } ?: emptyList()
                    } else {
                        listOf(child)
                    }
                    repos.forEach { repo ->
                        val key = PathSafety.relativize(root, repo)
                        perRepo[key] = directorySize(repo)
                    }
                }
            }
        }
        return WorkspaceUsage(
            totalBytes = perRepo.values.sum(),
            perRepositoryBytes = perRepo,
            freeBytes = filesDir.usableSpace,
        )
    }

    fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0
        if (dir.isFile) return dir.length()
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory && !isSymlink(child)) stack.addLast(child) else if (child.isFile) total += child.length()
            }
        }
        return total
    }

    /** Deletes only the app's scratch directory. Never touches user repositories. */
    fun clearTemp() {
        tempDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun createTempFile(prefix: String, suffix: String): File {
        ensureDir(tempDir)
        return File.createTempFile(prefix, suffix, tempDir)
    }

    private fun sanitizeSegment(raw: String): String {
        val cleaned = raw.trim().replace(Regex("""[^A-Za-z0-9._-]"""), "-").trim('-', '.', '_')
        if (cleaned.isEmpty()) throw IOException("Invalid repository name")
        return cleaned.take(100)
    }

    private fun isSymlink(file: File): Boolean = try {
        file.canonicalFile != file.absoluteFile
    } catch (_: IOException) {
        true
    }
}

data class WorkspaceUsage(
    val totalBytes: Long,
    val perRepositoryBytes: Map<String, Long>,
    val freeBytes: Long,
)
