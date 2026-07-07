package dev.prism.gradle.internal

import dev.prism.gradle.dsl.Forge16Configuration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.RepositoryEntry
import dev.prism.gradle.dsl.VersionConfiguration
import dev.prism.gradle.internal.accesswidener.AccessWidenerSupport
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.File

object Forge16Configurator {

    private const val USERDEV_PLUGIN = "net.minecraftforge.gradle.userdev.UserDevPlugin"

    fun configure(
        loaderProject: Project,
        commonProject: Project,
        versionConfig: VersionConfiguration,
        forge16Config: Forge16Configuration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        applyPluginAndBaseConfig(loaderProject, versionConfig, extraRepositories)
        configureMinecraft(loaderProject, commonProject, versionConfig, forge16Config, metadata, multiLoader = true)
        configureMixins(loaderProject, forge16Config, commonProject)

        JarNaming.configure(loaderProject, metadata, versionConfig, forge16Config)
        CommonLoaderWiring.wire(loaderProject, commonProject, metadata, sharedProject)
        TemplateExpansion.configure(loaderProject, versionConfig, metadata)

        for (action in forge16Config.rawProjectActions) {
            action.execute(loaderProject)
        }
    }

    fun configureSingle(
        project: Project,
        versionConfig: VersionConfiguration,
        forge16Config: Forge16Configuration,
        metadata: MetadataExtension,
        extraRepositories: List<RepositoryEntry> = emptyList(),
        sharedProject: Project? = null,
    ) {
        applyPluginAndBaseConfig(project, versionConfig, extraRepositories)
        configureMinecraft(project, null, versionConfig, forge16Config, metadata, multiLoader = false)
        configureMixins(project, forge16Config, null)

        JarNaming.configure(project, metadata, versionConfig, forge16Config)
        TemplateExpansion.configure(project, versionConfig, metadata)

        for (action in forge16Config.rawProjectActions) {
            action.execute(project)
        }
    }

    private fun applyPluginAndBaseConfig(
        project: Project,
        versionConfig: VersionConfiguration,
        extraRepositories: List<RepositoryEntry>,
    ) {
        project.pluginManager.apply("java-library")

        @Suppress("UNCHECKED_CAST")
        val pluginClass = Class.forName(USERDEV_PLUGIN) as Class<out Plugin<Project>>
        project.pluginManager.apply(pluginClass)

        RepositorySetup.configure(project, extraRepositories)
        project.repositories.maven { repo ->
            repo.name = "MinecraftForge"
            repo.setUrl("https://maven.minecraftforge.net/")
        }

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(versionConfig.resolvedCompileJdk))
            java.withSourcesJar()
        }
        JavaReleaseConfigurator.pinRelease(project, versionConfig.resolvedJavaVersion)
    }

    private fun configureMinecraft(
        project: Project,
        commonProject: Project?,
        versionConfig: VersionConfiguration,
        forge16Config: Forge16Configuration,
        metadata: MetadataExtension,
        multiLoader: Boolean,
    ) {
        val minecraft = project.extensions.getByName("minecraft")

        minecraft.javaClass.getMethod("mappings", String::class.java, String::class.java)
            .invoke(minecraft, forge16Config.mappingsChannel, forge16Config.mappingsVersion)

        wireAccessTransformers(project, commonProject, versionConfig, forge16Config, metadata, minecraft)

        val forgeCoordinate = "net.minecraftforge:forge:${versionConfig.minecraftVersion}-${forge16Config.loaderVersion}"
        project.dependencies.add("minecraft", forgeCoordinate)

        configureRuns(project, versionConfig, forge16Config, metadata, minecraft, multiLoader)

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.sourceSets.getByName("main").resources.srcDir("src/generated/resources")
        }
    }

    private fun wireAccessTransformers(
        project: Project,
        commonProject: Project?,
        versionConfig: VersionConfiguration,
        forge16Config: Forge16Configuration,
        metadata: MetadataExtension,
        minecraft: Any,
    ) {
        val atMethod = minecraft.javaClass.getMethod("accessTransformer", Any::class.java)
        var hasExplicitAt = false

        for (path in forge16Config.accessTransformers) {
            atMethod.invoke(minecraft, project.file(path))
            hasExplicitAt = true
        }

        commonProject?.file("src/main/resources/META-INF/accesstransformer.cfg")?.let { commonAt ->
            if (AccessWidenerSupport.hasAccessTransformerEntries(commonAt)) {
                atMethod.invoke(minecraft, commonAt)
                hasExplicitAt = true
            }
        }

        val loaderAt = project.file("src/main/resources/META-INF/accesstransformer.cfg")
        if (AccessWidenerSupport.hasAccessTransformerEntries(loaderAt)) {
            atMethod.invoke(minecraft, loaderAt)
            hasExplicitAt = true
        }

        if (!hasExplicitAt) {
            val awFile = AccessWidenerSupport.resolveAccessWidener(
                project, commonProject, versionConfig.unifiedAccessWidener, metadata.modId
            )
            if (awFile != null) {
                val generatedAt = AccessWidenerSupport.generateAccessTransformer(project, awFile, "forge16")
                atMethod.invoke(minecraft, generatedAt)
            }
        }
    }

    private fun configureRuns(
        project: Project,
        versionConfig: VersionConfiguration,
        forge16Config: Forge16Configuration,
        metadata: MetadataExtension,
        minecraft: Any,
        multiLoader: Boolean,
    ) {
        val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
        val mainSourceSet = javaExt.sourceSets.getByName("main")

        val runRoot = if (multiLoader) "runs/${versionConfig.minecraftVersion}/forge16" else "runs"

        @Suppress("UNCHECKED_CAST")
        val runs = minecraft.javaClass.getMethod("getRuns").invoke(minecraft) as NamedDomainObjectContainer<Any>

        createRun(project, runs, "client", "$runRoot/client", metadata, mainSourceSet)
        createRun(project, runs, "server", "$runRoot/server", metadata, mainSourceSet)
        EulaAcceptor.accept(project.file("$runRoot/server"))
        createRun(project, runs, "data", "$runRoot/data", metadata, mainSourceSet) { run ->
            invokeVarargs(run, "args", arrayOf<Any>(
                "--mod", metadata.modId,
                "--all",
                "--output", project.file("src/generated/resources/").absolutePath,
                "--existing", project.file("src/main/resources/").absolutePath,
            ))
        }

        applyExtraRuns(project, forge16Config, runs, metadata, mainSourceSet, runRoot)
    }

    private fun createRun(
        project: Project,
        runs: NamedDomainObjectContainer<Any>,
        name: String,
        workingDir: String,
        metadata: MetadataExtension,
        mainSourceSet: SourceSet,
        extra: (Any) -> Unit = {},
    ) {
        runs.create(name, Action { run ->
            run.javaClass.getMethod("workingDirectory", File::class.java)
                .invoke(run, project.file(workingDir))
            run.javaClass.getMethod("property", String::class.java, String::class.java)
                .invoke(run, "forge.logging.markers", "REGISTRIES")
            run.javaClass.getMethod("property", String::class.java, String::class.java)
                .invoke(run, "forge.logging.console.level", "debug")
            wireModSource(run, metadata.modId, mainSourceSet)
            extra(run)
        })
    }

    private fun wireModSource(run: Any, modId: String, mainSourceSet: SourceSet) {
        @Suppress("UNCHECKED_CAST")
        val mods = run.javaClass.getMethod("getMods").invoke(run) as NamedDomainObjectContainer<Any>
        mods.maybeCreate(modId).let { mod ->
            mod.javaClass.getMethod("source", SourceSet::class.java).invoke(mod, mainSourceSet)
        }
    }

    private fun applyExtraRuns(
        project: Project,
        forge16Config: Forge16Configuration,
        runs: NamedDomainObjectContainer<Any>,
        metadata: MetadataExtension,
        mainSourceSet: SourceSet,
        runRoot: String,
    ) {
        for (runConfig in forge16Config.extraRuns.runs) {
            runs.create(runConfig.name, Action { run ->
                val dir = runConfig.runDir ?: "$runRoot/${runConfig.name}"
                run.javaClass.getMethod("workingDirectory", File::class.java)
                    .invoke(run, project.file(dir))
                wireModSource(run, metadata.modId, mainSourceSet)

                for ((key, value) in runConfig.systemProperties) {
                    run.javaClass.getMethod("property", String::class.java, String::class.java)
                        .invoke(run, key, value)
                }
                if (runConfig.jvmArgs.isNotEmpty()) {
                    invokeVarargs(run, "jvmArgs", runConfig.jvmArgs.toTypedArray())
                }
                if (runConfig.programArgs.isNotEmpty()) {
                    invokeVarargs(run, "args", runConfig.programArgs.toTypedArray<Any>())
                }
            })
        }
    }

    private fun invokeVarargs(target: Any, method: String, args: Array<out Any>) {
        target.javaClass.getMethod(method, Array<Any>::class.java).invoke(target, args)
    }

    private fun configureMixins(
        project: Project,
        forge16Config: Forge16Configuration,
        commonProject: Project?,
    ) {
        val mixinConfigs = MixinAutoDetect.resolveMixinConfigs(project, commonProject, forge16Config.mixinOptions)
        if (mixinConfigs.isEmpty()) return
        if (!MixinAutoDetect.hasMixinSources(project, commonProject)) {
            project.logger.lifecycle(
                "Prism: Found Forge16 mixin config(s) $mixinConfigs for ${project.path}, but no @Mixin classes. " +
                    "Skipping mixin AP/manifest wiring."
            )
            return
        }

        project.dependencies.add("annotationProcessor", "org.spongepowered:mixin:0.8.5:processor")
        MixinAutoDetect.addMixinConfigsManifest(project, mixinConfigs)

        project.logger.lifecycle("Prism: Registered ${mixinConfigs.size} mixin config(s) for Forge16 via manifest: ${mixinConfigs.joinToString(",")}")
    }
}
