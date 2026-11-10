package dev.prism.gradle.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class MixinJsonRewriterTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun restoresTheManifestAndRenamesRefmapKeys() {
        val jar = File(directory, "mod.jar")
        JarOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(JarEntry("example.mixins.json"))
            out.write("""{"package": "com.example.mixin", "refmap": "example.refmap.json", "mixins": ["FooMixin"]}""".toByteArray())
            out.closeEntry()
            out.putNextEntry(JarEntry("example.refmap.json"))
            out.write("""{"mappings": {"com/example/mixin/FooMixin": {"getState": "m_49966_"}}, "data": {"searge": {"com/example/mixin/FooMixin": {"getState": "m_49966_"}}}}""".toByteArray())
            out.closeEntry()
        }
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes.putValue("MixinConfigs", "example.mixins.json")
        MixinJsonRewriter.rewriteInPlace(jar, ProguardMapping.Mapping(mapOf("com.example.mixin.FooMixin" to "com.example.obf.a")), manifest)
        JarFile(jar).use { rewritten ->
            assertEquals("example.mixins.json", rewritten.manifest.mainAttributes.getValue("MixinConfigs"))
            val refmap = rewritten.getInputStream(rewritten.getJarEntry("example.refmap.json")).use { it.readBytes().toString(Charsets.UTF_8) }
            assertFalse(refmap.contains("FooMixin"), refmap)
            assertEquals(2, Regex("\"com/example/obf/a\":").findAll(refmap).count(), refmap)
            assertTrue(refmap.contains("\"getState\": \"m_49966_\""), refmap)
        }
    }
}
