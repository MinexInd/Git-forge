package com.projecthub.gitforge.core

import java.io.File
import java.io.IOException

/**
 * Validates every path that arrives from the UI, the file tree, Git metadata or a
 * cloned repository before it is used to touch the filesystem.
 *
 * Threat model: a repository is untrusted content. A crafted entry name such as
 * `../../../../databases/app.db` or a symlink pointing outside the workspace must
 * never be able to read or write outside the workspace root.
 */
object PathSafety {

    /**
     * Resolves [relativePath] against [root] and guarantees the result stays inside
     * [root]. Throws [IOException] when the path escapes, is blank, or is absolute.
     */
    @Throws(IOException::class)
    fun resolveInside(root: File, relativePath: String): File {
        requireRoot(root)
        val cleaned = relativePath.replace('\\', '/').trim()
        if (cleaned.isEmpty()) throw IOException("Path is empty")
        if (cleaned.contains('\u0000')) throw IOException("Path contains a null byte")
        if (cleaned.startsWith("/")) throw IOException("Absolute paths are not allowed: $relativePath")
        if (DRIVE_PATH.matches(cleaned)) {
            throw IOException("Absolute paths are not allowed: $relativePath")
        }

        val canonicalRoot = root.canonicalFile
        val resolved = File(canonicalRoot, cleaned).canonicalFile

        if (!isInside(canonicalRoot, resolved)) {
            throw IOException("Path escapes the workspace: $relativePath")
        }
        return resolved
    }

    /** Matches a Windows style drive path, which has no meaning inside the app. */
    private val DRIVE_PATH = Regex("""^[A-Za-z]:[/\\].*""")

    /** True when [child] is [parent] itself or lives beneath it. */
    fun isInside(parent: File, child: File): Boolean {
        val p = parent.canonicalFile
        val c = child.canonicalFile
        if (p == c) return true
        return c.path.startsWith(p.path + File.separator)
    }

    /**
     * Like [resolveInside] but also rejects a path that reaches its target through
     * an existing symbolic link, which could otherwise escape the workspace.
     */
    @Throws(IOException::class)
    fun resolveExistingInside(root: File, relativePath: String): File {
        val resolved = resolveInside(root, relativePath)
        if (!resolved.exists()) throw IOException("File not found: $relativePath")

        val canonicalRoot = root.canonicalFile
        var current: File? = resolved
        while (current != null && current.absoluteFile != canonicalRoot) {
            if (current.absoluteFile.canonicalFile != current.absoluteFile) {
                throw IOException("Path crosses a symbolic link: $relativePath")
            }
            if (!isInside(canonicalRoot, current)) {
                throw IOException("Path resolves outside the workspace: $relativePath")
            }
            current = current.parentFile
        }
        return resolved
    }

    /** Validates a single file or directory name typed by the user. */
    @Throws(IOException::class)
    fun validateNewName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IOException("Name cannot be empty")
        if (trimmed == "." || trimmed == "..") throw IOException("'$trimmed' is not a valid name")
        if (trimmed.contains('/') || trimmed.contains('\\')) {
            throw IOException("Name cannot contain a path separator")
        }
        if (trimmed.contains('\u0000')) throw IOException("Name contains an illegal character")
        if (trimmed.length > 255) throw IOException("Name is too long")
        if (trimmed.startsWith(".git") && trimmed != ".gitignore" && trimmed != ".gitattributes") {
            throw IOException("Names beginning with '.git' are reserved")
        }
    }

    /**
     * Converts an absolute file inside [root] into a normalised, forward-slash
     * relative path suitable for display and for Git.
     */
    fun relativize(root: File, file: File): String {
        val rootPath = root.canonicalFile.path
        val filePath = file.canonicalFile.path
        if (filePath == rootPath) return ""
        val rel = filePath.removePrefix(rootPath).removePrefix(File.separator)
        return rel.replace(File.separatorChar, '/')
    }

    /** Rejects repository folder names that could collide with app-private data. */
    fun isSafeRepoFolderName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains('/') || trimmed.contains('\\')) return false
        if (trimmed.startsWith(".")) return false
        return trimmed.matches(Regex("""[A-Za-z0-9._-]{1,100}"""))
    }

    private fun requireRoot(root: File) {
        if (!root.isDirectory) throw IOException("Workspace root does not exist: ${root.name}")
    }
}
