package dev.prism.gradle.dsl

open class DependencyBlock {
    internal val implementations = mutableListOf<String>()
    internal val modImplementations = mutableListOf<String>()
    internal val compileOnlys = mutableListOf<String>()
    internal val runtimeOnlys = mutableListOf<String>()
    internal val modCompileOnlys = mutableListOf<String>()
    internal val modRuntimeOnlys = mutableListOf<String>()
    internal val jarJarDeps = mutableListOf<String>()

    fun implementation(dep: String) { implementations.add(dep) }
    fun modImplementation(dep: String) { modImplementations.add(dep) }
    fun compileOnly(dep: String) { compileOnlys.add(dep) }
    fun runtimeOnly(dep: String) { runtimeOnlys.add(dep) }
    fun modCompileOnly(dep: String) { modCompileOnlys.add(dep) }
    fun modRuntimeOnly(dep: String) { modRuntimeOnlys.add(dep) }
    fun jarJar(dep: String) { jarJarDeps.add(dep) }
}
