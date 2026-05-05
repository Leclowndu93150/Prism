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
import java.util.jar.JarFile

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
            exec.doFirst {
                val jarFile = primaryJarOutput(targetTask) ?: error("Prism: cannot resolve output jar for ${targetTask.name}")
                if (!jarFile.exists()) error("Prism: target jar does not exist: $jarFile")
                jarFileHolder[0] = jarFile
                val outDir = project.layout.buildDirectory.dir("prism/obf").get().asFile
                outDir.mkdirs()
                val tempOut = File(outDir, jarFile.name)
                if (tempOut.exists()) tempOut.delete()
                tempOutHolder[0] = tempOut
                val configFile = writeProguardConfig(project, jarFile, tempOut, outDir, loaderConfig, metadata, options)
                exec.args = listOf("@${configFile.absolutePath}")
            }
            exec.doLast {
                val jarFile = jarFileHolder[0] ?: return@doLast
                val tempOut = tempOutHolder[0] ?: return@doLast
                if (tempOut.exists()) {
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
        loaderConfig: LoaderConfiguration,
        metadata: MetadataExtension,
        options: ObfuscationOptions,
    ): File {
        val configFile = File(outDir, "${jarFile.nameWithoutExtension}.pro")
        val mappingFile = File(outDir, "${jarFile.nameWithoutExtension}.map")

        val libraryJars = collectLibraryJars(project)
        val scan = JarScanner.scan(jarFile)
        val keeps = buildKeepRules(scan, options)

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

    private fun collectLibraryJars(project: Project): List<File> {
        val result = mutableListOf<File>()
        val javaHome = System.getProperty("java.home")
        val jmodsDir = File(javaHome, "jmods")
        if (jmodsDir.isDirectory) {
            jmodsDir.listFiles { f -> f.extension == "jmod" }?.forEach(result::add)
        } else {
            val rt = File(javaHome, "lib/rt.jar")
            if (rt.isFile) result.add(rt)
        }
        val configsToScan = listOf("compileClasspath", "runtimeClasspath")
        for (name in configsToScan) {
            val cfg = project.configurations.findByName(name) ?: continue
            try {
                cfg.resolve().forEach { if (it.isFile && it.extension == "jar") result.add(it) }
            } catch (_: Throwable) {
            }
        }
        return result.distinct()
    }

    private fun buildKeepRules(scan: JarScanner.ScanResult, options: ObfuscationOptions): List<String> {
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
            rules += "-keep class ${dotted(cls)} { *; }"
        }
        for (cls in scan.shadowOwners) {
            rules += "-keepclassmembers class ${dotted(cls)} { *; }"
        }
        for (cls in scan.accessorOwners) {
            rules += "-keep,allowoptimization,allowshrinking interface ${dotted(cls)} { *; }"
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
        rules += "-keepclassmembers class * { @org.spongepowered.asm.mixin.gen.Accessor <methods>; @org.spongepowered.asm.mixin.gen.Invoker <methods>; }"
        rules += "-keep class * implements org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin { *; }"
        rules += "-keep @org.spongepowered.asm.mixin.Mixin class * { *; }"

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
    ) : ClassVisitor(ASM_API) {
        var className: String = ""

        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            className = name
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
            return object : MethodVisitor(ASM_API) {
                override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                    if (descriptor in SHADOW_ANNOTATIONS) shadowOwners += className
                    if (descriptor in ACCESSOR_ANNOTATIONS) accessorOwners += className
                    if (descriptor in SUBSCRIBE_EVENT_ANNOTATIONS) subscribeEventOwners += className
                    return null
                }
            }
        }
    }
}
