package dev.prism.gradle.internal

import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MavenRepoConfig
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project
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

        project.afterEvaluate { proj ->
            proj.extensions.configure(PublishingExtension::class.java) { publishing ->
                publishing.publications { publications ->
                    publications.create("prism", MavenPublication::class.java) { pub ->
                        pub.groupId = metadata.group.ifEmpty { proj.rootProject.group.toString() }
                        pub.artifactId = "${metadata.modId}-${versionConfig.minecraftVersion}-${loaderConfig.loaderDisplayName}".lowercase()
                        pub.version = metadata.version.ifEmpty { proj.rootProject.version.toString() }

                        pub.from(proj.components.findByName("java"))
                    }
                }

                addRepositories(publishing, mavenRepos)
            }
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

        commonProject.afterEvaluate { proj ->
            proj.extensions.configure(PublishingExtension::class.java) { publishing ->
                publishing.publications { publications ->
                    publications.create("prismCommon", MavenPublication::class.java) { pub ->
                        pub.groupId = metadata.group.ifEmpty { proj.rootProject.group.toString() }
                        pub.artifactId = "${metadata.modId}-${versionConfig.minecraftVersion}-common"
                        pub.version = metadata.version.ifEmpty { proj.rootProject.version.toString() }

                        pub.from(proj.components.findByName("java"))
                    }
                }

                addRepositories(publishing, mavenRepos)
            }
        }
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
        if (project.tasks.findByName("publishAllMaven") != null) return

        project.tasks.register("publishAllMaven") { task ->
            task.group = "publishing"
            task.description = "Publishes all mod JARs to configured Maven repositories"
        }

        project.childProjects.forEach { (name, child) ->
            if (name !in excludeChildren) {
                wireMavenTasks(project, child)
            }
        }
    }

    private fun wireMavenTasks(aggregateProject: Project, child: Project) {
        child.tasks.matching { it.name == "publish" }.configureEach { publishTask ->
            aggregateProject.tasks.named("publishAllMaven").configure { it.dependsOn(publishTask) }
        }
        child.childProjects.values.forEach { grandchild ->
            wireMavenTasks(aggregateProject, grandchild)
        }
    }
}
