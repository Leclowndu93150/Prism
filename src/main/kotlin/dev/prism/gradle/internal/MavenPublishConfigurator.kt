package dev.prism.gradle.internal

import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MavenRepoConfig
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

object MavenPublishConfigurator {

    fun configure(
        project: Project,
        versionConfig: VersionConfiguration,
        loaderConfig: LoaderConfiguration,
        metadata: MetadataExtension,
        mavenRepos: List<MavenRepoConfig>,
    ) {
        if (mavenRepos.isEmpty()) return

        project.pluginManager.apply("maven-publish")
        project.extensions.configure(PublishingExtension::class.java) { publishing ->
            publishing.publications { publications ->
                publications.create("prism", MavenPublication::class.java) { pub ->
                    pub.groupId = metadata.group.ifEmpty { project.rootProject.group.toString() }
                    pub.artifactId = "${metadata.modId}-${versionConfig.minecraftVersion}-${loaderConfig.loaderDisplayName}".lowercase()
                    pub.version = metadata.version.ifEmpty { project.rootProject.version.toString() }

                    pub.from(project.components.findByName("java"))
                }
            }

            addRepositories(publishing, mavenRepos)
        }
        hideMavenPublishLeafTasks(project)
    }

    private fun hideMavenPublishLeafTasks(project: Project) {
        project.tasks.matching { it.name == "publish" || it.name.startsWith("publishPrism") || it.name.startsWith("publishAllPublications") }
            .configureEach { it.group = null }

        if (!hasTask(project, AGGREGATE_NAME)) {
            project.tasks.register(AGGREGATE_NAME) { alias ->
                alias.group = "publishing"
                alias.description = "Publishes this project to every configured Maven repository (Prism alias)"
            }
        }
        if (hasTask(project, "publish")) {
            project.tasks.named(AGGREGATE_NAME).configure { it.dependsOn(project.tasks.named("publish")) }
        }
    }

    fun configureCommon(
        commonProject: Project,
        versionConfig: VersionConfiguration,
        metadata: MetadataExtension,
        mavenRepos: List<MavenRepoConfig>,
    ) {
        if (mavenRepos.isEmpty()) return

        commonProject.pluginManager.apply("maven-publish")
        commonProject.extensions.configure(PublishingExtension::class.java) { publishing ->
            publishing.publications { publications ->
                publications.create("prismCommon", MavenPublication::class.java) { pub ->
                    pub.groupId = metadata.group.ifEmpty { commonProject.rootProject.group.toString() }
                    pub.artifactId = "${metadata.modId}-${versionConfig.minecraftVersion}-common"
                    pub.version = metadata.version.ifEmpty { commonProject.rootProject.version.toString() }

                    pub.from(commonProject.components.findByName("java"))
                }
            }

            addRepositories(publishing, mavenRepos)
        }
        hideMavenPublishLeafTasks(commonProject)
    }

    private fun addRepositories(publishing: PublishingExtension, mavenRepos: List<MavenRepoConfig>) {
        publishing.repositories { repos ->
            for (mavenRepo in mavenRepos) {
                if (mavenRepo.isLocal) {
                    repos.mavenLocal()
                } else {
                    repos.maven { repo ->
                        repo.name = mavenRepo.name
                        repo.setUrl(mavenRepo.url!!)

                        if (mavenRepo.username != null && mavenRepo.password != null) {
                            repo.credentials { creds ->
                                creds.username = if (mavenRepo.usernameIsEnv) {
                                    System.getenv(mavenRepo.username)
                                } else {
                                    mavenRepo.username
                                }
                                creds.password = if (mavenRepo.passwordIsEnv) {
                                    System.getenv(mavenRepo.password)
                                } else {
                                    mavenRepo.password
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun createAggregateTask(project: Project, excludeChildren: Set<String> = emptySet()) {
        ensureAggregateTaskRegistered(project)
        project.childProjects.forEach { (name, child) ->
            if (name !in excludeChildren) {
                linkAggregateToChild(project, child)
            }
        }
    }

    fun createVersionAggregate(versionProject: Project) {
        ensureAggregateTaskRegistered(versionProject)
        versionProject.childProjects.values.forEach { child ->
            linkAggregateToChild(versionProject, child)
        }
    }

    fun linkAggregateToChild(parent: Project, child: Project) {
        ensureAggregateTaskRegistered(parent)
        if (hasTask(child, AGGREGATE_NAME)) {
            parent.tasks.named(AGGREGATE_NAME).configure { it.dependsOn(child.tasks.named(AGGREGATE_NAME)) }
        }
    }

    private fun ensureAggregateTaskRegistered(project: Project) {
        if (hasTask(project, AGGREGATE_NAME)) return
        project.tasks.register(AGGREGATE_NAME) { task ->
            task.group = "publishing"
            task.description = "Publishes all mod JARs to configured Maven repositories (Prism aggregate)"
        }
    }

    private fun hasTask(project: Project, taskName: String): Boolean = taskName in project.tasks.names

    private const val AGGREGATE_NAME = "prismPublishMaven"
}
