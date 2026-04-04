package dev.prism.gradle.dsl

open class DependencyBlock {
    internal val implementations = mutableListOf<String>()
    internal val modImplementations = mutableListOf<String>()
    internal val compileOnlys = mutableListOf<String>()
    internal val runtimeOnlys = mutableListOf<String>()
    internal val modCompileOnlys = mutableListOf<String>()
    internal val modRuntimeOnlys = mutableListOf<String>()
    internal val jarJarDeps = mutableListOf<String>()
    internal val annotationProcessors = mutableListOf<String>()
    internal val localJars = mutableListOf<LocalJarDep>()

    fun implementation(dep: String) { implementations.add(dep) }
    fun modImplementation(dep: String) { modImplementations.add(dep) }
    fun compileOnly(dep: String) { compileOnlys.add(dep) }
    fun runtimeOnly(dep: String) { runtimeOnlys.add(dep) }
    fun modCompileOnly(dep: String) { modCompileOnlys.add(dep) }
    fun modRuntimeOnly(dep: String) { modRuntimeOnlys.add(dep) }
    fun jarJar(dep: String) { jarJarDeps.add(dep) }
    fun annotationProcessor(dep: String) { annotationProcessors.add(dep) }
    fun localJar(path: String, configuration: String = "compileOnly") {
        localJars.add(LocalJarDep(path, configuration))
    }

    data class LocalJarDep(val path: String, val configuration: String)
}
