package dev.prism.gradle.dsl

open class ObfuscationOptions {
    internal val keepPatterns = mutableListOf<String>()
    internal val keepClassPatterns = mutableListOf<String>()
    internal val rawRules = mutableListOf<String>()
    internal var keepLineNumbers: Boolean = true
    internal var keepSourceFile: Boolean = false
    internal var optimizationPasses: Int = 3
    internal var allowAccessModification: Boolean = true
    internal var repackage: Boolean = true

    fun keep(pattern: String) {
        keepPatterns.add(pattern)
    }

    fun keepClass(pattern: String) {
        keepClassPatterns.add(pattern)
    }

    fun rule(raw: String) {
        rawRules.add(raw)
    }

    fun keepLineNumbers(value: Boolean) {
        keepLineNumbers = value
    }

    fun keepSourceFile(value: Boolean) {
        keepSourceFile = value
    }

    fun optimizationPasses(value: Int) {
        optimizationPasses = value
    }

    fun allowAccessModification(value: Boolean) {
        allowAccessModification = value
    }

    fun repackage(value: Boolean) {
        repackage = value
    }
}
