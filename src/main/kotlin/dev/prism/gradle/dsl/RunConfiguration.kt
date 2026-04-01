package dev.prism.gradle.dsl

import org.gradle.api.Action

enum class RunType {
    CLIENT, SERVER, DATA, CLIENT_DATA, SERVER_DATA
}

open class RunConfiguration(val name: String) {
    var type: RunType = RunType.CLIENT
    var username: String? = null
    var ideConfigGenerated: Boolean = true
    var runDir: String? = null
    internal val jvmArgs = mutableListOf<String>()
    internal val programArgs = mutableListOf<String>()
    internal val systemProperties = mutableMapOf<String, String>()

    fun client() { type = RunType.CLIENT }
    fun server() { type = RunType.SERVER }
    fun data() { type = RunType.DATA }
    fun clientData() { type = RunType.CLIENT_DATA }
    fun serverData() { type = RunType.SERVER_DATA }

    fun jvmArg(arg: String) { jvmArgs.add(arg) }
    fun programArg(arg: String) { programArgs.add(arg) }
    fun systemProperty(key: String, value: String) { systemProperties[key] = value }
}

open class RunsBlock {
    internal val runs = mutableListOf<RunConfiguration>()

    fun run(name: String, action: Action<RunConfiguration>) {
        val config = RunConfiguration(name)
        action.execute(config)
        runs.add(config)
    }

    fun client(name: String = "client", action: Action<RunConfiguration> = Action {}) {
        val config = RunConfiguration(name)
        config.client()
        action.execute(config)
        runs.add(config)
    }

    fun server(name: String = "server", action: Action<RunConfiguration> = Action {}) {
        val config = RunConfiguration(name)
        config.server()
        action.execute(config)
        runs.add(config)
    }
}
