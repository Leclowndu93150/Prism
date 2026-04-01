package dev.prism.gradle.internal

import org.gradle.api.Project

object RepositorySetup {
    fun configure(project: Project) {
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
        }
    }
}
