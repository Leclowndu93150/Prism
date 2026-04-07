package dev.prism.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.provider.Provider

open class PublishingConfiguration {
    var changelog: String? = null
    var changelogFile: String? = null
    var type: ReleaseType = ReleaseType.STABLE
    var displayName: String? = null
    var artifactTaskName: String? = null
    var artifactPath: String? = null

    val STABLE get() = ReleaseType.STABLE
    val BETA get() = ReleaseType.BETA
    val ALPHA get() = ReleaseType.ALPHA

    internal var curseforgeConfig: CurseForgeConfig? = null
    internal var modrinthConfig: ModrinthConfig? = null
    internal val pubDeps = PublishingDepsBlock()
    internal val mavenRepos = mutableListOf<MavenRepoConfig>()
    var publishCommonJar: Boolean = false

    val isConfigured: Boolean
        get() = curseforgeConfig != null || modrinthConfig != null

    val hasMaven: Boolean
        get() = mavenRepos.isNotEmpty()

    fun curseforge(action: Action<CurseForgeConfig>) {
        if (curseforgeConfig == null) curseforgeConfig = CurseForgeConfig()
        action.execute(curseforgeConfig!!)
    }

    fun modrinth(action: Action<ModrinthConfig>) {
        if (modrinthConfig == null) modrinthConfig = ModrinthConfig()
        action.execute(modrinthConfig!!)
    }

    fun dependencies(action: Action<PublishingDepsBlock>) {
        action.execute(pubDeps)
    }

    fun artifactTask(name: String) {
        artifactTaskName = name
    }

    fun artifactFile(path: String) {
        artifactPath = path
    }

    fun mavenLocal() {
        mavenRepos.add(MavenRepoConfig("MavenLocal", null, null, null, isLocal = true))
    }

    fun maven(action: Action<MavenRepoConfig>) {
        val config = MavenRepoConfig()
        action.execute(config)
        mavenRepos.add(config)
    }

    fun githubPackages(owner: String, repo: String) {
        mavenRepos.add(MavenRepoConfig(
            name = "GitHubPackages",
            url = "https://maven.pkg.github.com/$owner/$repo",
            username = "GITHUB_ACTOR",
            password = "GITHUB_TOKEN",
            usernameIsEnv = true,
            passwordIsEnv = true,
        ))
    }
}

enum class ReleaseType {
    STABLE, BETA, ALPHA
}

open class CurseForgeConfig {
    var accessToken: Provider<String>? = null
    var projectId: String = ""
}

open class ModrinthConfig {
    var accessToken: Provider<String>? = null
    var projectId: String = ""
}

open class MavenRepoConfig(
    var name: String = "",
    var url: String? = null,
    var username: String? = null,
    var password: String? = null,
    var isLocal: Boolean = false,
    var usernameIsEnv: Boolean = false,
    var passwordIsEnv: Boolean = false,
) {
    fun credentials(user: String, pass: String) {
        username = user
        password = pass
    }

    fun credentialsFromEnv(userEnv: String, passEnv: String) {
        username = userEnv
        password = passEnv
        usernameIsEnv = true
        passwordIsEnv = true
    }
}
