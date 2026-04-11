package dev.prism.gradle.internal

import dev.prism.gradle.dsl.LexForgeConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import dev.prism.gradle.internal.accesswidener.AccessWidenerSupport
import net.minecraftforge.gradle.ForgeGradleExtension
import net.minecraftforge.gradle.MinecraftExtensionForProject
import net.minecraftforge.gradle.SlimeLauncherOptions
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object LexForgeConfigurator {
    fun configure(
        loaderProject: Project,
        commonProject: Project,
        versionConfig: VersionConfiguration,
        lexForgeConfig: LexForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        applyPluginAndBaseConfig(loaderProject, versionConfig, extraRepositories)

        val minecraft = loaderProject.extensions.getByType(MinecraftExtensionForProject::class.java)
        configureMinecraft(loaderProject, commonProject, versionConfig, lexForgeConfig, metadata, minecraft, multiLoader = true)

        configureMixins(loaderProject, metadata, lexForgeConfig, commonProject)

        for (action in lexForgeConfig.rawMinecraftActions) {
            action.execute(minecraft)
        }

        JarNaming.configure(loaderProject, metadata, versionConfig, lexForgeConfig)
        CommonLoaderWiring.wire(loaderProject, commonProject, metadata, sharedProject)
        TemplateExpansion.configure(loaderProject, versionConfig, metadata)

        for (action in lexForgeConfig.rawProjectActions) {
            action.execute(loaderProject)
        }
    }

    fun configureSingle(
        project: Project,
        versionConfig: VersionConfiguration,
        lexForgeConfig: LexForgeConfiguration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        applyPluginAndBaseConfig(project, versionConfig, extraRepositories)

        val minecraft = project.extensions.getByType(MinecraftExtensionForProject::class.java)
        configureMinecraft(project, null, versionConfig, lexForgeConfig, metadata, minecraft, multiLoader = false)

        configureMixins(project, metadata, lexForgeConfig, null)

        for (action in lexForgeConfig.rawMinecraftActions) {
            action.execute(minecraft)
        }

        JarNaming.configure(project, metadata, versionConfig, lexForgeConfig)
        TemplateExpansion.configure(project, versionConfig, metadata)

        for (action in lexForgeConfig.rawProjectActions) {
            action.execute(project)
        }
    }

    private fun applyPluginAndBaseConfig(
        project: Project,
        versionConfig: VersionConfiguration,
        extraRepositories: List<RepositoryEntry>,
    ) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("net.minecraftforge.gradle")

        RepositorySetup.configure(project, extraRepositories)

        val minecraft = project.extensions.getByType(MinecraftExtensionForProject::class.java)
        val fg = project.extensions.getByType(ForgeGradleExtension::class.java)
        project.repositories.apply {
            minecraft.mavenizer(this)
            maven(fg.forgeMaven)
            maven(fg.minecraftLibsMaven)
        }

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(
                JavaLanguageVersion.of(versionConfig.resolvedJavaVersion)
            )
            java.withSourcesJar()
        }
    }

    private fun configureMinecraft(
        project: Project,
        commonProject: Project?,
        versionConfig: VersionConfiguration,
        lexForgeConfig: LexForgeConfiguration,
        metadata: MetadataExtension,
        minecraft: MinecraftExtensionForProject,
        multiLoader: Boolean,
    ) {
        val (channel, mappingsVersion) = resolveMappings(versionConfig, lexForgeConfig)
        minecraft.mappings(channel, mappingsVersion)

        wireAccessTransformers(project, commonProject, versionConfig, metadata, minecraft)

        val forgeCoordinate = "net.minecraftforge:forge:${versionConfig.minecraftVersion}-${lexForgeConfig.loaderVersion}"
        val mavenizerInstance = minecraft.dependency(forgeCoordinate)
        project.dependencies.addProvider("implementation", mavenizerInstance.asProvider())

        configureRuns(project, versionConfig, lexForgeConfig, metadata, minecraft, multiLoader)

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.sourceSets.getByName("main").resources.srcDir("src/generated/resources")
        }
    }

    private fun resolveMappings(
        versionConfig: VersionConfiguration,
        lexForgeConfig: LexForgeConfiguration,
    ): Pair<String, String> {
        val dslChannel = lexForgeConfig.mappingsChannel
        val dslVersion = lexForgeConfig.mappingsVersion

        if (dslChannel != null && dslVersion != null) {
            return dslChannel to dslVersion
        }

        val parchmentMc = versionConfig.parchmentMinecraftVersion
        val parchmentMappings = versionConfig.parchmentMappingsVersion
        if (parchmentMc != null && parchmentMappings != null) {
            return "parchment" to "$parchmentMappings-$parchmentMc"
        }

        return "official" to versionConfig.minecraftVersion
    }

    private fun wireAccessTransformers(
        project: Project,
        commonProject: Project?,
        versionConfig: VersionConfiguration,
        metadata: MetadataExtension,
        minecraft: MinecraftExtensionForProject,
    ) {
        var hasExplicitAt = false

        commonProject?.file("src/main/resources/META-INF/accesstransformer.cfg")?.let { commonAt ->
            if (AccessWidenerSupport.hasAccessTransformerEntries(commonAt)) {
                minecraft.accessTransformer.from(commonAt.absolutePath)
                hasExplicitAt = true
            }
        }

        val loaderAt = project.file("src/main/resources/META-INF/accesstransformer.cfg")
        if (AccessWidenerSupport.hasAccessTransformerEntries(loaderAt)) {
            minecraft.accessTransformer.from(loaderAt.absolutePath)
            hasExplicitAt = true
        }

        if (!hasExplicitAt) {
            val awFile = AccessWidenerSupport.resolveAccessWidener(
                project, commonProject, versionConfig.unifiedAccessWidener, metadata.modId
            )
            if (awFile != null) {
                val generatedAt = AccessWidenerSupport.generateAccessTransformer(project, awFile, "lexforge")
                minecraft.accessTransformer.from(generatedAt.absolutePath)
            }
        }
    }

    private fun configureRuns(
        project: Project,
        versionConfig: VersionConfiguration,
        lexForgeConfig: LexForgeConfiguration,
        metadata: MetadataExtension,
        minecraft: MinecraftExtensionForProject,
        multiLoader: Boolean,
    ) {
        val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
        val mainSourceSet = javaExt.sourceSets.getByName("main")

        val runRoot = if (multiLoader) "runs/${versionConfig.minecraftVersion}/lexforge" else "runs"

        minecraft.runs { runs ->
            @Suppress("UNCHECKED_CAST")
            val container = runs as NamedDomainObjectContainer<SlimeLauncherOptions>

            container.create("client") { run ->
                applyStandardRunDefaults(run, mainSourceSet)
                run.workingDir.set(project.file("$runRoot/client"))
            }
            container.create("server") { run ->
                applyStandardRunDefaults(run, mainSourceSet)
                run.workingDir.set(project.file("$runRoot/server"))
            }
            container.create("data") { run ->
                applyStandardRunDefaults(run, mainSourceSet)
                run.workingDir.set(project.file("$runRoot/data"))
                run.args(
                    "--mod", metadata.modId,
                    "--all",
                    "--output", project.file("src/generated/resources/").absolutePath,
                    "--existing", project.file("src/main/resources/").absolutePath,
                )
            }

            applyExtraRuns(project, versionConfig, lexForgeConfig, container, mainSourceSet, runRoot)
        }
    }

    private fun applyStandardRunDefaults(
        run: SlimeLauncherOptions,
        mainSourceSet: org.gradle.api.tasks.SourceSet,
    ) {
        run.inheritArgs.set(true)
        run.inheritJvmArgs.set(true)
        run.systemProperty("forge.logging.markers", "REGISTRIES")
        run.systemProperty("forge.logging.console.level", "debug")
        run.mods { mods ->
            mods.maybeCreate(run.name).source(mainSourceSet)
        }
    }

    private fun applyExtraRuns(
        project: Project,
        versionConfig: VersionConfiguration,
        lexForgeConfig: LexForgeConfiguration,
        container: NamedDomainObjectContainer<SlimeLauncherOptions>,
        mainSourceSet: org.gradle.api.tasks.SourceSet,
        runRoot: String,
    ) {
        for (runConfig in lexForgeConfig.extraRuns.runs) {
            container.create(runConfig.name) { run ->
                run.inheritArgs.set(true)
                run.inheritJvmArgs.set(true)
                run.mods { mods ->
                    mods.maybeCreate(runConfig.name).source(mainSourceSet)
                }

                val dir = runConfig.runDir ?: "$runRoot/${runConfig.name}"
                run.workingDir.set(project.file(dir))

                runConfig.username?.let { run.args("--username", it) }

                for ((key, value) in runConfig.systemProperties) {
                    run.systemProperty(key, value)
                }
                for (arg in runConfig.jvmArgs) {
                    run.jvmArgs(arg)
                }
                if (runConfig.programArgs.isNotEmpty()) {
                    run.args(runConfig.programArgs.toTypedArray())
                }
            }
        }
    }

    private fun configureMixins(
        project: Project,
        metadata: MetadataExtension,
        lexForgeConfig: LexForgeConfiguration,
        commonProject: Project?,
    ) {
        val mixinConfigs = MixinAutoDetect.resolveMixinConfigs(project, commonProject, lexForgeConfig.mixinOptions)
        if (mixinConfigs.isEmpty()) return
        if (!MixinAutoDetect.hasMixinSources(project, commonProject)) {
            project.logger.lifecycle(
                "Prism: Found LexForge mixin config(s) $mixinConfigs for ${project.path}, but no @Mixin classes. " +
                    "Skipping mixin AP/manifest wiring."
            )
            return
        }

        project.dependencies.add("annotationProcessor", "org.spongepowered:mixin:0.8.5:processor")
        MixinAutoDetect.addMixinConfigsManifest(project, mixinConfigs)

        project.logger.lifecycle("Prism: Registered ${mixinConfigs.size} mixin config(s) for LexForge via manifest: ${mixinConfigs.joinToString(",")}")
    }
}
