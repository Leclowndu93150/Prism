package dev.prism.gradle.internal

import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

/**
 * Pins `javac --release` (bytecode target) for every JavaCompile task in [project].
 *
 * The toolchain JDK is what runs the compiler; bytecode level is what gets emitted.
 * Calling this from each loader configurator lets prism support compileJdk > javaVersion
 * (compile on a newer JDK while still emitting older class files).
 */
internal object JavaReleaseConfigurator {
    fun pinRelease(project: Project, javaVersion: Int) {
        project.tasks.withType(JavaCompile::class.java).configureEach { task ->
            task.options.release.set(javaVersion)
        }
    }
}
