package com.projecthub.gitforge.security

/**
 * Stores GitHub credentials. Implementations must never write the token to plain
 * preferences, to a file inside the repository, to a log, or to the terminal.
 */
interface CredentialStore {

    /** True when a token is present. Cheap enough to call from the UI layer. */
    fun hasToken(): Boolean

    /** Returns the stored token, or null when the user is disconnected. */
    fun token(): String?

    /** Persists the token plus the account metadata returned by GitHub. */
    fun save(token: String, login: String?, scopes: String?, tokenType: String?)

    /** Removes all stored credential material. */
    fun clear()

    fun login(): String?

    fun scopes(): String?

    fun tokenType(): String?
}
