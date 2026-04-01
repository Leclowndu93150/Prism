package dev.prism.gradle.internal

import dev.prism.gradle.dsl.RepositoryEntry
import org.gradle.api.Project

object RepositorySetup {
    fun configure(project: Project, extraRepositories: List<RepositoryEntry> = emptyList()) {
        project.repositories.apply {
            mavenCentral()
            exclusiveContent { exclusive ->
                exclusive.forRepository {
                    maven { repo ->
                        repo.name = "Sponge"
                        repo.setUrl("https://repo.spongepowered.org/repository/maven-public")
                    }
                }
                exclusive.filter { it.includeGroupAndSubgroups("org.spongepowered") }
            }
            maven { repo ->
                repo.name = "NeoForge"
                repo.setUrl("https://maven.neoforged.net/releases")
            }
            maven { repo ->
                repo.name = "Fabric"
                repo.setUrl("https://maven.fabricmc.net/")
            }
            maven { repo ->
                repo.name = "Minecraft"
                repo.setUrl("https://libraries.minecraft.net/")
            }
            maven { repo ->
                repo.name = "ParchmentMC"
                repo.setUrl("https://maven.parchmentmc.org/")
            }

            for (entry in extraRepositories) {
                maven { repo ->
                    repo.name = entry.name
                    repo.setUrl(entry.url)
                }
            }
        }
    }
}
