package dev.prism.gradle.internal

import dev.prism.gradle.dsl.ForgeConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import dev.prism.gradle.internal.accesswidener.AccessWidenerSupport
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object ForgeConfigurator {
    fun configure(
        loaderProject: Project,
        commonProject: Project,
        versionConfig: VersionConfiguration,
        forgeConfig: ForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        loaderProject.pluginManager.apply("java-library")
        loaderProject.pluginManager.apply("net.neoforged.moddev.legacyforge")

        RepositorySetup.configure(loaderProject, extraRepositories)

        loaderProject.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(
                JavaLanguageVersion.of(versionConfig.resolvedJavaVersion)
            )
            java.withSourcesJar()
        }

        loaderProject.extensions.configure(LegacyForgeExtension::class.java) { legacyForge ->
            legacyForge.version = "${versionConfig.minecraftVersion}-${forgeConfig.loaderVersion}"

            legacyForge.validateAccessTransformers.set(true)

            if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
                legacyForge.parchment { parchment ->
                    parchment.minecraftVersion.set(versionConfig.parchmentMinecraftVersion)
                    parchment.mappingsVersion.set(versionConfig.parchmentMappingsVersion)
                }
            }

            val commonAt = commonProject.file("src/main/resources/META-INF/accesstransformer.cfg")
            val loaderAt = loaderProject.file("src/main/resources/META-INF/accesstransformer.cfg")
            var hasExplicitAt = false

            if (commonAt.exists()) {
                legacyForge.accessTransformers.from(commonAt.absolutePath)
                hasExplicitAt = true
            }
            if (loaderAt.exists()) {
                legacyForge.accessTransformers.from(loaderAt.absolutePath)
                hasExplicitAt = true
            }

            if (!hasExplicitAt) {
                val awFile = AccessWidenerSupport.resolveAccessWidener(
                    loaderProject, commonProject, versionConfig.unifiedAccessWidener, metadata.modId
                )
                if (awFile != null) {
                    val generatedAt = AccessWidenerSupport.generateAccessTransformer(loaderProject, awFile, "forge")
                    legacyForge.accessTransformers.from(generatedAt.absolutePath)
                }
            }

            legacyForge.runs { runs ->
                runs.configureEach { run ->
                    run.systemProperty("forge.logging.markers", "REGISTRIES")
                    run.systemProperty("forge.logging.console.level", "debug")
                    run.ideName.set("Forge ${run.name.replaceFirstChar { it.uppercase() }} (${versionConfig.minecraftVersion})")
                }
                runs.create("client") { run ->
                    run.client()
                    run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/forge/client"))
                }
                runs.create("server") { run ->
                    run.server()
                    run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/forge/server"))
                }
                runs.create("data") { run ->
                    run.data()
                    run.gameDirectory.set(loaderProject.file("runs/${versionConfig.minecraftVersion}/forge/data"))
                    run.programArguments.addAll(
                        "--mod", metadata.modId,
                        "--all",
                        "--output", loaderProject.file("src/generated/resources/").absolutePath,
                        "--existing", loaderProject.file("src/main/resources/").absolutePath
                    )
                }
            }

            legacyForge.mods { mods ->
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

        configureMixins(loaderProject, metadata, commonProject)

        loaderProject.extensions.configure(LegacyForgeExtension::class.java) { legacyForge ->
            RunApplicator.applyMdgRuns(loaderProject, forgeConfig.extraRuns, versionConfig, "forge", legacyForge.runs)
        }

        JarNaming.configure(loaderProject, metadata, versionConfig, forgeConfig)
        CommonLoaderWiring.wire(loaderProject, commonProject, metadata, sharedProject)
        TemplateExpansion.configure(loaderProject, versionConfig, metadata)
    }

    fun configureSingle(
        project: Project,
        versionConfig: VersionConfiguration,
        forgeConfig: ForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("net.neoforged.moddev.legacyforge")

        RepositorySetup.configure(project, extraRepositories)

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(versionConfig.resolvedJavaVersion))
            java.withSourcesJar()
        }

        project.extensions.configure(LegacyForgeExtension::class.java) { legacyForge ->
            legacyForge.version = "${versionConfig.minecraftVersion}-${forgeConfig.loaderVersion}"
            legacyForge.validateAccessTransformers.set(true)

            if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
                legacyForge.parchment { p ->
                    p.minecraftVersion.set(versionConfig.parchmentMinecraftVersion)
                    p.mappingsVersion.set(versionConfig.parchmentMappingsVersion)
                }
            }

            val at = project.file("src/main/resources/META-INF/accesstransformer.cfg")
            if (at.exists()) {
                legacyForge.accessTransformers.from(at.absolutePath)
            } else {
                val awFile = AccessWidenerSupport.resolveAccessWidener(
                    project, null, versionConfig.unifiedAccessWidener, metadata.modId
                )
                if (awFile != null) {
                    val generatedAt = AccessWidenerSupport.generateAccessTransformer(project, awFile, "forge")
                    legacyForge.accessTransformers.from(generatedAt.absolutePath)
                }
            }

            legacyForge.runs { runs ->
                runs.configureEach { run ->
                    run.systemProperty("forge.logging.markers", "REGISTRIES")
                    run.systemProperty("forge.logging.console.level", "debug")
                    run.ideName.set("Forge ${run.name.replaceFirstChar { it.uppercase() }} (${versionConfig.minecraftVersion})")
                }
                runs.create("client") { run ->
                    run.client()
                    run.gameDirectory.set(project.file("runs/client"))
                }
                runs.create("server") { run ->
                    run.server()
                    run.gameDirectory.set(project.file("runs/server"))
                }
                runs.create("data") { run ->
                    run.data()
                    run.gameDirectory.set(project.file("runs/data"))
                    run.programArguments.addAll("--mod", metadata.modId, "--all",
                        "--output", project.file("src/generated/resources/").absolutePath,
                        "--existing", project.file("src/main/resources/").absolutePath)
                }
            }

            legacyForge.mods { mods ->
                mods.create(metadata.modId) { mod ->
                    mod.sourceSet(project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main"))
                }
            }

            RunApplicator.applyMdgRuns(project, forgeConfig.extraRuns, versionConfig, "forge", legacyForge.runs)
        }

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.sourceSets.getByName("main").resources.srcDir("src/generated/resources")
        }

        configureMixins(project, metadata)

        JarNaming.configure(project, metadata, versionConfig, forgeConfig)
        TemplateExpansion.configure(project, versionConfig, metadata)
    }

    private fun configureMixins(project: Project, metadata: MetadataExtension, commonProject: Project? = null) {
        val mixinConfigs = MixinAutoDetect.findMixinConfigs(project).toMutableList()
        if (commonProject != null) {
            mixinConfigs.addAll(MixinAutoDetect.findMixinConfigs(commonProject))
        }
        if (mixinConfigs.isEmpty()) return

        project.dependencies.add("annotationProcessor", "org.spongepowered:mixin:0.8.5:processor")

        val mixinExt = project.extensions.findByName("mixin") ?: return
        val mainSourceSet = project.extensions.getByType(JavaPluginExtension::class.java)
            .sourceSets.getByName("main")

        for (config in mixinConfigs) {
            try {
                mixinExt.javaClass.getMethod("config", String::class.java).invoke(mixinExt, config)
                project.logger.lifecycle("Prism: Registered mixin config '$config' for Forge")
            } catch (_: Exception) {}
        }

        try {
            mixinExt.javaClass.getMethod("add", org.gradle.api.tasks.SourceSet::class.java, String::class.java)
                .invoke(mixinExt, mainSourceSet, "${metadata.modId}.refmap.json")
        } catch (_: Exception) {}
    }
}
