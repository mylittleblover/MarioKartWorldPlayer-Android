package com.mylittleblover.mkwplayer.pack

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.mylittleblover.mkwplayer.registry.ResourceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide single-task coordinator for `.mkwpack` streaming imports (#91-#97).
 *
 * Enforces:
 * - #94: Strictly one active import task at any instant (new requests rejected clearly while busy).
 * - #96: Direct SAF `ParcelFileDescriptor` channel streaming with persistable URI permission when granted
 *   and zero whole-zip caching/copying.
 * - #91, #93: Pre-commit cancellation stops safely and cleans staging without polluting installed library;
 *   crossing the atomic commit boundary locks out cancellation.
 * - #92: Auto-applies without post-verify confirmation dialogs and emits a concise changelog.
 * - #95, #97: Discovers interrupted checkpoints on startup and supports source re-pick identity verification.
 */
internal object PackImportCoordinator {

    sealed interface StartResult {
        data object Started : StartResult
        data class RejectedBusy(val reason: String) : StartResult
        data class RejectedInvalid(val reason: String) : StartResult
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeTask = AtomicBoolean(false)
    private val enteringCommit = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)

    private val _state = MutableStateFlow(PackImportUiState())
    val state: StateFlow<PackImportUiState> = _state.asStateFlow()

    @Volatile
    private var activeLibraryRoot: File? = null

    @Volatile
    private var onCommitHandler: (suspend (StreamingPackInstaller.Result.Installed) -> PackImportChangelog)? = null

    fun configureCommitHandler(handler: suspend (StreamingPackInstaller.Result.Installed) -> PackImportChangelog) {
        onCommitHandler = handler
    }

    /** Inspects durable crash checkpoints on startup (#95, #97). */
    fun checkPendingCheckpoint(context: Context, libraryRoot: File) {
        if (activeTask.get()) return
        activeLibraryRoot = libraryRoot
        scope.launch {
            if (activeTask.get()) return@launch
            val store = PackImportCheckpointStore(libraryRoot)
            val cp = store.read().getOrNull() ?: return@launch
            if (cp.committing) {
                // Interrupted during final swap: materialize whatever finished and clean checkpoint.
                InternalPackLibrary(libraryRoot).materialize()
                store.clearAll()
                return@launch
            }
            val sourceUri = cp.sourceUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            val readableAndMatching = sourceUri != null && canOpenAndVerifyIdentity(context, sourceUri, cp)
            if (readableAndMatching) {
                _state.value = PackImportUiState(
                    phase = PackImportPhase.RESUMABLE,
                    displayName = cp.displayName,
                    processedMembers = cp.completedMembers.size,
                    totalMembers = cp.totalMembers,
                    bytesProcessed = cp.bytesProcessed,
                    canCancel = true,
                    checkpoint = cp,
                    hasPersistablePermission = true,
                )
            } else {
                _state.value = PackImportUiState(
                    phase = PackImportPhase.PAUSED_NEEDS_REPICK,
                    displayName = cp.displayName,
                    processedMembers = cp.completedMembers.size,
                    totalMembers = cp.totalMembers,
                    bytesProcessed = cp.bytesProcessed,
                    canCancel = true,
                    checkpoint = cp,
                    hasPersistablePermission = false,
                    errorReason = context.getString(
                        com.mylittleblover.mkwplayer.R.string.import_err_permission_expired_fmt,
                        cp.displayName,
                        cp.completedMembers.size,
                        cp.totalMembers,
                    ),
                )
            }
        }
    }

    /** Starts importing a newly picked `.mkwpack` URI directly without whole-zip copy (#94, #96). */
    fun startUriImport(
        context: Context,
        uri: Uri,
        libraryRoot: File,
        appFilesDir: File,
        startForeground: Boolean = true,
    ): StartResult {
        val currentCp = _state.value.checkpoint
        val isRepick = _state.value.phase == PackImportPhase.PAUSED_NEEDS_REPICK && currentCp != null
        return launchChannelTask(
            appContext = context.applicationContext,
            libraryRoot = libraryRoot,
            appFilesDir = appFilesDir,
            displayName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "pack.mkwpack",
            sourceUri = uri.toString(),
            resumeCheckpoint = currentCp.takeIf { isRepick },
            startForeground = startForeground,
            hasPersistablePermission = takePersistableReadGrant(context, uri),
        ) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error(context.getString(com.mylittleblover.mkwplayer.R.string.import_err_open_fd))
            val channel = FileInputStream(pfd.fileDescriptor).channel
            Pair(channel, pfd)
        }
    }

    /** Resumes an interrupted import whose persistent URI permission is still valid (#95, #96). */
    fun resumePending(
        context: Context,
        libraryRoot: File,
        appFilesDir: File,
        startForeground: Boolean = true,
    ): StartResult {
        val cp = _state.value.checkpoint ?: PackImportCheckpointStore(libraryRoot).read().getOrNull()
            ?: return StartResult.RejectedInvalid(context.getString(com.mylittleblover.mkwplayer.R.string.import_err_no_checkpoint))
        val uri = cp.sourceUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: return StartResult.RejectedInvalid(
                context.getString(com.mylittleblover.mkwplayer.R.string.import_err_needs_repick_fmt, cp.displayName),
            )

        return launchChannelTask(
            appContext = context.applicationContext,
            libraryRoot = libraryRoot,
            appFilesDir = appFilesDir,
            displayName = cp.displayName,
            sourceUri = cp.sourceUri,
            resumeCheckpoint = cp,
            startForeground = startForeground,
            hasPersistablePermission = true,
        ) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw SecurityException(context.getString(com.mylittleblover.mkwplayer.R.string.import_err_access_denied))
            val channel = FileInputStream(pfd.fileDescriptor).channel
            Pair(channel, pfd)
        }
    }

    /** Re-picks a file for an interrupted import whose URI permission was lost (#97). */
    fun resumeWithRepickedUri(
        context: Context,
        newUri: Uri,
        libraryRoot: File,
        appFilesDir: File,
        startForeground: Boolean = true,
    ): StartResult {
        val cp = _state.value.checkpoint ?: PackImportCheckpointStore(libraryRoot).read().getOrNull()
            ?: return StartResult.RejectedInvalid(context.getString(com.mylittleblover.mkwplayer.R.string.import_err_no_checkpoint))
        return launchChannelTask(
            appContext = context.applicationContext,
            libraryRoot = libraryRoot,
            appFilesDir = appFilesDir,
            displayName = queryDisplayName(context, newUri) ?: newUri.lastPathSegment ?: cp.displayName,
            sourceUri = newUri.toString(),
            resumeCheckpoint = cp,
            startForeground = startForeground,
            hasPersistablePermission = takePersistableReadGrant(context, newUri),
        ) {
            val pfd = context.contentResolver.openFileDescriptor(newUri, "r")
                ?: error(context.getString(com.mylittleblover.mkwplayer.R.string.import_err_open_selected))
            val channel = FileInputStream(pfd.fileDescriptor).channel
            Pair(channel, pfd)
        }
    }

    /**
     * Requests cancellation (#91, #93).
     * Returns false when an atomic commit is already underway (uncancellable point).
     */
    fun requestCancel(libraryRoot: File? = activeLibraryRoot): Boolean {
        if (!activeTask.get()) {
            val phase = _state.value.phase
            if (phase == PackImportPhase.RESUMABLE || phase == PackImportPhase.PAUSED_NEEDS_REPICK) {
                libraryRoot?.let { PackImportCheckpointStore(it).clearAll() }
                _state.value = PackImportUiState(phase = PackImportPhase.CANCELLED)
                return true
            }
            return false
        }
        if (enteringCommit.get()) return false
        cancelRequested.set(true)
        return true
    }

    fun dismissCompletedOrError() {
        if (!activeTask.get()) {
            val phase = _state.value.phase
            if (phase == PackImportPhase.COMPLETED ||
                phase == PackImportPhase.CANCELLED ||
                phase == PackImportPhase.FAILED
            ) {
                _state.value = PackImportUiState()
            }
        }
    }

    /**
     * Synchronous single-task engine shared by Android SAF execution and JVM unit tests (#91-#97).
     * Rejects concurrent starts immediately with [StartResult.RejectedBusy] without queuing (#94).
     */
    internal fun executeSynchronouslyForTest(
        libraryRoot: File,
        appFilesDir: File,
        displayName: String,
        sourceUri: String?,
        resumeCheckpoint: ImportCheckpoint? = null,
        clearCheckpointOnCancel: Boolean = true,
        openChannel: () -> SeekableByteChannel,
    ): Pair<StartResult, StreamingPackInstaller.Result?> {
        if (!activeTask.compareAndSet(false, true)) {
            return StartResult.RejectedBusy("Another pack import is already running. Wait for it to finish or cancel it first.") to null
        }
        activeLibraryRoot = libraryRoot
        enteringCommit.set(false)
        cancelRequested.set(false)
        val channelResult = runCatching {
            openChannel().use { channel ->
                kotlinx.coroutines.runBlocking {
                    runPipeline(
                        channel = channel,
                        libraryRoot = libraryRoot,
                        appFilesDir = appFilesDir,
                        displayName = displayName,
                        sourceUri = sourceUri,
                        resumeCheckpoint = resumeCheckpoint,
                        hasPersistablePermission = true,
                        clearCheckpointOnCancel = clearCheckpointOnCancel,
                    )
                }
            }
        }
        activeTask.set(false)
        enteringCommit.set(false)
        val result = channelResult.getOrElse { err ->
            val failure = StreamingPackInstaller.Result.Refused(err.message ?: "Import failed.")
            _state.value = PackImportUiState(
                phase = PackImportPhase.FAILED,
                displayName = displayName,
                errorReason = failure.reason,
            )
            failure
        }
        return StartResult.Started to result
    }

    private fun launchChannelTask(
        appContext: Context,
        libraryRoot: File,
        appFilesDir: File,
        displayName: String,
        sourceUri: String?,
        resumeCheckpoint: ImportCheckpoint?,
        startForeground: Boolean,
        hasPersistablePermission: Boolean,
        channelOpener: () -> Pair<SeekableByteChannel, AutoCloseable>,
    ): StartResult {
        if (!activeTask.compareAndSet(false, true)) {
            return StartResult.RejectedBusy(
                appContext.getString(com.mylittleblover.mkwplayer.R.string.import_err_busy),
            )
        }
        activeLibraryRoot = libraryRoot
        enteringCommit.set(false)
        cancelRequested.set(false)
        _state.value = PackImportUiState(
            phase = PackImportPhase.PROBING,
            displayName = displayName,
            canCancel = true,
            checkpoint = resumeCheckpoint,
            hasPersistablePermission = hasPersistablePermission,
        )
        if (startForeground) {
            runCatching { PackImportService.start(appContext) }
        }

        scope.launch {
            try {
                val pair = channelOpener()
                pair.second.use {
                    pair.first.use { channel ->
                        runPipeline(
                            channel = channel,
                            libraryRoot = libraryRoot,
                            appFilesDir = appFilesDir,
                            displayName = displayName,
                            sourceUri = sourceUri,
                            resumeCheckpoint = resumeCheckpoint,
                            hasPersistablePermission = hasPersistablePermission,
                            clearCheckpointOnCancel = true,
                            committingLabel = appContext.getString(com.mylittleblover.mkwplayer.R.string.import_member_committing),
                        )
                    }
                }
            } catch (sec: SecurityException) {
                val deniedText = sec.message ?: appContext.getString(com.mylittleblover.mkwplayer.R.string.import_err_access_denied)
                val cp = resumeCheckpoint ?: PackImportCheckpointStore(libraryRoot).read().getOrNull()
                if (cp != null) {
                    _state.value = PackImportUiState(
                        phase = PackImportPhase.PAUSED_NEEDS_REPICK,
                        displayName = cp.displayName,
                        processedMembers = cp.completedMembers.size,
                        totalMembers = cp.totalMembers,
                        bytesProcessed = cp.bytesProcessed,
                        canCancel = true,
                        checkpoint = cp,
                        hasPersistablePermission = false,
                        errorReason = appContext.getString(
                            com.mylittleblover.mkwplayer.R.string.import_err_permission_repick_fmt,
                            deniedText,
                        ),
                    )
                } else {
                    _state.value = PackImportUiState(
                        phase = PackImportPhase.FAILED,
                        displayName = displayName,
                        errorReason = appContext.getString(
                            com.mylittleblover.mkwplayer.R.string.import_err_permission_read_fmt,
                            deniedText,
                        ),
                    )
                }
            } catch (err: Exception) {
                _state.value = PackImportUiState(
                    phase = PackImportPhase.FAILED,
                    displayName = displayName,
                    errorReason = err.message ?: appContext.getString(com.mylittleblover.mkwplayer.R.string.import_err_failed),
                )
            } finally {
                enteringCommit.set(false)
                activeTask.set(false)
            }
        }
        return StartResult.Started
    }

    private suspend fun runPipeline(
        channel: SeekableByteChannel,
        libraryRoot: File,
        appFilesDir: File,
        displayName: String,
        sourceUri: String?,
        resumeCheckpoint: ImportCheckpoint?,
        hasPersistablePermission: Boolean,
        clearCheckpointOnCancel: Boolean,
        committingLabel: String = "Committing library",
    ): StreamingPackInstaller.Result {
        val result = StreamingPackInstaller.installFromChannel(
            channel = channel,
            libraryRoot = libraryRoot,
            appFilesDir = appFilesDir,
            displayName = displayName,
            sourceUri = sourceUri,
            resumeCheckpoint = resumeCheckpoint,
            cancelFlag = cancelRequested,
            clearCheckpointOnCancel = clearCheckpointOnCancel,
            listener = object : StreamingPackInstaller.Listener {
                override fun onProbed(manifest: PackManifest, totalMembers: Int, totalBytes: Long) {
                    _state.value = _state.value.copy(
                        phase = PackImportPhase.IMPORTING,
                        displayName = manifest.name.ifBlank { displayName },
                        totalMembers = totalMembers,
                        totalBytes = totalBytes,
                        canCancel = true,
                    )
                }

                override fun onMemberProgress(
                    processedMembers: Int,
                    totalMembers: Int,
                    memberName: String,
                    bytesProcessed: Long,
                    totalBytes: Long,
                ) {
                    _state.value = _state.value.copy(
                        phase = PackImportPhase.IMPORTING,
                        processedMembers = processedMembers,
                        totalMembers = totalMembers,
                        currentMember = memberName,
                        bytesProcessed = bytesProcessed,
                        totalBytes = totalBytes,
                        canCancel = !enteringCommit.get(),
                    )
                }

                override fun onEnteringCommit() {
                    enteringCommit.set(true)
                    _state.value = _state.value.copy(
                        phase = PackImportPhase.COMMITTING,
                        canCancel = false,
                        currentMember = committingLabel,
                    )
                }
            },
        )

        when (result) {
            is StreamingPackInstaller.Result.Installed -> {
                val mergedManifest = result.mergedLibrary?.manifest ?: result.manifest
                val fallbackChangelog = PackImportChangelog(
                    packId = result.manifest.packId,
                    packVersion = result.manifest.packVersion,
                    packName = result.manifest.name.ifBlank { displayName },
                    isDelta = result.manifest.isDelta,
                    addedOrUpdatedAssets = result.addedOrUpdatedAssets,
                    addedOrUpdatedTracks = result.addedOrUpdatedTracks,
                    totalLibraryTracks = mergedManifest.tracks.size,
                    playableLibraryTracks = result.coverage.playableCount,
                    jukeboxTrackCount = mergedManifest.tracks.count { it.kind == ResourceKind.JUKEBOX },
                    poolStatus = result.poolStatus,
                    poolDataVersion = result.poolDataVersion,
                    poolEdgeCount = result.poolEdgeCount,
                )
                val finalChangelog = onCommitHandler?.invoke(result) ?: fallbackChangelog
                _state.value = PackImportUiState(
                    phase = PackImportPhase.COMPLETED,
                    displayName = finalChangelog.packName,
                    processedMembers = _state.value.totalMembers,
                    totalMembers = _state.value.totalMembers,
                    bytesProcessed = _state.value.totalBytes,
                    totalBytes = _state.value.totalBytes,
                    canCancel = false,
                    changelog = finalChangelog,
                    hasPersistablePermission = hasPersistablePermission,
                )
            }
            is StreamingPackInstaller.Result.Cancelled -> {
                _state.value = PackImportUiState(
                    phase = PackImportPhase.CANCELLED,
                    displayName = displayName,
                    errorReason = result.reason,
                )
            }
            is StreamingPackInstaller.Result.SourceMismatch -> {
                val cp = resumeCheckpoint ?: PackImportCheckpointStore(libraryRoot).read().getOrNull()
                _state.value = PackImportUiState(
                    phase = PackImportPhase.PAUSED_NEEDS_REPICK,
                    displayName = result.expectedDisplayName,
                    processedMembers = cp?.completedMembers?.size ?: 0,
                    totalMembers = cp?.totalMembers ?: 0,
                    bytesProcessed = cp?.bytesProcessed ?: 0L,
                    canCancel = true,
                    checkpoint = cp,
                    errorReason = result.reason,
                )
            }
            is StreamingPackInstaller.Result.Refused -> {
                // Corrupt member / refusal during non-IO verification shouldn't leave broken staging behind.
                if (resumeCheckpoint == null) {
                    PackImportCheckpointStore(libraryRoot).clearAll()
                }
                _state.value = PackImportUiState(
                    phase = PackImportPhase.FAILED,
                    displayName = displayName,
                    errorReason = result.reason,
                )
            }
        }
        return result
    }

    private fun canOpenAndVerifyIdentity(
        context: Context,
        uri: Uri,
        checkpoint: ImportCheckpoint,
    ): Boolean = runCatching {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@runCatching false
        pfd.use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { ch ->
                SeekableZipReader.open(ch, ownsChannel = false).use { zip ->
                    zip.fileSize == checkpoint.sourceByteSize &&
                        zip.archiveFingerprint == checkpoint.archiveFingerprint
                }
            }
        }
    }.getOrDefault(false)

    private fun takePersistableReadGrant(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
    }.getOrNull()
}
