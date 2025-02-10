package com.rk.libcommons

var pendingCommand: TerminalCommand? = null

data class TerminalCommand(
    val alpine: Boolean = true,
    val shell: String,
    val args: Array<String> = arrayOf(),
    val id: String,
    val terminatePreviousSession: Boolean = true,
    val workingDir: String,
    val env: Array<String> = arrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TerminalCommand

        if (shell != other.shell) return false
        if (!args.contentEquals(other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shell.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }
}
