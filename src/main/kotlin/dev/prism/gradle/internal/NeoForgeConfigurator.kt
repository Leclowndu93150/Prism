package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.NeoForgeConfiguration
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import dev.prism.gradle.internal.accesswidener.AccessWidenerSupport
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object NeoForgeConfigurator {

    private fun hasSplitDataRuns(mcVersion: String): Boolean {
        val parts = mcVersion.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        if (major > 1) return true
        if (minor > 21) return true
        if (minor == 21 && patch >= 4) return true
        return false
    }

    fun configure(
        loaderProject: Project,
        commonProject: Project,
        versionConfig: VersionConfiguration,
        neoForgeConfig: NeoForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        loaderProject.pluginManager.apply("java-library")
        loaderProject.pluginManager.apply("net.neoforged.moddev")
        neoForgeConfig.extraConfigurations.forEach { loaderProject.configurations.maybeCreate(it) }

        RepositorySetup.configure(loaderProject, extraRepositories)

        loaderProject.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(
                JavaLanguageVersion.of(versionConfig.resolvedJavaVersion)
            )
            java.withSourcesJar()
        }

        loaderProject.extensions.configure(NeoForgeExtension::class.java) { neoForge ->
            neoForge.version = neoForgeConfig.loaderVersion

            if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
                neoForge.parchment { parchment ->
                    parchment.minecraftVersion.set(versionConfig.parchmentMinecraftVersion)
                    parchment.mappingsVersion.set(versionConfig.parchmentMappingsVersion)
                }
            }

            val commonAt = commonProject.file("src/main/resources/META-INF/accesstransformer.cfg")
            val loaderAt = loaderProject.file("src/main/resources/META-INF/accesstransformer.cfg")
            var hasExplicitAt = false

            if (commonAt.exists()) {
                neoForge.accessTransformers.from(commonAt.absolutePath)
                hasExplicitAt = true
            }
            if (loaderAt.exists()) {
                neoForge.accessTransformers.from(loaderAt.absolutePath)
                hasExplicitAt = true
            }

            if (!hasExplicitAt) {
                val awFile = AccessWidenerSupport.resolveAccessWidener(
                    loaderProject, commonProject, versionConfig.unifiedAccessWidener, metadata.modId
                )
                if (awFile != null) {
                    val generatedAt = AccessWidenerSupport.generateAccessTransformer(loaderProject, awFile, "neoforge")
                    neoForge.accessTransformers.from(generatedAt.absolutePath)
                }
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

                if (hasSplitDataRuns(versionConfig.minecraftVersion)) {
                    runs.create("clientData") { run ->
                        run.clientData()
                        run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/neoforge/clientData"))
                        run.programArguments.addAll(
                            "--mod", metadata.modId,
                            "--all",
                            "--output", loaderProject.file("src/generated/resources/").absolutePath,
                            "--existing", loaderProject.file("src/main/resources/").absolutePath
                        )
                    }
                    runs.create("serverData") { run ->
                        run.serverData()
                        run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/neoforge/serverData"))
                        run.programArguments.addAll(
                            "--mod", metadata.modId,
                            "--all",
                            "--output", loaderProject.file("src/generated/resources/").absolutePath,
                            "--existing", loaderProject.file("src/main/resources/").absolutePath
                        )
                    }
                } else {
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

        loaderProject.extensions.configure(NeoForgeExtension::class.java) { neoForge ->
            RunApplicator.applyMdgRuns(loaderProject, neoForgeConfig.extraRuns, versionConfig, "neoforge", neoForge.runs)
        }

        val mixinConfigs = MixinAutoDetect.resolveMixinConfigs(loaderProject, commonProject, neoForgeConfig.mixinOptions)
        MixinAutoDetect.injectNeoForgeMixins(loaderProject, mixinConfigs)

        loaderProject.extensions.configure(NeoForgeExtension::class.java) { neoForge ->
            for (action in neoForgeConfig.rawNeoForgeActions) {
                action.execute(neoForge)
            }
        }

        JarNaming.configure(loaderProject, metadata, versionConfig, neoForgeConfig)
        CommonLoaderWiring.wire(loaderProject, commonProject, metadata, sharedProject)
        TemplateExpansion.configure(loaderProject, versionConfig, metadata)

        for (action in neoForgeConfig.rawProjectActions) {
            action.execute(loaderProject)
        }
    }

    fun configureSingle(
        project: Project,
        versionConfig: VersionConfiguration,
        neoForgeConfig: NeoForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("net.neoforged.moddev")
        neoForgeConfig.extraConfigurations.forEach { project.configurations.maybeCreate(it) }

        RepositorySetup.configure(project, extraRepositories)

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(versionConfig.resolvedJavaVersion))
            java.withSourcesJar()
        }

        project.extensions.configure(NeoForgeExtension::class.java) { neoForge ->
            neoForge.version = neoForgeConfig.loaderVersion

            if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
                neoForge.parchment { p ->
                    p.minecraftVersion.set(versionConfig.parchmentMinecraftVersion)
                    p.mappingsVersion.set(versionConfig.parchmentMappingsVersion)
                }
            }

            val at = project.file("src/main/resources/META-INF/accesstransformer.cfg")
            if (at.exists()) {
                neoForge.accessTransformers.from(at.absolutePath)
            } else {
                val awFile = AccessWidenerSupport.resolveAccessWidener(
                    project, null, versionConfig.unifiedAccessWidener, metadata.modId
                )
                if (awFile != null) {
                    val generatedAt = AccessWidenerSupport.generateAccessTransformer(project, awFile, "neoforge")
                    neoForge.accessTransformers.from(generatedAt.absolutePath)
                }
            }

            neoForge.runs { runs ->
                runs.configureEach { run ->
                    run.systemProperty("neoforge.enabledGameTestNamespaces", metadata.modId)
                    run.ideName.set("NeoForge ${run.name.replaceFirstChar { it.uppercase() }} (${versionConfig.minecraftVersion})")
                }
                runs.create("client") { run ->
                    run.client()
                    run.gameDirectory.set(project.file("runs/client"))
                }
                runs.create("server") { run ->
                    run.server()
                    run.gameDirectory.set(project.file("runs/server"))
                }
                if (hasSplitDataRuns(versionConfig.minecraftVersion)) {
                    runs.create("clientData") { run ->
                        run.clientData()
                        run.gameDirectory.set(project.file("runs/clientData"))
                        run.programArguments.addAll("--mod", metadata.modId, "--all",
                            "--output", project.file("src/generated/resources/").absolutePath,
                            "--existing", project.file("src/main/resources/").absolutePath)
                    }
                    runs.create("serverData") { run ->
                        run.serverData()
                        run.gameDirectory.set(project.file("runs/serverData"))
                        run.programArguments.addAll("--mod", metadata.modId, "--all",
                            "--output", project.file("src/generated/resources/").absolutePath,
                            "--existing", project.file("src/main/resources/").absolutePath)
                    }
                } else {
                    runs.create("data") { run ->
                        run.data()
                        run.gameDirectory.set(project.file("runs/data"))
                        run.programArguments.addAll("--mod", metadata.modId, "--all",
                            "--output", project.file("src/generated/resources/").absolutePath,
                            "--existing", project.file("src/main/resources/").absolutePath)
                    }
                }
            }

            neoForge.mods { mods ->
                mods.create(metadata.modId) { mod ->
                    mod.sourceSet(project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main"))
                }
            }

            RunApplicator.applyMdgRuns(project, neoForgeConfig.extraRuns, versionConfig, "neoforge", neoForge.runs)
            for (action in neoForgeConfig.rawNeoForgeActions) {
                action.execute(neoForge)
            }
        }

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.sourceSets.getByName("main").resources.srcDir("src/generated/resources")
        }

        val mixinConfigs = MixinAutoDetect.resolveMixinConfigs(project, null, neoForgeConfig.mixinOptions)
        MixinAutoDetect.injectNeoForgeMixins(project, mixinConfigs)

        JarNaming.configure(project, metadata, versionConfig, neoForgeConfig)
        TemplateExpansion.configure(project, versionConfig, metadata)

        for (action in neoForgeConfig.rawProjectActions) {
            action.execute(project)
        }
    }
}
