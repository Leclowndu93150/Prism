package dev.prism.gradle.internal

import dev.prism.gradle.dsl.LegacyForgeConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

object LegacyForgeConfigurator {

    fun configure(
        loaderProject: Project,
        commonProject: Project,
        versionConfig: VersionConfiguration,
        legacyConfig: LegacyForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        applyRfg(loaderProject, versionConfig, legacyConfig, metadata, extraRepositories)
        CommonLoaderWiring.wire(loaderProject, commonProject, metadata, sharedProject)
        TemplateExpansion.configure(loaderProject, versionConfig, metadata)
    }

    fun configureSingle(
        project: Project,
        versionConfig: VersionConfiguration,
        legacyConfig: LegacyForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        applyRfg(project, versionConfig, legacyConfig, metadata, extraRepositories)
        TemplateExpansion.configure(project, versionConfig, metadata)
    }

    private fun applyRfg(
        project: Project,
        versionConfig: VersionConfiguration,
        legacyConfig: LegacyForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry>,
    ) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("com.gtnewhorizons.retrofuturagradle")
        legacyConfig.extraConfigurations.forEach { project.configurations.maybeCreate(it) }

        RepositorySetup.configure(project, extraRepositories)

        val javaVersion = if (legacyConfig.useModernJavaSyntax) 16 else 8
        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
            java.toolchain.vendor.set(JvmVendorSpec.AZUL)
            java.withSourcesJar()
        }

        val mcExt = project.extensions.getByName("minecraft")

        val mcVersionProp = mcExt.javaClass.getMethod("getMcVersion").invoke(mcExt)
        mcVersionProp.javaClass.getMethod("set", Any::class.java).invoke(mcVersionProp, legacyConfig.mcVersion)

        val usernameProp = mcExt.javaClass.getMethod("getUsername").invoke(mcExt)
        usernameProp.javaClass.getMethod("set", Any::class.java).invoke(usernameProp, legacyConfig.username)

        val channelProp = mcExt.javaClass.getMethod("getMcpMappingChannel").invoke(mcExt)
        channelProp.javaClass.getMethod("set", Any::class.java).invoke(channelProp, legacyConfig.mappingChannel)

        val mappingVersionProp = mcExt.javaClass.getMethod("getMcpMappingVersion").invoke(mcExt)
        mappingVersionProp.javaClass.getMethod("set", Any::class.java).invoke(mappingVersionProp, legacyConfig.mappingVersion)

        for (tweaker in legacyConfig.mixinTweakers) {
            val tweakList = mcExt.javaClass.getMethod("getExtraTweakClasses").invoke(mcExt)
            tweakList.javaClass.getMethod("add", Any::class.java).invoke(tweakList, tweaker)
        }

        val runJvmArgs = mcExt.javaClass.getMethod("getExtraRunJvmArguments").invoke(mcExt)
        runJvmArgs.javaClass.getMethod("add", Any::class.java)
            .invoke(runJvmArgs, "-ea:${metadata.group}")

        if (legacyConfig.accessTransformers.isNotEmpty()) {
            project.afterEvaluate { proj ->
                val deobfTask = proj.tasks.findByName("deobfuscateMergedJarToSrg")
                if (deobfTask != null) {
                    val atFiles = deobfTask.javaClass.getMethod("getAccessTransformerFiles").invoke(deobfTask)
                    for (at in legacyConfig.accessTransformers) {
                        atFiles.javaClass.getMethod("from", Array<Any>::class.java)
                            .invoke(atFiles, arrayOf<Any>(at))
                    }
                }
            }
        }

        JarNaming.configure(project, metadata, versionConfig, legacyConfig)

        for (action in legacyConfig.rawProjectActions) {
            action.execute(project)
        }
    }
}
