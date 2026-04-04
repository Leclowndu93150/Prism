package dev.prism.gradle.internal

import dev.prism.gradle.dsl.DependencyBlock
import org.gradle.api.Project

object DependencyConfigurator {
    fun apply(project: Project, deps: DependencyBlock, isFabric: Boolean = false) {
        val hasModConfigs = project.configurations.findByName("modImplementation") != null

        for (dep in deps.implementations) {
            project.dependencies.add("implementation", dep)
        }
        for (dep in deps.compileOnlys) {
            project.dependencies.add("compileOnly", dep)
        }
        for (dep in deps.runtimeOnlys) {
            project.dependencies.add("runtimeOnly", dep)
        }

        val modImpl = if (hasModConfigs) "modImplementation" else "implementation"
        val modCompile = if (hasModConfigs) "modCompileOnly" else "compileOnly"
        val modRuntime = if (hasModConfigs) "modRuntimeOnly" else "runtimeOnly"

        for (dep in deps.modImplementations) {
            project.dependencies.add(modImpl, dep)
        }
        for (dep in deps.modCompileOnlys) {
            project.dependencies.add(modCompile, dep)
        }
        for (dep in deps.modRuntimeOnlys) {
            project.dependencies.add(modRuntime, dep)
        }

        for (dep in deps.annotationProcessors) {
            project.dependencies.add("annotationProcessor", dep)
        }

        if (deps.jarJarDeps.isNotEmpty()) {
            val includeConfig = project.configurations.findByName("include")
            if (isFabric && includeConfig != null) {
                for (dep in deps.jarJarDeps) {
                    project.dependencies.add("include", dep)
                }
            } else {
                for (dep in deps.jarJarDeps) {
                    if (project.configurations.findByName("jarJar") != null) {
                        project.dependencies.add("jarJar", dep)
                    }
                    project.dependencies.add("implementation", dep)
                }
            }
        }

        for (localJar in deps.localJars) {
            val resolvedPath = if (java.io.File(localJar.path).isAbsolute) {
                localJar.path
            } else {
                "${project.rootProject.projectDir}/${localJar.path}"
            }
            val files = project.files(resolvedPath)
            project.dependencies.add(localJar.configuration, files)
        }
    }
}
