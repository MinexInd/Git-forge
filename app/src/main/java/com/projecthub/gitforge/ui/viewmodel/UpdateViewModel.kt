package com.projecthub.gitforge.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projecthub.gitforge.BuildConfig
import com.projecthub.gitforge.core.AppLog
import com.projecthub.gitforge.di.AppContainer
import com.projecthub.gitforge.update.InstallResult
import com.projecthub.gitforge.update.UpdateCheck
import com.projecthub.gitforge.update.UpdateProgress
import com.projecthub.gitforge.update.UpdateRelease
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** UI state for the update screen. */
data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val channel: String = "",
    val checking: Boolean = false,
    val check: UpdateCheck? = null,
    val progress: UpdateProgress = UpdateProgress.Idle,
    val stagedFile: File? = null,
    val message: String? = null,
    val canInstall: Boolean = true,
)

/**
 * Drives the update screen: check, download, verify, and hand off to the installer.
 *
 * It holds no token. The releases endpoint is public, so the check runs the same way
 * whether or not the user is signed in to GitHub, and no credential is put on the
 * update path at all.
 */
class UpdateViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(
        UpdateUiState(channel = container.updateChannel.label),
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    /** Asks GitHub whether a newer build exists on this app's channel. */
    fun checkForUpdate() {
        if (_state.value.checking) return
        _state.value = _state.value.copy(checking = true, message = null, stagedFile = null)

        viewModelScope.launch {
            val result = try {
                val release = container.releaseClient.findUpdate(
                    currentVersion = BuildConfig.VERSION_NAME,
                    channel = container.updateChannel,
                )
                if (release == null) {
                    UpdateCheck.UpToDate(BuildConfig.VERSION_NAME)
                } else {
                    UpdateCheck.Available(release, container.updateChannel)
                }
            } catch (e: Exception) {
                AppLog.w("Update check failed", e)
                UpdateCheck.Failed(e.message ?: "The update check failed.")
            }

            _state.value = _state.value.copy(
                checking = false,
                check = result,
                canInstall = container.updateManager.canInstallPackages(),
            )
        }
    }

    /** Downloads and verifies the APK for [release], then stages it for installation. */
    fun download(release: UpdateRelease) {
        if (downloadJob?.isActive == true) return
        val asset = release.assetFor(container.updateChannel) ?: run {
            _state.value = _state.value.copy(
                message = "This release has no APK for the ${container.updateChannel.label} channel.",
            )
            return
        }

        downloadJob = viewModelScope.launch {
            val result = container.updateManager.stage(asset) { progress ->
                _state.value = _state.value.copy(progress = progress)
            }
            _state.value = when (result) {
                is InstallResult.Staged -> _state.value.copy(
                    stagedFile = result.file,
                    message = "Verified. Tap Install to continue.",
                )

                is InstallResult.ChecksumMismatch -> _state.value.copy(
                    progress = UpdateProgress.Idle,
                    message = "The download did not match the published checksum and was discarded.",
                )

                is InstallResult.SignatureMismatch -> _state.value.copy(
                    progress = UpdateProgress.Idle,
                    message = "This build is signed with a different key than the installed app, so it cannot " +
                        "update it. Install a build signed with the same key instead.",
                )

                is InstallResult.Failed -> _state.value.copy(
                    progress = UpdateProgress.Idle,
                    message = result.message,
                )
            }
        }
    }

    /** Launches the system installer for the staged APK. */
    fun installStaged() {
        val file = _state.value.stagedFile ?: return
        if (!container.updateManager.canInstallPackages()) {
            container.updateManager.openInstallPermissionSettings()
            _state.value = _state.value.copy(
                canInstall = false,
                message = "Allow GitForge to install unknown apps, then tap Install again.",
            )
            return
        }
        if (container.updateManager.install(file)) {
            _state.value = _state.value.copy(message = "The system installer is open. Confirm to update.")
        } else {
            _state.value = _state.value.copy(message = "The installer could not be opened on this device.")
        }
    }

    /** Re-checks the install permission after the user returns from system settings. */
    fun refreshInstallPermission() {
        _state.value = _state.value.copy(canInstall = container.updateManager.canInstallPackages())
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = UpdateViewModel(container) as T
        }
    }
}
