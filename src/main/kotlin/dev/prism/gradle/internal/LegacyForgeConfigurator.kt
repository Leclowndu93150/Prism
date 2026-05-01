package dev.prism.gradle.internal

import dev.prism.gradle.dsl.LegacyForgeConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
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
        applyRfg(loaderProject, versionConfig, legacyConfig, metadata, extraRepositories, commonProject)
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
        applyRfg(project, versionConfig, legacyConfig, metadata, extraRepositories, null)
        TemplateExpansion.configure(project, versionConfig, metadata)
    }

    private fun applyRfg(
        project: Project,
        versionConfig: VersionConfiguration,
        legacyConfig: LegacyForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry>,
        commonProject: Project?,
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

        configureMixinBooter(project, legacyConfig)
        configureMixins(project, legacyConfig, commonProject)

        project.tasks.withType(JavaCompile::class.java).configureEach { task ->
            task.options.compilerArgs.addAll(listOf("-Xdiags:verbose", "-Xlint:-options"))
        }

        JarNaming.configure(project, metadata, versionConfig, legacyConfig)

        for (action in legacyConfig.rawProjectActions) {
            action.execute(project)
        }
    }

    private fun configureMixinBooter(project: Project, legacyConfig: LegacyForgeConfiguration) {
        if (!legacyConfig.mixinBooter) return
        project.repositories.maven { repo ->
            repo.name = "CleanroomMC"
            repo.setUrl("https://repo.cleanroommc.com/releases")
            repo.mavenContent { it.includeGroup("zone.rong") }
        }
        val coords = "zone.rong:mixinbooter:${legacyConfig.mixinBooterVersion}"
        project.dependencies.add("implementation", project.dependencies.create(coords).also {
            (it as? org.gradle.api.artifacts.ModuleDependency)?.isTransitive = false
        })
        project.dependencies.add("annotationProcessor", project.dependencies.create(coords).also {
            (it as? org.gradle.api.artifacts.ModuleDependency)?.isTransitive = false
        })
    }

    private fun configureMixins(
        project: Project,
        legacyConfig: LegacyForgeConfiguration,
        commonProject: Project?,
    ) {
        val mixinConfigs = MixinAutoDetect.resolveMixinConfigs(project, commonProject, legacyConfig.mixinOptions)
        val hasMixins = mixinConfigs.isNotEmpty() || MixinAutoDetect.hasMixinSources(project, commonProject)
        if (!hasMixins) return

        if (mixinConfigs.isNotEmpty()) {
            MixinAutoDetect.addMixinConfigsManifest(project, mixinConfigs)
        }

        val corePlugin = legacyConfig.coreModClass
            ?: MixinAutoDetect.findCorePluginClass(project, commonProject)

        project.tasks.withType(Jar::class.java).configureEach { jar ->
            if (corePlugin != null) {
                jar.manifest.attributes["FMLCorePlugin"] = corePlugin
            }
            jar.manifest.attributes["FMLCorePluginContainsFMLMod"] = "true"
            jar.manifest.attributes["ForceLoadAsMod"] = "true"
        }

        if (corePlugin != null) {
            project.logger.lifecycle("Prism: Registered FML core plugin '$corePlugin' for ${project.path}")
        } else {
            project.logger.lifecycle("Prism: Wired late-mixin manifest flags for ${project.path} (no IFMLLoadingPlugin detected)")
        }
    }
}
