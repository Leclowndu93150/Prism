package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object NeoForgeConfigurator {
    fun configure(
        loaderProject: Project,
        commonProject: Project,
        versionConfig: VersionConfiguration,
        neoForgeConfig: NeoForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
    ) {
        loaderProject.pluginManager.apply("java-library")
        loaderProject.pluginManager.apply("net.neoforged.moddev")

        RepositorySetup.configure(loaderProject, extraRepositories)

        loaderProject.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(
                JavaLanguageVersion.of(versionConfig.resolvedJavaVersion)
            )
            java.withSourcesJar()
        }

        loaderProject.extensions.configure(NeoForgeExtension::class.java) { neoForge ->
            neoForge.version = neoForgeConfig.loaderVersion

            if (versionConfig.parchmentMinecraftVersion != null) {
                neoForge.parchment { parchment ->
                    parchment.minecraftVersion.set(versionConfig.parchmentMinecraftVersion)
                    parchment.mappingsVersion.set(versionConfig.parchmentMappingsVersion)
                }
            }

            val at = commonProject.file("src/main/resources/META-INF/accesstransformer.cfg")
            if (at.exists()) {
                neoForge.setAccessTransformers(at.absolutePath)
            }

            neoForge.runs { runs ->
                runs.configureEach { run ->
                    run.systemProperty("neoforge.enabledGameTestNamespaces", metadata.modId)
                    run.ideName.set("NeoForge ${run.name.replaceFirstChar { it.uppercase() }} (${versionConfig.minecraftVersion})")
                }
                runs.create("client") { run ->
                    run.client()
                    run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/neoforge/client"))
                }
                runs.create("server") { run ->
                    run.server()
                    run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/neoforge/server"))
                }
                runs.create("data") { run ->
                    run.data()
                    run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/neoforge/data"))
                    run.programArguments.addAll(
                        "--mod", metadata.modId,
                        "--all",
                        "--output", loaderProject.file("src/generated/resources/").absolutePath,
                        "--existing", loaderProject.file("src/main/resources/").absolutePath
                    )
                }
            }

            neoForge.mods { mods ->
                mods.create(metadata.modId) { mod ->
                    mod.sourceSet(
                        loaderProject.extensions.getByType(JavaPluginExtension::class.java)
                            .sourceSets.getByName("main")
                    )
                }
            }
        }

        loaderProject.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.sourceSets.getByName("main").resources.srcDir("src/generated/resources")
        }

        JarNaming.configure(loaderProject, metadata, versionConfig, neoForgeConfig)
        CommonLoaderWiring.wire(loaderProject, commonProject, metadata)
        TemplateExpansion.configure(loaderProject, versionConfig, metadata)
    }
}
