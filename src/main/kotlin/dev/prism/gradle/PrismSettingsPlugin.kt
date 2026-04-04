package dev.prism.gradle

import dev.prism.gradle.dsl.PrismSettingsExtension
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

class PrismSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val extension = PrismSettingsExtension(settings)
        settings.extensions.add("prism", extension)

        settings.pluginManagement { pm ->
            pm.plugins {
                it.id("org.jetbrains.kotlin.jvm").version("2.1.20").apply(false)
            }
            pm.repositories { repos ->
                repos.gradlePluginPortal()
                repos.mavenCentral()
                repos.exclusiveContent { exclusive ->
                    exclusive.forRepository {
                        repos.maven { repo ->
                            repo.name = "Fabric"
                            repo.setUrl("https://maven.fabricmc.net/")
                        }
                    }
                    exclusive.filter { it.includeGroupAndSubgroups("net.fabricmc") }
                }
                repos.exclusiveContent { exclusive ->
                    exclusive.forRepository {
                        repos.maven { repo ->
                            repo.name = "Sponge"
                            repo.setUrl("https://repo.spongepowered.org/repository/maven-public/")
                        }
                    }
                    exclusive.filter { it.includeGroupAndSubgroups("org.spongepowered") }
                }
                repos.maven { repo ->
                    repo.name = "NeoForge"
                    repo.setUrl("https://maven.neoforged.net/releases")
                }
                repos.maven { repo ->
                    repo.name = "Minecraft"
                    repo.setUrl("https://libraries.minecraft.net/")
                }
                repos.maven { repo ->
                    repo.name = "GTNH"
                    repo.setUrl("https://nexus.gtnewhorizons.com/repository/public/")
                }
            }
        }
    }
}
