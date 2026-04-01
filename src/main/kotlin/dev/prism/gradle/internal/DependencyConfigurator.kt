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

        val modImpl = if (isFabric && hasModConfigs) "modImplementation" else "implementation"
        val modCompile = if (isFabric && hasModConfigs) "modCompileOnly" else "compileOnly"
        val modRuntime = if (isFabric && hasModConfigs) "modRuntimeOnly" else "runtimeOnly"

        for (dep in deps.modImplementations) {
            project.dependencies.add(modImpl, dep)
        }
        for (dep in deps.modCompileOnlys) {
            project.dependencies.add(modCompile, dep)
        }
        for (dep in deps.modRuntimeOnlys) {
            project.dependencies.add(modRuntime, dep)
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
    }
}
