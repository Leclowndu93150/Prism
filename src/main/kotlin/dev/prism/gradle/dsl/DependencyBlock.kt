package dev.prism.gradle.dsl

open class DependencyBlock {
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
