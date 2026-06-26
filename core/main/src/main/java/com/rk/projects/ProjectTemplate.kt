package com.rk.projects

/**
 * Available project templates exposed by the "Create Project" flow.
 *
 * [recommendsSandbox] is true for templates whose toolchain (gradle, native npm modules, python
 * venvs, etc.) must run from an exec-capable Linux filesystem. Android shared storage
 * (Documents/Downloads) is mounted noexec and ignores Unix permission bits, so build tooling
 * cannot run there. For those templates we default the location to the terminal sandbox home.
 */
enum class ProjectTemplate(
    val displayName: String,
    val recommendsSandbox: Boolean,
    val showsPackageName: Boolean = false,
) {
    NONE("None", recommendsSandbox = false),
    PYTHON3("Python 3", recommendsSandbox = false),
    PYTHON("Python", recommendsSandbox = false),
    NODEJS("Node.js", recommendsSandbox = true),
    MINECRAFT_MOD("Minecraft Java Mod", recommendsSandbox = true, showsPackageName = true),
    ANDROID_COMPOSE("Android (Jetpack Compose)", recommendsSandbox = true, showsPackageName = true);

    val showsMinecraftOptions: Boolean
        get() = this == MINECRAFT_MOD
}

/** Mod loaders supported by the Minecraft Java Mod template. */
enum class ModLoader(val displayName: String) {
    FABRIC("Fabric"),
    FORGE("Forge"),
}

/**
 * A fully resolved request to create a project. The dialog produces one of these and hands it to
 * [ProjectScaffolder].
 *
 * @param name project (and root directory) name.
 * @param template the chosen template.
 * @param parentDir directory in which the project root folder will be created.
 * @param packageName java/kotlin package (Minecraft / Android).
 * @param author author name written into manifests.
 * @param modLoader Fabric or Forge (Minecraft only).
 * @param modId Minecraft mod id (lowercase, used as the resources/config id).
 * @param modDescription human readable mod description.
 * @param modVersion mod artifact version.
 * @param minecraftVersion target Minecraft version (e.g. "1.21.1").
 * @param jdkVersion Java language/toolchain version (e.g. "17", "21").
 */
data class ProjectConfig(
    val name: String,
    val template: ProjectTemplate,
    val parentDir: java.io.File,
    val packageName: String = "",
    val author: String = "",
    val modLoader: ModLoader? = null,
    val modId: String = "",
    val modDescription: String = "",
    val modVersion: String = "1.0.0",
    val minecraftVersion: String = "",
    val jdkVersion: String = "21",
    val initGit: Boolean = false,
) {
    /** Sanitised mod id derived from [modId] (falls back to the project name). */
    fun resolvedModId(): String {
        val base = modId.ifBlank { name }
        return base.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifBlank { "modid" }
    }

    /** Package as a relative path (e.g. com.example -> com/example). */
    fun packagePath(): String = packageName.replace('.', '/')
}
