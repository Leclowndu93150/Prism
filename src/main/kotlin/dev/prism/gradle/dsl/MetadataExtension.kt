package dev.prism.gradle.dsl

open class MetadataExtension {
    var modId: String = ""
    var name: String = ""
    var description: String = ""
    var license: String = ""
    var version: String = ""
    var group: String = ""
    var archivesName: String = ""

    private val _authors = mutableListOf<String>()
    private val _credits = mutableListOf<String>()
    private val _expand = mutableMapOf<String, Any>()
    private val _includedFiles = mutableListOf<IncludedFile>()

    val authors: List<String> get() = _authors
    val credits: List<String> get() = _credits
    val expandProperties: Map<String, Any> get() = _expand
    val includedFiles: List<IncludedFile> get() = _includedFiles

    fun author(name: String) {
        _authors.add(name)
    }

    fun credit(name: String) {
        _credits.add(name)
    }

    fun expand(key: String, value: Any) {
        _expand[key] = value
    }

    fun includeFile(path: String, rename: String? = null) {
        _includedFiles.add(IncludedFile(path, rename))
    }
}

data class IncludedFile(val path: String, val rename: String?)
