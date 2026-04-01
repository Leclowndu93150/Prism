package dev.prism.gradle.dsl

open class MetadataExtension {
    var modId: String = ""
    var name: String = ""
    var description: String = ""
    var license: String = ""
    var version: String = ""
    var group: String = ""

    private val _authors = mutableListOf<String>()
    private val _credits = mutableListOf<String>()

    val authors: List<String> get() = _authors
    val credits: List<String> get() = _credits

    fun author(name: String) {
        _authors.add(name)
    }

    fun credit(name: String) {
        _credits.add(name)
    }
}
