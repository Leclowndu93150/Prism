package dev.prism.gradle.internal

import dev.prism.gradle.dsl.FabricConfiguration
import dev.prism.gradle.dsl.LegacyForgeConfiguration
import dev.prism.gradle.dsl.LexForgeConfiguration
import dev.prism.gradle.dsl.LoaderConfiguration
import dev.prism.gradle.dsl.MetadataExtension
import dev.prism.gradle.dsl.ObfuscationOptions
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

object ObfuscationConfigurator {
    private const val MARKER = "dev.prism.obfConfigured"
    private const val PROGUARD_VERSION = "7.9.1"

    fun configure(
        project: Project,
        loaderConfig: LoaderConfiguration,
        metadata: MetadataExtension,
        options: ObfuscationOptions,
    ) {
        val extras = project.extensions.extraProperties
        if (extras.has(MARKER)) return
        extras.set(MARKER, true)

        val cfg = project.configurations.maybeCreate("prismObf").apply {
            isCanBeResolved = true
            isCanBeConsumed = false
            isVisible = false
        }
        project.repositories.mavenCentral()
        project.dependencies.add(cfg.name, "com.guardsquare:proguard-base:$PROGUARD_VERSION")

        val targetTaskName = primaryJarTaskName(loaderConfig)

        project.afterEvaluate { proj ->
            val targetTask = proj.tasks.findByName(targetTaskName) ?: proj.tasks.findByName("jar") ?: return@afterEvaluate
            val obfTask = registerObfTask(proj, targetTask, loaderConfig, metadata, cfg, options)
            targetTask.finalizedBy(obfTask)
        }
    }

    private fun primaryJarTaskName(loaderConfig: LoaderConfiguration): String = when (loaderConfig) {
        is FabricConfiguration -> "remapJar"
        is LegacyForgeConfiguration -> "reobfJar"
        is LexForgeConfiguration -> "reobfJar"
        else -> "jar"
    }

    private fun registerObfTask(
        project: Project,
        targetTask: Task,
        loaderConfig: LoaderConfiguration,
        metadata: MetadataExtension,
        proguardCfg: Configuration,
        options: ObfuscationOptions,
    ): TaskProvider<JavaExec> {
        val taskName = "${targetTask.name}PrismObf"
        return project.tasks.register(taskName, JavaExec::class.java) { exec ->
            exec.group = "build"
            exec.classpath = proguardCfg
            exec.mainClass.set("proguard.ProGuard")
            val jarFileHolder = arrayOfNulls<File>(1)
            val tempOutHolder = arrayOfNulls<File>(1)
            val mappingFileHolder = arrayOfNulls<File>(1)
            val renameMixinsHolder = booleanArrayOf(false)
            exec.doFirst {
                val jarFile = primaryJarOutput(targetTask) ?: error("Prism: cannot resolve output jar for ${targetTask.name}")
                if (!jarFile.exists()) error("Prism: target jar does not exist: $jarFile")
                jarFileHolder[0] = jarFile
                val outDir = project.layout.buildDirectory.dir("prism/obf").get().asFile
                outDir.mkdirs()
                val tempOut = File(outDir, jarFile.name)
                if (tempOut.exists()) tempOut.delete()
                tempOutHolder[0] = tempOut
                val mappingFile = File(outDir, "${jarFile.nameWithoutExtension}.map")
                mappingFileHolder[0] = mappingFile
                val namespace = JarManifestUtil.mappingNamespace(jarFile)
                val scan = JarScanner.scan(jarFile)
                renameMixinsHolder[0] = options.renameMixins && scan.mixinClasses.isNotEmpty()
                val configFile = writeProguardConfig(
                    project, jarFile, tempOut, outDir, mappingFile,
                    loaderConfig, metadata, options, scan, namespace, renameMixinsHolder[0],
                )
                exec.args = listOf("@${configFile.absolutePath}")
            }
            exec.doLast {
                val jarFile = jarFileHolder[0] ?: return@doLast
                val tempOut = tempOutHolder[0] ?: return@doLast
                val mappingFile = mappingFileHolder[0]
                if (tempOut.exists()) {
                    if (renameMixinsHolder[0] && mappingFile != null && mappingFile.exists()) {
                        MixinJsonRewriter.rewriteInPlace(tempOut, ProguardMapping.parse(mappingFile))
                    }
                    Files.move(tempOut.toPath(), jarFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun primaryJarOutput(task: Task): File? {
        if (task is Jar) return task.archiveFile.get().asFile
        return task.outputs.files.files.firstOrNull { it.isFile && it.extension == "jar" }
    }

    private fun writeProguardConfig(
        project: Project,
        jarFile: File,
        tempOut: File,
        outDir: File,
        mappingFile: File,
        loaderConfig: LoaderConfiguration,
        metadata: MetadataExtension,
        options: ObfuscationOptions,
        scan: JarScanner.ScanResult,
        namespace: String?,
        renameMixins: Boolean,
    ): File {
        val configFile = File(outDir, "${jarFile.nameWithoutExtension}.pro")

        val libraryJars = collectLibraryJars(project, loaderConfig, namespace)
        val keeps = buildKeepRules(scan, options, renameMixins)

        val sb = StringBuilder()
        sb.appendLine("-injars '${jarFile.absolutePath}'(!META-INF/MANIFEST.MF)")
        sb.appendLine("-outjars '${tempOut.absolutePath}'")
        for (lib in libraryJars) {
            sb.appendLine("-libraryjars '${lib.absolutePath}'")
        }
        sb.appendLine("-printmapping '${mappingFile.absolutePath}'")
        sb.appendLine("-dontwarn")
        sb.appendLine("-dontnote")
        sb.appendLine("-ignorewarnings")
        val attrs = mutableListOf(
            "*Annotation*",
            "Signature",
            "InnerClasses",
            "EnclosingMethod",
            "Exceptions",
            "Deprecated",
            "RuntimeVisibleAnnotations",
            "RuntimeInvisibleAnnotations",
            "RuntimeVisibleParameterAnnotations",
            "RuntimeInvisibleParameterAnnotations",
            "RuntimeVisibleTypeAnnotations",
            "RuntimeInvisibleTypeAnnotations",
            "AnnotationDefault",
        )
        if (options.keepLineNumbers) attrs += "LineNumberTable"
        if (options.keepSourceFile) attrs += "SourceFile"
        sb.appendLine("-keepattributes ${attrs.joinToString(",")}")
        if (options.allowAccessModification) sb.appendLine("-allowaccessmodification")
        sb.appendLine("-overloadaggressively")
        if (options.repackage) sb.appendLine("-repackageclasses ''")
        sb.appendLine("-optimizationpasses ${options.optimizationPasses}")
        sb.appendLine("-keepparameternames")
        sb.appendLine("-adaptresourcefilenames **.properties,**.json,**.xml,**.txt,**.cfg,**.toml")
        sb.appendLine("-adaptresourcefilecontents **.properties,**.xml,**.cfg,**.toml")
        sb.appendLine()
        for (rule in keeps) {
            sb.appendLine(rule)
        }
        for (raw in options.rawRules) {
            sb.appendLine(raw)
        }

        configFile.writeText(sb.toString())
        return configFile
    }

    private fun collectLibraryJars(project: Project, loaderConfig: LoaderConfiguration, namespace: String?): List<File> {
        val result = mutableListOf<File>()
        val javaHome = System.getProperty("java.home")
        val jmodsDir = File(javaHome, "jmods")
        if (jmodsDir.isDirectory) {
            jmodsDir.listFiles { f -> f.extension == "jmod" }?.forEach(result::add)
        } else {
            val rt = File(javaHome, "lib/rt.jar")
            if (rt.isFile) result.add(rt)
        }

        val useIntermediary = loaderConfig is FabricConfiguration && namespace == "intermediary"
        if (useIntermediary) {
            result += FabricIntermediaryLibraries.resolve(project)
        } else {
            val configsToScan = listOf("compileClasspath", "runtimeClasspath")
            for (name in configsToScan) {
                val cfg = project.configurations.findByName(name) ?: continue
                try {
                    cfg.resolve().forEach { if (it.isFile && it.extension == "jar") result.add(it) }
                } catch (_: Throwable) {
                }
            }
        }
        return result.distinct()
    }

    private fun buildKeepRules(scan: JarScanner.ScanResult, options: ObfuscationOptions, renameMixins: Boolean): List<String> {
        val rules = mutableListOf<String>()

        rules += "-keepclassmembers,allowoptimization enum * { public static **[] values(); public static ** valueOf(java.lang.String); }"
        rules += "-keepclassmembers class * implements java.io.Serializable { static final long serialVersionUID; private static final java.io.ObjectStreamField[] serialPersistentFields; private void writeObject(java.io.ObjectOutputStream); private void readObject(java.io.ObjectInputStream); java.lang.Object writeReplace(); java.lang.Object readResolve(); }"
        rules += "-keepclasseswithmembers,includedescriptorclasses class * { native <methods>; }"
        rules += "-keepclassmembers class * extends java.lang.Throwable { <init>(...); }"

        for (cls in scan.entrypointClasses) {
            rules += "-keep class ${dotted(cls)} { *; }"
        }
        for (cls in scan.modClasses) {
            rules += "-keep class ${dotted(cls)} { *; }"
        }
        for (cls in scan.eventBusSubscriberClasses) {
            rules += "-keep class ${dotted(cls)} { *; }"
        }
        for (cls in scan.mixinClasses) {
            val isInterface = scan.interfaceClasses.contains(cls)
            if (renameMixins) {
                if (isInterface) {
                    rules += "-keep,allowobfuscation interface ${dotted(cls)} { *; }"
                } else {
                    rules += "-keep,allowobfuscation class ${dotted(cls)} { *; }"
                }
            } else {
                rules += "-keep class ${dotted(cls)} { *; }"
            }
        }
        if (renameMixins) {
            val mixinPackages = scan.mixinClasses.mapNotNull {
                val idx = it.lastIndexOf('/')
                if (idx > 0) it.substring(0, idx) else null
            }.toSet()
            for (pkg in mixinPackages) {
                rules += "-keeppackagenames ${dotted(pkg)}"
            }
        }
        for (cls in scan.shadowOwners) {
            rules += "-keepclassmembers class ${dotted(cls)} { *; }"
        }
        if (!renameMixins) {
            for (cls in scan.accessorOwners) {
                rules += "-keep,allowoptimization,allowshrinking interface ${dotted(cls)} { *; }"
            }
        }
        for (cls in scan.subscribeEventOwners) {
            rules += "-keep class ${dotted(cls)} { *; }"
        }
        for (cls in scan.objectHolderOwners) {
            rules += "-keepclassmembers class ${dotted(cls)} { *; }"
        }
        for (cls in scan.capabilityInjectOwners) {
            rules += "-keepclassmembers class ${dotted(cls)} { *; }"
        }
        for (cls in scan.codecOwners) {
            rules += "-keepclassmembers class ${dotted(cls)} { public static *** CODEC; public static *** STREAM_CODEC; public static *** TYPE; public static *** MAP_CODEC; }"
        }
        for (cls in scan.customPayloadOwners) {
            rules += "-keep class ${dotted(cls)} { *; }"
        }

        rules += "-keepclassmembers class * { @org.spongepowered.asm.mixin.Shadow <fields>; @org.spongepowered.asm.mixin.Shadow <methods>; }"
        if (renameMixins) {
            for (m in scan.unnamedAccessorMethods) {
                val (returnDesc, paramDescs) = splitMethodDescriptor(m.descriptor)
                val returnType = descriptorToType(returnDesc)
                val paramTypes = paramDescs.joinToString(",") { descriptorToType(it) }
                rules += "-keepclassmembers,allowshrinking,allowoptimization class ${dotted(m.owner)} { $returnType ${m.name}($paramTypes); }"
            }
        } else {
            rules += "-keepclassmembers class * { @org.spongepowered.asm.mixin.gen.Accessor <methods>; @org.spongepowered.asm.mixin.gen.Invoker <methods>; }"
        }
        rules += "-keep class * implements org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin { *; }"
        if (renameMixins) {
            rules += "-keep,allowobfuscation @org.spongepowered.asm.mixin.Mixin class * { *; }"
        } else {
            rules += "-keep @org.spongepowered.asm.mixin.Mixin class * { *; }"
        }

        rules += "-keep @net.minecraftforge.fml.common.Mod class * { *; }"
        rules += "-keep @net.neoforged.fml.common.Mod class * { *; }"
        rules += "-keep @net.minecraftforge.fml.common.Mod\$EventBusSubscriber class * { *; }"
        rules += "-keep @net.neoforged.fml.common.EventBusSubscriber class * { *; }"
        rules += "-keepclassmembers class * { @net.minecraftforge.eventbus.api.SubscribeEvent <methods>; }"
        rules += "-keepclassmembers class * { @net.neoforged.bus.api.SubscribeEvent <methods>; }"
        rules += "-keepclassmembers class * { @net.minecraftforge.registries.ObjectHolder <fields>; }"
        rules += "-keepclassmembers class * { @net.neoforged.neoforge.registries.ObjectHolder <fields>; }"
        rules += "-keepclassmembers class * { @net.minecraftforge.common.capabilities.CapabilityInject <fields>; }"

        rules += "-keep class * implements net.fabricmc.api.ModInitializer { *; }"
        rules += "-keep class * implements net.fabricmc.api.ClientModInitializer { *; }"
        rules += "-keep class * implements net.fabricmc.api.DedicatedServerModInitializer { *; }"
        rules += "-keep class * implements net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint { *; }"
        rules += "-keep class * implements net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint { *; }"

        rules += "-keep class * implements net.minecraft.network.protocol.common.custom.CustomPacketPayload { *; }"

        for (pattern in options.keepClassPatterns) {
            rules += "-keep class $pattern"
        }
        for (pattern in options.keepPatterns) {
            rules += "-keep class $pattern { *; }"
        }

        return rules
    }

    private fun dotted(internalName: String): String = internalName.replace('/', '.')

    internal fun splitMethodDescriptor(descriptor: String): Pair<String, List<String>> {
        val open = descriptor.indexOf('(')
        val close = descriptor.indexOf(')')
        if (open != 0 || close < 0) return "V" to emptyList()
        val returnDesc = descriptor.substring(close + 1)
        val params = mutableListOf<String>()
        var i = 1
        while (i < close) {
            val c = descriptor[i]
            when (c) {
                'L' -> {
                    val end = descriptor.indexOf(';', i)
                    params += descriptor.substring(i, end + 1)
                    i = end + 1
                }
                '[' -> {
                    var j = i
                    while (descriptor[j] == '[') j++
                    if (descriptor[j] == 'L') {
                        val end = descriptor.indexOf(';', j)
                        params += descriptor.substring(i, end + 1)
                        i = end + 1
                    } else {
                        params += descriptor.substring(i, j + 1)
                        i = j + 1
                    }
                }
                else -> {
                    params += c.toString()
                    i++
                }
            }
        }
        return returnDesc to params
    }

    internal fun descriptorToType(desc: String): String {
        var arrays = 0
        var idx = 0
        while (idx < desc.length && desc[idx] == '[') { arrays++; idx++ }
        val base = when (desc[idx]) {
            'V' -> "void"
            'Z' -> "boolean"
            'B' -> "byte"
            'C' -> "char"
            'S' -> "short"
            'I' -> "int"
            'J' -> "long"
            'F' -> "float"
            'D' -> "double"
            'L' -> desc.substring(idx + 1, desc.length - 1).replace('/', '.')
            else -> "java.lang.Object"
        }
        return base + "[]".repeat(arrays)
    }
}

internal object JarManifestUtil {
    fun mappingNamespace(jarFile: File): String? {
        return try {
            JarFile(jarFile).use { jf ->
                jf.manifest?.mainAttributes?.getValue("Fabric-Mapping-Namespace")
            }
        } catch (_: Throwable) {
            null
        }
    }
}

internal object FabricIntermediaryLibraries {
    fun resolve(project: Project): List<File> {
        val result = mutableListOf<File>()

        try {
            val cls = Class.forName("net.fabricmc.loom.LoomGradleExtension")
            val get = cls.getMethod("get", Project::class.java)
            val ext = get.invoke(null, project)
            val nsCls = Class.forName("net.fabricmc.loom.api.mappings.layered.MappingsNamespace")
            val intermediary = nsCls.getField("INTERMEDIARY").get(null)
            @Suppress("UNCHECKED_CAST")
            val paths = cls.getMethod("getMinecraftJars", nsCls).invoke(ext, intermediary) as List<java.nio.file.Path>
            paths.forEach { result.add(it.toFile()) }
        } catch (_: Throwable) {
        }

        val mcLibsConfigs = listOf(
            "minecraftLibraries",
            "minecraftClientLibraries",
            "minecraftServerLibraries",
            "minecraftCompileLibraries",
            "minecraftRuntimeLibraries",
        )
        for (name in mcLibsConfigs) {
            val cfg = project.configurations.findByName(name) ?: continue
            try {
                cfg.resolve().forEach { if (it.isFile && it.extension == "jar") result.add(it) }
            } catch (_: Throwable) {
            }
        }

        val cfg = project.configurations.findByName("modCompileClasspath")
        if (cfg != null) {
            try {
                cfg.resolve().forEach { if (it.isFile && it.extension == "jar") result.add(it) }
            } catch (_: Throwable) {
            }
        }

        val plainConfigs = listOf("compileClasspath", "runtimeClasspath")
        for (name in plainConfigs) {
            val pc = project.configurations.findByName(name) ?: continue
            try {
                pc.resolve().forEach { f ->
                    if (!f.isFile || f.extension != "jar") return@forEach
                    val path = f.absolutePath.replace('\\', '/')
                    val isMcOrModRemapped = path.contains("/loom-cache/minecraftMaven/") ||
                            path.contains("/loom-cache/remapped_mods/") ||
                            path.contains("/fabric-loom/minecraftMaven/") ||
                            path.contains("/fabric-loom/.cache/remapped_mods/")
                    if (!isMcOrModRemapped) result.add(f)
                }
            } catch (_: Throwable) {
            }
        }

        return result.distinct()
    }
}

internal object ProguardMapping {
    data class Mapping(val classRenames: Map<String, String>)

    fun parse(mapFile: File): Mapping {
        val classRenames = mutableMapOf<String, String>()
        mapFile.useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line.startsWith(" ") || line.startsWith("\t") || line.startsWith("#")) continue
                if (!line.endsWith(":")) continue
                val body = line.substring(0, line.length - 1)
                val arrow = body.indexOf(" -> ")
                if (arrow < 0) continue
                val original = body.substring(0, arrow).trim()
                val obfuscated = body.substring(arrow + 4).trim()
                if (original.isNotEmpty() && obfuscated.isNotEmpty()) {
                    classRenames[original] = obfuscated
                }
            }
        }
        return Mapping(classRenames)
    }
}

internal object MixinJsonRewriter {
    fun rewriteInPlace(jarFile: File, mapping: ProguardMapping.Mapping) {
        val tmp = File(jarFile.parentFile, "${jarFile.name}.rewrite.tmp")
        if (tmp.exists()) tmp.delete()
        JarFile(jarFile).use { jf ->
            JarOutputStream(tmp.outputStream()).use { jos ->
                val entries = jf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) {
                        jos.putNextEntry(JarEntry(entry.name))
                        jos.closeEntry()
                        continue
                    }
                    val data = jf.getInputStream(entry).use { it.readBytes() }
                    val newData = if (isMixinJsonName(entry.name)) {
                        rewriteMixinJson(data.toString(Charsets.UTF_8), mapping)?.toByteArray(Charsets.UTF_8) ?: data
                    } else data
                    val outEntry = JarEntry(entry.name)
                    outEntry.time = entry.time
                    jos.putNextEntry(outEntry)
                    jos.write(newData)
                    jos.closeEntry()
                }
            }
        }
        Files.move(tmp.toPath(), jarFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun isMixinJsonName(name: String): Boolean {
        if (!name.endsWith(".json")) return false
        if (name.contains('/')) return false
        return name.endsWith(".mixins.json") || name.contains("mixin", ignoreCase = true)
    }

    private fun rewriteMixinJson(text: String, mapping: ProguardMapping.Mapping): String? {
        val pkgMatch = Regex("\"package\"\\s*:\\s*\"([^\"]+)\"").find(text) ?: return null
        val originalPkg = pkgMatch.groupValues[1]

        val arrays = listOf("mixins", "client", "server")
        val classFqns = mutableMapOf<String, String>()
        for (arr in arrays) {
            val arrMatch = Regex("\"$arr\"\\s*:\\s*\\[([^\\]]*)\\]").find(text) ?: continue
            val body = arrMatch.groupValues[1]
            Regex("\"([^\"]+)\"").findAll(body).forEach {
                val rel = it.groupValues[1]
                val fqn = "$originalPkg.$rel"
                classFqns[rel] = fqn
            }
        }
        if (classFqns.isEmpty()) return null

        val renamed = classFqns.mapValues { (_, fqn) -> mapping.classRenames[fqn] ?: fqn }
        val commonPkg = commonPackagePrefix(renamed.values)
        var result = text
        result = result.replaceFirst(
            Regex("\"package\"\\s*:\\s*\"[^\"]+\""),
            "\"package\": \"${commonPkg.ifEmpty { originalPkg }}\""
        )
        for (arr in arrays) {
            val arrMatch = Regex("\"$arr\"\\s*:\\s*\\[([^\\]]*)\\]").find(result) ?: continue
            val body = arrMatch.groupValues[1]
            val newBody = body.replace(Regex("\"([^\"]+)\"")) { m ->
                val rel = m.groupValues[1]
                val fqn = "$originalPkg.$rel"
                val newFqn = mapping.classRenames[fqn] ?: fqn
                val newRel = if (commonPkg.isNotEmpty() && newFqn.startsWith("$commonPkg.")) {
                    newFqn.substring(commonPkg.length + 1)
                } else newFqn
                "\"$newRel\""
            }
            result = result.replaceFirst(arrMatch.value, "\"$arr\": [$newBody]")
        }
        return result
    }

    private fun commonPackagePrefix(fqns: Collection<String>): String {
        if (fqns.isEmpty()) return ""
        val split = fqns.map { it.split('.').dropLast(1) }
        val min = split.minOf { it.size }
        val prefix = mutableListOf<String>()
        for (i in 0 until min) {
            val part = split[0][i]
            if (split.all { it[i] == part }) prefix.add(part) else break
        }
        return prefix.joinToString(".")
    }
}

internal object JarScanner {
    private const val ASM_API = Opcodes.ASM9

    private val MOD_ANNOTATIONS = setOf(
        "Lnet/minecraftforge/fml/common/Mod;",
        "Lnet/neoforged/fml/common/Mod;",
    )
    private val EVENT_BUS_SUBSCRIBER_ANNOTATIONS = setOf(
        "Lnet/minecraftforge/fml/common/Mod\$EventBusSubscriber;",
        "Lnet/neoforged/fml/common/EventBusSubscriber;",
    )
    private val MIXIN_CLASS_ANNOTATIONS = setOf(
        "Lorg/spongepowered/asm/mixin/Mixin;",
    )
    private val SHADOW_ANNOTATIONS = setOf(
        "Lorg/spongepowered/asm/mixin/Shadow;",
        "Lorg/spongepowered/asm/mixin/Final;",
    )
    private val ACCESSOR_ANNOTATIONS = setOf(
        "Lorg/spongepowered/asm/mixin/gen/Accessor;",
        "Lorg/spongepowered/asm/mixin/gen/Invoker;",
    )
    private val SUBSCRIBE_EVENT_ANNOTATIONS = setOf(
        "Lnet/minecraftforge/eventbus/api/SubscribeEvent;",
        "Lnet/neoforged/bus/api/SubscribeEvent;",
    )
    private val OBJECT_HOLDER_ANNOTATIONS = setOf(
        "Lnet/minecraftforge/registries/ObjectHolder;",
        "Lnet/neoforged/neoforge/registries/ObjectHolder;",
    )
    private val CAPABILITY_INJECT_ANNOTATIONS = setOf(
        "Lnet/minecraftforge/common/capabilities/CapabilityInject;",
    )
    private val ENTRYPOINT_INTERFACES = setOf(
        "net/fabricmc/api/ModInitializer",
        "net/fabricmc/api/ClientModInitializer",
        "net/fabricmc/api/DedicatedServerModInitializer",
        "net/fabricmc/loader/api/entrypoint/PreLaunchEntrypoint",
        "net/fabricmc/fabric/api/datagen/v1/DataGeneratorEntrypoint",
        "org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin",
    )
    private val CUSTOM_PAYLOAD_INTERFACES = setOf(
        "net/minecraft/network/protocol/common/custom/CustomPacketPayload",
    )

    data class AccessorMethod(val owner: String, val name: String, val descriptor: String)

    data class ScanResult(
        val mixinClasses: Set<String>,
        val modClasses: Set<String>,
        val eventBusSubscriberClasses: Set<String>,
        val entrypointClasses: Set<String>,
        val shadowOwners: Set<String>,
        val accessorOwners: Set<String>,
        val subscribeEventOwners: Set<String>,
        val objectHolderOwners: Set<String>,
        val capabilityInjectOwners: Set<String>,
        val codecOwners: Set<String>,
        val customPayloadOwners: Set<String>,
        val interfaceClasses: Set<String>,
        val unnamedAccessorMethods: List<AccessorMethod>,
    )

    fun scan(jarFile: File): ScanResult {
        val mixinClasses = mutableSetOf<String>()
        val modClasses = mutableSetOf<String>()
        val eventBusSubs = mutableSetOf<String>()
        val entrypoints = mutableSetOf<String>()
        val shadowOwners = mutableSetOf<String>()
        val accessorOwners = mutableSetOf<String>()
        val subscribeEventOwners = mutableSetOf<String>()
        val objectHolderOwners = mutableSetOf<String>()
        val capabilityInjectOwners = mutableSetOf<String>()
        val codecOwners = mutableSetOf<String>()
        val customPayloadOwners = mutableSetOf<String>()
        val interfaceClasses = mutableSetOf<String>()
        val unnamedAccessorMethods = mutableListOf<AccessorMethod>()

        val mixinJsonClasses = mutableSetOf<String>()
        val configEntrypoints = mutableSetOf<String>()

        try {
            JarFile(jarFile).use { jf ->
                val entries = jf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    when {
                        entry.isDirectory -> {}
                        name.endsWith(".class") -> {
                            jf.getInputStream(entry).use { input ->
                                val reader = ClassReader(input)
                                reader.accept(Visitor(
                                    mixinClasses, modClasses, eventBusSubs, entrypoints,
                                    shadowOwners, accessorOwners, subscribeEventOwners,
                                    objectHolderOwners, capabilityInjectOwners,
                                    codecOwners, customPayloadOwners,
                                    interfaceClasses, unnamedAccessorMethods,
                                ), ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                            }
                        }
                        name == "fabric.mod.json" -> {
                            try {
                                val text = jf.getInputStream(entry).bufferedReader().readText()
                                configEntrypoints += parseFabricEntrypoints(text)
                            } catch (_: Throwable) {
                            }
                        }
                        isMixinJson(name) -> {
                            try {
                                val text = jf.getInputStream(entry).bufferedReader().readText()
                                if (text.contains("\"package\"")) {
                                    mixinJsonClasses += parseMixinClasses(text)
                                }
                            } catch (_: Throwable) {
                            }
                        }
                        name == "META-INF/mods.toml" || name == "META-INF/neoforge.mods.toml" -> {
                            try {
                                val text = jf.getInputStream(entry).bufferedReader().readText()
                                configEntrypoints += parseModsTomlClasses(text)
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        mixinClasses += mixinJsonClasses.map { it.replace('.', '/') }
        entrypoints += configEntrypoints.map { it.replace('.', '/') }

        return ScanResult(
            mixinClasses, modClasses, eventBusSubs, entrypoints,
            shadowOwners, accessorOwners, subscribeEventOwners,
            objectHolderOwners, capabilityInjectOwners,
            codecOwners, customPayloadOwners,
            interfaceClasses, unnamedAccessorMethods,
        )
    }

    private fun isMixinJson(name: String): Boolean {
        if (!name.endsWith(".json")) return false
        if (name.contains('/')) return false
        return name.endsWith(".mixins.json") || name.contains("mixin", ignoreCase = true)
    }

    private fun parseMixinClasses(json: String): Set<String> {
        val result = mutableSetOf<String>()
        val pkg = Regex("\"package\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: return result
        val arrays = listOf("mixins", "client", "server")
        for (arr in arrays) {
            val match = Regex("\"$arr\"\\s*:\\s*\\[([^\\]]*)\\]").find(json) ?: continue
            val body = match.groupValues[1]
            Regex("\"([^\"]+)\"").findAll(body).forEach {
                val cls = it.groupValues[1]
                result += "$pkg.$cls"
            }
        }
        return result
    }

    private fun parseFabricEntrypoints(json: String): Set<String> {
        val result = mutableSetOf<String>()
        val entryRegex = Regex("\"entrypoints\"\\s*:\\s*\\{")
        val start = entryRegex.find(json) ?: return result
        var depth = 0
        var idx = start.range.last
        var endIdx = idx
        while (idx < json.length) {
            val c = json[idx]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) { endIdx = idx; break }
            }
            idx++
        }
        val block = json.substring(start.range.last + 1, endIdx)
        Regex("\"value\"\\s*:\\s*\"([A-Za-z_][\\w.\\$]*)\"").findAll(block).forEach {
            result += it.groupValues[1]
        }
        Regex("\"([A-Za-z_][\\w.\\$]*\\.[A-Za-z_][\\w.\\$]*)\"").findAll(block).forEach {
            val v = it.groupValues[1]
            if (v.contains('.')) result += v
        }
        return result.filter { it.contains('.') }.toSet()
    }

    private fun parseModsTomlClasses(text: String): Set<String> {
        val result = mutableSetOf<String>()
        Regex("\"([\\w.\\$]+\\.[A-Z][\\w\\$]*)\"").findAll(text).forEach {
            result += it.groupValues[1]
        }
        return result
    }

    private class Visitor(
        val mixinClasses: MutableSet<String>,
        val modClasses: MutableSet<String>,
        val eventBusSubs: MutableSet<String>,
        val entrypoints: MutableSet<String>,
        val shadowOwners: MutableSet<String>,
        val accessorOwners: MutableSet<String>,
        val subscribeEventOwners: MutableSet<String>,
        val objectHolderOwners: MutableSet<String>,
        val capabilityInjectOwners: MutableSet<String>,
        val codecOwners: MutableSet<String>,
        val customPayloadOwners: MutableSet<String>,
        val interfaceClasses: MutableSet<String>,
        val unnamedAccessorMethods: MutableList<AccessorMethod>,
    ) : ClassVisitor(ASM_API) {
        var className: String = ""

        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            className = name
            if ((access and Opcodes.ACC_INTERFACE) != 0) interfaceClasses += name
            if (interfaces != null) {
                for (iface in interfaces) {
                    if (iface in ENTRYPOINT_INTERFACES) entrypoints += name
                    if (iface in CUSTOM_PAYLOAD_INTERFACES) customPayloadOwners += name
                }
            }
        }

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            if (descriptor in MOD_ANNOTATIONS) modClasses += className
            if (descriptor in EVENT_BUS_SUBSCRIBER_ANNOTATIONS) eventBusSubs += className
            if (descriptor in MIXIN_CLASS_ANNOTATIONS) mixinClasses += className
            return null
        }

        override fun visitField(access: Int, name: String?, descriptor: String?, signature: String?, value: Any?): FieldVisitor {
            if (name == "CODEC" || name == "STREAM_CODEC" || name == "TYPE" || name == "MAP_CODEC") {
                codecOwners += className
            }
            return object : FieldVisitor(ASM_API) {
                override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                    if (descriptor in SHADOW_ANNOTATIONS) shadowOwners += className
                    if (descriptor in OBJECT_HOLDER_ANNOTATIONS) objectHolderOwners += className
                    if (descriptor in CAPABILITY_INJECT_ANNOTATIONS) capabilityInjectOwners += className
                    return null
                }
            }
        }

        override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
            val methodName = name ?: ""
            val methodDesc = descriptor ?: ""
            return object : MethodVisitor(ASM_API) {
                override fun visitAnnotation(annDesc: String?, visible: Boolean): AnnotationVisitor? {
                    if (annDesc in SHADOW_ANNOTATIONS) shadowOwners += className
                    if (annDesc in SUBSCRIBE_EVENT_ANNOTATIONS) subscribeEventOwners += className
                    if (annDesc in ACCESSOR_ANNOTATIONS) {
                        accessorOwners += className
                        val capturedClass = className
                        return object : AnnotationVisitor(ASM_API) {
                            var hasExplicitValue = false
                            override fun visit(name: String?, value: Any?) {
                                if (name == "value" && value is String && value.isNotEmpty()) {
                                    hasExplicitValue = true
                                }
                            }
                            override fun visitEnd() {
                                if (!hasExplicitValue) {
                                    unnamedAccessorMethods += AccessorMethod(capturedClass, methodName, methodDesc)
                                }
                            }
                        }
                    }
                    return null
                }
            }
        }
    }
}
