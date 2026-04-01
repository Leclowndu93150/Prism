package dev.prism.gradle.internal

import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object CommonConfigurator {
    fun configure(
        commonProject: Project,
        versionConfig: VersionConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
    ) {
        commonProject.pluginManager.apply("java-library")

        RepositorySetup.configure(commonProject, extraRepositories)

        commonProject.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(
                JavaLanguageVersion.of(versionConfig.resolvedJavaVersion)
            )
            java.withSourcesJar()
        }

        if (versionConfig.neoFormVersion != null) {
            applyWithNeoForm(commonProject, versionConfig, versionConfig.neoFormVersion!!)
        } else {
            val resolved = NeoFormVersionResolver.resolve(versionConfig.minecraftVersion, commonProject)
            if (resolved.useMcp) {
                applyWithMcp(commonProject, versionConfig, resolved.version)
            } else {
                applyWithNeoForm(commonProject, versionConfig, resolved.version)
            }
        }

        commonProject.dependencies.add("compileOnly", "org.spongepowered:mixin:0.8.5")
        commonProject.dependencies.add("compileOnly", "io.github.llamalad7:mixinextras-common:0.3.5")
        commonProject.dependencies.add("annotationProcessor", "io.github.llamalad7:mixinextras-common:0.3.5")

        val commonJava = commonProject.configurations.create("commonJava") { cfg ->
            cfg.isCanBeResolved = false
            cfg.isCanBeConsumed = true
        }

        val commonResources = commonProject.configurations.create("commonResources") { cfg ->
            cfg.isCanBeResolved = false
            cfg.isCanBeConsumed = true
        }

        commonProject.afterEvaluate { proj ->
            val javaExt = proj.extensions.getByType(JavaPluginExtension::class.java)
            val mainSourceSet = javaExt.sourceSets.getByName("main")
            proj.artifacts.add("commonJava", mainSourceSet.java.sourceDirectories.singleFile)
            proj.artifacts.add("commonResources", mainSourceSet.resources.sourceDirectories.singleFile)
        }

        val group = metadata.group.ifEmpty { commonProject.rootProject.group.toString() }
        listOf("apiElements", "runtimeElements", "sourcesElements", "javadocElements").forEach { variant ->
            commonProject.configurations.findByName(variant)?.outgoing { outgoing ->
                outgoing.capability("$group:${metadata.modId}:${commonProject.version}")
            }
        }

        TemplateExpansion.configure(commonProject, versionConfig, metadata)
    }

    private fun applyWithNeoForm(project: Project, versionConfig: VersionConfiguration, neoFormVersion: String) {
        project.pluginManager.apply("net.neoforged.moddev")

        val neoForgeExt = project.extensions.getByName("neoForge")
        neoForgeExt.javaClass.getMethod("setNeoFormVersion", String::class.java)
            .invoke(neoForgeExt, neoFormVersion)

        if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
            val parchmentMethod = neoForgeExt.javaClass.getMethod("parchment", org.gradle.api.Action::class.java)
            parchmentMethod.invoke(neoForgeExt, org.gradle.api.Action<Any> { parchment ->
                parchment.javaClass.getMethod("getMinecraftVersion").invoke(parchment).let { prop ->
                    prop.javaClass.getMethod("set", Any::class.java).invoke(prop, versionConfig.parchmentMinecraftVersion)
                }
                parchment.javaClass.getMethod("getMappingsVersion").invoke(parchment).let { prop ->
                    prop.javaClass.getMethod("set", Any::class.java).invoke(prop, versionConfig.parchmentMappingsVersion)
                }
            })
        }

        val at = project.file("src/main/resources/META-INF/accesstransformer.cfg")
        if (at.exists()) {
            val atCollection = neoForgeExt.javaClass.getMethod("getAccessTransformers").invoke(neoForgeExt)
            atCollection.javaClass.getMethod("from", Array<Any>::class.java)
                .invoke(atCollection, arrayOf<Any>(at.absolutePath))
        }
    }

    private fun applyWithMcp(project: Project, versionConfig: VersionConfiguration, mcpVersion: String) {
        project.pluginManager.apply("net.neoforged.moddev.legacyforge")

        val legacyExt = project.extensions.getByName("legacyForge")
        legacyExt.javaClass.getMethod("setMcpVersion", String::class.java)
            .invoke(legacyExt, mcpVersion)

        if (versionConfig.parchmentMinecraftVersion != null && versionConfig.parchmentMappingsVersion != null) {
            val parchmentMethod = legacyExt.javaClass.getMethod("parchment", org.gradle.api.Action::class.java)
            parchmentMethod.invoke(legacyExt, org.gradle.api.Action<Any> { parchment ->
                parchment.javaClass.getMethod("getMinecraftVersion").invoke(parchment).let { prop ->
                    prop.javaClass.getMethod("set", Any::class.java).invoke(prop, versionConfig.parchmentMinecraftVersion)
                }
                parchment.javaClass.getMethod("getMappingsVersion").invoke(parchment).let { prop ->
                    prop.javaClass.getMethod("set", Any::class.java).invoke(prop, versionConfig.parchmentMappingsVersion)
                }
            })
        }

        val at = project.file("src/main/resources/META-INF/accesstransformer.cfg")
        if (at.exists()) {
            val atCollection = legacyExt.javaClass.getMethod("getAccessTransformers").invoke(legacyExt)
            atCollection.javaClass.getMethod("from", Array<Any>::class.java)
                .invoke(atCollection, arrayOf<Any>(at.absolutePath))
        }
    }
}
