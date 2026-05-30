package dev.prism.gradle.internal

import dev.prism.gradle.dsl.RepositoryEntry
import org.gradle.api.Project

object RepositorySetup {
    fun configure(project: Project, extraRepositories: List<RepositoryEntry> = emptyList()) {
        project.repositories.apply {
            mavenCentral()
            maven { repo ->
                repo.name = "Sponge"
                repo.setUrl("https://repo.spongepowered.org/repository/maven-public/")
                repo.content { it.includeGroupAndSubgroups("org.spongepowered") }
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
                if (entry.artifactPattern != null) {
                    ivy { repo ->
                        repo.name = entry.name
                        repo.setUrl(entry.url)
                        repo.patternLayout { layout -> layout.artifact(entry.artifactPattern) }
                        repo.metadataSources { it.artifact() }
                    }
                } else {
                    maven { repo ->
                        repo.name = entry.name
                        repo.setUrl(entry.url)
                    }
                }
            }
        }
    }
}
