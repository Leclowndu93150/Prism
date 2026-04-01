package dev.prism.gradle.internal

import dev.prism.gradle.dsl.RunConfiguration
import dev.prism.gradle.dsl.RunType
import dev.prism.gradle.dsl.RunsBlock
import dev.prism.gradle.dsl.VersionConfiguration
import org.gradle.api.Project

object RunApplicator {

    fun applyFabricRuns(
        project: Project,
        runsBlock: RunsBlock,
        versionConfig: VersionConfiguration,
        loom: Any,
    ) {
        if (runsBlock.runs.isEmpty()) return

        val runsMethod = loom.javaClass.methods.first { it.name == "runs" && it.parameterCount == 1 }
        runsMethod.invoke(loom, org.gradle.api.Action<Any> { runs ->
            for (runConfig in runsBlock.runs) {
                val container = runs as org.gradle.api.NamedDomainObjectContainer<*>
                container.create(runConfig.name) { run ->
                    when (runConfig.type) {
                        RunType.CLIENT -> invoke(run!!, "client")
                        RunType.SERVER -> invoke(run!!, "server")
                        else -> invoke(run!!, "client")
                    }

                    invoke(run!!, "setConfigName", "${displayName(runConfig)} (${versionConfig.minecraftVersion})")
                    invoke(run, "ideConfigGenerated", runConfig.ideConfigGenerated)

                    val dir = runConfig.runDir
                        ?: "runs/${versionConfig.minecraftVersion}/fabric/${runConfig.name}"
                    invoke(run, "runDir", dir)

                    runConfig.username?.let {
                        invoke(run, "programArg", "--username=$it")
                    }

                    for (arg in runConfig.jvmArgs) {
                        invoke(run, "vmArg", arg)
                    }
                    for (arg in runConfig.programArgs) {
                        invoke(run, "programArg", arg)
                    }
                }
            }
        })
    }

    fun applyMdgRuns(
        project: Project,
        runsBlock: RunsBlock,
        versionConfig: VersionConfiguration,
        loaderName: String,
        runsContainer: Any,
    ) {
        if (runsBlock.runs.isEmpty()) return

        val container = runsContainer as org.gradle.api.NamedDomainObjectContainer<*>
        for (runConfig in runsBlock.runs) {
            container.create(runConfig.name) { run ->
                when (runConfig.type) {
                    RunType.CLIENT -> invoke(run!!, "client")
                    RunType.SERVER -> invoke(run!!, "server")
                    RunType.DATA -> invoke(run!!, "data")
                    RunType.CLIENT_DATA -> invoke(run!!, "clientData")
                    RunType.SERVER_DATA -> invoke(run!!, "serverData")
                }

                val ideName = run!!.javaClass.getMethod("getIdeName").invoke(run)
                ideName.javaClass.getMethod("set", Any::class.java)
                    .invoke(ideName, "${displayName(runConfig)} (${versionConfig.minecraftVersion})")

                val dir = runConfig.runDir
                    ?: "runs/${versionConfig.minecraftVersion}/$loaderName/${runConfig.name}"
                val gameDir = run.javaClass.getMethod("getGameDirectory").invoke(run)
                gameDir.javaClass.getMethod("set", Any::class.java)
                    .invoke(gameDir, project.file(dir))

                runConfig.username?.let { username ->
                    run.javaClass.getMethod("systemProperty", String::class.java, String::class.java)
                        .invoke(run, "devLogin.username", username)
                }

                for ((key, value) in runConfig.systemProperties) {
                    run.javaClass.getMethod("systemProperty", String::class.java, String::class.java)
                        .invoke(run, key, value)
                }

                for (arg in runConfig.jvmArgs) {
                    run.javaClass.getMethod("jvmArgument", String::class.java).invoke(run, arg)
                }

                for (arg in runConfig.programArgs) {
                    run.javaClass.getMethod("programArgument", String::class.java).invoke(run, arg)
                }
            }
        }
    }

    private fun displayName(runConfig: RunConfiguration): String {
        val typePrefix = when (runConfig.type) {
            RunType.CLIENT -> "Client"
            RunType.SERVER -> "Server"
            RunType.DATA -> "Data"
            RunType.CLIENT_DATA -> "Client Data"
            RunType.SERVER_DATA -> "Server Data"
        }
        return if (runConfig.name == runConfig.type.name.lowercase()) typePrefix
        else "${typePrefix} [${runConfig.name}]"
    }

    private fun invoke(obj: Any, name: String, vararg args: Any?) {
        val method = obj.javaClass.methods.first { it.name == name && it.parameterCount == args.size }
        method.invoke(obj, *args)
    }
}
