package dev.prism.gradle.internal

import dev.prism.gradle.dsl.DependencyBlock
import org.gradle.api.Project

object DependencyConfigurator {
    private fun String.capitalized(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    fun apply(project: Project, deps: DependencyBlock, isFabric: Boolean = false) {
        val hasModConfigs = project.configurations.findByName("modImplementation") != null

        for (dep in deps.apis) {
            project.dependencies.add("api", dep)
        }
        for (dep in deps.implementations) {
            project.dependencies.add("implementation", dep)
        }
        for (dep in deps.compileOnlyApis) {
            project.dependencies.add("compileOnlyApi", dep)
        }
        for (dep in deps.compileOnlys) {
            project.dependencies.add("compileOnly", dep)
        }
        for (dep in deps.runtimeOnlys) {
            project.dependencies.add("runtimeOnly", dep)
        }

        val modApi = if (project.configurations.findByName("modApi") != null) "modApi" else "api"
        val modImpl = if (hasModConfigs) "modImplementation" else "implementation"
        val modCompileApi = if (project.configurations.findByName("modCompileOnlyApi") != null) "modCompileOnlyApi" else "compileOnlyApi"
        val modCompile = if (hasModConfigs) "modCompileOnly" else "compileOnly"
        val modRuntime = if (hasModConfigs) "modRuntimeOnly" else "runtimeOnly"

        for (dep in deps.modApis) {
            project.dependencies.add(modApi, dep)
        }
        for (dep in deps.modImplementations) {
            project.dependencies.add(modImpl, dep)
        }
        for (dep in deps.modCompileOnlyApis) {
            project.dependencies.add(modCompileApi, dep)
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

        for (dep in deps.customDeps) {
            project.dependencies.add(dep.configuration, dep.dependency)
        }

        for (dep in deps.customModDeps) {
            val remappedName = "mod${dep.configuration.capitalized()}"
            val target = when {
                project.configurations.findByName(remappedName) != null -> remappedName
                project.configurations.findByName(dep.configuration) != null -> {
                    project.logger.warn("Prism: ${project.path} has no remapped configuration '$remappedName'. Falling back to '${dep.configuration}'.")
                    dep.configuration
                }
                else -> {
                    project.logger.warn("Prism: ${project.path} has no configuration '${dep.configuration}' or '$remappedName'. Falling back to implementation.")
                    "implementation"
                }
            }
            project.dependencies.add(target, dep.dependency)
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
