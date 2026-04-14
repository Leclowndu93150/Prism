package dev.prism.gradle.dsl

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Action

open class DependencyBlock {
    open class ShadowConfig {
        internal data class RelocationRule(
            val pattern: String,
            val destination: String,
            val includes: List<String>,
            val excludes: List<String>,
            val skipStringConstants: Boolean,
        )

        open class RelocationSpec {
            internal val includes = mutableListOf<String>()
            internal val excludes = mutableListOf<String>()
            internal var skipStringConstants: Boolean = false

            fun include(pattern: String) { includes.add(pattern) }
            fun exclude(pattern: String) { excludes.add(pattern) }
            fun skipStringConstants(skip: Boolean = true) { skipStringConstants = skip }
        }

        internal var defaultRelocationEnabled = true
        internal var defaultRelocationPrefix: String? = null
        internal val defaultRelocationIncludes = mutableListOf<String>()
        internal val defaultRelocationExcludes = mutableListOf<String>()
        internal val relocations = mutableListOf<RelocationRule>()
        internal val taskExcludes = mutableListOf<String>()
        internal val stripPatterns = mutableListOf<String>()
        internal val manifestAttributesToRemove = linkedSetOf("Class-Path", "Multi-Release")
        internal val mergeServiceFileRoots = mutableListOf<String?>()
        internal val rawActions = mutableListOf<Action<ShadowJar>>()

        fun relocation(enabled: Boolean) { defaultRelocationEnabled = enabled }
        fun relocationPrefix(prefix: String) { defaultRelocationPrefix = prefix }
        fun includeRelocation(pattern: String) { defaultRelocationIncludes.add(pattern) }
        fun excludeRelocation(pattern: String) { defaultRelocationExcludes.add(pattern) }
        fun relocate(pattern: String, destination: String, action: Action<RelocationSpec> = Action {}) {
            val spec = RelocationSpec()
            action.execute(spec)
            relocations.add(RelocationRule(pattern, destination, spec.includes.toList(), spec.excludes.toList(), spec.skipStringConstants))
        }
        fun exclude(vararg patterns: String) { taskExcludes.addAll(patterns) }
        fun strip(vararg patterns: String) { stripPatterns.addAll(patterns) }
        fun removeManifestAttribute(name: String) { manifestAttributesToRemove.add(name) }
        fun mergeServiceFiles(rootPath: String? = null) { mergeServiceFileRoots.add(rootPath) }
        fun raw(action: Action<ShadowJar>) { rawActions.add(action) }
    }

    internal data class ShadowSettings(
        val enabled: Boolean,
        val prefix: String?,
        val includes: List<String>,
        val excludes: List<String>,
        val relocations: List<ShadowConfig.RelocationRule>,
        val taskExcludes: List<String>,
        val stripPatterns: List<String>,
        val manifestAttributesToRemove: List<String>,
        val mergeServiceFileRoots: List<String?>,
        val rawActions: List<Action<ShadowJar>>,
    )

    internal val apis = mutableListOf<String>()
    internal val modApis = mutableListOf<String>()
    internal val implementations = mutableListOf<String>()
    internal val modImplementations = mutableListOf<String>()
    internal val compileOnlyApis = mutableListOf<String>()
    internal val modCompileOnlyApis = mutableListOf<String>()
    internal val compileOnlys = mutableListOf<String>()
    internal val runtimeOnlys = mutableListOf<String>()
    internal val modCompileOnlys = mutableListOf<String>()
    internal val modRuntimeOnlys = mutableListOf<String>()
    internal val jarJarDeps = mutableListOf<String>()
    internal val shadowDeps = mutableListOf<String>()
    internal val shadowConfig = ShadowConfig()
    internal val annotationProcessors = mutableListOf<String>()
    internal val localJars = mutableListOf<LocalJarDep>()
    internal val customDeps = mutableListOf<NamedDependency>()
    internal val customModDeps = mutableListOf<NamedDependency>()

    fun api(dep: String) { apis.add(dep) }
    fun implementation(dep: String) { implementations.add(dep) }
    fun modApi(dep: String) { modApis.add(dep) }
    fun modImplementation(dep: String) { modImplementations.add(dep) }
    fun compileOnlyApi(dep: String) { compileOnlyApis.add(dep) }
    fun compileOnly(dep: String) { compileOnlys.add(dep) }
    fun modCompileOnlyApi(dep: String) { modCompileOnlyApis.add(dep) }
    fun runtimeOnly(dep: String) { runtimeOnlys.add(dep) }
    fun modCompileOnly(dep: String) { modCompileOnlys.add(dep) }
    fun modRuntimeOnly(dep: String) { modRuntimeOnlys.add(dep) }
    fun jarJar(dep: String) { jarJarDeps.add(dep) }
    fun shadow(dep: String) { shadowDeps.add(dep) }
    fun shadow(action: Action<ShadowConfig>) { action.execute(shadowConfig) }
    fun shadow(dep: String, action: Action<ShadowConfig>) {
        shadowDeps.add(dep)
        action.execute(shadowConfig)
    }
    fun shadowRelocation(enabled: Boolean) { shadowConfig.relocation(enabled) }
    fun shadowRelocationPrefix(prefix: String) { shadowConfig.relocationPrefix(prefix) }
    fun annotationProcessor(dep: String) { annotationProcessors.add(dep) }
    fun configuration(name: String, dep: String) {
        customDeps.add(NamedDependency(name, dep))
    }
    fun modConfiguration(name: String, dep: String) {
        customModDeps.add(NamedDependency(name, dep))
    }
    fun localJar(path: String, configuration: String = "compileOnly") {
        localJars.add(LocalJarDep(path, configuration))
    }

    data class LocalJarDep(val path: String, val configuration: String)
    data class NamedDependency(val configuration: String, val dependency: String)
}
