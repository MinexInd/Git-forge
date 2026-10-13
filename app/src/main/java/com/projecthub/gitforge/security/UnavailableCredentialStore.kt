package com.projecthub.gitforge.security

import com.projecthub.gitforge.core.AppLog

/**
 * Stand-in used when the device Keystore cannot be opened.
 *
 * It reports "no token" for every read, so the UI shows the app as disconnected and
 * sign-in as unavailable, and it refuses to persist anything rather than silently
 * falling back to unencrypted storage. A token written in the clear would be worse
 * than no token at all.
 */
class UnavailableCredentialStore(private val cause: Throwable) : CredentialStore {

    val reason: String =
        "Secure credential storage is unavailable on this device, so GitHub sign-in is " +
            "disabled. Cause: ${cause::class.java.simpleName}: ${cause.message}"

    override fun hasToken(): Boolean = false

    override fun token(): String? = null

    override fun login(): String? = null

    override fun scopes(): String? = null

    override fun tokenType(): String? = null

    override fun save(token: String, login: String?, scopes: String?, tokenType: String?) {
        AppLog.e("Refusing to store a credential: $reason")
    }

    override fun clear() = Unit
}
