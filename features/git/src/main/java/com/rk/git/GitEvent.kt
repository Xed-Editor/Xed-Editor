package com.rk.git

import com.rk.events.Event
import com.rk.file.FileObject

/** Events related to Git version control operations. */
sealed interface GitEvent : Event {

    /** Event triggered when a Git repository has been successfully initialized. */
    data class RepositoryInitialized(val root: FileObject) : GitEvent

    /** Event triggered when a Git repository has been successfully cloned. */
    data class RepositoryCloned(
        val remoteUrl: String,
        val branch: String,
        val destination: FileObject,
    ) : GitEvent

    /** Event triggered when a new Git branch has been created. */
    data class BranchCreated(
        val root: FileObject,
        val name: String,
        val fromBranch: String?,
    ) : GitEvent

    /** Event triggered when a Git branch has been deleted. */
    data class BranchDeleted(val root: FileObject, val name: String) : GitEvent

    /** Event triggered when a Git branch has been checked out. */
    data class BranchCheckedOut(val root: FileObject, val name: String) : GitEvent

    /** Event triggered when a Git branch has been renamed. */
    data class BranchRenamed(
        val root: FileObject,
        val oldName: String,
        val newName: String,
    ) : GitEvent

    /** Event triggered when a Git branch has been merged. */
    data class Merged(val root: FileObject, val branch: String, val targetBranch: String) : GitEvent

    /** Event triggered when a Git branch has been rebased. */
    data class Rebased(val root: FileObject, val branch: String, val targetBranch: String) : GitEvent

    /**
     * Event triggered when a new Git commit has been created.
     *
     * @see CommitAmended
     */
    data class CommitCreated(
        val root: FileObject,
        val message: String,
    ) : GitEvent

    /**
     * Event triggered when a Git commit has been amended.
     *
     * @see CommitCreated
     */
    data class CommitAmended(
        val root: FileObject,
        val message: String,
    ) : GitEvent

    /** Event triggered when a Git fetch operation has completed. */
    data class FetchCompleted(val root: FileObject, val remote: String, val branch: String) : GitEvent

    /** Event triggered when a Git pull operation has completed. */
    data class PullCompleted(
        val root: FileObject,
        val remote: String,
        val branch: String,
    ) : GitEvent

    /** Event triggered when a Git push operation has completed. */
    data class PushCompleted(
        val root: FileObject,
        val remote: String,
        val branch: String,
        val force: Boolean = false,
    ) : GitEvent

    /** Event triggered when the Git working tree has been updated with changes. */
    data class WorkingTreeUpdated(val root: FileObject, val changes: List<GitChange>) : GitEvent
}
